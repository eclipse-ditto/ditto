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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

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

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.gateway.service.streaming.signals.Jwt;
import org.eclipse.ditto.gateway.service.streaming.signals.StartStreaming;
import org.eclipse.ditto.gateway.service.streaming.signals.StopStreaming;
import org.eclipse.ditto.gateway.service.streaming.signals.StreamControlMessage;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;

/**
 * Extracts WebSocket Protocol message from the given payload string and returns a {@link org.eclipse.ditto.gateway.service.streaming.signals.StartStreaming},
 * {@link org.eclipse.ditto.gateway.service.streaming.signals.StopStreaming} instance or {@code null} if the payload did not contain one of the defined
 * {@link ProtocolMessageType}.
 */
final class ProtocolMessageExtractor implements Function<String, Optional<StreamControlMessage>> {

    private static final String PARAMETER_SEPARATOR = "?";
    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_NAMESPACES = "namespaces";
    private static final String PARAM_JWT = "jwtToken";
    private static final String PARAM_EXTRA_FIELDS = "extraFields";

    private final AuthorizationContext connectionAuthContext;
    private final CharSequence connectionCorrelationId;

    /**
     * Constructs a new {@code ProtocolMessageExtractor} object.
     *
     * @param connectionAuthContext the {@link AuthorizationContext} of the connection.
     * @param connectionCorrelationId the correlation ID of the connection.
     */
    ProtocolMessageExtractor(final AuthorizationContext connectionAuthContext,
            final CharSequence connectionCorrelationId) {
        this.connectionAuthContext = connectionAuthContext;
        this.connectionCorrelationId = connectionCorrelationId;
    }

    @Override
    public Optional<StreamControlMessage> apply(final String protocolMessage) {
        if (null == protocolMessage) {
            return Optional.empty();
        }

        return getProtocolMessageType(protocolMessage)
                .map(protocolMessageType -> {
                    final StreamControlMessage result;
                    if (ProtocolMessageType.JWT == protocolMessageType) {
                        result = getJwt(protocolMessage);
                    } else if (protocolMessageType.isStartSending()) {
                        result = getStartStreaming(protocolMessageType, protocolMessage);
                    } else {
                        result = getStopStreaming(protocolMessageType);
                    }
                    return result;
                });
    }

    private static Optional<ProtocolMessageType> getProtocolMessageType(final String message) {
        for (final ProtocolMessageType protocolMessage : ProtocolMessageType.values()) {
            if (message.startsWith(protocolMessage.getIdentifier())) {
                return Optional.of(protocolMessage);
            }
        }
        return Optional.empty();
    }

    private Jwt getJwt(final String protocolMessage) {
        final Map<String, String> params = determineParams(protocolMessage);
        final String tokenString = params.getOrDefault(PARAM_JWT, "");

        return Jwt.newInstance(tokenString, connectionCorrelationId);
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
        if (protocolMessage.contains(PARAMETER_SEPARATOR)) {
            final String parametersString = protocolMessage.split(Pattern.quote(PARAMETER_SEPARATOR), 2)[1];
            return Arrays.stream(parametersString.split("&"))
                    .map(paramWithValue -> paramWithValue.split("=", 2))
                    .collect(Collectors.toMap(pv -> urlDecode(pv[0]), pv -> urlDecode(pv[1])));
        }
        return Collections.emptyMap();
    }

    private static String urlDecode(final String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            return URLDecoder.decode(value);
        }
    }

    private StartStreaming getStartStreaming(final ProtocolMessageType protocolMessageType, final String protocolMsg) {
        final StreamingType streamingType = protocolMessageType.getStreamingTypeOrThrow();
        final Map<String, String> params = determineParams(protocolMsg);

        return StartStreaming.getBuilder(streamingType, connectionCorrelationId, connectionAuthContext)
                .withNamespaces(getNamespaces(params.get(PARAM_NAMESPACES)))
                .withFilter(params.get(PARAM_FILTER))
                .withExtraFields(getExtraFields(params.get(PARAM_EXTRA_FIELDS)))
                .withCorrelationId(params.get(DittoHeaderDefinition.CORRELATION_ID.getKey()))
                .build();
    }

    private static List<String> getNamespaces(@Nullable final String namespacesParam) {
        if (null != namespacesParam && !namespacesParam.isEmpty()) {
            return Arrays.asList(namespacesParam.split(","));
        }
        return Collections.emptyList();
    }

    @Nullable
    private static ThingFieldSelector getExtraFields(@Nullable final String extraFieldsParam) {
        if (null != extraFieldsParam && !extraFieldsParam.isEmpty()) {
            return ThingFieldSelector.fromString(extraFieldsParam);
        }
        return null;
    }

    private StopStreaming getStopStreaming(final ProtocolMessageType protocolMessageType) {
        return new StopStreaming(protocolMessageType.getStreamingTypeOrThrow(), connectionCorrelationId);
    }

}
