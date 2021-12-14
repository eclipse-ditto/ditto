/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link EvictingConnectionLogger}.
 */
public final class EvictingConnectionLoggerTest {

    private static final int SUCCESS_CAPACITY = 2;
    // needs to be > 2 for all tests to work
    private static final int FAILURE_CAPACITY = 4;
    private static final LogCategory CATEGORY = LogCategory.TARGET;
    private static final LogType TYPE = LogType.MAPPED;

    private static final ThingId THING_ID = ThingId.of("any:thing");

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private EvictingConnectionLogger.Builder builder;

    @Before
    public void before() {
        builder = EvictingConnectionLogger.newBuilder(SUCCESS_CAPACITY, FAILURE_CAPACITY, CATEGORY, TYPE);
    }

    @Test
    public void logTypeAndCategoryAreUsedForLogEntries() {
        final var logger = builder.build();
        final var infoProvider = InfoProviderFactory.forSignal(RetrieveThing.of(EvictingConnectionLoggerTest.THING_ID,
                getDittoHeadersWithCorrelationId()));
        logger.success(infoProvider);

        final var entry = getFirstAndOnlyEntry(logger);

        LogEntryAssertions.assertThat(entry)
                .hasCategory(CATEGORY)
                .hasType(TYPE)
                .hasInfo(infoProvider);
    }

    @Test
    public void withoutDebugLogEnabled() {
        final var logger = builder.build();

        final var textPayload = "{\"foo\":\"bar\"}";
        final var info = infoProviderWithHeaderAndPayloadDebugLogging(textPayload);

        logger.success(info);
        final var entry = getFirstAndOnlyEntry(logger);

        LogEntryAssertions.assertThat(entry)
                .hasMessageNotContainingHeaderKeysOrValues(info.getHeaders())
                .hasMessageNotContainingPayload(textPayload);
    }

    @Test
    public void withDebugLogDisabled() {
        final var logger = builder.logHeadersAndPayload().build();

        final var textPayload = "{\"foo\":\"bar\"}";
        final var headers = DittoHeaders.newBuilder(getDittoHeadersWithCorrelationId())
                .putHeader("foo", "bar")
                .putHeader("connectivity-debug-log", "OFF")
                .build();
        final var externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(headers).withText(textPayload).build();
        final var info = InfoProviderFactory.forExternalMessage(externalMessage);

        logger.success(info);
        final var entry = getFirstAndOnlyEntry(logger);

        LogEntryAssertions.assertThat(entry)
                .hasMessageNotContainingPayload(textPayload)
                .hasMessageContainingHeaderKeys(info.getHeaders());

    }

    @Test
    public void withDebugLogEnabled() {
        final var logger = builder.logHeadersAndPayload().build();

        final var headers = getDittoHeadersWithCorrelationId();
        final var textPayload = "{\"foo\":\"bar\"}";
        final var externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(headers).withText(textPayload).build();
        final var infoProvider = InfoProviderFactory.forExternalMessage(externalMessage);

        logger.success(infoProvider);
        final var entry = getFirstAndOnlyEntry(logger);

        LogEntryAssertions.assertThat(entry)
                .hasMessageContainingPayload(textPayload)
                .hasMessageContainingHeaderValues(infoProvider.getHeaders());
    }

    @Test
    public void withDebugLogHeaders() {
        final var logger = builder.logHeadersAndPayload().build();

        final var infoProvider =
                InfoProviderFactory.forHeaders(DittoHeaders.newBuilder(getDittoHeadersWithCorrelationId())
                        .putHeader("foo", "bar")
                        .putHeader("connectivity-debug-log", "HEADER")
                        .build());

        logger.success(infoProvider);
        final var entry = getFirstAndOnlyEntry(logger);

        LogEntryAssertions.assertThat(entry)
                .hasMessageContainingHeaderValues(infoProvider.getHeaders());
    }

    @Test
    public void withDebugLogPayload() {
        final var logger = builder.logHeadersAndPayload().build();

        final var textPayload = "{\"foo\":\"bar\"}";
        final var info = infoProviderWithPayloadDebugLogging(textPayload);

        logger.success(info);
        final var entry = getFirstAndOnlyEntry(logger);

        LogEntryAssertions.assertThat(entry)
                .hasMessageContainingPayload(textPayload);
    }

