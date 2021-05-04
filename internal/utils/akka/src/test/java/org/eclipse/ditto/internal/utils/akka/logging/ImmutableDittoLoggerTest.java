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
import static org.assertj.core.api.Assertions.entry;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areEffectivelyImmutable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.JUnitSoftAssertions;
import org.assertj.core.data.MapEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.impl.ObservableMdcAdapter;

/**
 * Unit test for {@link ImmutableDittoLogger}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableDittoLoggerTest {

    private static final String CORRELATION_ID_KEY = CommonMdcEntryKey.CORRELATION_ID.toString();
    private static final String CONNECTION_ID_KEY = "connection-id";

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock
    private Logger plainSlf4jLogger;

    private CapturingMdcAdapterObserver mdcObserver;

    @Before
    public void setUp() {
        mdcObserver = new CapturingMdcAdapterObserver();
        ObservableMdcAdapter.registerObserver(testName.getMethodName(), mdcObserver);
    }

    @After
    public void tearDown() {
        ObservableMdcAdapter.deregisterObserver(testName.getMethodName());
    }

    @Ignore("MutabilityChecker cannot cope with the implementation")
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableDittoLogger.class,
                areEffectivelyImmutable(),
                provided(Logger.class).isAlsoImmutable());
    }

    @Test
    public void getInstanceWithNullLogger() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableDittoLogger.of(null))
                .withMessage("The logger must not be null!")
                .withNoCause();
    }

    @Test
    public void getNameReturnsExpected() {
        final ImmutableDittoLogger underTest = ImmutableDittoLogger.of(plainSlf4jLogger);

        assertThat(underTest.getName()).isEqualTo(plainSlf4jLogger.getName());
    }

    @Test
    public void withSameCorrelationIdReturnsSameInstance() {
        final String correlationId = getCorrelationId();
        ImmutableDittoLogger underTest = ImmutableDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        final ImmutableDittoLogger secondLogger = underTest.withCorrelationId(correlationId);

        assertThat(secondLogger).isSameAs(underTest);
    }

    @Test
    public void logInfoWithCorrelationId() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(true);
        ImmutableDittoLogger underTest = ImmutableDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        underTest.info(msg);

        Mockito.verify(plainSlf4jLogger).info(msg);
        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId));
        softly.assertThat(mdcObserver.getAllRemovedKeys())
                .as("Removed MDC entries")
                .containsOnly(CORRELATION_ID_KEY);
    }

    @Test
    public void putNothingToMdcAndDoNotLogAsInfoIsDisabled() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(false);
        ImmutableDittoLogger underTest = ImmutableDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        underTest.info(msg);

        Mockito.verify(plainSlf4jLogger, Mockito.times(0)).info(msg);
        softly.assertThat(mdcObserver.getAllPutEntries()).isEmpty();
        softly.assertThat(mdcObserver.getAllRemovedKeys()).isEmpty();
    }

    @Test
    public void putNothingToMdcButLogInfo() {
        final String correlationId = null;
        final String msg = "Foo!";
        ImmutableDittoLogger underTest = ImmutableDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        underTest.info(msg);

        Mockito.verify(plainSlf4jLogger, Mockito.times(0)).isInfoEnabled();
        Mockito.verify(plainSlf4jLogger).info(msg);
        softly.assertThat(mdcObserver.getAllPutEntries()).as("Put MDC entries").isEmpty();
        softly.assertThat(mdcObserver.getAllRemovedKeys()).as("Removed MDC entries").isEmpty();
    }

    @Test
    public void putCorrelationIdToMdcAndLogDebugInfoWarnAndError() {
        final String correlationId = getCorrelationId();
        final String debugMsg = "BugMeNot, {}!";
        final String infoMsg = "Foo!";
        final String warnMsg = "Bar!";
        final String errorMsg = "Baz!";
        final MapEntry<String, String> correlationIdMdcEntry = entry(CORRELATION_ID_KEY, correlationId);
        Mockito.when(plainSlf4jLogger.isDebugEnabled()).thenReturn(true);
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(true);
        Mockito.when(plainSlf4jLogger.isWarnEnabled()).thenReturn(true);
        Mockito.when(plainSlf4jLogger.isErrorEnabled()).thenReturn(true);

        ImmutableDittoLogger underTest = ImmutableDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        underTest.debug(debugMsg, "please");
        underTest.info(infoMsg);
        underTest.warn(warnMsg);
        underTest.error(errorMsg);

        Mockito.verify(plainSlf4jLogger).debug(debugMsg, "please");
        Mockito.verify(plainSlf4jLogger).info(infoMsg);
        Mockito.verify(plainSlf4jLogger).warn(warnMsg);
        Mockito.verify(plainSlf4jLogger).error(errorMsg);
        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsExactly(correlationIdMdcEntry, correlationIdMdcEntry, correlationIdMdcEntry,
                        correlationIdMdcEntry);
        softly.assertThat(mdcObserver.getAllRemovedKeys())
                .as("Removed MDC entries")
                .containsExactly(CORRELATION_ID_KEY, CORRELATION_ID_KEY, CORRELATION_ID_KEY, CORRELATION_ID_KEY);
    }

    @Test
    public void twoThreadsTwoLoggers() {
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(true);

        final ImmutableDittoLogger initialLogger = ImmutableDittoLogger.of(plainSlf4jLogger);
        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final Runnable loggingThread1 = () -> initialLogger.withCorrelationId("logger1-1").info("logger1-1");
        final Runnable loggingThread2 = () -> initialLogger.withCorrelationId("logger2-1").info("logger2-1");
        final CompletableFuture<Void> future1 = CompletableFuture.runAsync(loggingThread2, executorService);
        final CompletableFuture<Void> future2 = CompletableFuture.runAsync(loggingThread1, executorService);
        final CompletableFuture<Void> allLoggingFuture = CompletableFuture.allOf(future1, future2);
        allLoggingFuture.join();

        softly.assertThat(allLoggingFuture).isCompleted();
        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, "logger2-1"), entry(CORRELATION_ID_KEY, "logger1-1"));
    }

    @Test
    public void withTwoMdcEntries() {
        Mockito.when(plainSlf4jLogger.isErrorEnabled()).thenReturn(true);
        final ImmutableDittoLogger initialLogger = ImmutableDittoLogger.of(plainSlf4jLogger);
        final String correlationId = getCorrelationId();
        final String connectionId = "myConnection";
        final String logMessage = "The connection is closed!";

        final ImmutableDittoLogger underTest =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId);

        underTest.error(logMessage);

        Mockito.verify(plainSlf4jLogger).error(logMessage);
        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId), entry(CONNECTION_ID_KEY, connectionId));
        softly.assertThat(mdcObserver.getAllRemovedKeys())
                .as("Removed MDC entries")
                .containsOnly(CORRELATION_ID_KEY, CONNECTION_ID_KEY);
    }

    @Test
    public void removeCorrelationIdViaNullValue() {
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(true);
        final ImmutableDittoLogger initialLogger = ImmutableDittoLogger.of(plainSlf4jLogger);
        final String correlationId = getCorrelationId();
        final String connectionId = "myConnection";

        final ImmutableDittoLogger withTwoMdcEntries =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId);

        withTwoMdcEntries.info("Foo");

        final ImmutableDittoLogger withOneMdcEntry =
                withTwoMdcEntries.withMdcEntries(CORRELATION_ID_KEY, null, CONNECTION_ID_KEY, connectionId);

        withOneMdcEntry.info("Bar");

        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId),
                        entry(CONNECTION_ID_KEY, connectionId),
                        entry(CONNECTION_ID_KEY, connectionId));
        softly.assertThat(mdcObserver.getAllRemovedKeys())
                .as("Removed MDC entries")
                .containsOnly(CORRELATION_ID_KEY, CONNECTION_ID_KEY, CORRELATION_ID_KEY, CONNECTION_ID_KEY);
    }

    @Test
    public void removeMdcEntryViaKey() {
        Mockito.when(plainSlf4jLogger.isDebugEnabled()).thenReturn(true);
        final ImmutableDittoLogger initialLogger = ImmutableDittoLogger.of(plainSlf4jLogger);
        final String correlationId = getCorrelationId();
        final String connectionId = "myConnection";

        final ImmutableDittoLogger withTwoMdcEntries =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId);

        withTwoMdcEntries.debug("Foo");

        final ImmutableDittoLogger withOneMdcEntry = withTwoMdcEntries.removeMdcEntry(CONNECTION_ID_KEY);

        withOneMdcEntry.debug("Bar");

        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId),
                        entry(CONNECTION_ID_KEY, connectionId),
                        entry(CORRELATION_ID_KEY, correlationId));
        softly.assertThat(mdcObserver.getAllRemovedKeys())
                .as("Removed MDC entries")
                .containsOnly(CORRELATION_ID_KEY, CONNECTION_ID_KEY, CORRELATION_ID_KEY, CONNECTION_ID_KEY);
    }

    private String getCorrelationId() {
        return testName.getMethodName();
    }

}
