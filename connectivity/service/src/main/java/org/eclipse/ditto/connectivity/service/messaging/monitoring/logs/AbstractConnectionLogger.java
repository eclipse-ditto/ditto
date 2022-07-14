/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

/**
 * Abstract base implementation of {@link ConnectionLogger} with some shared functionalities of all loggers.
 *
 * @param <B> the type of the {@code AbstractConnectionLoggerBuilder}.
 * @param <T> the type of the {@code AbstractConnectionLogger}.
 */
abstract class AbstractConnectionLogger<
                B extends AbstractConnectionLogger.AbstractConnectionLoggerBuilder<B, T>,
                T extends AbstractConnectionLogger<B, T>>
        implements ConnectionLogger {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(AbstractConnectionLogger.class);

    private static final String FALLBACK_EXCEPTION_TEXT = "not specified";

    private final LogCategory category;
    private final LogType type;

    private final String defaultSuccessMessage;
    private final String defaultFailureMessage;
    private final String defaultExceptionMessage;

    private final boolean logHeadersAndPayload;

    @Nullable private final String address;

    protected AbstractConnectionLogger(final B builder) {
        category = builder.category;
        type = builder.type;
        address = builder.address;

        defaultSuccessMessage = builder.defaultSuccessMessage;
        defaultFailureMessage = builder.defaultFailureMessage;
        defaultExceptionMessage = builder.defaultExceptionMessage;

        logHeadersAndPayload = builder.logHeadersAndPayload;
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider) {
        success(infoProvider, defaultSuccessMessage, type.getType());
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider,
            @Nullable final DittoRuntimeException dittoRuntimeException) {
        if (null != dittoRuntimeException) {
            failure(infoProvider, defaultFailureMessage, type.getType(), dittoRuntimeException.getMessage() +
                    dittoRuntimeException.getDescription().map(" "::concat).orElse(""));
        } else {
            failure(infoProvider, defaultFailureMessage, type.getType(), FALLBACK_EXCEPTION_TEXT);
        }
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final Throwable exception) {
        if (null != exception) {
            exception(infoProvider, defaultExceptionMessage, type.getType(), exception.getMessage());
        } else {
            exception(infoProvider, defaultExceptionMessage, type.getType(), FALLBACK_EXCEPTION_TEXT);
        }
    }

    protected static String formatMessage(final String message, final Object... messageArguments) {
        if (messageArguments.length > 0) {
            try {
                return MessageFormat.format(message, messageArguments);
            } catch (final IllegalArgumentException e) {
                LOGGER.info("The log message contains an invalid pattern: {} ", message);
                // log the message anyway without substituting any placeholders
                return message;
            }
        }
        return message;
    }

    protected String formatMessage(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {

        final String formattedMessage = formatMessage(message, messageArguments);
        return addHeadersAndPayloadToMessage(infoProvider, formattedMessage);
    }

    private String addHeadersAndPayloadToMessage(final ConnectionMonitor.InfoProvider infoProvider,
            final String initialMessage) {

        if (!infoProvider.isEmpty() && logHeadersAndPayload) {
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

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractConnectionLogger<?, ?> that = (AbstractConnectionLogger<?, ?>) o;
        return logHeadersAndPayload == that.logHeadersAndPayload &&
                category == that.category &&
                type == that.type &&
                Objects.equals(defaultSuccessMessage, that.defaultSuccessMessage) &&
                Objects.equals(defaultFailureMessage, that.defaultFailureMessage) &&
                Objects.equals(defaultExceptionMessage, that.defaultExceptionMessage) &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, type, defaultSuccessMessage, defaultFailureMessage,
                defaultExceptionMessage, logHeadersAndPayload, address);
    }

    @Override
    public String toString() {
        return "category=" + category +
                ", type=" + type +
                ", defaultSuccessMessage=" + defaultSuccessMessage +
                ", defaultFailureMessage=" + defaultFailureMessage +
                ", defaultExceptionMessage=" + defaultExceptionMessage +
                ", logHeadersAndPayload=" + logHeadersAndPayload +
                ", address=" + address +
                "]";
    }

    protected LogEntry getLogEntry(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final LogLevel logLevel) {

        return ConnectivityModelFactory.newLogEntryBuilder(infoProvider.getCorrelationId(), infoProvider.getTimestamp(),
                category, type, logLevel, message)
                .address(address)
                .entityId(infoProvider.getEntityId())
                .build();
    }

    /**
     * Abstract Builder for {@code ConnectionLogger}s.
     *
     * @param <B> the type of the {@code AbstractConnectionLoggerBuilder}.
     * @param <T> the type of the {@code AbstractConnectionLogger} to build.
     */
    abstract static class AbstractConnectionLoggerBuilder
            <B extends AbstractConnectionLoggerBuilder<B, T>, T extends AbstractConnectionLogger<B, T>>  {

        private static final String DEFAULT_SUCCESS_MESSAGE = "Message was {0}";
        private static final String DEFAULT_FAILURE_MESSAGE = "Failure while message was {0}: {1}";
        private static final String DEFAULT_EXCEPTION_MESSAGE = "Unexpected failure while message was {0}: {1}";

        final LogCategory category;
        final LogType type;
        String defaultSuccessMessage = DEFAULT_SUCCESS_MESSAGE;
        String defaultFailureMessage = DEFAULT_FAILURE_MESSAGE;
        String defaultExceptionMessage = DEFAULT_EXCEPTION_MESSAGE;
        boolean logHeadersAndPayload = false;

        @Nullable String address;

        protected AbstractConnectionLoggerBuilder(final LogCategory category,
                final LogType type) {

            this.category = checkNotNull(category, "Logging category");
            this.type = checkNotNull(type, "Logging type");
        }

        /**
         * Use the address for the built {@code EvictingConnectionLogger}.
         *
         * @param address the source or target address for which the logger stores logs.
         * @return the builder for method chaining.
         */
        @SuppressWarnings("unchecked")
        B withAddress(@Nullable final String address) {
            this.address = address;
            return (B) this;
        }

        /**
         * Use as default success message for the built {@code EvictingConnectionLogger}. It is used if no message
         * is specified while logging.
         *
         * @param defaultSuccessMessage default message for success logs.
         * @return the builder for method chaining.
         */
        @SuppressWarnings("unchecked")
        B withDefaultSuccessMessage(final String defaultSuccessMessage) {
            this.defaultSuccessMessage = checkNotNull(defaultSuccessMessage, "Default success message");
            return (B) this;
        }

        /**
         * Use as default failure message for the built {@code EvictingConnectionLogger}. It is used if no message
         * is specified while logging.
         *
         * @param defaultFailureMessage default message for failure logs.
         * @return the builder for method chaining.
         */
        @SuppressWarnings("unchecked")
        B withDefaultFailureMessage(final String defaultFailureMessage) {
            this.defaultFailureMessage = checkNotNull(defaultFailureMessage, "Default failure message");
            return (B) this;
        }

        /**
         * Use as default exception message for the built {@code EvictingConnectionLogger}. It is used if no message
         * is specified while logging.
         *
         * @param defaultExceptionMessage default message for exception logs.
         * @return the builder for method chaining.
         */
        @SuppressWarnings("unchecked")
        B withDefaultExceptionMessage(final String defaultExceptionMessage) {
            this.defaultExceptionMessage = checkNotNull(defaultExceptionMessage, "Default exception message");
            return (B) this;
        }

        /**
         * Enables logging the headers and the payload of messages. The detail level of the logged contents depends a
         * user-settable header.
         *
         * @return the builder for method chaining.
         */
        @SuppressWarnings("unchecked")
        B logHeadersAndPayload() {
            logHeadersAndPayload = true;
            return (B) this;
        }

        /**
         * Build the logger.
         *
         * @return a new instance of the logger.
         */
        public abstract T build();

    }

}
