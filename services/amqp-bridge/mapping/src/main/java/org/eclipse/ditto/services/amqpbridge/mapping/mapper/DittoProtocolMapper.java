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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;


/**
 * Default mapper; interprets payload as stringified JSON ditto protocol message
 */
public class DittoProtocolMapper implements PayloadMapper {

    public static final String OPTION_DISABLE_CONTENT_TYPE_CHECK = "disableContentTypeCheck";


    private static final String DITTO_PROTOCOL_CONTENT_TYPE = DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;

    private static final List<String> CONTENT_TYPES = Arrays.asList(DITTO_PROTOCOL_CONTENT_TYPE);

    private boolean isContentTypeCheckDisabled = false;

    public DittoProtocolMapper() {
        this(false);
    }

    public DittoProtocolMapper(boolean isContentTypeCheckDisabled) {
        this.isContentTypeCheckDisabled = isContentTypeCheckDisabled;
    }

    @Override
    public List<String> getSupportedContentTypes() {
        return CONTENT_TYPES;
    }

    @Override
    public void configure(final PayloadMapperOptions options) {
        final Map<String,String> map = options.getAsMap();
        isContentTypeCheckDisabled = Boolean.parseBoolean(map.get(OPTION_DISABLE_CONTENT_TYPE_CHECK)); // null becomes false
    }

    @Override
    public Adaptable mapIncoming(final PayloadMapperMessage message) throws PayloadMappingException {
        if (!isContentTypeCheckDisabled && !CONTENT_TYPES.contains(message.getContentType())) {
            throw new PayloadMappingException("Unsupported content type: " + message.getContentType());
        }

        final Optional<String> data = message.getStringData();
        if (!data.isPresent()) {
            throw new PayloadMappingException("Message is empty");

//            final String stringPayload = message.getTextPayload()
//                    .orElseGet(() -> message.getBytePayload()
//                            .map(ByteBuffer::array)
//                            .map(ba -> new String(ba, StandardCharsets.UTF_8))
//                            .orElseThrow(() -> new IllegalArgumentException("The received message payload " +
//                                    "was null, which is not a valid json command.")));
        }


        // use correlationId from json payload if present
        // TODO DG rly required??
//                jsonifiableAdaptable.getHeaders()
//                        .flatMap(DittoHeaders::getCorrelationId)
//                        .ifPresent(dittoHeadersBuilder::correlationId);
        try {
            final JsonObject json = JsonFactory.newObject(data.get());
            return ProtocolFactory.jsonifiableAdaptableFromJson(json);
        } catch (Exception e ){
            throw new PayloadMappingException("Mapping failed",e);
        }

    }

    @Override
    public PayloadMapperMessage mapOutgoing(final Adaptable dittoProtocolAdaptable) throws PayloadMappingException {
        final String payload = ProtocolFactory.wrapAsJsonifiableAdaptable(dittoProtocolAdaptable)
                .toJson(dittoProtocolAdaptable.getHeaders().orElse(DittoHeaders.empty())).toString();

        return PayloadMappers.createPayloadMapperMessage(DITTO_PROTOCOL_CONTENT_TYPE, null, payload,
                dittoProtocolAdaptable.getHeaders().map(h -> (Map<String, String>)h).orElse(Collections.emptyMap()));
    }
}
