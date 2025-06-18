/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorRef;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.util.ConnectionPubSub;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;

/**
 * Interceptor that checks if responses should be diverted to another connection.
 * This interceptor is responsible for:
 * 1. Checking if a connection has response diversion configured
 * 2. Enhancing signals with diversion metadata
 * 3. Publishing signals to the target connection via PubSub
 * 4. Preventing circular diversion chains
 */
@Immutable
public class ResponseDiversionInterceptor {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ResponseDiversionInterceptor.class);
    private static final Set<String> DEFAULT_RESPONSE_TYPES =
            Set.of(ResponseType.RESPONSE.getName(), ResponseType.ERROR.getName());

    private final Connection connection;
    private final ConnectionPubSub pubSub;
    private final String sourceConnectionId;

    private ResponseDiversionInterceptor(final Connection connection,
            final ConnectionPubSub pubSub) {
        this.connection = checkNotNull(connection, "connection");
        this.pubSub = checkNotNull(pubSub, "pubSub");
        this.sourceConnectionId = connection.getId().toString();
    }

    /**
     * Creates a new interceptor instance.
     *
     * @param connection the connection configuration
     * @param pubSub the PubSub service for publishing diverted responses
     * @return the new interceptor instance
     */
    public static ResponseDiversionInterceptor of(final Connection connection,
            final ConnectionPubSub pubSub) {
        return new ResponseDiversionInterceptor(connection, pubSub);
    }

    /**
     * Intercepts an outbound signal and diverts it if configured.
     * Only command responses are eligible for diversion.
     *
     * @param outboundSignal the outbound signal to intercept
     * @return true if the signal was diverted (and should not be sent normally), false otherwise
     */
    public boolean interceptAndDivert(final OutboundSignal outboundSignal) {
        checkNotNull(outboundSignal, "outboundSignal");
        // TODO DIVERSION:
        //  Consider static config extraction instead of header based dynamic
        //  as static will do evaluation only once and then reroute all responses based on config.
        //  Maybe a hybrid approach where static config is used for default diversion or one or the other.
        //  HEADER '"ditto-divert-response-to"' (dynamic from mapping or js mapper)
        final Signal<?> signal = outboundSignal.getSource();

        // Divert instances of command responses.
        if (!(signal instanceof CommandResponse)) {
            LOGGER.debug("Signal is not a CommandResponse, skipping diversion: {}", signal.getType());
            return false;
        }

        // Check if this response type should be diverted
        final String origin = signal.getDittoHeaders().getOrigin().map(String::trim).orElse("");
        @Nullable final String targetConnection = signal.getDittoHeaders().getOrDefault(DittoHeaderDefinition.DITTO_DIVERT_RESPONSE_TO.getKey(), null);
        final Set<String> expectedResponses = expectedResponseTypes(signal);
        final String responseType = signalResponseType(signal);
        if (!origin.equals(targetConnection) && !(expectedResponseTypes(signal).isEmpty() || expectedResponses.contains(responseType))) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.withCorrelationId(signal)
                        .debug("Response type '{}' not in expected types: {}, skipping diversion", responseType,
                                expectedResponses);
            }
            return false;
        }

        return Optional.ofNullable(targetConnection)
                .map(targetId -> divertToConnection(signal, targetId.trim()))
                .orElse(false);
    }

    private Set<String> expectedResponseTypes(final Signal<?> signal) {
        final String typesString =
                signal.getDittoHeaders().get(DittoHeaderDefinition.DITTO_DIVERT_EXPECTED_RESPONSE_TYPES.getKey());
        final Set<String> allResponseTypes =
                Arrays.stream(ResponseType.values()).map(ResponseType::getName).collect(Collectors.toSet());
        if (typesString != null && !typesString.trim().isEmpty()) {
            return Stream.of(typesString.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .filter(allResponseTypes::contains)
                    .collect(Collectors.toSet());
        }
        return DEFAULT_RESPONSE_TYPES; // Default to RESPONSE and ERROR types if not specified
    }

    @Nullable
    private String signalResponseType(final Signal<?> signal) {
        if (signal instanceof CommandResponse<?> response) {
            return response.getResponseType().getName();
        }

        return null; // Not a command response, no type to return
    }

    /**
     * Diverts a signal to the specified target connection.
     *
     * @param signal the signal to divert
     * @param targetConnectionIdString the target connection ID string
     * @return true if diversion was successful, false otherwise
     */
    private boolean divertToConnection(final Signal<?> signal, final String targetConnectionIdString) {
        // Enhance signal with diversion headers
        final Signal<?> enhancedSignal = enhanceSignalForDiversion(signal);
        try {
            final ConnectionId targetConnectionId = ConnectionId.of(targetConnectionIdString);

            // Check for self-diversion
            if (targetConnectionId.toString().equals(sourceConnectionId)) {
                LOGGER.withCorrelationId(signal)
                        .warn("Connection cannot divert responses to itself: {}", sourceConnectionId);
                return false;
            }


            LOGGER.withCorrelationId(signal)
                    .info("Diverting response from connection '{}' to connection '{}'",
                            sourceConnectionId, targetConnectionId);
            final String groupIndexKey;
            if (enhancedSignal instanceof WithEntityId withEntityId){
                groupIndexKey = withEntityId.getEntityId().toString();
            } else {
                groupIndexKey = enhancedSignal.getName();
            }
            // Publish to target connection with retry capability
            pubSub.publishSignalForDiversion(enhancedSignal, targetConnectionId, groupIndexKey, ActorRef.noSender());

            return true; // The Signal was diverted

        } catch (final Exception e) {
            LOGGER.withCorrelationId(enhancedSignal)
                    .error("Failed to divert response to connection: {}", targetConnectionIdString, e);
            return false;
        }
    }

    /**
     * Enhances a signal with diversion tracking headers.
     *
     * @param signal the original signal
     * @return the enhanced signal
     */
    private Signal<?> enhanceSignalForDiversion(final Signal<?> signal) {
        final DittoHeaders originalHeaders = signal.getDittoHeaders();

        final DittoHeaders enhancedHeaders = DittoHeaders.newBuilder(originalHeaders)
                .putHeader(DittoHeaderDefinition.DITTO_DIVERTED_RESPONSE_FROM.getKey(), connection.getId().toString())
                .build();

        return signal.setDittoHeaders(enhancedHeaders);
    }
}