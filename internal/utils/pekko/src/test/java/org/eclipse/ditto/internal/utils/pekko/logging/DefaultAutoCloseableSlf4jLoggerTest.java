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
package org.eclipse.ditto.internal.utils.pekko.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.entry;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

/**
 * Unit test for {@link DefaultAutoCloseableSlf4jLogger}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultAutoCloseableSlf4jLoggerTest {

    private static final String CORRELATION_ID_KEY = CommonMdcEntryKey.CORRELATION_ID.toString();
    private static final String CONNECTION_ID_KEY = "connection-id";

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock
    private Logger plainSlf4jLogger;

    private CapturingMdcAdapterObserver mdcObserver;

    @BeforeClass
    public static void configureProvider() {
        System.setProperty("slf4j.provider", StaticMDCServiceProvider.class.getName());
    }

    @Before
    public void setUp() {
        mdcObserver = new CapturingMdcAdapterObserver();
        ObservableMdcAdapter.registerObserver(testName.getMethodName(), mdcObserver);
    }

    @After
    public void tearDown() {
        ObservableMdcAdapter.deregisterObserver(testName.getMethodName());
    }

    @Test
    public void getInstanceWithNullLogger() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultAutoCloseableSlf4jLogger.of(null))
                .withMessage("The logger must not be null!")
                .withNoCause();
    }

    @Test
    public void getNameReturnsExpected() {
        final DefaultAutoCloseableSlf4jLogger underTest = DefaultAutoCloseableSlf4jLogger.of(plainSlf4jLogger);

        assertThat(underTest.getName()).isEqualTo(plainSlf4jLogger.getName());
    }

    @Test
    public void logInfoWithCorrelationId() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(true);
        DefaultAutoCloseableSlf4jLogger underTest = DefaultAutoCloseableSlf4jLogger.of(plainSlf4jLogger);
        underTest = underTest.setCorrelationId(correlationId);

        underTest.info(msg);

        Mockito.verify(plainSlf4jLogger).info(msg);
        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId));
        softly.assertThat(mdcObserver.getAllRemovedKeys())
                .as("Removed MDC entries")
                .isEmpty();
    }

    @Test
    public void putNothingToMdcAndDoNotLogAsInfoIsDisabled() {
        final String msg = "Foo!";
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(false);
        DefaultAutoCloseableSlf4jLogger underTest = DefaultAutoCloseableSlf4jLogger.of(plainSlf4jLogger);
        underTest = underTest.setCorrelationId(getCorrelationId());

        underTest.info(msg);

        Mockito.verify(plainSlf4jLogger, Mockito.times(0)).info(msg);
        softly.assertThat(mdcObserver.getAllPutEntries()).isEmpty();
        softly.assertThat(mdcObserver.getAllRemovedKeys()).isEmpty();
    }

    @Test
    public void logDebugAndWarnWithTwoMdcValuesThenClose() {
        final String correlationId = getCorrelationId();
        final String connectionId = "my-connection";
        final String debugMsg = "Foo!";
        final String warnMsg = "Behold!";
        Mockito.when(plainSlf4jLogger.isDebugEnabled()).thenReturn(true);
        Mockito.when(plainSlf4jLogger.isWarnEnabled()).thenReturn(true);

        final DefaultAutoCloseableSlf4jLogger underTest = DefaultAutoCloseableSlf4jLogger.of(plainSlf4jLogger);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, connectionId);
        underTest.debug(debugMsg);
        underTest.warn(warnMsg);
        underTest.close();

        Mockito.verify(plainSlf4jLogger).debug(debugMsg);
        Mockito.verify(plainSlf4jLogger).warn(warnMsg);
        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId), entry(CONNECTION_ID_KEY, connectionId),
                        entry(CORRELATION_ID_KEY, correlationId), entry(CONNECTION_ID_KEY, connectionId));
        softly.assertThat(mdcObserver.getAllRemovedKeys())
                .as("Removed MDC entries")
                .contains(CORRELATION_ID_KEY, CONNECTION_ID_KEY);
    }

    @Test
    public void removeMdcEntryViaKey() {
        Mockito.when(plainSlf4jLogger.isDebugEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();
        final String connectionId = "my-connection";

        final DefaultAutoCloseableSlf4jLogger underTest = DefaultAutoCloseableSlf4jLogger.of(plainSlf4jLogger);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, connectionId);

        underTest.debug("Foo");

        underTest.removeMdcEntry(CONNECTION_ID_KEY);

        underTest.debug("Bar");

        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId),
                        entry(CONNECTION_ID_KEY, connectionId),
                        entry(CORRELATION_ID_KEY, correlationId));
        softly.assertThat(mdcObserver.getAllRemovedKeys())
                .as("Removed MDC entries")
                .containsOnly(CONNECTION_ID_KEY);
    }

    @Test
    public void removeMdcEntryViaNullValue() {
        Mockito.when(plainSlf4jLogger.isDebugEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();
        final String connectionId = "my-connection";

        final DefaultAutoCloseableSlf4jLogger underTest = DefaultAutoCloseableSlf4jLogger.of(plainSlf4jLogger);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, connectionId);

        underTest.debug("Foo");

        underTest.putMdcEntry(CORRELATION_ID_KEY, null);

        underTest.debug("Bar");

        softly.assertThat(mdcObserver.getAllPutEntries())
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId),
                        entry(CONNECTION_ID_KEY, connectionId),
                        entry(CONNECTION_ID_KEY, connectionId));
        softly.assertThat(mdcObserver.getAllRemovedKeys())
                .as("Removed MDC entries")
                .containsOnly(CORRELATION_ID_KEY);
    }

    private String getCorrelationId() {
        return testName.getMethodName();
    }

}
