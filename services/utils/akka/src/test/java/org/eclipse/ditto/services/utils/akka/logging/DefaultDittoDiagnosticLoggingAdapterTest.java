/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.akka.logging;

import java.time.Duration;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit tests for {@link DefaultDittoDiagnosticLoggingAdapter}.
 */
public final class DefaultDittoDiagnosticLoggingAdapterTest {

    private DiagnosticLoggingAdapter log;
    private DefaultDittoDiagnosticLoggingAdapter loggingAdapter;

    @Before
    public void setup() {
        log = Mockito.mock(DiagnosticLoggingAdapter.class);
        Mockito.when(log.isErrorEnabled()).thenReturn(true);
        Mockito.when(log.isWarningEnabled()).thenReturn(true);
        Mockito.when(log.isInfoEnabled()).thenReturn(true);
        Mockito.when(log.isDebugEnabled()).thenReturn(true);
        loggingAdapter = DefaultDittoDiagnosticLoggingAdapter.of(log);
    }

    @Test
    public void logMoreThan4LoggingArgsError() {
        final String template = "one: {}, two: {}, three: {}, four: {}, five: {}, six: {}";
        loggingAdapter.error(template,
                "one",
                "two",
                "three",
                "four",
                "five",
                "six");
        Mockito.verify(log, Mockito.atLeastOnce()).isErrorEnabled();
        Mockito.verify(log).error(template, new Object[]{
                "one",
                "two",
                "three",
                "four",
                "five",
                "six"
        });
    }

    @Test
    public void logMoreThan4LoggingArgsWarning() {
        final String template = "one: {}, two: {}, three: {}, four: {}, five: {}, six: {}, seven: {}";
        final Duration oneSecond = Duration.ofSeconds(1);
        final Duration twoSeconds = Duration.ofSeconds(2);
        final Duration threeSeconds = Duration.ofSeconds(3);
        final Duration fourSeconds = Duration.ofSeconds(4);
        final Duration fiveSeconds = Duration.ofSeconds(5);
        final Duration sixSeconds = Duration.ofSeconds(6);
        final Duration sevenSeconds = Duration.ofSeconds(7);
        loggingAdapter.warning(template,
                oneSecond,
                twoSeconds,
                threeSeconds,
                fourSeconds,
                fiveSeconds,
                sixSeconds,
                sevenSeconds);
        Mockito.verify(log, Mockito.atLeastOnce()).isWarningEnabled();
        Mockito.verify(log).warning(template, new Object[]{
                oneSecond,
                twoSeconds,
                threeSeconds,
                fourSeconds,
                fiveSeconds,
                sixSeconds,
                sevenSeconds
        });
    }

    @Test
    public void logMoreThan4LoggingArgsInfo() {
        final String template = "one: {}, two: {}, three: {}, four: {}, five: {}";
        loggingAdapter.info(template,
                1,
                2,
                3,
                4,
                5);
        Mockito.verify(log, Mockito.atLeastOnce()).isInfoEnabled();
        Mockito.verify(log).info(template, new Object[]{
                1,
                2,
                3,
                4,
                5
        });
    }

    @Test
    public void logMoreThan4LoggingArgsDebug() {
        final String template = "one: {}, two: {}, three: {}, four: {}, five: {}";
        loggingAdapter.debug(template,
                1,
                2,
                3,
                4,
                "foobar");
        Mockito.verify(log, Mockito.atLeastOnce()).isDebugEnabled();
        Mockito.verify(log).debug(template, new Object[]{
                1,
                2,
                3,
                4,
                "foobar"
        });
    }

}