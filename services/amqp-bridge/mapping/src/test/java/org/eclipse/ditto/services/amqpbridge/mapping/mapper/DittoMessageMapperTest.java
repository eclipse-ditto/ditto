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


import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;

public class DittoMessageMapperTest extends MessageMapperTest {

    @Override
    protected MessageMapper createMapper() {
        return new DittoMessageMapper(DefaultMessageMapperOptions.empty());
    }

    @Override
    protected String createSupportedContentType() {
        return DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;
    }

    @Override
    protected List<DefaultMessageMapperOptions> createValidConfig() {
        List<DefaultMessageMapperOptions> options = new LinkedList<>();
//        options.add(MessageMapperConfiguration.from(Collections.emptyMap()));

        Arrays.asList("true", "false", null, "asdfjökla", "").forEach(s -> {
                    Map<String, String> map = new HashMap<>();
                    map.put(MessageMapper.OPT_CONTENT_TYPE_REQUIRED, s);
                    map.put(MessageMapper.OPT_CONTENT_TYPE, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
                    options.add(DefaultMessageMapperOptions.from(map));
                }
        );

        return options;
    }

    @Override
    protected Map<DefaultMessageMapperOptions, Throwable> createInvalidConfig() {
        // there are none
        Map<DefaultMessageMapperOptions, Throwable> map = new HashMap<>();
        map.put(DefaultMessageMapperOptions.empty(), new IllegalArgumentException("Missing option <contentType>"));
        return map;
    }

    @Override
    protected DefaultMessageMapperOptions createIncomingConfig() {
        Map<String, String> map = new HashMap<>();
        map.put(MessageMapper.OPT_CONTENT_TYPE_REQUIRED, String.valueOf(true));
        map.put(MessageMapper.OPT_CONTENT_TYPE, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        return DefaultMessageMapperOptions.from(map);
    }

    @Override
    protected Map<ExternalMessage, Adaptable> createValidIncomingMappings() {
        return Stream.of(
                valid1(),
                valid2()
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<ExternalMessage, Adaptable> valid1() {
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

        ExternalMessage message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText(adaptable.toJsonString()).build();
        Adaptable expected = ProtocolFactory.newAdaptableBuilder(adaptable).build();

        return new AbstractMap.SimpleEntry<>(message, expected);
    }

    private Map.Entry<ExternalMessage, Adaptable> valid2() {
        Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(MessageMapper.CONTENT_TYPE_KEY, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        JsonObject json = JsonFactory.newObjectBuilder()
                .set("path","/some/path")
                .build();

        Adaptable expected = ProtocolFactory.newAdaptableBuilder(ProtocolFactory.jsonifiableAdaptableFromJson(json))
                .withHeaders(DittoHeaders.of(headers))
                .build();
        ExternalMessage message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText(json.toString()).build();
        return new AbstractMap.SimpleEntry<ExternalMessage, Adaptable>(message, expected);
    }

    @Override
    protected Map<ExternalMessage, Throwable> createInvalidIncomingMappings() {
        Map<ExternalMessage, Throwable> mappings = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(MessageMapper.CONTENT_TYPE_KEY, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        ExternalMessage message;
        message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText("").build();
        mappings.put(message, new IllegalArgumentException("Failed to extract string payload from message:"));

        // --

        message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText("{}").build();
        mappings.put(message, new IllegalArgumentException("Failed to map '{}'",
                new JsonMissingFieldException("/path")));

        // --

        message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText("no json").build();
        mappings.put(message, new IllegalArgumentException("Failed to map 'no json'",
                new JsonParseException("Failed to create JSON object from string!")));

        return mappings;
    }

    @Override
    protected DefaultMessageMapperOptions createOutgoingConfig() {
        Map<String, String> map = new HashMap<>();
        map.put(MessageMapper.OPT_CONTENT_TYPE_REQUIRED, String.valueOf(true));
        map.put(MessageMapper.OPT_CONTENT_TYPE, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        return DefaultMessageMapperOptions.from(map);
    }

    @Override
    protected Map<Adaptable, ExternalMessage> createValidOutgoingMappings() {
        Map<Adaptable, ExternalMessage> mappings = new HashMap<>();

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

        ExternalMessage message =
                AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText(adaptable.toJsonString()).build();
        mappings.put(adaptable, message);

        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("topic", "org.eclipse.ditto/thing1/things/twin/commands/create")
                .set("path","/some/path")
                .build();
        adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder(adaptable)
                .withHeaders(DittoHeaders.of(headers)).build());

        message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText(adaptable.toJsonString()).build();
        mappings.put(adaptable, message);

        return mappings;
    }

    @Override
    protected Map<Adaptable, Throwable> createInvalidOutgoingMappings() {
        // adaptible is strongly typed and can always be jsonified, no invalid test needed.
        return Collections.emptyMap();
    }
}
