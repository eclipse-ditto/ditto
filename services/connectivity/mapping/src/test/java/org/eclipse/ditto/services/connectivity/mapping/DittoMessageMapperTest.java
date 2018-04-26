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
package org.eclipse.ditto.services.connectivity.mapping;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class DittoMessageMapperTest {

    private static final String EXPECTED_TOPIC_PATH = "/things/twin/commands/";

    @SuppressWarnings("NullableProblems") private DittoMessageMapper underTest;

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

    private Map<ExternalMessage, Optional<Adaptable>> createValidIncomingMappings() {
        return Stream.of(
                valid1(),
                valid2()
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<ExternalMessage, Optional<Adaptable>> valid1() {
        final Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        final JsonifiableAdaptable adaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
                        (ProtocolFactory.newTopicPathBuilder("org.eclipse.ditto" +
                                ":thing1").things().twin().commands().modify().build())
                .withHeaders(DittoHeaders.of(headers))
                .withPayload(ProtocolFactory
                        .newPayloadBuilder(JsonPointer.of("/features"))
                        .withValue(JsonFactory.nullLiteral())
                        .build())
                .build());

        final ExternalMessage message = ConnectivityModelFactory.newExternalMessageBuilder(headers, EXPECTED_TOPIC_PATH)
                .withText(adaptable.toJsonString())
                .build();
        final Optional<Adaptable> expected = Optional.of(ProtocolFactory.newAdaptableBuilder(adaptable).build());

        return new AbstractMap.SimpleEntry<>(message, expected);
    }

    private Map.Entry<ExternalMessage, Optional<Adaptable>> valid2() {
        final Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("path","/some/path")
                .build();

        final Optional<Adaptable> expected = Optional.ofNullable(
                ProtocolFactory.newAdaptableBuilder(ProtocolFactory.jsonifiableAdaptableFromJson(json))
                        .withHeaders(DittoHeaders.of(headers))
                        .build());
        final ExternalMessage message = ConnectivityModelFactory.newExternalMessageBuilder(headers, EXPECTED_TOPIC_PATH)
                .withText(json.toString())
                .build();
        return new AbstractMap.SimpleEntry<>(message, expected);
    }

    private Map<ExternalMessage, Throwable> createInvalidIncomingMappings() {
        final Map<ExternalMessage, Throwable> mappings = new HashMap<>();

        final Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        ExternalMessage message;
        message = ConnectivityModelFactory.newExternalMessageBuilder(headers, EXPECTED_TOPIC_PATH).withText("").build();
        mappings.put(message, MessageMappingFailedException.newBuilder("").build());

        message =
                ConnectivityModelFactory.newExternalMessageBuilder(headers, EXPECTED_TOPIC_PATH).withText("{}").build();
        mappings.put(message, new DittoJsonException(new JsonMissingFieldException("/path")));

        message = ConnectivityModelFactory.newExternalMessageBuilder(headers, EXPECTED_TOPIC_PATH)
                .withText("no json")
                .build();
        mappings.put(message, new DittoJsonException(
                new JsonParseException("Failed to create JSON object from string!")));

        return mappings;
    }


    private Map<Adaptable, Optional<ExternalMessage>> createValidOutgoingMappings() {
        final Map<Adaptable, Optional<ExternalMessage>> mappings = new HashMap<>();

        final Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        JsonifiableAdaptable adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
                (ProtocolFactory.newTopicPathBuilder("org.eclipse.ditto" +
                        ":thing1").things().twin().commands().modify().build())
                .withHeaders(DittoHeaders.of(headers))
                .withPayload(ProtocolFactory
                        .newPayloadBuilder(JsonPointer.of("/features"))
                        .withValue(JsonFactory.nullLiteral())
                        .build())
                .build());

        Optional<ExternalMessage> message =
                Optional.of(ConnectivityModelFactory.newExternalMessageBuilder(headers,
                        expectedPath("org.eclipse.ditto/thing1", "modify"))
                        .withText(adaptable.toJsonString())
                        .build());
        mappings.put(adaptable, message);

        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("topic", "org.eclipse.ditto/thing2/things/twin/commands/create")
                .set("path","/some/path")
                .build();
        adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder(adaptable)
                .withHeaders(DittoHeaders.of(headers)).build());

        message = Optional.of(ConnectivityModelFactory.newExternalMessageBuilder(headers,
                expectedPath("org.eclipse.ditto/thing2", "create"))
                .withText(adaptable.toJsonString())
                .build());
        mappings.put(adaptable, message);

        return mappings;
    }

    private String expectedPath(final String id, final String action) {
        return id + EXPECTED_TOPIC_PATH + action;
    }

    private Map<Adaptable, Throwable> createInvalidOutgoingMappings() {
        // adaptable is strongly typed and can always be jsonified, no invalid test needed.
        return Collections.emptyMap();
    }
}
