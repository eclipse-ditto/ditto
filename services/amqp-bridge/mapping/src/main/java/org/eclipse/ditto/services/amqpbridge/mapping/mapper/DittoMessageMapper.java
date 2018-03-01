/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import static org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMappers.CONTENT_TYPE_KEY;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.model.amqpbridge.MessageMappingFailedException;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;

/**
 * A message mapper implementation for the Ditto Protocol.
 * Expects messages to contain a JSON serialized Ditto Protocol message.
 */
public final class DittoMessageMapper implements MessageMapper {

    /**
     * The context representing this mapper
     */
    public static final MappingContext CONTEXT = AmqpBridgeModelFactory.newMappingContext(
            DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE,
            DittoMessageMapper.class.getCanonicalName(),
            Collections.emptyMap()
    );

    @Override
    public String getContentType() {
        return DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;
    }


    @Override
    public void configure(final MessageMapperConfiguration configuration) {
        // no op
    }


    @Override
    public Adaptable map(final ExternalMessage message) {
        final String payload = extractPayloadAsString(message);
        final JsonifiableAdaptable jsonifiableAdaptable = DittoJsonException.wrapJsonRuntimeException(() ->
                ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(payload))
        );

        final DittoHeaders mergedHeaders = mergeHeaders(message, jsonifiableAdaptable);
        return ProtocolFactory.newAdaptableBuilder(jsonifiableAdaptable).withHeaders(mergedHeaders).build();
    }


    @Override
    public ExternalMessage map(final Adaptable adaptable) {
        final ExternalMessage.MessageType messageType = MessageMappers.determineMessageType(adaptable);
        final Map<String, String> headers = new LinkedHashMap<>(adaptable.getHeaders().orElse(DittoHeaders.empty()));
        headers.put(CONTENT_TYPE_KEY, getContentType());

        final String jsonString = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable).toJsonString();

        return AmqpBridgeModelFactory.newExternalMessageBuilder(headers, messageType)
                .withText(jsonString)
                .build();
    }

    private static String extractPayloadAsString(final ExternalMessage message) {
        final Optional<String> payload;
        if (message.isTextMessage()) {
            payload = message.getTextPayload();
        } else if (message.isBytesMessage()) {
            final Charset charset = Optional.ofNullable(message.getHeaders().get(MessageMappers.CONTENT_TYPE_KEY))
                    .map(MessageMappers::determineCharset)
                    .orElse(StandardCharsets.UTF_8);
            payload = message.getBytePayload().map(ByteBuffer::array).map(ba -> new String(ba, charset));
        } else {
            payload = Optional.empty();
        }

        return payload.filter(s -> !s.isEmpty()).orElseThrow(() ->
                MessageMappingFailedException.newBuilder(message.findContentType().orElse(""))
                        .description(
                                "As payload was absent or empty, please make sure to send payload in your messages.")
                        .build());
    }

    /**
     * Merge message headers of message and adaptable. Adaptable headers do override message headers!
     *
     * @param message the message
     * @param adaptable the adaptable
     * @return the merged headers
     */
    private static DittoHeaders mergeHeaders(final ExternalMessage message, final Adaptable adaptable) {
        final Map<String, String> headers = new HashMap<>(message.getHeaders());
        adaptable.getHeaders().ifPresent(headers::putAll);
        return DittoHeaders.of(headers);
    }

}
