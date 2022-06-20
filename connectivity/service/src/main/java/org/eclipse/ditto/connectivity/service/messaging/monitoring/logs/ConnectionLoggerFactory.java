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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;
import org.komamitsu.fluency.Fluency;

/**
 * Factory for building {@link ConnectionLogger} instances that
 * will already contain predefined default messages. The messages will provide some context depending on the category
 * and type of the logger.
 * <em> Note that only currently used loggers have implemented a special contextual message. If new loggers are used,
 * this factory should be enhanced for these new cases.</em>
 */
final class ConnectionLoggerFactory {

    private static final String EMPTY_PAYLOAD_MAPPING_MESSAGE = "Payload mapping returned null, message is dropped.";

    private ConnectionLoggerFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a new {@link ConnectionLogger} that will
     * evict old messages. Moreover, it will have some predefined default log messages for its {@code logCategory} and
     * {@code logType}.
     *
     * @param successCapacity how many success messages will be stored by the logger.
     * @param failureCapacity how many failure messages will be stored by the logger.
     * @param logCategory the category of the logger.
     * @param logType the type of the logger.
     * @param address the address of the logger, e.g. a source or target address.
     * @return a new evicting logger.
     * @throws java.lang.NullPointerException if any non-nullable argument is {@code null}.
     * @throws java.lang.AssertionError if {@code logCategory} is invalid.
     */
    static ConnectionLogger newEvictingLogger(
            final int successCapacity, final int failureCapacity,
            final LogCategory logCategory, final LogType logType,
            @Nullable final String address) {

        return switch (logCategory) {
            case SOURCE -> newSourceLogger(logType, successCapacity, failureCapacity, address);
            case TARGET -> newTargetLogger(logType, successCapacity, failureCapacity, address);
            case RESPONSE -> newResponseLogger(logType, successCapacity, failureCapacity, address);
            case CONNECTION -> newConnectionLogger(logType, successCapacity, failureCapacity, address);
            default -> throw new AssertionError("Missing switch case.");
        };
    }

    /**
     * Crates a new {@link ExceptionalConnectionLogger} that logs the operations executed on it for tracing.
     * @param connectionId the connectionId for which the logger is created.
     * @param exception the exception that caused the creation of this logger.
     * @return the new instance.
     */
    static ConnectionLogger newExceptionalLogger(final ConnectionId connectionId, final Exception exception) {
        return new ExceptionalConnectionLogger(connectionId, exception);
    }

    /**
     * Creates a new {@link MuteableConnectionLogger} that can be muted and unmuted.
     *
     * @param connectionId the connection for which the logger is created.
     * @param delegate the delegate that will be called while the logger is unmuted.
     * @return a new muteable logger.
     * @throws java.lang.NullPointerException if {@code delegate} is null.
     */
    static MuteableConnectionLogger newMuteableLogger(final ConnectionId connectionId, final ConnectionLogger delegate) {
        return new DefaultMuteableConnectionLogger(connectionId, checkNotNull(delegate));
    }

    /**
     * Creates a new {@link FluentPublishingConnectionLoggerContext} used by
     * {@link #newPublishingLogger(ConnectionId, LogCategory, LogType, String, FluentPublishingConnectionLoggerContext)}
     * as connection static context information.
     *
     * @param fluencyForwarder the fluency forwarder for the logger.
     * @param waitUntilAllBufferFlushedDurationOnClose the duration of how long to wait after closing the Fluency buffer.
     * @param logLevels the log levels which should be included when publishing logs.
     * @param logHeadersAndPayload whether to also include headers and payload information in published logs.
     * @param logTag an optional log-tag to use and overwrite the default one: {@code connection:<connection-id>}
     * @param additionalLogContext additional log context to include in each logged entry.
     * @return a new fluent publishing connection logger context.
     */
    static FluentPublishingConnectionLoggerContext newPublishingLoggerContext(final Fluency fluencyForwarder,
            final Duration waitUntilAllBufferFlushedDurationOnClose,
            final Collection<LogLevel> logLevels,
            final boolean logHeadersAndPayload,
            @Nullable final CharSequence logTag,
            final Map<String, Object> additionalLogContext) {

        return new FluentPublishingConnectionLoggerContext(fluencyForwarder, waitUntilAllBufferFlushedDurationOnClose,
                logLevels, logHeadersAndPayload, logTag, additionalLogContext);
    }

