/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.routes.websocket;

import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.START_SEND_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.START_SEND_LIVE_COMMANDS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.START_SEND_LIVE_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.START_SEND_MESSAGES;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.STOP_SEND_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.STOP_SEND_LIVE_COMMANDS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.STOP_SEND_LIVE_EVENTS;
import static org.eclipse.ditto.services.gateway.endpoints.routes.websocket.ProtocolMessages.STOP_SEND_MESSAGES;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamControlMessage;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;

/**
 * Extracts WebSocket Protocol message from the given payload string and returns a {@link StartStreaming},
 * {@link StopStreaming} instance or {@code null} if the payload did not contain one of the defined
 * {@link ProtocolMessages}.
 */
final class ProtocolMessageExtractor implements Function<String, StreamControlMessage> {

    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_NAMESPACES = "namespaces";

    private final AuthorizationContext connectionAuthContext;
    private final String connectionCorrelationId;

    /**
     * Instantiates a new {@link ProtocolMessageExtractor}.
     *
     * @param connectionAuthContext the {@link AuthorizationContext} of the connection
     * @param connectionCorrelationId the correlation id of the connection
     */
    ProtocolMessageExtractor(final AuthorizationContext connectionAuthContext, final String connectionCorrelationId) {
        this.connectionAuthContext = connectionAuthContext;
        this.connectionCorrelationId = connectionCorrelationId;
    }

    @Override
    @Nullable
    public StreamControlMessage apply(final String protocolMessage) {
        // twin events
        if (START_SEND_EVENTS.matches(protocolMessage)) {
            return buildStartStreaming(START_SEND_EVENTS, protocolMessage);
        } else if (STOP_SEND_EVENTS.matches(protocolMessage)) {
            return new StopStreaming(StreamingType.EVENTS, connectionCorrelationId);
        }
        // live events
        else if (START_SEND_LIVE_EVENTS.matches(protocolMessage)) {
            return buildStartStreaming(START_SEND_LIVE_EVENTS, protocolMessage);
        } else if (STOP_SEND_LIVE_EVENTS.matches(protocolMessage)) {
            return new StopStreaming(StreamingType.LIVE_EVENTS, connectionCorrelationId);
        }
        // live commands
        else if (START_SEND_LIVE_COMMANDS.matches(protocolMessage)) {
            return buildStartStreaming(START_SEND_LIVE_COMMANDS, protocolMessage);
        } else if (STOP_SEND_LIVE_COMMANDS.matches(protocolMessage)) {
            return new StopStreaming(StreamingType.LIVE_COMMANDS, connectionCorrelationId);
        }
        // messages
        else if (START_SEND_MESSAGES.matches(protocolMessage)) {
            return buildStartStreaming(START_SEND_MESSAGES, protocolMessage);
        } else if (STOP_SEND_MESSAGES.matches(protocolMessage)) {
            return new StopStreaming(StreamingType.MESSAGES, connectionCorrelationId);
        } else {
            return null;
        }
    }

    private StartStreaming buildStartStreaming(final ProtocolMessages message, final String protocolMessage) {
        if (message.matchesWithParameters(protocolMessage)) {
            final Map<String, String> params = determineParams(protocolMessage);
            final List<String> namespaces = Optional.ofNullable(params.get(PARAM_NAMESPACES))
                    .filter(ids -> !ids.isEmpty())
                    .map(ids -> ids.split(","))
                    .map(Arrays::asList)
                    .orElse(Collections.emptyList());
            final String filter = params.get(PARAM_FILTER);
            return new StartStreaming(message.getStreamingType(), connectionCorrelationId, connectionAuthContext,
                    namespaces, filter);
        } else {
            return new StartStreaming(message.getStreamingType(), connectionCorrelationId, connectionAuthContext,
                    Collections.emptyList(), null);
        }
    }

    /**
     * Parses the passed {@code protocolMessage} for an optional parameters string (e.g. containing a "filter") and
     * creating a Map from it.
     *
     * @param protocolMessage the protocolMessage containing parameters, e.g.: {@code
     * START-SEND-EVENTS?filter=eq(foo,1)}
     * @return the map containing the resolved params
     */
    private static Map<String, String> determineParams(final String protocolMessage) {
        if (protocolMessage.contains(ProtocolMessages.PARAMETER_SEPARATOR)) {
            final String parametersString =
                    protocolMessage.split(Pattern.quote(ProtocolMessages.PARAMETER_SEPARATOR), 2)[1];
            return Arrays.stream(parametersString.split("&"))
                    .map(paramWithValue -> paramWithValue.split("=", 2))
                    .collect(Collectors.toMap(pv -> urlDecode(pv[0]), pv -> urlDecode(pv[1])));
        } else {
            return Collections.emptyMap();
        }
    }

    private static String urlDecode(final String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            return URLDecoder.decode(value);
        }
    }
}