    @Test
    public void withDebugLogHeadersAndPayload() {
        final var logger = builder.logHeadersAndPayload().build();

        final var textPayload = "{\"foo\":\"bar\"}";
        final var info = infoProviderWithHeaderAndPayloadDebugLogging(textPayload);

        logger.success(info);
        final var entry = getFirstAndOnlyEntry(logger);

        LogEntryAssertions.assertThat(entry)
                .hasMessageContainingPayload(textPayload)
                .hasMessageContainingHeaderValues(info.getHeaders());
    }

    @Test
    public void defaultMessagesAreUsedForEntries() {
        final var defaultSuccessMessage = "this is a success";
        final var defaultFailureMessage = "this is also success";
        final var defaultExceptionMessage = "still good if shown :)";

        final var logger = builder.withDefaultSuccessMessage(defaultSuccessMessage)
                .withDefaultFailureMessage(defaultFailureMessage)
                .withDefaultExceptionMessage(defaultExceptionMessage)
                .build();

        logger.success(getInfoProvider());
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessage(defaultSuccessMessage);

        logger.clear();
        logger.failure(getInfoProvider());
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessage(defaultFailureMessage);

        logger.clear();
        logger.exception(getInfoProvider());
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessage(defaultExceptionMessage);
    }

    @Test
    public void addressIsUsedForEntries() {
        final var address = "an://address:123";
        final var logger = builder.withAddress(address).build();

        logger.success(getInfoProvider());

        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasAddress(address);
    }

    @Test
    public void evictingWorksForSuccess() {
        final var logger = builder.build();

        logNtimes(SUCCESS_CAPACITY + 1, logger::success);

        final var logs = logger.getLogs();
        assertThat(logs)
                .hasSize(SUCCESS_CAPACITY)
                .allMatch(entry -> LogLevel.SUCCESS.equals(entry.getLogLevel()));
    }

    @Test
    public void evictingWorksForFailure() {
        final var logger = builder.build();

        logNtimes(FAILURE_CAPACITY + 1, logger::failure);

        assertThat(logger.getLogs())
                .hasSize(FAILURE_CAPACITY)
                .allMatch(entry -> LogLevel.FAILURE.equals(entry.getLogLevel()));
    }

    @Test
    public void evictingWorksForException() {
        final var logger = builder.build();

        logNtimes(FAILURE_CAPACITY + 1, logger::exception);

        assertThat(logger.getLogs())
                .hasSize(FAILURE_CAPACITY)
                .allMatch(entry -> LogLevel.FAILURE.equals(entry.getLogLevel()));
    }

    @Test
    public void getLogsReturnsAllLogTypes() {

        final var logger = builder.build();

        logNtimes(SUCCESS_CAPACITY, logger::success);
        logNtimes(FAILURE_CAPACITY, logger::failure);

        assertThat(logger.getLogs())
                .hasSize(SUCCESS_CAPACITY + FAILURE_CAPACITY);
    }

    @Test
    public void failureAndExceptionEndsInSameLog() {
        final var logger = builder.build();

        logger.failure(getInfoProvider());
        logger.exception(getInfoProvider());

        assertThat(logger.getLogs())
                .hasSize(2)
                .allMatch(entry -> LogLevel.FAILURE.equals(entry.getLogLevel()));
    }

    @Test
    public void failureUsesMessageOfDittoRuntimeException() {
        final var logger = builder.build();
        final var errorMessage = "This is a special message of ditto runtime exception";
        final DittoRuntimeException exception = ThingIdInvalidException.newBuilder("invalid")
                .message(errorMessage)
                .build();

        logger.failure(getInfoProvider(), exception);

        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessageContaining(errorMessage);
    }

