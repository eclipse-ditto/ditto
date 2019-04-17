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

package org.eclipse.ditto.services.connectivity.messaging.monitoring.logs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.model.connectivity.LogLevel;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
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

    private static final String THING_ID = "any:thing";
    private static final ConnectionMonitor.InfoProvider INFO_PROVIDER_WITH_THING = infoProviderWithThingId(THING_ID);

    @Test
    public void logTypeAndCategoryAreUsedForLogEntries() {
        final EvictingConnectionLogger logger = builder().build();
        logger.success(INFO_PROVIDER_WITH_THING);

        final LogEntry entry = getFirstAndOnlyEntry(logger);

        LogEntryAssertions.assertThat(entry)
                .hasCategory(CATEGORY)
                .hasType(TYPE)
                .hasInfo(INFO_PROVIDER_WITH_THING);
    }

    @Test
    public void defaultMessagesAreUsedForEntries() {
        final String defaultSuccessMessage = "this is a success";
        final String defaultFailureMessage = "this is also success";
        final String defaultExceptionMessage = "still good if shown :)";

        final EvictingConnectionLogger logger = builder().withDefaultSuccessMessage(defaultSuccessMessage)
                .withDefaultFailureMessage(defaultFailureMessage)
                .withDefaultExceptionMessage(defaultExceptionMessage)
                .build();

        logger.success(randomInfoProvider());
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessage(defaultSuccessMessage);

        logger.clear();
        logger.failure(randomInfoProvider());
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessage(defaultFailureMessage);

        logger.clear();
        logger.exception(randomInfoProvider());
        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessage(defaultExceptionMessage);
    }

    @Test
    public void addressIsUsedForEntries() {
        final String address = "an://address:123";
        final EvictingConnectionLogger logger = builder().withAddress(address).build();

        logger.success(randomInfoProvider());

        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasAddress(address);
    }

    @Test
    public void evictingWorksForSuccess() {
        final EvictingConnectionLogger logger = builder().build();

        logNtimes(SUCCESS_CAPACITY + 1, logger::success);

        final Collection<LogEntry> logs = logger.getLogs();
        assertThat(logs)
                .hasSize(SUCCESS_CAPACITY)
                .allMatch(entry -> LogLevel.SUCCESS.equals(entry.getLogLevel()));
    }

    @Test
    public void evictingWorksForFailure() {
        final EvictingConnectionLogger logger = builder().build();

        logNtimes(FAILURE_CAPACITY + 1, logger::failure);

        final Collection<LogEntry> logs = logger.getLogs();
        assertThat(logs)
                .hasSize(FAILURE_CAPACITY)
                .allMatch(entry -> LogLevel.FAILURE.equals(entry.getLogLevel()));

    }

    @Test
    public void evictingWorksForException() {
        final EvictingConnectionLogger logger = builder().build();

        logNtimes(FAILURE_CAPACITY + 1, logger::exception);

        final Collection<LogEntry> logs = logger.getLogs();
        assertThat(logs)
                .hasSize(FAILURE_CAPACITY)
                .allMatch(entry -> LogLevel.FAILURE.equals(entry.getLogLevel()));
    }

    @Test
    public void getLogsReturnsAllLogTypes() {

        final EvictingConnectionLogger logger = builder().build();

        logNtimes(SUCCESS_CAPACITY, logger::success);
        logNtimes(FAILURE_CAPACITY, logger::failure);

        final Collection<LogEntry> logs = logger.getLogs();
        assertThat(logs)
                .hasSize(SUCCESS_CAPACITY + FAILURE_CAPACITY);
    }

    @Test
    public void failureAndExceptionEndsInSameLog() {

        final EvictingConnectionLogger logger = builder().build();

        logger.failure(randomInfoProvider());
        logger.exception(randomInfoProvider());

        final Collection<LogEntry> logs = logger.getLogs();
        assertThat(logs)
                .hasSize(2)
                .allMatch(entry -> LogLevel.FAILURE.equals(entry.getLogLevel()));
    }

    @Test
    public void failureUsesMessageOfDittoRuntimeException() {
        final EvictingConnectionLogger logger = builder().build();
        final String errorMessage = "This is a special message of ditto runtime exception";
        final DittoRuntimeException exception = DittoRuntimeException.newBuilder("any.error", HttpStatusCode.BAD_REQUEST)
                .message(errorMessage)
                .build();

        logger.failure(randomInfoProvider(), exception);

        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
                .hasMessageContaining(errorMessage);
    }

    @Test
    public void failureDoesntFailOnNullException() {
        final EvictingConnectionLogger logger = builder().build();

        logger.failure(randomInfoProvider(), null);

        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
            .isNotNull();
    }

    @Test
    public void exceptionDoesntFailOnNullException() {
        final EvictingConnectionLogger logger = builder().build();

        logger.exception(randomInfoProvider(), null);

        LogEntryAssertions.assertThat(getFirstAndOnlyEntry(logger))
            .isNotNull();
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(EvictingConnectionLogger.class)
                .verify();
    }

    private void logNtimes(final int n, final Consumer<ConnectionMonitor.InfoProvider> logger) {
        Stream.iterate(0, UnaryOperator.identity())
                .limit(n)
                .forEach(unused -> logger.accept(randomInfoProvider()));
    }

    private LogEntry getFirstAndOnlyEntry(final ConnectionLogger logger) {
        final Collection<LogEntry> logs = logger.getLogs();
        assertThat(logs)
                .hasSize(1);

        return logs.stream().findFirst().orElseThrow(() -> new IllegalStateException("this should never happen"));
    }
    private EvictingConnectionLogger.Builder builder() {
        return EvictingConnectionLogger.newBuilder(SUCCESS_CAPACITY, FAILURE_CAPACITY, CATEGORY, TYPE);
    }

    private ConnectionMonitor.InfoProvider randomInfoProvider() {
        return ImmutableInfoProvider.forHeaders(DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build());
    }

    private static ConnectionMonitor.InfoProvider infoProviderWithThingId(final String thingId) {
        return ImmutableInfoProvider.forSignal(RetrieveThing.of(thingId, DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build()));
    }

    private static class LogEntryAssert extends AbstractAssert<LogEntryAssert, LogEntry> {

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
                return this.hasNoAddress();
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

        private LogEntryAssert hasThingId(@Nullable final String thingId) {
            if (null == thingId) {
                return hasNoThingId();
            }

            isNotNull();
            assertThat(actual.getThingId()).contains(thingId);
            return this;
        }

        private LogEntryAssert hasNoThingId() {
            isNotNull();
            assertThat(actual.getThingId()).isEmpty();
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
            return hasThingId(info.getThingId())
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
    }

    private static class LogEntryAssertions extends Assertions {

        private static LogEntryAssert assertThat(final LogEntry logEntry) {
            return new LogEntryAssert(logEntry);
        }

    }

}
