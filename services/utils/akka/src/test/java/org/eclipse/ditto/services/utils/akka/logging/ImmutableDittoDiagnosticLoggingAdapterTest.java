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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link ImmutableDittoDiagnosticLoggingAdapter}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableDittoDiagnosticLoggingAdapterTest {

    private static final String CORRELATION_ID_KEY = CommonMdcEntryKey.CORRELATION_ID.toString();
    private static final String CONNECTION_ID_KEY = "connection-id";
    private static final String CONNECTION_ID_VALUE = "my-connection";

    @Rule
    public final TestName testName = new TestName();

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
    public void withSameCorrelationIdReturnsSameInstance() {
        final String correlationId = getCorrelationId();

        ImmutableDittoDiagnosticLoggingAdapter underTest =
                ImmutableDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest = underTest.withCorrelationId(correlationId);
        final ImmutableDittoDiagnosticLoggingAdapter secondLogger = underTest.withCorrelationId(correlationId);

        assertThat(secondLogger).isSameAs(underTest);
    }

    @Test
    public void withCorrelationIdLogInfo() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(true);

        ImmutableDittoDiagnosticLoggingAdapter underTest =
                ImmutableDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest = underTest.withCorrelationId(correlationId);
        underTest.info(msg);

        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
        Mockito.verify(plainLoggingAdapter).notifyInfo(msg);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void putNothingToMdcAndDoNotLogAsInfoIsDisabled() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(false);

        ImmutableDittoDiagnosticLoggingAdapter underTest =
                ImmutableDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest = underTest.withCorrelationId(correlationId);
        underTest.info(msg);

        Mockito.verify(plainLoggingAdapter, Mockito.times(0)).notifyInfo(msg);
        Mockito.verify(plainLoggingAdapter, Mockito.times(0)).setMDC(Mockito.anyMap());
    }

    @Test
    public void withNullCorrelationIdLogInfo() {
        final String correlationId = null;
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(true);

        ImmutableDittoDiagnosticLoggingAdapter underTest =
                ImmutableDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest = underTest.withCorrelationId(correlationId);
        underTest.info(msg);

        Mockito.verify(plainLoggingAdapter).isInfoEnabled();
        Mockito.verify(plainLoggingAdapter).notifyInfo(msg);
        Mockito.verify(plainLoggingAdapter, Mockito.times(0)).setMDC(Mockito.anyMap());
    }

    @Test
    public void withTwoMdcEntryPairsLogDebugInfoWarningAndError() {
        final String correlationId = getCorrelationId();
        final String debugMsg = "BugMeNot, {}!";
        final String infoMsg = "Foo!";
        final String warnMsg = "Bar!";
        final String errorMsg = "Baz!";
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(true);
        Mockito.when(plainLoggingAdapter.isWarningEnabled()).thenReturn(true);
        Mockito.when(plainLoggingAdapter.isErrorEnabled()).thenReturn(true);

        ImmutableDittoDiagnosticLoggingAdapter underTest =
                ImmutableDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        underTest = underTest.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        underTest.debug(debugMsg, "please");
        underTest.info(infoMsg);
        underTest.warning(warnMsg);
        underTest.error(errorMsg);

        Mockito.verify(plainLoggingAdapter).notifyDebug("BugMeNot, please!");
        Mockito.verify(plainLoggingAdapter).notifyInfo(infoMsg);
        Mockito.verify(plainLoggingAdapter).notifyWarning(warnMsg);
        Mockito.verify(plainLoggingAdapter).notifyError(errorMsg);
        Mockito.verify(plainLoggingAdapter, Mockito.times(4))
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter, Mockito.times(4)).setMDC(Map.of());
    }

    @Test
    public void withTwoMdcEntriesLogError() {
        Mockito.when(plainLoggingAdapter.isErrorEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();
        final IllegalStateException illegalStateException = new IllegalStateException("connection unavailable");
        final String logMessage = "The connection is closed!";

        final ImmutableDittoDiagnosticLoggingAdapter initialLogger =
                ImmutableDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        final ImmutableDittoDiagnosticLoggingAdapter underTest =
                initialLogger.withMdcEntry(MdcEntry.of(CORRELATION_ID_KEY, correlationId),
                        MdcEntry.of(CONNECTION_ID_KEY, CONNECTION_ID_VALUE));

        underTest.error(illegalStateException, logMessage);

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).notifyError(illegalStateException, logMessage);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void removeCorrelationIdViaNullValue() {
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();

        final ImmutableDittoDiagnosticLoggingAdapter initialLogger =
                ImmutableDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        final ImmutableDittoDiagnosticLoggingAdapter withTwoMdcEntries =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        withTwoMdcEntries.info("Foo");
        final ImmutableDittoDiagnosticLoggingAdapter withOneMdcEntry =
                withTwoMdcEntries.withMdcEntries(CORRELATION_ID_KEY, null, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        withOneMdcEntry.info("Bar");

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter, Mockito.times(2)).setMDC(Map.of());
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
    }

    @Test
    public void removeMdcEntryViaKey() {
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();

        final ImmutableDittoDiagnosticLoggingAdapter initialLogger =
                ImmutableDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter);
        final ImmutableDittoDiagnosticLoggingAdapter withTwoMdcEntries =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        withTwoMdcEntries.debug("Foo");
        final ImmutableDittoDiagnosticLoggingAdapter withOneMdcEntry =
                withTwoMdcEntries.removeMdcEntry(CONNECTION_ID_KEY);
        withOneMdcEntry.debug("Bar");

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter, Mockito.times(2)).setMDC(Map.of());
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
    }

    private String getCorrelationId() {
        return testName.getMethodName();
    }

}
