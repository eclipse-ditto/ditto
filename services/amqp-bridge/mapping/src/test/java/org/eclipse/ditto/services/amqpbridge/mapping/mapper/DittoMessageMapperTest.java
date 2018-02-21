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


import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;

public class DittoMessageMapperTest extends MessageMapperTest {

    @Override
    protected MessageMapper createMapper() {
        return new DittoMessageMapper(MessageMapperConfiguration.empty());
    }

    @Override
    protected String createSupportedContentType() {
        return DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;
    }

    @Override
    protected List<MessageMapperConfiguration> createValidConfig() {
        List<MessageMapperConfiguration> options = new LinkedList<>();
//        options.add(MessageMapperConfiguration.from(Collections.emptyMap()));

        Arrays.asList("true", "false", null, "asdfjökla", "").forEach(s -> {
                    Map<String, String> map = new HashMap<>();
                    map.put(MessageMapper.OPT_CONTENT_TYPE_REQUIRED, s);
                    map.put(MessageMapper.OPT_CONTENT_TYPE, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
                    options.add(MessageMapperConfiguration.from(map));
                }
        );

        return options;
    }

    @Override
    protected Map<MessageMapperConfiguration, Throwable> createInvalidConfig() {
        // there are none
        Map<MessageMapperConfiguration, Throwable> map = new HashMap<>();
        map.put(MessageMapperConfiguration.empty(), new IllegalArgumentException("Missing option <contentType>"));
        return map;
    }

    @Override
    protected MessageMapperConfiguration createIncomingConfig() {
        Map<String, String> map = new HashMap<>();
        map.put(MessageMapper.OPT_CONTENT_TYPE_REQUIRED, String.valueOf(true));
        map.put(MessageMapper.OPT_CONTENT_TYPE, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        return MessageMapperConfiguration.from(map);
    }

    @Override
    protected Map<InternalMessage, Adaptable> createValidIncomingMappings() {
        Map<InternalMessage, Adaptable> mappings = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(MessageMapper.CONTENT_TYPE_KEY, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        JsonifiableAdaptable adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
                (ProtocolFactory.newTopicPathBuilder("asd" +
                        ":jkl").things().twin().commands().modify().build())
                .withHeaders(DittoHeaders.of(headers))
                .withPayload(ProtocolFactory
                        .newPayloadBuilder(JsonPointer.of("/features"))
                        .withValue(JsonFactory.nullLiteral())
                        .build())
                .build());

        InternalMessage message = new InternalMessage.Builder(headers).withText(adaptable.toJsonString()).build();
        mappings.put(message, adaptable);

        JsonObject json = JsonFactory.newObjectBuilder()
                .set("path","/some/path")
                .build();
        message = new InternalMessage.Builder(headers).withText(json.toString()).build();
        mappings.put(message, ProtocolFactory.jsonifiableAdaptableFromJson(json));

        return mappings;
    }

    @Override
    protected Map<InternalMessage, Throwable> createInvalidIncomingMappings() {
        Map<InternalMessage, Throwable> mappings = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(MessageMapper.CONTENT_TYPE_KEY, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        InternalMessage message;
        message = new InternalMessage.Builder(headers).withText("").build();
        mappings.put(message, new IllegalArgumentException("Message contains no payload"));

        // --

        message = new InternalMessage.Builder(headers).withText("{}").build();
        mappings.put(message, new IllegalArgumentException("Failed to map '{}'",
                new JsonMissingFieldException("/path")));

        // --

        message = new InternalMessage.Builder(headers).withText("no json").build();
        mappings.put(message, new IllegalArgumentException("Failed to map 'no json'",
                new JsonParseException("Failed to create JSON object from string!")));

        return mappings;
    }

    @Override
    protected MessageMapperConfiguration createOutgoingConfig() {
        Map<String, String> map = new HashMap<>();
        map.put(MessageMapper.OPT_CONTENT_TYPE_REQUIRED, String.valueOf(true));
        map.put(MessageMapper.OPT_CONTENT_TYPE, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        return MessageMapperConfiguration.from(map);
    }

    @Override
    protected Map<Adaptable, InternalMessage> createValidOutgoingMappings() {
        Map<Adaptable, InternalMessage> mappings = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(MessageMapper.CONTENT_TYPE_KEY, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        JsonifiableAdaptable adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
                (ProtocolFactory.newTopicPathBuilder("asd" +
                        ":jkl").things().twin().commands().modify().build())
                .withHeaders(DittoHeaders.of(headers))
                .withPayload(ProtocolFactory
                        .newPayloadBuilder(JsonPointer.of("/features"))
                        .withValue(JsonFactory.nullLiteral())
                        .build())
                .build());

        InternalMessage message = new InternalMessage.Builder(headers).withText(adaptable.toJsonString()).build();
        mappings.put(adaptable, message);

        JsonObject json = JsonFactory.newObjectBuilder()
                .set("path","/some/path")
                .build();
        adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder(adaptable)
                .withHeaders(DittoHeaders.of(headers)).build());

        message = new InternalMessage.Builder(headers).withText(adaptable.toJsonString()).build();
        mappings.put(adaptable, message);

        return mappings;
    }

    @Override
    protected Map<Adaptable, Throwable> createInvalidOutgoingMappings() {
        // adaptible is strongly typed and can always be jsonified, no invalid test needed.
        return Collections.emptyMap();
    }
}