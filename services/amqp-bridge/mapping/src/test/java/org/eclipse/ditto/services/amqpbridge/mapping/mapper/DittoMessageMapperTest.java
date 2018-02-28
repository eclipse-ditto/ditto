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


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
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
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


@SuppressWarnings("NullableProblems")
public class DittoMessageMapperTest {

    private DittoMessageMapper underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new DittoMessageMapper();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void mapMessage() {
        createValidIncomingMappings().forEach((in, out) -> assertThat(underTest.map(in)).isEqualTo(out));
    }

    @Test
    public void mapMessageFails() {
        createInvalidIncomingMappings().forEach((in, e) -> assertThatExceptionOfType(e.getClass()).isThrownBy(
                () -> underTest.map(in)));
    }

    @Test
    public void mapAdaptable() {
        createValidOutgoingMappings().forEach((in, out) -> assertThat(underTest.map(in)).isEqualTo(out));
    }

    @Test
    public void mapAdaptableFails() {
        createInvalidOutgoingMappings().forEach((in, e) -> assertThatExceptionOfType(e.getClass()).isThrownBy(
                () -> underTest.map(in)));
    }

    private Map<ExternalMessage, Adaptable> createValidIncomingMappings() {
        return Stream.of(
                valid1(),
                valid2()
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<ExternalMessage, Adaptable> valid1() {
        Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(MessageMapperConfigurationProperties.CONTENT_TYPE, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

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
        headers.put(MessageMapperConfigurationProperties.CONTENT_TYPE, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        JsonObject json = JsonFactory.newObjectBuilder()
                .set("path","/some/path")
                .build();

        Adaptable expected = ProtocolFactory.newAdaptableBuilder(ProtocolFactory.jsonifiableAdaptableFromJson(json))
                .withHeaders(DittoHeaders.of(headers))
                .build();
        ExternalMessage message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText(json.toString()).build();
        return new AbstractMap.SimpleEntry<ExternalMessage, Adaptable>(message, expected);
    }

    private Map<ExternalMessage, Throwable> createInvalidIncomingMappings() {
        Map<ExternalMessage, Throwable> mappings = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(MessageMappers.CONTENT_TYPE_KEY, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        ExternalMessage message;
        message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText("").build();
//        mappings.put(message, new IllegalArgumentException("Failed to extract string payload from message:"));

        // --

        message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText("{}").build();
//        mappings.put(message, new IllegalArgumentException("Failed to map '{}'",
//                new JsonMissingFieldException("/path")));

        // --

        message = AmqpBridgeModelFactory.newExternalMessageBuilderForCommand(headers).withText("no json").build();
//        mappings.put(message, DittoJsonException.wrapJsonRuntimeException(new JsonParseException("Failed to create JSON object from" +
//                " string!")).build());

        return mappings;
    }


    private Map<Adaptable, ExternalMessage> createValidOutgoingMappings() {
        Map<Adaptable, ExternalMessage> mappings = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(MessageMappers.CONTENT_TYPE_KEY, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

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

    private Map<Adaptable, Throwable> createInvalidOutgoingMappings() {
        // adaptible is strongly typed and can always be jsonified, no invalid test needed.
        return Collections.emptyMap();
    }
}
