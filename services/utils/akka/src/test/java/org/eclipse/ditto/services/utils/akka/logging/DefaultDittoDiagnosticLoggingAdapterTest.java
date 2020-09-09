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
 * Unit test for {@link DefaultDittoDiagnosticLoggingAdapter}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultDittoDiagnosticLoggingAdapterTest {

    private static final String CORRELATION_ID_KEY = CommonMdcEntryKey.CORRELATION_ID.toString();
    private static final String CONNECTION_ID_KEY = "connection-id";
    private static final String CONNECTION_ID_VALUE = "my-connection";

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock
    private DiagnosticLoggingAdapter plainLoggingAdapter;

    @Test
    public void getInstanceWithNullLogger() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultDittoDiagnosticLoggingAdapter.of(null))
                .withMessage("The loggingAdapter must not be null!")
                .withNoCause();
    }

    @Test
    public void withCorrelationIdLogInfo() {
        final String correlationId = getCorrelationId();
        final String msg1 = "Foo!";
        final String msg2 = "Bar!";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.withCorrelationId(correlationId);
        underTest.info(msg1);
        underTest.info(msg2);

        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
        Mockito.verify(plainLoggingAdapter).info(msg1);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
        Mockito.verify(plainLoggingAdapter).info(msg2);
    }

    @Test
    public void withMdcEntryLogDebug() {
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.withMdcEntry(CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        underTest.debug(msg);

        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).debug(msg);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void withTwoMdcEntryPairsLogDebug() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        underTest.debug(msg);

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).debug(msg);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void withThreeMdcEntryPairsLogDebug() {
        final String correlationId = getCorrelationId();
        final String k3 = "plumbus";
        final String v3 = "fleeb";
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE, k3, v3);
        underTest.debug(msg);

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE, k3, v3));
        Mockito.verify(plainLoggingAdapter).debug(msg);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void withTwoMdcEntriesLogWarning() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isWarningEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.withMdcEntry(MdcEntry.of(CORRELATION_ID_KEY, correlationId),
                MdcEntry.of(CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        underTest.warning(msg);

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).warning(msg);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void putNothingToMdcAndDoNotLogAsInfoIsDisabled() {
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(false);

        DefaultDittoDiagnosticLoggingAdapter underTest = DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.setCorrelationId(getCorrelationId());
        underTest.info(msg);

        Mockito.verify(plainLoggingAdapter, Mockito.times(0)).info(msg);
        Mockito.verify(plainLoggingAdapter, Mockito.times(0)).setMDC(Mockito.anyMap());
    }

    @Test
    public void logDebugAndWarnWithTwoMdcValuesThenDiscardMdcEntries() {
        final String correlationId = getCorrelationId();
        final String connectionId = "my-connection";
        final String debugMsg = "Foo!";
        final String warnMsg = "Behold!";
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        Mockito.when(plainLoggingAdapter.isWarningEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest = DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, connectionId);
        underTest.debug(debugMsg);
        underTest.warning(warnMsg);
        underTest.discardMdcEntries();

        Mockito.verify(plainLoggingAdapter).debug(debugMsg);
        Mockito.verify(plainLoggingAdapter).warning(warnMsg);
        Mockito.verify(plainLoggingAdapter, Mockito.times(2))
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId));
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void removeMdcEntryViaKey() {
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();
        final String connectionId = "my-connection";

        final DefaultDittoDiagnosticLoggingAdapter underTest = DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, connectionId);
        underTest.debug("Foo");
        underTest.removeMdcEntry(CONNECTION_ID_KEY);
        underTest.debug("Bar");

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId));
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
    }

    @Test
    public void removeMdcEntryViaNullValue() {
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();
        final String connectionId = "my-connection";

        final DefaultDittoDiagnosticLoggingAdapter underTest = DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, connectionId);
        underTest.debug("Foo");
        underTest.putMdcEntry(CORRELATION_ID_KEY, null);
        underTest.debug("Bar");

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId));
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CONNECTION_ID_KEY, connectionId));
    }

    @Test
    public void setCorrelationIdLogErrorDoNotClose() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isErrorEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest = DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.setCorrelationId(correlationId);
        underTest.error(msg);

        Mockito.verify(plainLoggingAdapter).isErrorEnabled();
        Mockito.verify(plainLoggingAdapter).getMDC();
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
        Mockito.verify(plainLoggingAdapter).error(msg);
        Mockito.verifyNoMoreInteractions(plainLoggingAdapter);
    }

    @Test
    public void setCorrelationIdLogInfoThenDiscardCorrelationId() {
        final String correlationId = getCorrelationId();
        final String msg1 = "Foo!";
        final String msg2 = "No correlation ID in MDC.";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest = DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest.setCorrelationId(correlationId);
        underTest.info(msg1);
        underTest.discardCorrelationId();
        underTest.info(msg2);

        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
        Mockito.verify(plainLoggingAdapter).info(msg1);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
        Mockito.verify(plainLoggingAdapter).info(msg2);
    }

    private String getCorrelationId() {
        return testName.getMethodName();
    }

}
