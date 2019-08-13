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
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DittoMessageMapper}.
 */
public final class DittoMessageMapperTest {

    @SuppressWarnings("NullableProblems") private DittoMessageMapper underTest;

    @Before
    public void setUp() {
        underTest = new DittoMessageMapper();
    }

    @After
    public void tearDown() {
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

    private static Map<ExternalMessage, Optional<Adaptable>> createValidIncomingMappings() {
        return Stream.of(
                valid1(),
                valid2()
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map.Entry<ExternalMessage, Optional<Adaptable>> valid1() {
        final Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        final ThingId thingId = ThingId.of("org.eclipse.ditto:thing1");
        final JsonifiableAdaptable adaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
                        (ProtocolFactory.newTopicPathBuilder(thingId).things().twin().commands().modify().build())
                .withHeaders(DittoHeaders.of(headers))
                .withPayload(ProtocolFactory
                        .newPayloadBuilder(JsonPointer.of("/features"))
                        .withValue(JsonFactory.nullLiteral())
                        .build())
                .build());

        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withTopicPath(adaptable.getTopicPath())
                .withText(adaptable.toJsonString())
                .build();
        final Optional<Adaptable> expected = Optional.of(ProtocolFactory.newAdaptableBuilder(adaptable).build());

        return new AbstractMap.SimpleEntry<>(message, expected);
    }

    private static Map.Entry<ExternalMessage, Optional<Adaptable>> valid2() {
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
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText(json.toString())
                .build();
        return new AbstractMap.SimpleEntry<>(message, expected);
    }

    private static Map<ExternalMessage, Throwable> createInvalidIncomingMappings() {
        final Map<ExternalMessage, Throwable> mappings = new HashMap<>();

        final Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        ExternalMessage message;
        message = ExternalMessageFactory.newExternalMessageBuilder(headers).withText("").build();
        mappings.put(message, MessageMappingFailedException.newBuilder("").build());

        message =
                ExternalMessageFactory.newExternalMessageBuilder(headers).withText("{}").build();
        mappings.put(message, new DittoJsonException(new JsonMissingFieldException("/path")));

        message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText("no json")
                .build();
        mappings.put(message, new DittoJsonException(
                new JsonParseException("Failed to create JSON object from string!")));

        return mappings;
    }

    private static Map<Adaptable, Optional<ExternalMessage>> createValidOutgoingMappings() {
        final Map<Adaptable, Optional<ExternalMessage>> mappings = new HashMap<>();

        final Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
        headers.put(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);

        ThingId thingId = ThingId.of("org.eclipse.ditto:thing1");
        JsonifiableAdaptable adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
                (ProtocolFactory.newTopicPathBuilder(thingId).things().twin().commands().modify().build())
                .withHeaders(DittoHeaders.of(headers))
                .withPayload(ProtocolFactory
                        .newPayloadBuilder(JsonPointer.of("/features"))
                        .withValue(JsonFactory.nullLiteral())
                        .build())
                .build());

        Optional<ExternalMessage> message =
                Optional.of(ExternalMessageFactory.newExternalMessageBuilder(headers)
                        .withTopicPath(adaptable.getTopicPath())
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

        message = Optional.of(ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withTopicPath(adaptable.getTopicPath())
                .withText(adaptable.toJsonString())
                .build());
        mappings.put(adaptable, message);

        return mappings;
    }

    private static Map<Adaptable, Throwable> createInvalidOutgoingMappings() {
        // adaptable is strongly typed and can always be jsonified, no invalid test needed.
        return Collections.emptyMap();
    }

}