    @Test
    public void exceptionsUsesMessageOfExceptionOrElseDefault() {
        final var defaultSuccessMessage = "this is a success";
        final var defaultFailureMessage = "this is also success";
        final var defaultExceptionMessage = "hey there: {1}";

        final var test = "test";
        final var notSpecified = "not specified";

        final var logger = builder.withDefaultSuccessMessage(defaultSuccessMessage)
                .withDefaultFailureMessage(defaultFailureMessage)
                .withDefaultExceptionMessage(defaultExceptionMessage).build();

        logger.exception(getInfoProvider(), new Exception(test));
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessage(formatString("hey there: {0}", test));

        logger.clear();
        logger.exception(getInfoProvider(), null);
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessage(formatString("hey there: {0}", notSpecified));

        final var withoutFormatting = "withoutAnyFormatting";

        final var logger1 = builder.withDefaultSuccessMessage(defaultSuccessMessage)
                .withDefaultFailureMessage(defaultFailureMessage)
                .withDefaultExceptionMessage(withoutFormatting).build();

        logger1.exception(getInfoProvider(), new Exception(test));
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger1))
                .hasMessage(withoutFormatting);

        logger1.clear();
        logger1.exception(getInfoProvider(), null);
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger1))
                .hasMessage(withoutFormatting);
    }

    @Test
    public void failureDoesntFailOnNullException() {
        final var logger = builder.build();

        logger.failure(getInfoProvider(), null);

        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .isNotNull();
    }

    @Test
    public void exceptionDoesntFailOnNullException() {
        final var logger = builder.build();

        logger.exception(getInfoProvider(), null);

        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .isNotNull();
    }

    @Test
    public void ignoresForbiddenMessageFormatCharacters() {

        final var logger = builder.logHeadersAndPayload().build();

        final var payloadWithBadCharacters = "{curly brackets and single quotes aren't allowed in MsgFmt}";
        final var info = infoProviderWithPayloadDebugLogging(payloadWithBadCharacters);

        logger.success(info, "any message {0}", "that has at least one argument");
        final var entry = getFirstAndOnlyEntry(logger);

        LogEntryAssertions.assertThat(entry)
                .hasMessageContainingPayload(payloadWithBadCharacters);
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(EvictingConnectionLogger.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void logWithInvalidPattern() {
        final var logger = builder.logHeadersAndPayload().build();

        // {} throws NumberFormatException
        logger.success("success {}", true);

        final var entry = getFirstAndOnlyEntry(logger);
        LogEntryAssertions.assertThat(entry)
                .hasMessage("success {}");
    }

    @Test
    public void logWithMissingArgument() {
        final var logger = builder.logHeadersAndPayload().build();

        logger.success("success {0}");

        final var entry = getFirstAndOnlyEntry(logger);
        LogEntryAssertions.assertThat(entry)
                .hasMessage("success {0}");
    }

    @Test
    public void logNullEntryThrowsException() {
        final var underTest = builder.build();

        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> underTest.logEntry(null))
                .withMessage("The logEntry must not be null!")
                .withNoCause();
    }

    @Test
    public void logSuccessEntry() {
        final var correlationId = testNameCorrelationId.getCorrelationId();
        final var successLogEntry = ConnectivityModelFactory.newLogEntryBuilder(correlationId.toString(),
                        Instant.now(),
                        LogCategory.SOURCE,
                        LogType.CONSUMED,
                        LogLevel.SUCCESS,
                        "This is a success message.")
                .entityId(THING_ID)
                .build();
        final var underTest = builder.build();

        underTest.logEntry(successLogEntry);

        assertThat(underTest.getLogs()).containsOnly(successLogEntry);
    }

    @Test
    public void logFailureEntry() {
        final var correlationId = testNameCorrelationId.getCorrelationId();
        final var failureLogEntry = ConnectivityModelFactory.newLogEntryBuilder(correlationId.toString(),
                        Instant.now(),
                        LogCategory.RESPONSE,
                        LogType.DROPPED,
                        LogLevel.FAILURE,
                        "This is a failure message.")
                .entityId(THING_ID)
                .build();
        final var underTest = builder.build();

        underTest.logEntry(failureLogEntry);

        assertThat(underTest.getLogs()).containsOnly(failureLogEntry);
    }

    private void logNtimes(final int n, final Consumer<ConnectionMonitor.InfoProvider> logger) {
        Stream.iterate(0, UnaryOperator.identity())
                .limit(n)
                .forEach(unused -> logger.accept(getInfoProvider()));
    }

    private static String formatString(final String format, final String value) {
        return new MessageFormat(format).format(new Object[]{value});
    }

    private static LogEntry getFirstAndOnlyEntry(final ConnectionLogger logger) {
        final var logs = logger.getLogs();
        assertThat(logs).hasSize(1);

        return logs.stream().findFirst().orElseThrow(() -> new IllegalStateException("this should never happen"));
    }

    private ConnectionMonitor.InfoProvider getInfoProvider() {
        return InfoProviderFactory.forHeaders(getDittoHeadersWithCorrelationId());
    }

    private DittoHeaders getDittoHeadersWithCorrelationId() {
        return DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    private ConnectionMonitor.InfoProvider infoProviderWithPayloadDebugLogging(final String textPayload) {
        final var headers = DittoHeaders.newBuilder(getDittoHeadersWithCorrelationId())
                .putHeader("foo", "bar")
                .putHeader("connectivity-debug-log", "PAYLOAD")
                .build();
        final var externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(headers).withText(textPayload).build();
        return InfoProviderFactory.forExternalMessage(externalMessage);
    }

    private ConnectionMonitor.InfoProvider infoProviderWithHeaderAndPayloadDebugLogging(final String textPayload) {
        final var headers = DittoHeaders.newBuilder(getDittoHeadersWithCorrelationId())
                .putHeader("foo", "bar")
                .putHeader("connectivity-debug-log", "ALL")
                .build();
        final var externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(headers).withText(textPayload).build();
        return InfoProviderFactory.forExternalMessage(externalMessage);
    }

    private static final class LogEntryAssert extends AbstractAssert<LogEntryAssert, LogEntry> {

        private LogEntryAssert(final LogEntry logEntry) {
            super(logEntry, LogEntryAssert.class);
        }

        private LogEntryAssert hasCategory(final LogCategory category) {
            isNotNull();
            assertThat(actual.getLogCategory()).isEqualTo(category);
            return this;
        }

        private LogEntryAssert hasType(final LogType type) {
            isNotNull();
            assertThat(actual.getLogType()).isEqualTo(type);
            return this;
        }

        private LogEntryAssert hasAddress(@Nullable final String address) {
            if (null == address) {
                return hasNoAddress();
            }

            isNotNull();
            assertThat(actual.getAddress()).contains(address);
            return this;
        }

        private LogEntryAssert hasNoAddress() {
            isNotNull();
            assertThat(actual.getAddress()).isEmpty();
            return this;
        }

        private LogEntryAssert hasEntityId(@Nullable final EntityId entityId) {
            if (null == entityId) {
                return hasNoEntityId();
            }

            isNotNull();
            assertThat(actual.getEntityId()).contains(entityId);
            return this;
        }

        private LogEntryAssert hasNoEntityId() {
            isNotNull();
            assertThat(actual.getEntityId()).isEmpty();
            return this;
        }

        private LogEntryAssert hasTimestamp(final Instant timestamp) {
            isNotNull();
            assertThat(actual.getTimestamp()).isEqualTo(timestamp);
            return this;
        }

        private LogEntryAssert hasCorrelationId(final String correlationId) {
            isNotNull();
            assertThat(actual.getCorrelationId()).isEqualTo(correlationId);
            return this;
        }

        private LogEntryAssert hasInfo(final ConnectionMonitor.InfoProvider info) {
            return hasEntityId(info.getEntityId())
                    .hasTimestamp(info.getTimestamp())
                    .hasCorrelationId(info.getCorrelationId());
        }

        private LogEntryAssert hasMessage(final String message) {
            isNotNull();
            assertThat(actual.getMessage()).isEqualTo(message);
            return this;
        }

        private LogEntryAssert hasMessageContaining(final CharSequence... values) {
            isNotNull();
            assertThat(actual.getMessage()).contains(values);
            return this;
        }

        private LogEntryAssert hasMessageContainingHeaderKeys(final Map<String, String> headers) {
            return hasMessageContaining(headers.keySet().toString());
        }

        private LogEntryAssert hasMessageContainingHeaderValues(final Map<String, String> headers) {
            return hasMessageContaining(headers.entrySet().toString());
        }

        private LogEntryAssert hasMessageContainingPayload(final String payload) {
            return hasMessageContaining(payload);
        }

        private LogEntryAssert hasMessageNotContainingHeaderKeysOrValues(final Map<String, String> headers) {
            return hasMessageNotContaining(headers.keySet().toString(), headers.entrySet().toString());
        }

        private LogEntryAssert hasMessageNotContainingPayload(final String payload) {
            return hasMessageNotContaining(payload);
        }

        private LogEntryAssert hasMessageNotContaining(final CharSequence... values) {
            isNotNull();
            assertThat(actual.getMessage()).doesNotContain(values);
            return this;
        }

    }

    private static class LogEntryAssertions extends Assertions {

        private static LogEntryAssert assertThat(final LogEntry logEntry) {
            return new LogEntryAssert(logEntry);
        }

    }

}
