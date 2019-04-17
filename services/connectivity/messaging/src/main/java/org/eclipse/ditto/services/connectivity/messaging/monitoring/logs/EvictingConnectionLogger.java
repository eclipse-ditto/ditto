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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.ImmutableLogEntry;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.model.connectivity.LogLevel;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;

/**
 * Implementation of {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger} that
 * has fixed capacity for its success and failure logs and will evict old logs when new logs are added.
 */
final class EvictingConnectionLogger implements ConnectionLogger {

    private static final String FALLBACK_EXCEPTION_TEXT = "not specified";

    private final LogCategory category;
    private final LogType type;

    private final EvictingQueue<LogEntry> successLogs;
    private final EvictingQueue<LogEntry> failureLogs;

    private final String defaultSuccessMessage;
    private final String defaultFailureMessage;
    private final String defaultExceptionMessage;

    @Nullable private final String address;

    private EvictingConnectionLogger(final Builder builder) {
        this.category = builder.category;
        this.type = builder.type;
        this.address = builder.address;

        this.successLogs = DefaultEvictingQueue.withCapacity(builder.successCapacity);
        this.failureLogs = DefaultEvictingQueue.withCapacity(builder.failureCapacity);

        this.defaultSuccessMessage = builder.defaultSuccessMessage;
        this.defaultFailureMessage = builder.defaultFailureMessage;
        this.defaultExceptionMessage = builder.defaultExceptionMessage;
    }

    /**
     * Create a new builder.
     * @param successCapacity how many success logs should be stored by the logger.
     * @param failureCapacity how many failure logs should be stored by the logger.
     * @param category category of logs stored by the logger.
     * @param type type of logs stored by the logger.
     * @return a new Builder for {@code EvictingConnectionLogger}.
     * @throws java.lang.NullPointerException if any non-nullable argument is {@code null}.
     */
    static Builder newBuilder(final int successCapacity, final int failureCapacity,
            final LogCategory category,
            final LogType type) {
        return new Builder(successCapacity, failureCapacity, category, type);
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider) {
        success(infoProvider, defaultSuccessMessage);
    }

    @Override
    public void success(final String correlationId, final Instant timestamp, final String message,
            @Nullable final String thingId) {
        successLogs.add(getLogEntry(correlationId, timestamp, message, address, thingId, LogLevel.SUCCESS));
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final DittoRuntimeException dittoRuntimeException) {
        if (null != dittoRuntimeException) {
            failure(infoProvider, defaultFailureMessage, dittoRuntimeException.getMessage());
        } else {
            failure(infoProvider, defaultFailureMessage, FALLBACK_EXCEPTION_TEXT);
        }

    }

    @Override
    public void failure(final String correlationId, final Instant timestamp, final String message,
            @Nullable final String thingId) {
        failureLogs.add(getLogEntry(correlationId, timestamp, message, address, thingId, LogLevel.FAILURE));
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final Exception exception) {
        exception(infoProvider.getCorrelationId(), infoProvider.getTimestamp(), defaultExceptionMessage, infoProvider.getThingId());
    }

    @Override
    public void exception(final String correlationId, final Instant timestamp, final String message,
            @Nullable final String thingId) {
        failureLogs.add(getLogEntry(correlationId, timestamp, message, address, thingId, LogLevel.FAILURE));
    }

    @Override
    public void clear() {
        successLogs.clear();
        failureLogs.clear();
    }

