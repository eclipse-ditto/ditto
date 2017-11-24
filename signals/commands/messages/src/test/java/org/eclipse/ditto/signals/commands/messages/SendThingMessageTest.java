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
package org.eclipse.ditto.signals.commands.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessagesModelFactory;
import org.eclipse.ditto.model.messages.ThingIdInvalidException;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SendThingMessage}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class SendThingMessageTest {

    private static final String THING_ID = "test.ns:theThingId";
    private static final String SUBJECT = "theSubject";
    private static final String CONTENT_TYPE = "application/xml";
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().correlationId(CORRELATION_ID).build();

    private static final String KNOWN_RAW_PAYLOAD_STR = "<some>42</some>";
    private static final byte[] KNOWN_RAW_PAYLOAD_BYTES = KNOWN_RAW_PAYLOAD_STR.getBytes(StandardCharsets.UTF_8);

    private static final Message<?> MESSAGE = MessagesModelFactory.newMessageBuilder(
            MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, SUBJECT)
                    .contentType(CONTENT_TYPE)
                    .build())
            .rawPayload(ByteBuffer.wrap(KNOWN_RAW_PAYLOAD_BYTES))
            .build();

    private static final Message<?> MESSAGE_EMPTY_PAYLOAD = MessagesModelFactory.newMessageBuilder(
            MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, SUBJECT)
                    .contentType(CONTENT_TYPE)
                    .build())
            .build();

    private static final JsonObject KNOWN_MESSAGE_AS_JSON = JsonFactory.newObjectBuilder()
            .set(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS, MESSAGE.getHeaders().toJson())
            .set(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD, JsonFactory.newValue(new String(Base64.getEncoder()
                    .encode(KNOWN_RAW_PAYLOAD_BYTES), StandardCharsets.UTF_8))
            )
            .build();

    private static final JsonObject KNOWN_EMPTY_PAYLOAD_MESSAGE_AS_JSON = JsonFactory.newObjectBuilder()
            .set(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS, MESSAGE.getHeaders().toJson())
            .build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SendThingMessage.TYPE)
            .set(MessageCommand.JsonFields.JSON_THING_ID, THING_ID)
            .set(MessageCommand.JsonFields.JSON_MESSAGE, KNOWN_MESSAGE_AS_JSON)
            .build();

    private static final JsonObject KNOWN_JSON_WITH_EMPTY_PAYLOAD = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SendThingMessage.TYPE)
            .set(MessageCommand.JsonFields.JSON_THING_ID, THING_ID)
            .set(MessageCommand.JsonFields.JSON_MESSAGE, KNOWN_EMPTY_PAYLOAD_MESSAGE_AS_JSON)
            .build();


    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SendThingMessage.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(Message.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SendThingMessage.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullThingId() {
        SendThingMessage.of(null, MESSAGE, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullMessage() {
        SendThingMessage.of(THING_ID, null, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullDittoHeaders() {
        SendThingMessage.of(THING_ID, MESSAGE, null);
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryCreateWithInvalidThingId() {
        SendThingMessage.of("foobar", MESSAGE, DITTO_HEADERS);
    }

    @Test
    public void tryCreateValidMessage() {
        SendThingMessage.of(THING_ID, MESSAGE, DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final SendThingMessage<?> underTest =
                SendThingMessage.of(THING_ID, MESSAGE, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void toJsonWithEmptyPayloadReturnsExpected() {
        final SendThingMessage<?> underTest =
                SendThingMessage.of(THING_ID, MESSAGE_EMPTY_PAYLOAD, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        System.out.println(actualJson.toString());
        System.out.println(KNOWN_JSON_WITH_EMPTY_PAYLOAD.toString());

        assertThat(actualJson).isEqualTo(KNOWN_JSON_WITH_EMPTY_PAYLOAD);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SendThingMessage<?> underTest =
                SendThingMessage.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingId()).isEqualTo(THING_ID);
        assertThat(underTest.getMessageType()).isEqualTo(SendThingMessage.NAME);
        assertThat(underTest.getMessage()).isEqualTo(MESSAGE);
    }

    @Test
    public void createInstanceFromValidJsonWithEmptyPayload() {
        final SendThingMessage<?> underTest =
                SendThingMessage.fromJson(KNOWN_JSON_WITH_EMPTY_PAYLOAD, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingId()).isEqualTo(THING_ID);
        assertThat(underTest.getMessageType()).isEqualTo(SendThingMessage.NAME);
        assertThat(underTest.getMessage()).isEqualTo(MESSAGE_EMPTY_PAYLOAD);
        assertThat(underTest.getMessage().getPayload()).isEmpty();
        assertThat(underTest.getMessage().getRawPayload()).isEmpty();
    }


    @Test
    public void toJsonWithCustomContentType() {
        final MessageHeaders headers = MessageHeaders.newBuilder(MessageDirection.TO, "the:thingId", "theSubject")
                .contentType("unknownBinaryContentType")
                .build();
        final String body = "binary message body";
        final Message<?> message = Message.newBuilder(headers).rawPayload(ByteBuffer.wrap(body.getBytes())).build();

        final SendThingMessage<?> underTest =
                SendThingMessage.of("the:thingId", message, TestConstants.EMPTY_DITTO_HEADERS);

        final JsonValue serialized = underTest.toJson(FieldType.regularOrSpecial());
        final SendThingMessage<?> deserialized =
                SendThingMessage.fromJson(serialized.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(deserialized).isEqualTo(underTest);
    }
}