    /**
     * Creates a new {@link FluentPublishingConnectionLogger} that is used to forward all connection logs to a fluentd
     * or fluentbit endpoint.
     *
     * @param connectionId the connection for which the logger is created.
     * @param logCategory the log category for which the logger is created.
     * @param logType the log type for which the logger is created.
     * @param address the address for which the logger is created.
     * @param context the connection static context information for creating the publishing connection logger.
     * @return a new fluent publishing connection logger instance.
     */
    static FluentPublishingConnectionLogger newPublishingLogger(final ConnectionId connectionId,
            final LogCategory logCategory, final LogType logType, @Nullable final String address,
            final FluentPublishingConnectionLoggerContext context) {

        final FluentPublishingConnectionLogger.Builder builder = FluentPublishingConnectionLogger
                .newBuilder(connectionId, logCategory, logType, context.getFluencyForwarder(),
                        context.getWaitUntilAllBufferFlushedDurationOnClose()
                )
                        .withAddress(address)
                        .withAdditionalLogContext(context.getAdditionalLogContext())
                        .withLogLevels(context.getLogLevels())
                        .withInstanceIdentifier(InstanceIdentifierSupplier.getInstance().get());
        if (context.isLogHeadersAndPayload()) {
            builder.logHeadersAndPayload();
        }
        context.getLogTag().ifPresent(builder::withFluentTag);

        return builder.build();
    }

    private static ConnectionLogger newSourceLogger(final LogType type, final int successCapacity,
            final int failureCapacity,
            @Nullable final String address) {

        final EvictingConnectionLogger.Builder builder =
                EvictingConnectionLogger.newBuilder(successCapacity, failureCapacity, LogCategory.SOURCE, type)
                        .withAddress(address);

        switch (type) {
            case CONSUMED:
                builder.withDefaultSuccessMessage("Received signal.")
                        .withDefaultFailureMessage("Ran into a failure when parsing an input command: {1}")
                        .withDefaultExceptionMessage(
                                "Ran into an unexpected failure when parsing an input command: {1}")
                        .logHeadersAndPayload();
                break;
            case MAPPED:
                builder.withDefaultSuccessMessage("Mapped incoming signal.")
                        .withDefaultFailureMessage("Ran into a failure when mapping incoming signal: {1}")
                        .withDefaultExceptionMessage("Unexpected failure when mapping incoming signal: {1}")
                        .logHeadersAndPayload();
                break;
            case DROPPED:
                builder.withDefaultSuccessMessage(EMPTY_PAYLOAD_MAPPING_MESSAGE);
                break;
            case ENFORCED:
                builder.withDefaultSuccessMessage("Successfully applied enforcement on incoming signal.")
                        .withDefaultFailureMessage("Ran into a failure when enforcing incoming signal: {1}")
                        .withDefaultExceptionMessage("Unexpected failure when enforcing incoming signal: {1}")
                        .logHeadersAndPayload();
                break;
            case ACKNOWLEDGED:
                builder.withDefaultSuccessMessage("Successfully acknowledged incoming signal.")
                        .withDefaultFailureMessage("Ran into a failure when acknowledging incoming signal: {1}")
                        .withDefaultExceptionMessage("Unexpected failure when acknowledging incoming signal: {1}")
                        .logHeadersAndPayload();
                break;
            default:
                // use the defaults already provided by the builder.
        }

        return builder.build();
    }

