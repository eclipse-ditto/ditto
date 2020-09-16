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
package org.eclipse.ditto.services.connectivity.mapping;

import static java.util.Collections.singletonList;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;

/**
 * A message mapper implementation for the Ditto Protocol.
 * Expects messages to contain a JSON serialized Ditto Protocol message.
 */
@PayloadMapper(
        alias = {"Ditto",
                // legacy full qualified name
                "org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper"})
public final class DittoMessageMapper extends AbstractMessageMapper {


    static final JsonObject DEFAULT_OPTIONS = JsonObject.newBuilder()
            .set(MessageMapperConfiguration.CONTENT_TYPE_BLOCKLIST,
                    String.join(",", "application/vnd.eclipse-hono-empty-notification",
                            "application/vnd.eclipse-hono-dc-notification+json"))
            .build();

    /**
     * The context representing this mapper
     */
    public static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContext(
            DittoMessageMapper.class.getCanonicalName(),
            DEFAULT_OPTIONS
    );

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        final String payload = extractPayloadAsString(message);
        final JsonifiableAdaptable jsonifiableAdaptable = DittoJsonException.wrapJsonRuntimeException(() ->
                ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(payload))
        );

        final DittoHeaders mergedHeaders = jsonifiableAdaptable.getDittoHeaders();
        return singletonList(
                ProtocolFactory.newAdaptableBuilder(jsonifiableAdaptable).withHeaders(mergedHeaders).build());
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        final String jsonString = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable).toJsonString();

        final boolean isError = TopicPath.Criterion.ERRORS.equals(adaptable.getTopicPath().getCriterion());
        final boolean isResponse = adaptable.getPayload().getStatus().isPresent();
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = DittoHeaders.newBuilder()
                .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        adaptable.getHeaders().flatMap(DittoHeaders::getCorrelationId)
                .ifPresent(dittoHeadersBuilder::correlationId);
        return singletonList(
                ExternalMessageFactory.newExternalMessageBuilder(dittoHeadersBuilder.build())
                        .withTopicPath(adaptable.getTopicPath())
                        .withText(jsonString)
                        .asResponse(isResponse)
                        .asError(isError)
                        .build());
    }

    @Override
    public JsonObject getDefaultOptions() {
        return DEFAULT_OPTIONS;
    }

    private static String extractPayloadAsString(final ExternalMessage message) {
        final Optional<String> payload;
        if (message.isTextMessage()) {
            payload = message.getTextPayload();
        } else if (message.isBytesMessage()) {
            final Charset charset = determineCharset(message.getHeaders());
            payload = message.getBytePayload().map(charset::decode).map(CharBuffer::toString);
        } else {
            payload = Optional.empty();
        }

        return payload.filter(s -> !s.isEmpty()).orElseThrow(() ->
                MessageMappingFailedException.newBuilder(message.findContentType().orElse(""))
                        .description(
                                "As payload was absent or empty, please make sure to send payload in your messages.")
                        .dittoHeaders(DittoHeaders.of(message.getHeaders()))
                        .build());
    }

    private static Charset determineCharset(final Map<String, String> messageHeaders) {
        return CharsetDeterminer.getInstance().apply(messageHeaders.get(ExternalMessage.CONTENT_TYPE_HEADER));
    }

}
