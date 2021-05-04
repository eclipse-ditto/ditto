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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link ImmutableDittoLoggingAdapter}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableDittoLoggingAdapterTest {

    private static final String CORRELATION_ID_KEY = CommonMdcEntryKey.CORRELATION_ID.toString();
    private static final String CONNECTION_ID_KEY = "connection-id";
    private static final String CONNECTION_ID_VALUE = "my-connection";

    @Rule
    public final TestName testName = new TestName();

    @Mock
    private DiagnosticLoggingAdapter loggingAdapterWithNonEmptyMdc;

    @Mock
    private DiagnosticLoggingAdapter loggingAdapterWithEmptyMdc;

    @Mock
    private Supplier<DiagnosticLoggingAdapter> diagnosticLoggingAdapterFactory;

    @Before
    public void setUp() {
        Mockito.when(loggingAdapterWithEmptyMdc.getMDC()).thenReturn(new HashMap<>(5));
    }

    @After
    public void tearDown() {
        Mockito.verify(loggingAdapterWithEmptyMdc, Mockito.times(0)).setMDC(Mockito.anyMap());
    }

    @Test
    public void getInstanceWithNullDiagnosticLoggingAdapterFactory() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableDittoLoggingAdapter.of(null))
                .withMessage("The diagnosticLoggingAdapterFactory must not be null!")
                .withNoCause();
    }

    @Test
    public void withSameCorrelationIdReturnsSameInstance() {
        final String correlationId = getCorrelationId();
        final Map<String, Object> mdcWithCorrelationId = Map.of(CORRELATION_ID_KEY, correlationId);
        Mockito.when(diagnosticLoggingAdapterFactory.get())
                .thenReturn(loggingAdapterWithEmptyMdc)
                .thenReturn(loggingAdapterWithNonEmptyMdc);
        Mockito.when(loggingAdapterWithNonEmptyMdc.getMDC()).thenReturn(new HashMap<>(mdcWithCorrelationId));

        ImmutableDittoLoggingAdapter underTest = ImmutableDittoLoggingAdapter.of(diagnosticLoggingAdapterFactory);
        underTest = underTest.withCorrelationId(correlationId);
        final ImmutableDittoLoggingAdapter secondLogger = underTest.withCorrelationId(correlationId);

        Mockito.verify(loggingAdapterWithNonEmptyMdc).setMDC(mdcWithCorrelationId);
        assertThat(secondLogger).isSameAs(underTest);
    }

    @Test
    public void withCorrelationIdLogInfo() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(diagnosticLoggingAdapterFactory.get())
                .thenReturn(loggingAdapterWithEmptyMdc)
                .thenReturn(loggingAdapterWithNonEmptyMdc);
        Mockito.when(loggingAdapterWithNonEmptyMdc.isInfoEnabled()).thenReturn(true);

        ImmutableDittoLoggingAdapter underTest = ImmutableDittoLoggingAdapter.of(diagnosticLoggingAdapterFactory);
        underTest = underTest.withCorrelationId(correlationId);
        underTest.info(msg);

        Mockito.verify(loggingAdapterWithNonEmptyMdc).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
        Mockito.verify(loggingAdapterWithNonEmptyMdc).notifyInfo(msg);
    }

    @Test
    public void doNotLogAsInfoIsDisabled() {
        final String correlationId = getCorrelationId();
        final Map<String, Object> mdcWithCorrelationId = Map.of(CORRELATION_ID_KEY, correlationId);
        final String msg = "Foo!";
        Mockito.when(diagnosticLoggingAdapterFactory.get())
                .thenReturn(loggingAdapterWithEmptyMdc)
                .thenReturn(loggingAdapterWithNonEmptyMdc);
        Mockito.when(loggingAdapterWithNonEmptyMdc.isInfoEnabled()).thenReturn(false);
        Mockito.when(loggingAdapterWithNonEmptyMdc.getMDC()).thenReturn(new HashMap<>(mdcWithCorrelationId));

        ImmutableDittoLoggingAdapter underTest = ImmutableDittoLoggingAdapter.of(diagnosticLoggingAdapterFactory);
        underTest = underTest.withCorrelationId(correlationId);
        underTest.info(msg);

        Mockito.verify(loggingAdapterWithNonEmptyMdc, Mockito.times(0)).notifyInfo(msg);
        Mockito.verify(loggingAdapterWithNonEmptyMdc).setMDC(mdcWithCorrelationId);
    }

    @Test
    public void withNullCorrelationIdLogInfo() {
        final String correlationId = null;
        final String msg = "Foo!";
        Mockito.when(diagnosticLoggingAdapterFactory.get())
                .thenReturn(loggingAdapterWithEmptyMdc)
                .thenReturn(loggingAdapterWithNonEmptyMdc);
        Mockito.when(loggingAdapterWithEmptyMdc.isInfoEnabled()).thenReturn(true);

        ImmutableDittoLoggingAdapter underTest = ImmutableDittoLoggingAdapter.of(diagnosticLoggingAdapterFactory);
        underTest = underTest.withCorrelationId(correlationId);
        underTest.info(msg);

        Mockito.verify(loggingAdapterWithEmptyMdc).isInfoEnabled();
        Mockito.verify(loggingAdapterWithEmptyMdc).notifyInfo(msg);
        Mockito.verify(loggingAdapterWithEmptyMdc, Mockito.times(0)).setMDC(Mockito.anyMap());
        Mockito.verifyNoInteractions(loggingAdapterWithNonEmptyMdc);
    }

    @Test
    public void withTwoMdcEntryPairsLogDebugInfoWarningAndError() {
        final String correlationId = getCorrelationId();
        final Map<String, Object> mdcWithTwoEntries =
                Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        final String debugMsg = "BugMeNot, {}!";
        final String infoMsg = "Foo!";
        final String warnMsg = "Bar!";
        final String errorMsg = "Baz!";
        Mockito.when(diagnosticLoggingAdapterFactory.get())
                .thenReturn(loggingAdapterWithEmptyMdc)
                .thenReturn(loggingAdapterWithNonEmptyMdc);
        Mockito.when(loggingAdapterWithNonEmptyMdc.isDebugEnabled()).thenReturn(true);
        Mockito.when(loggingAdapterWithNonEmptyMdc.isInfoEnabled()).thenReturn(true);
        Mockito.when(loggingAdapterWithNonEmptyMdc.isWarningEnabled()).thenReturn(true);
        Mockito.when(loggingAdapterWithNonEmptyMdc.isErrorEnabled()).thenReturn(true);
        Mockito.when(loggingAdapterWithNonEmptyMdc.getMDC()).thenReturn(new HashMap<>(mdcWithTwoEntries));

        ImmutableDittoLoggingAdapter underTest = ImmutableDittoLoggingAdapter.of(diagnosticLoggingAdapterFactory);
        underTest = underTest.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        underTest.debug(debugMsg, "please");
        underTest.info(infoMsg);
        underTest.warning(warnMsg);
        underTest.error(errorMsg);

        Mockito.verify(loggingAdapterWithNonEmptyMdc).setMDC(mdcWithTwoEntries);
        Mockito.verify(loggingAdapterWithNonEmptyMdc).notifyDebug("BugMeNot, please!");
        Mockito.verify(loggingAdapterWithNonEmptyMdc).notifyInfo(infoMsg);
        Mockito.verify(loggingAdapterWithNonEmptyMdc).notifyWarning(warnMsg);
        Mockito.verify(loggingAdapterWithNonEmptyMdc).notifyError(errorMsg);
    }

    @Test
    public void withTwoMdcEntriesLogError() {
        final String correlationId = getCorrelationId();
        final Map<String, Object> mdcWithTwoEntries =
                Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        final IllegalStateException illegalStateException = new IllegalStateException("connection unavailable");
        final String logMessage = "The connection is closed!";
        Mockito.when(diagnosticLoggingAdapterFactory.get())
                .thenReturn(loggingAdapterWithEmptyMdc)
                .thenReturn(loggingAdapterWithNonEmptyMdc);
        Mockito.when(loggingAdapterWithNonEmptyMdc.isErrorEnabled()).thenReturn(true);
        Mockito.when(loggingAdapterWithNonEmptyMdc.getMDC()).thenReturn(new HashMap<>(mdcWithTwoEntries));

        final ImmutableDittoLoggingAdapter initialLogger =
                ImmutableDittoLoggingAdapter.of(diagnosticLoggingAdapterFactory);
        final ImmutableDittoLoggingAdapter underTest =
                initialLogger.withMdcEntry(MdcEntry.of(CORRELATION_ID_KEY, correlationId),
                        MdcEntry.of(CONNECTION_ID_KEY, CONNECTION_ID_VALUE));

        underTest.error(illegalStateException, logMessage);

        Mockito.verify(diagnosticLoggingAdapterFactory, Mockito.times(2)).get();
        Mockito.verify(loggingAdapterWithNonEmptyMdc).setMDC(mdcWithTwoEntries);
        Mockito.verify(loggingAdapterWithNonEmptyMdc).notifyError(illegalStateException, logMessage);
    }

    @Test
    public void removeCorrelationIdViaNullValue() {
        final String correlationId = getCorrelationId();
        final Map<String, Object> mdcOfLoggerWithTwoMdcEntries =
                Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        final Map<String, Object> mdcOfLoggerWithOneMdcEntry = Map.of(CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        final DiagnosticLoggingAdapter loggingAdapterWithTwoMdcEntries = Mockito.mock(DiagnosticLoggingAdapter.class);
        final DiagnosticLoggingAdapter loggingAdapterWithOneMdcEntry = Mockito.mock(DiagnosticLoggingAdapter.class);
        Mockito.when(diagnosticLoggingAdapterFactory.get())
                .thenReturn(loggingAdapterWithEmptyMdc)
                .thenReturn(loggingAdapterWithTwoMdcEntries)
                .thenReturn(loggingAdapterWithOneMdcEntry);
        Mockito.when(loggingAdapterWithTwoMdcEntries.isInfoEnabled()).thenReturn(true);
        Mockito.when(loggingAdapterWithOneMdcEntry.isInfoEnabled()).thenReturn(true);
        Mockito.when(loggingAdapterWithTwoMdcEntries.getMDC()).thenReturn(new HashMap<>(mdcOfLoggerWithTwoMdcEntries));
        Mockito.when(loggingAdapterWithOneMdcEntry.getMDC()).thenReturn(new HashMap<>(mdcOfLoggerWithOneMdcEntry));

        final ImmutableDittoLoggingAdapter initialLogger =
                ImmutableDittoLoggingAdapter.of(diagnosticLoggingAdapterFactory);
        final ImmutableDittoLoggingAdapter withTwoMdcEntries =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        withTwoMdcEntries.info("Foo");
        final ImmutableDittoLoggingAdapter withOneMdcEntry =
                withTwoMdcEntries.withMdcEntries(CORRELATION_ID_KEY, null, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        withOneMdcEntry.info("Bar");

        Mockito.verify(diagnosticLoggingAdapterFactory, Mockito.times(3)).get();
        Mockito.verify(loggingAdapterWithTwoMdcEntries).setMDC(mdcOfLoggerWithTwoMdcEntries);
        Mockito.verify(loggingAdapterWithTwoMdcEntries).notifyInfo("Foo");
        Mockito.verify(loggingAdapterWithOneMdcEntry).setMDC(mdcOfLoggerWithOneMdcEntry);
        Mockito.verify(loggingAdapterWithOneMdcEntry).notifyInfo("Bar");
    }

    @Test
    public void removeMdcEntryViaKey() {
        final String correlationId = getCorrelationId();
        final Map<String, Object> mdcOfLoggerWithTwoMdcEntries =
                Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        final Map<String, Object> mdcOfLoggerWithOneMdcEntry = Map.of(CORRELATION_ID_KEY, correlationId);
        final DiagnosticLoggingAdapter loggingAdapterWithTwoMdcEntries = Mockito.mock(DiagnosticLoggingAdapter.class);
        final DiagnosticLoggingAdapter loggingAdapterWithOneMdcEntry = Mockito.mock(DiagnosticLoggingAdapter.class);
        Mockito.when(diagnosticLoggingAdapterFactory.get())
                .thenReturn(loggingAdapterWithEmptyMdc)
                .thenReturn(loggingAdapterWithTwoMdcEntries)
                .thenReturn(loggingAdapterWithOneMdcEntry);
        Mockito.when(loggingAdapterWithTwoMdcEntries.isDebugEnabled()).thenReturn(true);
        Mockito.when(loggingAdapterWithOneMdcEntry.isDebugEnabled()).thenReturn(true);
        Mockito.when(loggingAdapterWithTwoMdcEntries.getMDC()).thenReturn(new HashMap<>(mdcOfLoggerWithTwoMdcEntries));
        Mockito.when(loggingAdapterWithOneMdcEntry.getMDC()).thenReturn(new HashMap<>(mdcOfLoggerWithOneMdcEntry));

        final ImmutableDittoLoggingAdapter initialLogger =
                ImmutableDittoLoggingAdapter.of(diagnosticLoggingAdapterFactory);
        final ImmutableDittoLoggingAdapter withTwoMdcEntries =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        withTwoMdcEntries.debug("Foo");
        final ImmutableDittoLoggingAdapter withOneMdcEntry =
                withTwoMdcEntries.removeMdcEntry(CONNECTION_ID_KEY);
        withOneMdcEntry.debug("Bar");

        Mockito.verify(diagnosticLoggingAdapterFactory, Mockito.times(3)).get();
        Mockito.verify(loggingAdapterWithTwoMdcEntries).setMDC(mdcOfLoggerWithTwoMdcEntries);
        Mockito.verify(loggingAdapterWithTwoMdcEntries).notifyDebug("Foo");
        Mockito.verify(loggingAdapterWithOneMdcEntry).setMDC(mdcOfLoggerWithOneMdcEntry);
        Mockito.verify(loggingAdapterWithOneMdcEntry).notifyDebug("Bar");
    }

    private String getCorrelationId() {
        return testName.getMethodName();
    }

}