    @Override
    public Collection<LogEntry> getLogs() {
        final List<LogEntry> logs = new ArrayList<>(successLogs.size() + failureLogs.size());
        logs.addAll(successLogs);
        logs.addAll(failureLogs);
        return logs;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EvictingConnectionLogger that = (EvictingConnectionLogger) o;
        return category == that.category &&
                type == that.type &&
                Objects.equals(successLogs, that.successLogs) &&
                Objects.equals(failureLogs, that.failureLogs) &&
                Objects.equals(defaultSuccessMessage, that.defaultSuccessMessage) &&
                Objects.equals(defaultFailureMessage, that.defaultFailureMessage) &&
                Objects.equals(defaultExceptionMessage, that.defaultExceptionMessage) &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, type, successLogs, failureLogs, defaultSuccessMessage, defaultFailureMessage,
                defaultExceptionMessage, address);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", category=" + category +
                ", type=" + type +
                ", successLogs=" + successLogs +
                ", failureLogs=" + failureLogs +
                ", defaultSuccessMessage=" + defaultSuccessMessage +
                ", defaultFailureMessage=" + defaultFailureMessage +
                ", defaultExceptionMessage=" + defaultExceptionMessage +
                ", address=" + address +
                "]";
    }

    private LogEntry getLogEntry(final String correlationId, final Instant timestamp, final String message,
            @Nullable final String address, @Nullable final String thingId, final LogLevel logLevel) {
        return ImmutableLogEntry.getBuilder(correlationId, timestamp, category, type, logLevel, message,
                address, thingId)
                .build();
    }

    /**
     * Builder for {@code EvictingConnectionLogger}.
     */
    static class Builder {

        private static final String DEFAULT_SUCCESS_MESSAGE = "Processed message.";
        private static final String DEFAULT_FAILURE_MESSAGE = "Failure while processing message : {0}";
        private static final String DEFAULT_EXCEPTION_MESSAGE = "Unexpected failure while processing message.";

        private final int successCapacity;
        private final int failureCapacity;
        private final LogCategory category;
        private final LogType type;
        private String defaultSuccessMessage = DEFAULT_SUCCESS_MESSAGE;
        private String defaultFailureMessage = DEFAULT_FAILURE_MESSAGE;
        private String defaultExceptionMessage = DEFAULT_EXCEPTION_MESSAGE;

        @Nullable private String address;

        private Builder(final int successCapacity, final int failureCapacity,
                final LogCategory category,
                final LogType type) {
            this.successCapacity = successCapacity;
            this.failureCapacity = failureCapacity;
            this.category = checkNotNull(category, "Logging category");
            this.type = checkNotNull(type, "Logging type");
        }

        /**
         * Use the address for the built {@code EvictingConnectionLogger}.
         * @param address the source or target address for which the logger stores logs.
         * @return the builder for method chaining.
         */
        Builder withAddress(@Nullable final String address) {
            this.address = address;
            return this;
        }

        /**
         * Use as default success message for the built {@code EvictingConnectionLogger}. It is used if no message
         * is specified while logging.
         * @param defaultSuccessMessage default message for success logs.
         * @return the builder for method chaining.
         */
        Builder withDefaultSuccessMessage(final String defaultSuccessMessage) {
            this.defaultSuccessMessage = checkNotNull(defaultSuccessMessage, "Default success message");
            return this;
        }

        /**
         * Use as default failure message for the built {@code EvictingConnectionLogger}. It is used if no message
         * is specified while logging.
         * @param defaultFailureMessage default message for failure logs.
         * @return the builder for method chaining.
         */
        Builder withDefaultFailureMessage(final String defaultFailureMessage) {
            this.defaultFailureMessage = checkNotNull(defaultFailureMessage, "Default failure message");
            return this;
        }

        /**
         * Use as default exception message for the built {@code EvictingConnectionLogger}. It is used if no message
         * is specified while logging.
         * @param defaultExceptionMessage default message for exception logs.
         * @return the builder for method chaining.
         */
        Builder withDefaultExceptionMessage(final String defaultExceptionMessage) {
            this.defaultExceptionMessage = checkNotNull(defaultExceptionMessage, "Default exception message");
            return this;
        }

        /**
         * Build the logger.
         * @return a new instance of {@code EvictingConnectionLogger}.
         */
        public EvictingConnectionLogger build() {
            return new EvictingConnectionLogger(this);
        }

    }

}
