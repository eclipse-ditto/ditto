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

import java.time.Duration;
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
    private static final String LOGGER_NAME = DefaultDittoDiagnosticLoggingAdapterTest.class.getName();

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock
    private DiagnosticLoggingAdapter plainLoggingAdapter;

    @Test
    public void getInstanceWithNullLogger() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultDittoDiagnosticLoggingAdapter.of(null, LOGGER_NAME))
                .withMessage("The loggingAdapter must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithNullLoggerName() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, null))
                .withMessage("The loggerName must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithBlankLoggerName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, ""))
                .withMessage("The argument 'loggerName' must not be empty!")
                .withNoCause();
    }

    @Test
    public void getNameReturnsExpected() {
        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);

        assertThat(underTest.getName()).isEqualTo(LOGGER_NAME);
    }

    @Test
    public void withCorrelationIdLogInfo() {
        final String correlationId = getCorrelationId();
        final String msg1 = "Foo!";
        final String msg2 = "Bar!";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.withCorrelationId(correlationId);
        underTest.info(msg1);
        underTest.info(msg2);

        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
        Mockito.verify(plainLoggingAdapter).notifyInfo(msg1);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
        Mockito.verify(plainLoggingAdapter).notifyInfo(msg2);
    }

    @Test
    public void withMdcEntryLogDebug() {
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.withMdcEntry(CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        underTest.debug(msg);

        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).notifyDebug(msg);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void withTwoMdcEntryPairsLogDebug() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE);
        underTest.debug(msg);

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).notifyDebug(msg);
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
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE, k3, v3);
        underTest.debug(msg);

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE, k3, v3));
        Mockito.verify(plainLoggingAdapter).notifyDebug(msg);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void withTwoMdcEntriesLogWarning() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isWarningEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.withMdcEntry(MdcEntry.of(CORRELATION_ID_KEY, correlationId),
                MdcEntry.of(CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        underTest.warning(msg);

        Mockito.verify(plainLoggingAdapter)
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, CONNECTION_ID_VALUE));
        Mockito.verify(plainLoggingAdapter).notifyWarning(msg);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void putNothingToMdcAndDoNotLogAsInfoIsDisabled() {
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(false);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
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

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.putMdcEntry(CORRELATION_ID_KEY, correlationId);
        underTest.putMdcEntry(CONNECTION_ID_KEY, connectionId);
        underTest.debug(debugMsg);
        underTest.warning(warnMsg);
        underTest.discardMdcEntries();

        Mockito.verify(plainLoggingAdapter).notifyDebug(debugMsg);
        Mockito.verify(plainLoggingAdapter).notifyWarning(warnMsg);
        Mockito.verify(plainLoggingAdapter, Mockito.times(2))
                .setMDC(Map.of(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId));
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
    }

    @Test
    public void removeMdcEntryViaKey() {
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        final String correlationId = getCorrelationId();
        final String connectionId = "my-connection";

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
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

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
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
    public void setCorrelationIdLogErrorDoNotDiscard() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isErrorEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.setCorrelationId(correlationId);
        underTest.error(msg);

        Mockito.verify(plainLoggingAdapter).isErrorEnabled();
        Mockito.verify(plainLoggingAdapter).getMDC();
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
        Mockito.verify(plainLoggingAdapter).notifyError(msg);
        Mockito.verifyNoMoreInteractions(plainLoggingAdapter);
    }

    @Test
    public void setCorrelationIdLogErrorDoNotClose() {
        final String correlationId = getCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainLoggingAdapter.isErrorEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.setCorrelationId(correlationId);
        underTest.error(msg);

        Mockito.verify(plainLoggingAdapter).isErrorEnabled();
        Mockito.verify(plainLoggingAdapter).getMDC();
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
        Mockito.verify(plainLoggingAdapter).notifyError(msg);
        Mockito.verifyNoMoreInteractions(plainLoggingAdapter);
    }

    @Test
    public void setCorrelationIdLogInfoThenDiscardCorrelationId() {
        final String correlationId = getCorrelationId();
        final String msg1 = "Foo!";
        final String msg2 = "No correlation ID in MDC.";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.setCorrelationId(correlationId);
        underTest.info(msg1);
        underTest.discardCorrelationId();
        underTest.info(msg2);

        Mockito.verify(plainLoggingAdapter).setMDC(Map.of(CORRELATION_ID_KEY, correlationId));
        Mockito.verify(plainLoggingAdapter).notifyInfo(msg1);
        Mockito.verify(plainLoggingAdapter).setMDC(Map.of());
        Mockito.verify(plainLoggingAdapter).notifyInfo(msg2);
    }

    @Test
    public void logMoreThan4LoggingArgsError() {
        final String template = "one: {}, two: {}, three: {}, four: {}, five: {}, six: {}";
        Mockito.when(plainLoggingAdapter.isErrorEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.error(template,
                "one",
                "two",
                "three",
                "four",
                "five",
                "six");

        Mockito.verify(plainLoggingAdapter, Mockito.atLeastOnce()).isErrorEnabled();
        Mockito.verify(plainLoggingAdapter).notifyError(String.format(template.replace("{}", "%s"),
                "one",
                "two",
                "three",
                "four",
                "five",
                "six"
        ));
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
        Mockito.when(plainLoggingAdapter.isWarningEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.warning(template,
                oneSecond,
                twoSeconds,
                threeSeconds,
                fourSeconds,
                fiveSeconds,
                sixSeconds,
                sevenSeconds);

        Mockito.verify(plainLoggingAdapter, Mockito.atLeastOnce()).isWarningEnabled();
        Mockito.verify(plainLoggingAdapter).notifyWarning(String.format(template.replace("{}", "%s"),
                oneSecond,
                twoSeconds,
                threeSeconds,
                fourSeconds,
                fiveSeconds,
                sixSeconds,
                sevenSeconds
        ));
    }

    @Test
    public void logMoreThan4LoggingArgsInfo() {
        final String template = "one: {}, two: {}, three: {}, four: {}, five: {}";
        Mockito.when(plainLoggingAdapter.isInfoEnabled()).thenReturn(true);

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.info(template,
                1,
                2,
                3,
                4,
                5);

        Mockito.verify(plainLoggingAdapter, Mockito.atLeastOnce()).isInfoEnabled();
        Mockito.verify(plainLoggingAdapter).notifyInfo(String.format(template.replace("{}", "%s"),
                1,
                2,
                3,
                4,
                5
        ));
    }

    @Test
    public void logMoreThan4LoggingArgsDebug() {
        Mockito.when(plainLoggingAdapter.isDebugEnabled()).thenReturn(true);
        final String template = "one: {}, two: {}, three: {}, four: {}, five: {}";

        final DefaultDittoDiagnosticLoggingAdapter underTest =
                DefaultDittoDiagnosticLoggingAdapter.of(plainLoggingAdapter, LOGGER_NAME);
        underTest.debug(template,
                1,
                2,
                3,
                4,
                "foobar");

        Mockito.verify(plainLoggingAdapter, Mockito.atLeastOnce()).isDebugEnabled();
        Mockito.verify(plainLoggingAdapter).notifyDebug(String.format(template.replace("{}", "%s"),
                1,
                2,
                3,
                4,
                "foobar"));
    }

    private String getCorrelationId() {
        return testName.getMethodName();
    }

}
