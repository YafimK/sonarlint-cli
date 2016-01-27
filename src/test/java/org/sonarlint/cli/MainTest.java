/*
 * SonarLint CLI
 * Copyright (C) 2016 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.cli;

import org.junit.Before;
import org.junit.Test;
import org.sonarlint.cli.report.ReportFactory;
import org.sonarlint.cli.util.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class MainTest {
  private Main main;
  private SonarLint sonarLint;
  private ReportFactory reportFactory;
  private Options opts;
  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  @Before
  public void setUp() {
    opts = mock(Options.class);
    reportFactory = mock(ReportFactory.class);
    when(opts.properties()).thenReturn(new Properties());
    createLogger();
    sonarLint = mock(SonarLint.class);
    main = new Main(opts, Logger.get(), sonarLint, reportFactory);
  }

  private Logger createLogger() {
    out = new ByteArrayOutputStream();
    PrintStream outStream = new PrintStream(out);
    err = new ByteArrayOutputStream();
    PrintStream errStream = new PrintStream(err);
    Logger.set(outStream, errStream);
    return Logger.get();
  }

  private String getLogs(ByteArrayOutputStream stream) {
    return new String(stream.toByteArray(), StandardCharsets.UTF_8);
  }

  @Test
  public void testMain() {
    assertThat(main.run()).isEqualTo(Main.SUCCESS);

    verify(sonarLint).validate(any(Properties.class));
    verify(sonarLint).setDefaults(any(Properties.class));
    verify(sonarLint).start(any(Properties.class));
    verify(sonarLint).stop();
  }

  @Test
  public void exitOnHelp() {
    when(opts.isHelp()).thenReturn(true);
    assertThat(main.run()).isEqualTo(Main.SUCCESS);
    verifyZeroInteractions(sonarLint);
  }

  @Test
  public void exitOnVersion() {
    when(opts.isVersion()).thenReturn(true);
    assertThat(main.run()).isEqualTo(Main.SUCCESS);
    verifyZeroInteractions(sonarLint);
  }

  @Test
  public void errorStart() {
    Exception e = createException("invalid operation", "analysis failed");
    doThrow(e).when(sonarLint).start(any(Properties.class));
    assertThat(main.run()).isEqualTo(Main.ERROR);
    assertThat(getLogs(out)).contains("EXECUTION FAILURE");
    assertThat(getLogs(err)).contains("invalid operation");
  }

  @Test
  public void runInteractive() throws IOException, InterruptedException {
    when(opts.isInteractive()).thenReturn(true);
    PipedOutputStream out = new PipedOutputStream();
    OutputStreamWriter writter = new OutputStreamWriter(out);
    PipedInputStream in = new PipedInputStream(out);

    final AtomicInteger mutableInt = new AtomicInteger(Main.ERROR);
    main.setIn(in);

    Thread t = new Thread() {
      @Override
      public void run() {
        mutableInt.set(main.run());
      }
    };
    t.start();

    writter.write(System.lineSeparator());
    writter.close();
    t.join(20000);

    assertThat(mutableInt.get()).isEqualTo(Main.SUCCESS);
    verify(sonarLint, times(1)).stop();
    verify(sonarLint, times(2)).runAnalysis(any(Properties.class), eq(reportFactory));
  }

  @Test
  public void verbose() {
    assertThat(main.run()).isEqualTo(Main.SUCCESS);
    assertThat(Logger.get().isDebugEnabled()).isFalse();

    when(opts.isVerbose()).thenReturn(true);
    assertThat(main.run()).isEqualTo(Main.SUCCESS);
    assertThat(Logger.get().isDebugEnabled()).isTrue();
  }

  @Test
  public void errorStop() {
    Exception e = createException("invalid operation", "analysis failed");
    doThrow(e).when(sonarLint).stop();
    assertThat(main.run()).isEqualTo(Main.ERROR);
    assertThat(getLogs(out)).contains("EXECUTION FAILURE");
    assertThat(getLogs(err)).contains("invalid operation");
  }

  @Test
  public void errorAnalysis() {
    Exception e = createException("invalid operation", "analysis failed");
    doThrow(e).when(sonarLint).runAnalysis(any(Properties.class), eq(reportFactory));
    assertThat(main.run()).isEqualTo(Main.ERROR);
    assertThat(getLogs(out)).contains("EXECUTION FAILURE");
    assertThat(getLogs(err)).contains("invalid operation");
  }

  @Test
  public void showStack() {
    when(opts.showStack()).thenReturn(true);
    Exception e = createException("invalid operation", "analysis failed");
    doThrow(e).when(sonarLint).start(any(Properties.class));
    assertThat(main.run()).isEqualTo(Main.ERROR);
    assertThat(getLogs(out)).contains("EXECUTION FAILURE");
    assertThat(getLogs(err)).contains("invalid operation");
    assertThat(getLogs(err)).contains("analysis failed");
  }

  public Exception createException(String firstMsg, String secondMsg) {
    Exception wrapped = new NullPointerException(firstMsg);
    return new IllegalStateException(secondMsg, wrapped);
  }
}