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
public final class ResponseDiversionInterceptor {

    /**
     * The specific configuration key for authorized connections as sources.
     * This configuration is a comma-separated list of connection IDs that are allowed to divert responses to this connection.
     * If this configuration is not set or empty, no connections are authorized to divert responses to this connection.
     * If this configuration is set, only the connections listed are authorized to divert responses to this connection.
     * Example: "conn-1, conn-2, conn-3"
     */
    public static final String AUTHORIZED_CONNECTIONS_AS_SOURCES = "authorized-connections-as-sources";
    /**
     * Whether to preserve the normal response path via the source connection when this connection is configured as
     * diversion source.
     * If configured true, diverted responses will be sent both via the diversion target connection and via the source
     * connection.
     * If configured false, diverted responses will only be sent via the diversion target connection.
     * Note: This setting has no effect if the connection is not configured as diversion source.
     */
    public static final String PRESERVE_NORMAL_RESPONSE_VIA_SOURCE = "preserve-normal-response-via-source";
    /**
     * Default value for {@link #PRESERVE_NORMAL_RESPONSE_VIA_SOURCE} (i.e. do not preserve the normal response path
     * via the source connection).
     */
    public static final String PRESERVE_NORMAL_RESPONSE_VIA_SOURCE_DEFAULT = "false";

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ResponseDiversionInterceptor.class);
    private static final Set<String> DEFAULT_RESPONSE_TYPES =
            Set.of(ResponseType.RESPONSE.getName(), ResponseType.ERROR.getName());

    private final Connection connection;
    private final Set<String> authorizedSources;
    private final ConnectionPubSub pubSub;
    private final String sourceConnectionId;
    private final boolean preserveNormalResponseViaSource;

    private ResponseDiversionInterceptor(final Connection connection,
            final ConnectionPubSub pubSub) {
        this.connection = checkNotNull(connection, "connection");
        this.authorizedSources = Arrays.stream(connection.getSpecificConfig()
                        .getOrDefault(AUTHORIZED_CONNECTIONS_AS_SOURCES, "")
                        .split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        this.preserveNormalResponseViaSource = Boolean.parseBoolean(
                connection.getSpecificConfig()
                        .getOrDefault(PRESERVE_NORMAL_RESPONSE_VIA_SOURCE,
                                PRESERVE_NORMAL_RESPONSE_VIA_SOURCE_DEFAULT)
                        .trim());
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
     * Checks if the source connection ID is authorized to divert responses.
     * If the connection id is not in the list of authorized sources in the connection specific configuration,
     * it will return false.
     *
     * @param outboundSignal the outbound signal to check
     * @return true if the source connection ID is authorized, false otherwise
     */
    public boolean isAuthorized(final OutboundSignal outboundSignal) {
        // Check if the source connection ID is in the authorized sources
        final String sourceConnectionId = outboundSignal.getSource().getDittoHeaders()
                .get(DittoHeaderDefinition.DIVERTED_RESPONSE_FROM_CONNECTION.getKey());
        return sourceConnectionId != null && authorizedSources.contains(sourceConnectionId.trim());
    }

    /**
     * Checks if the given outbound signal is eligible for diversion.
     * Only command responses that have a diversion target connection header and are not yet diverted are considered eligible.
     *
     * @param outboundSignal the outbound signal to check
     * @return true if the signal is eligible for diversion, false otherwise
     */
    public boolean isForDiversion(final OutboundSignal outboundSignal) {
        return outboundSignal.getSource() instanceof CommandResponse<?> commandResponse &&
                // Don't divert already diverted responses
                !commandResponse.getDittoHeaders()
                        .containsKey(DittoHeaderDefinition.DIVERTED_RESPONSE_FROM_CONNECTION.getKey()) &&
                commandResponse.getDittoHeaders()
                        .containsKey(DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey());
    }

    /**
     * Indicates whether to preserve the normal response path via the source connection when this connection is
     * configured as diversion source.
     * If configured true, diverted responses will be sent both via the diversion target connection and via the source
     * connection.
     * If configured false, diverted responses will only be sent via the diversion target connection.
     * Note: This setting has no effect if the connection is not configured as diversion source.
     * @return true to preserve the normal response path via the source connection, false otherwise
     */
    public boolean shouldPreserveNormalResponseViaSource() {
        return preserveNormalResponseViaSource;
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
        final Signal<?> signal = outboundSignal.getSource();

        // Divert instances of command responses.
        if (!(signal instanceof final CommandResponse<?> response)) {
            LOGGER.withCorrelationId(signal)
                    .debug("Signal is not a CommandResponse, skipping diversion: {}", signal.getType());
            return false;
        }

        // Check if this response type should be diverted
        final String origin = response.getDittoHeaders().getOrigin().map(String::trim).orElse("");
        @Nullable final String targetConnection = response.getDittoHeaders()
                .getOrDefault(DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), null);
        final Set<String> expectedResponses = expectedResponseTypes(response);
        final String responseType = signalResponseType(response);
        if (!origin.equals(targetConnection) && !(expectedResponseTypes(response).isEmpty() ||
                expectedResponses.contains(responseType))) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.withCorrelationId(response)
                        .debug("Response type '{}' not in expected types: {}, skipping diversion", responseType,
                                expectedResponses);
            }
            return false;
        }

        return Optional.ofNullable(targetConnection)
                .map(targetId -> divertToConnection(response, targetId.trim()))
                .orElse(false);
    }

    private Set<String> expectedResponseTypes(final CommandResponse<?> response) {
        final String typesString =
                response.getDittoHeaders().get(DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey());
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

    private String signalResponseType(final CommandResponse<?> response) {
        return response.getResponseType().getName();
    }

    /**
     * Diverts a response to the specified target connection.
     *
     * @param response the CommandResponse to divert
     * @param targetConnectionIdString the target connection ID string
     * @return true if diversion was successful, false otherwise
     */
    private boolean divertToConnection(final CommandResponse<?> response, final String targetConnectionIdString) {
        // Enhance signal with diversion headers
        final CommandResponse<?> enhancedResponse = addSourceConnectionHeader(response);
        try {
            final ConnectionId targetConnectionId = ConnectionId.of(targetConnectionIdString);

            // Check for self-diversion
            if (targetConnectionId.toString().equals(sourceConnectionId)) {
                LOGGER.withCorrelationId(response)
                        .warn("Connection cannot divert responses to itself: {}", sourceConnectionId);
                return false;
            }


            LOGGER.withCorrelationId(response)
                    .info("Diverting response from connection '{}' to connection '{}'",
                            sourceConnectionId, targetConnectionId);
            final String groupIndexKey;
            if (enhancedResponse instanceof WithEntityId withEntityId){
                groupIndexKey = withEntityId.getEntityId().toString();
            } else {
                groupIndexKey = enhancedResponse.getName();
            }
            // Publish to target connection with retry capability
            pubSub.publishResponseForDiversion(enhancedResponse, targetConnectionId, groupIndexKey, ActorRef.noSender());

            return true; // The Signal was diverted

        } catch (final Exception e) {
            LOGGER.withCorrelationId(enhancedResponse)
                    .error("Failed to divert response to connection: {}", targetConnectionIdString, e);
            return false;
        }
    }

    /**
     * Add to the response diversion tracking headers.
     *
     * @param response the original response
     * @return the enhanced response
     */
    private CommandResponse<?> addSourceConnectionHeader(final CommandResponse<?> response) {
        final DittoHeaders originalHeaders = response.getDittoHeaders();

        final DittoHeaders enhancedHeaders = DittoHeaders.newBuilder(originalHeaders)
                .putHeader(DittoHeaderDefinition.DIVERTED_RESPONSE_FROM_CONNECTION.getKey(), connection.getId().toString())
                .build();

        return response.setDittoHeaders(enhancedHeaders);
    }

    boolean isAlreadyDiverted(final OutboundSignal outboundSignal) {
        return outboundSignal.getSource().getDittoHeaders()
                .containsKey(DittoHeaderDefinition.DIVERTED_RESPONSE_FROM_CONNECTION.getKey());
    }
}