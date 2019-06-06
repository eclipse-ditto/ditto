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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogType;

/**
 * Factory for building {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger} instances that
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
     * Creates a new {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger} that will
     * evict old messages. Moreover it will have some predefined default log messages for its {@code logCategory} and
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

        switch (logCategory) {
            case SOURCE:
                return newSourceLogger(logType, successCapacity, failureCapacity, address);
            case TARGET:
                return newTargetLogger(logType, successCapacity, failureCapacity, address);
            case RESPONSE:
                return newResponseLogger(logType, successCapacity, failureCapacity, address);
            case CONNECTION:
                return newConnectionLogger(logType, successCapacity, failureCapacity, address);
            default:
                throw new AssertionError("Missing switch case.");
        }

    }

    /**
     * Creates a new {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.MuteableConnectionLogger} that can
     * be muted and unmuted.
     *
     * @param connectionId the connection for which the logger is created.
     * @param delegate the delegate that will be called while the logger is unmuted.
     * @return a new muteable logger.
     * @throws java.lang.NullPointerException if {@code delegate} is null.
     */
    static MuteableConnectionLogger newMuteableLogger(final String connectionId, final ConnectionLogger delegate) {
        return new DefaultMuteableConnectionLogger(connectionId, checkNotNull(delegate));
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
                        .withDefaultFailureMessage("Ran into a failure when parsing an input command: {0}")
                        .withDefaultExceptionMessage(
                                "Ran into an unexpected failure when parsing an input command.")
                        .logHeadersAndPayload();
                break;
            case MAPPED:
                builder.withDefaultSuccessMessage("Mapped incoming signal.")
                        .withDefaultFailureMessage("Ran into a failure when mapping incoming signal: {0}")
                        .withDefaultExceptionMessage("Unexpected failure when mapping incoming signal.");
                break;
            case DROPPED:
                builder.withDefaultSuccessMessage(EMPTY_PAYLOAD_MAPPING_MESSAGE);
                break;
            case ENFORCED:
                builder.withDefaultSuccessMessage("Successfully applied enforcement on incoming signal.")
                        .withDefaultFailureMessage("Ran into a failure when enforcing incoming signal: {0}")
                        .withDefaultExceptionMessage("Unexpected failure when enforcing incoming signal.")
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
                builder.withDefaultSuccessMessage("Successfully mapped outbound signal.")
                        .withDefaultFailureMessage("Ran into a failure when mapping outgoing signal: {0}")
                        .withDefaultExceptionMessage("Unexpected failure when mapping outgoing signal.");
                break;
            case DROPPED:
                builder.withDefaultSuccessMessage(EMPTY_PAYLOAD_MAPPING_MESSAGE)
                        .logHeadersAndPayload();
                break;
            case PUBLISHED:
                builder.withDefaultSuccessMessage("Successfully published signal.")
                        .withDefaultFailureMessage("Ran into a failure when publishing signal: {0}")
                        .withDefaultExceptionMessage("Unexpected failure when publishing signal.")
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
                builder.withDefaultSuccessMessage("Successfully mapped outbound signal.")
                        .withDefaultFailureMessage("Ran into a failure when mapping outgoing signal: {0}")
                        .withDefaultExceptionMessage("Unexpected failure when mapping outgoing signal.");
                break;
            case DROPPED:
                builder.withDefaultSuccessMessage(EMPTY_PAYLOAD_MAPPING_MESSAGE)
                        .withDefaultFailureMessage("Response dropped, missing replyTo address.")
                        .logHeadersAndPayload();
                break;
            case PUBLISHED:
                builder.withDefaultSuccessMessage("Successfully published response.")
                        .withDefaultFailureMessage("Ran into a failure when publishing response: {0}")
                        .withDefaultExceptionMessage("Unexpected failure when publishing response.")
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
