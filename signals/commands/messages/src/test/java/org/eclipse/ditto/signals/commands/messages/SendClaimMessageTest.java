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

import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.ThingIdInvalidException;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SendClaimMessage}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class SendClaimMessageTest {

    private static final String THING_ID = "test.ns:theThingId";
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().correlationId(CORRELATION_ID).build();
    private static final String KNOWN_RAW_PAYLOAD_STR = "Those reading that are super cool dudes!$$§_ds+üä#das";

    private static final MessageHeaders MESSAGE_HEADERS = MessageHeaders.newBuilderForClaiming(THING_ID)
            .contentType("text/plain")
            .timeout(42)
            .build();

    private static final Message<?> MESSAGE = Message.newBuilder(MESSAGE_HEADERS)
            .payload(KNOWN_RAW_PAYLOAD_STR)
            .build();

    private static final Message<?> DESERIALIZED_MESSAGE = Message.newBuilder(MESSAGE_HEADERS)
            .payload(KNOWN_RAW_PAYLOAD_STR)
            .build();

    private static final JsonObject KNOWN_MESSAGE_AS_JSON = JsonFactory.newObjectBuilder()
            .set(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS, MESSAGE_HEADERS.toJson())
            .set(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD, JsonFactory.newValue(KNOWN_RAW_PAYLOAD_STR))
            .build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SendClaimMessage.TYPE)
            .set(MessageCommand.JsonFields.JSON_THING_ID, THING_ID)
            .set(MessageCommand.JsonFields.JSON_MESSAGE, KNOWN_MESSAGE_AS_JSON)
            .build();

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SendClaimMessage.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SendClaimMessage.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullThingId() {
        SendClaimMessage.of(null, MESSAGE, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullMessage() {
        SendClaimMessage.of(THING_ID, null, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullDittoHeaders() {
        SendClaimMessage.of(THING_ID, MESSAGE, null);
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryCreateWithInvalidThingId() {
        SendClaimMessage.of("foobar", MESSAGE, DITTO_HEADERS);
    }

    @Test
    public void tryCreateValidMessage() {
        SendClaimMessage.of(THING_ID, MESSAGE, DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final SendClaimMessage<?> underTest = SendClaimMessage.of(THING_ID, MESSAGE, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void toJsonDoesNotBase64EncodeTextBody() {
        final SendClaimMessage<?> underTest =
                SendClaimMessage.of(THING_ID, DESERIALIZED_MESSAGE, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SendClaimMessage<?> underTest =
                SendClaimMessage.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingId()).isEqualTo(THING_ID);
        assertThat(underTest.getMessageType()).isEqualTo(SendClaimMessage.NAME);
        assertThat(underTest.getMessage()).isEqualTo(DESERIALIZED_MESSAGE);
    }

    @Test
    public void createInstanceFromJsonWithoutPayload() {
        final Message<?> emptyMessage = Message.newBuilder(MESSAGE_HEADERS).build();
        final SendClaimMessage<?> underTest =
                SendClaimMessage.of(THING_ID, emptyMessage, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject jsonWithoutPayload = underTest.toJson();
        final SendClaimMessage<?> result =
                SendClaimMessage.fromJson(jsonWithoutPayload, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject resultJson = result.toJson();
        assertThat(resultJson).isEqualTo(jsonWithoutPayload);
    }

    @Test
    public void createResponseFromJsonWithoutPayload() {
        final Message<?> emptyMessage = Message.newBuilder(MESSAGE_HEADERS).build();
        final SendClaimMessageResponse<?> underTest = SendClaimMessageResponse.of(THING_ID, emptyMessage,
                HttpStatusCode.OK, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject jsonWithoutPayload = underTest.toJson();
        final SendClaimMessageResponse<?> result =
                SendClaimMessageResponse.fromJson(jsonWithoutPayload, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject resultJson = result.toJson();

        assertThat(resultJson).isEqualTo(jsonWithoutPayload);
    }

}
