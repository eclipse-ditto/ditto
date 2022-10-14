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
package org.eclipse.ditto.connectivity.service.mapping;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.MessageMappingFailedException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link DittoMessageMapper}.
 */
public final class DittoMessageMapperTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private DittoMessageMapper underTest;

    @Before
    public void setUp() {
        underTest = new DittoMessageMapper(Mockito.mock(ActorSystem.class), Mockito.mock(Config.class));
    }

    @Test
    public void mapMessage() {
        final var validMapping = valid1();
        softly.assertThat(underTest.map(validMapping.getKey())).isEqualTo(validMapping.getValue());
    }

    @Test
    public void mapMessageFails() {
        final var invalidIncomingMappings = createInvalidIncomingMappings();
        invalidIncomingMappings.forEach(
                (in, e) -> softly.assertThatThrownBy(() -> underTest.map(in)).hasSameClassAs(e));
    }

    @Test
    public void mapAdaptable() {
        final var validOutgoingMappings = createValidOutgoingMappings();
        validOutgoingMappings.forEach((in, out) -> softly.assertThat(underTest.map(in)).isEqualTo(out));
    }

    @Test
    public void mapAdaptableFails() {
        final var invalidOutgoingMappings = createInvalidOutgoingMappings();
        invalidOutgoingMappings.forEach(
                (in, e) -> softly.assertThatThrownBy(() -> underTest.map(in)).hasSameClassAs(e));
    }

    private static Map.Entry<ExternalMessage, List<Adaptable>> valid1() {
        final Map<String, String> headers = new HashMap<>();
        headers.put("header-key", "header-value");
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

        // by default, the DittoMessageMapper should not automatically use all headers from the ExternalMessage
        //  those would have to be mapped by an explicit header mapping
        final ExternalMessage message = ExternalMessageFactory.newExternalMessageBuilder(
                Map.of(ExternalMessage.CONTENT_TYPE_HEADER, DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE))
                .withTopicPath(adaptable.getTopicPath())
                .withText(adaptable.toJsonString())
                .build();
        final List<Adaptable> expected =
                Collections.singletonList(ProtocolFactory.newAdaptableBuilder(adaptable).build());

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
        mappings.put(message,
                new DittoJsonException(new JsonParseException("Failed to create JSON object from string!")));

        message = ExternalMessageFactory.newExternalMessageBuilder(headers)
                .withText(String.valueOf(JsonObject.newBuilder().set("myKey", "myValue").build()))
                .build();
        mappings.put(message,
                new DittoJsonException(new JsonMissingFieldException(JsonifiableAdaptable.JsonFields.TOPIC)));

        return mappings;
    }

    private static Map<Adaptable, List<ExternalMessage>> createValidOutgoingMappings() {
        final Map<Adaptable, List<ExternalMessage>> mappings = new HashMap<>();

        final String correlationId = UUID.randomUUID().toString();
        final DittoHeaders expectedMessageHeaders = DittoHeaders.newBuilder()
                .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
                .correlationId(correlationId)
                .build();
        final DittoHeaders adaptableHeaders = DittoHeaders.newBuilder()
                .putHeader("header-key", "header-value")
                .correlationId(correlationId)
                .build();


        ThingId thingId = ThingId.of("org.eclipse.ditto:thing1");
        JsonifiableAdaptable adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder
                (ProtocolFactory.newTopicPathBuilder(thingId).things().twin().commands().modify().build())
                .withHeaders(adaptableHeaders)
                .withPayload(ProtocolFactory
                        .newPayloadBuilder(JsonPointer.of("/features"))
                        .withValue(JsonFactory.nullLiteral())
                        .build())
                .build());

        List<ExternalMessage> message =
                Collections.singletonList(ExternalMessageFactory.newExternalMessageBuilder(expectedMessageHeaders)
                        .withTopicPath(adaptable.getTopicPath())
                        .withText(adaptable.toJsonString())
                        .build());
        mappings.put(adaptable, message);

        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("topic", "org.eclipse.ditto/thing2/things/twin/commands/create")
                .set("path", "/some/path")
                .build();
        adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(json);
        adaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(ProtocolFactory.newAdaptableBuilder(adaptable)
                .withHeaders(adaptableHeaders).build());

        message = Collections.singletonList(ExternalMessageFactory.newExternalMessageBuilder(expectedMessageHeaders)
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