    private static ConnectionLogger newTargetLogger(final LogType type, final int successCapacity,
            final int failureCapacity,
            @Nullable final String address) {

        final EvictingConnectionLogger.Builder builder =
                EvictingConnectionLogger.newBuilder(successCapacity, failureCapacity, LogCategory.TARGET, type)
                        .withAddress(address);

        switch (type) {
            case DISPATCHED:
                builder.withDefaultSuccessMessage("Successfully dispatched signal.")
                        .logHeadersAndPayload();
                break;
            case FILTERED:
                builder.withDefaultSuccessMessage("Signal successfully passed possible filters.");
                break;
            case MAPPED:
                builder.withDefaultSuccessMessage("Successfully mapped outgoing signal.")
                        .withDefaultFailureMessage("Ran into a failure when mapping outgoing signal: {1}")
                        .withDefaultExceptionMessage("Unexpected failure when mapping outgoing signal: {1}");
                break;
            case DROPPED:
                builder.withDefaultSuccessMessage(EMPTY_PAYLOAD_MAPPING_MESSAGE)
                        .logHeadersAndPayload();
                break;
            case PUBLISHED:
                builder.withDefaultSuccessMessage("Successfully published signal.")
                        .withDefaultFailureMessage("Ran into a failure when publishing signal: {1}")
                        .withDefaultExceptionMessage("Unexpected failure when publishing signal: {1}")
                        .logHeadersAndPayload();
                break;
            case ACKNOWLEDGED:
                builder.withDefaultSuccessMessage("Received successful acknowledgement for published signal.")
                        .withDefaultFailureMessage("Ran into a failure for expected acknowledgement of published " +
                                "signal: {1}")
                        .withDefaultExceptionMessage("Unexpected failure for expected acknowledgement of published " +
                                "signal: {1}")
                        .logHeadersAndPayload();
                break;
            default:
                // use the defaults already provided by the builder.
        }
        return builder.build();
    }

    private static ConnectionLogger newResponseLogger(final LogType type, final int successCapacity,
            final int failureCapacity,
            @Nullable final String address) {

        final EvictingConnectionLogger.Builder builder =
                EvictingConnectionLogger.newBuilder(successCapacity, failureCapacity, LogCategory.RESPONSE, type)
                        .withAddress(address);

        switch (type) {
            case DISPATCHED:
                builder.withDefaultSuccessMessage("Received response.")
                        .withDefaultFailureMessage("Response was not successful. This may be the case for when a thing " +
                                "could not be found or the 'authorization subject' of the consuming source was not allowed " +
                                "to write a thing.")
                        .withDefaultExceptionMessage("Response was not successful. This may be the case for when a thing " +
                                "could not be found or the 'authorization subject' of the consuming source was not allowed " +
                                "to write a thing.")
                        .logHeadersAndPayload();
                break;
            case FILTERED:
                final String message = MessageFormat.format(
                        "Dropped response since requester did not require response via Header {0}",
                        DittoHeaderDefinition.RESPONSE_REQUIRED);
                builder.withDefaultSuccessMessage(message);
                break;
            case MAPPED:
                builder.withDefaultSuccessMessage("Successfully mapped outbound response.")
                        .withDefaultFailureMessage("Ran into a failure when mapping outgoing signal: {1}")
                        .withDefaultExceptionMessage("Unexpected failure when mapping outgoing signal: {1}");
                break;
            case DROPPED:
                builder.withDefaultSuccessMessage(EMPTY_PAYLOAD_MAPPING_MESSAGE)
                        .withDefaultFailureMessage("Response dropped, missing replyTo address.")
                        .logHeadersAndPayload();
                break;
            case PUBLISHED:
                builder.withDefaultSuccessMessage("Successfully published response.")
                        .withDefaultFailureMessage("Ran into a failure when publishing response: {1}")
                        .withDefaultExceptionMessage("Unexpected failure when publishing response: {1}")
                        .logHeadersAndPayload();
                break;
            case ACKNOWLEDGED:
                builder.withDefaultSuccessMessage("Received successful acknowledgement for published response.")
                        .withDefaultFailureMessage("Ran into a failure for expected acknowledgement of published " +
                                "response: {1}")
                        .withDefaultExceptionMessage("Unexpected failure for expected acknowledgement of published " +
                                "response: {1}")
                        .logHeadersAndPayload();
                break;
            default:
                // use the defaults already provided by the builder.
        }
        return builder.build();
    }

    private static ConnectionLogger newConnectionLogger(final LogType type, final int successCapacity, final int failureCapacity,
            @Nullable final String address) {

        return EvictingConnectionLogger.newBuilder(successCapacity, failureCapacity, LogCategory.CONNECTION, type)
                .withAddress(address)
                .build();
    }

}
