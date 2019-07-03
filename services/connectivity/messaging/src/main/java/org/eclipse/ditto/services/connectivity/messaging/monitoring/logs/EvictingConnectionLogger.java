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

import java.text.MessageFormat;
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
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger} that
 * has fixed capacity for its success and failure logs and will evict old logs when new logs are added.
 */
final class EvictingConnectionLogger implements ConnectionLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvictingConnectionLogger.class);

    private static final String FALLBACK_EXCEPTION_TEXT = "not specified";

    private final LogCategory category;
    private final LogType type;

    private final EvictingQueue<LogEntry> successLogs;
    private final EvictingQueue<LogEntry> failureLogs;

    private final String defaultSuccessMessage;
    private final String defaultFailureMessage;
    private final String defaultExceptionMessage;

    private final boolean logHeadersAndPayload;

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

        this.logHeadersAndPayload = builder.logHeadersAndPayload;

        LOGGER.trace("Successfully built new EvictingConnectionLogger: {}", this);
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

    private static String formatMessage(final String message, final Object... messageArguments) {
        if (messageArguments.length > 0) {
            return MessageFormat.format(message, messageArguments);
        }
        return message;
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider) {
        success(infoProvider, defaultSuccessMessage);
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider, final String message, final Object... messageArguments) {
        final String formattedMessage = formatMessage(infoProvider, message, messageArguments);
        logTraceWithCorrelationId(infoProvider.getCorrelationId(), "success", infoProvider, formattedMessage);
        successLogs.add(getLogEntry(infoProvider, formattedMessage, LogLevel.SUCCESS));

    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final DittoRuntimeException dittoRuntimeException) {
        if (null != dittoRuntimeException) {
            failure(infoProvider, defaultFailureMessage, dittoRuntimeException.getMessage() +
                    dittoRuntimeException.getDescription().map(" "::concat).orElse(""));
        } else {
            failure(infoProvider, defaultFailureMessage, FALLBACK_EXCEPTION_TEXT);
        }

    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        final String formattedMessage = formatMessage(infoProvider, message, messageArguments);
        logTraceWithCorrelationId(infoProvider.getCorrelationId(), "failure", infoProvider, formattedMessage);
        failureLogs.add(getLogEntry(infoProvider, formattedMessage, LogLevel.FAILURE));
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final Exception exception) {
        exception(infoProvider, defaultExceptionMessage);
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        final String formattedMessage = formatMessage(infoProvider, message, messageArguments);
        logTraceWithCorrelationId(infoProvider.getCorrelationId(), "exception", infoProvider, formattedMessage);
        failureLogs.add(getLogEntry(infoProvider, formattedMessage, LogLevel.FAILURE));
    }

    @Override
    public void clear() {
        LOGGER.trace("Clearing all logs.");
        successLogs.clear();
        failureLogs.clear();
    }

    @Override
    public Collection<LogEntry> getLogs() {
        final List<LogEntry> logs = new ArrayList<>(successLogs.size() + failureLogs.size());
        logs.addAll(successLogs);
        logs.addAll(failureLogs);

        LOGGER.trace("Returning logs: {}", logs);
        return logs;
    }

    private String formatMessage(final ConnectionMonitor.InfoProvider infoProvider, final String message, final Object... messageArguments) {
        final String formattedMessage = formatMessage(message, messageArguments);
        return addHeadersAndPayloadToMessage(infoProvider, formattedMessage);
    }

    private String addHeadersAndPayloadToMessage(final ConnectionMonitor.InfoProvider infoProvider, final String initialMessage) {

        if (this.logHeadersAndPayload) {
            final String headersMessage = getDebugHeaderMessage(infoProvider);
            final String payloadMessage = getDebugPayloadMessage(infoProvider);
            return initialMessage + headersMessage + payloadMessage;
        }

        return initialMessage;
    }

    private static String getDebugHeaderMessage(final ConnectionMonitor.InfoProvider infoProvider) {

        if (ConnectivityHeaders.isHeadersDebugLogEnabled(infoProvider.getHeaders())) {
            return MessageFormat.format(" - Message headers: {0}", infoProvider.getHeaders().entrySet());
        }
        return MessageFormat.format(" - Message header keys: {0}", infoProvider.getHeaders().keySet());
    }

    private static String getDebugPayloadMessage(final ConnectionMonitor.InfoProvider infoProvider) {

        if (ConnectivityHeaders.isPayloadDebugLogEnabled(infoProvider.getHeaders())) {
            return MessageFormat.format(" - Message payload: {0}", infoProvider.getPayload());
        }
        return "";
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
        return logHeadersAndPayload == that.logHeadersAndPayload &&
                category == that.category &&
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
                defaultExceptionMessage, logHeadersAndPayload, address);
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
                ", logHeadersAndPayload=" + logHeadersAndPayload +
                ", address=" + address +
                "]";
    }

    private LogEntry getLogEntry(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final LogLevel logLevel) {
        return ImmutableLogEntry.getBuilder(infoProvider.getCorrelationId(), infoProvider.getTimestamp(), category, type, logLevel, message,
                address, infoProvider.getThingId())
                .build();
    }

    private void logTraceWithCorrelationId(final String correlationId, final String level,
            final ConnectionMonitor.InfoProvider infoProvider, final String message) {
        if (LOGGER.isTraceEnabled()) {
            LogUtil.enhanceLogWithCorrelationId(correlationId);
            LOGGER.trace("Saving {} log at <{}> for thing <{}> with message: {}", level,
                    infoProvider.getTimestamp(), infoProvider.getThingId(), message);
        }
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
        private boolean logHeadersAndPayload = false;

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
         * Enables logging the headers and the payload of messages. The detail level of the logged contents depends a
         * user-settable header.
         * @return the builder for method chaining.
         */
        Builder logHeadersAndPayload() {
            this.logHeadersAndPayload = true;
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
