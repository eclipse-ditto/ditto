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
package org.eclipse.ditto.internal.utils.akka.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link DefaultDiagnosticLoggingAdapter}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultDiagnosticLoggingAdapterTest {

    private static final String CORRELATION_ID_KEY = CommonMdcEntryKey.CORRELATION_ID.toString();
    private static final String CONNECTION_ID_KEY = "connection-id";
    private static final String CONNECTION_ID_VALUE = "my-connection";
    private static final String LOGGER_NAME = DefaultDiagnosticLoggingAdapterTest.class.getName();

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock
    private DiagnosticLoggingAdapter plainLoggingAdapter;

    @Test
    public void getInstanceWithNullLogger() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultDiagnosticLoggingAdapter.of(null, LOGGER_NAME))
                .withMessage("The loggingAdapter must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithNullLoggerName() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultDiagnosticLoggingAdapter.of(plainLoggingAdapter, null))
                .withMessage("The loggerName must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithBlankLoggerName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DefaultDiagnosticLoggingAdapter.of(plainLoggingAdapter, ""))
                .withMessage("The argument 'loggerName' must not be empty!")
                .withNoCause();
    }

    @Test
    public void getNameReturnsExpected() {
        final DefaultDiagnosticLoggingAdapter underTest =
                DefaultDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);

        assertThat(underTest.getName()).isEqualTo(LOGGER_NAME);
    }

    @Test
    public void putTwoMdcEntriesLogDebugAndWarnThenDiscardMdcEntries() {
        final String correlationId = getCorrelationId();
        final String debugMsg = "Foo!";
        final String warnMsg = "Behold!";
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        Mockito.when(plainLoggingAdapter.isWarningEnabled()).thenReturn(true);

        final DefaultDiagnosticLoggingAdapter underTest =
                DefaultDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        underTest.debug(debugMsg);
        underTest.warning(warnMsg);
        underTest.discardMdcEntries();

        Mockito.verify(plainLoggingAdapter).notifyDebug(debugMsg);
        Mockito.verify(plainLoggingAdapter).notifyWarning(warnMsg);
        Mockito.verify(plainLoggingAdapter, Mockito.times(2))
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void removeMdcEntryViaKey() {
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();

        final DefaultDiagnosticLoggingAdapter underTest =
                DefaultDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        underTest.debug("Foo");
        underTest.removeMdcEntry(CONNECTION_ID_KEY);
        underTest.debug("Bar");

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
    }

    @Test
    public void removeMdcEntryViaNullValue() {
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();

        final DefaultDiagnosticLoggingAdapter underTest =
                DefaultDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        underTest.debug("Foo");
        underTest.putMdcEntry(CORRELATION_ID_KEY, null);
        underTest.debug("Bar");

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
    }

    private String getCorrelationId() {
        return testName.getMethodName();
    }

}
