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
package org.eclipse.ditto.messages.model.signals.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.messages.model.FeatureIdInvalidException;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.MessagesModelFactory;
import org.eclipse.ditto.messages.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SendFeatureMessage}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class SendFeatureMessageTest {

    private static final ThingId THING_ID = ThingId.of("test.ns", "theThingId");
    private static final String FEATURE_ID = "theFeatureId";
    private static final String SUBJECT = "theSubject";
    private static final String CONTENT_TYPE = "application/json";
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().correlationId(CORRELATION_ID).build();

    private static final String KNOWN_RAW_PAYLOAD_STR = "{\"some\":42}";

    private static final Message<?> MESSAGE = MessagesModelFactory.newMessageBuilder(
            MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, SUBJECT)
                    .contentType(CONTENT_TYPE)
                    .featureId(FEATURE_ID)
                    .build())
            .payload(JsonFactory.newObject(KNOWN_RAW_PAYLOAD_STR))
            .build();

    private static final Message<?> DESERIALIZED_MESSAGE = MessagesModelFactory.newMessageBuilder(
            MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, SUBJECT)
                    .contentType(CONTENT_TYPE)
                    .featureId(FEATURE_ID)
                    .build())
            .payload(JsonFactory.newObject(KNOWN_RAW_PAYLOAD_STR))
            .rawPayload(ByteBuffer.wrap(KNOWN_RAW_PAYLOAD_STR.getBytes()))
            .build();

    private static final JsonObject KNOWN_MESSAGE_AS_JSON = JsonFactory.newObjectBuilder()
            .set(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS, MESSAGE.getHeaders().toJson())
            .set(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD, JsonFactory.newObject(KNOWN_RAW_PAYLOAD_STR))
            .build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, SendFeatureMessage.TYPE)
            .set(MessageCommand.JsonFields.JSON_THING_ID, THING_ID.toString())
            .set(SendFeatureMessage.JSON_FEATURE_ID, FEATURE_ID)
            .set(MessageCommand.JsonFields.JSON_MESSAGE, KNOWN_MESSAGE_AS_JSON)
            .build();

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SendFeatureMessage.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SendFeatureMessage.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullThingId() {
        SendFeatureMessage.of(null, FEATURE_ID, MESSAGE, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullFeatureId() {
        SendFeatureMessage.of(THING_ID, null, MESSAGE, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullMessage() {
        SendFeatureMessage.of(THING_ID, FEATURE_ID, null, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullDittoHeaders() {
        SendFeatureMessage.of(THING_ID, FEATURE_ID, MESSAGE, null);
    }

    @Test
    public void tryToCreateInstanceWithNonMatchingThingId() {
        final ThingId notMatchingThingId = ThingId.of(THING_ID.getNamespace(), THING_ID.getName() + "-nomatch");
        assertThatExceptionOfType(ThingIdInvalidException.class)
                .isThrownBy(() -> SendFeatureMessage.of(notMatchingThingId, FEATURE_ID, MESSAGE, DITTO_HEADERS))
                .withMessageContaining("-nomatch")
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithMissingFeatureId() {
        final MessageHeaders messageHeaders = MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, SUBJECT)
                .contentType(CONTENT_TYPE)
                .build();
        final Message<Object> message = MessagesModelFactory.newMessageBuilder(messageHeaders)
                .payload(JsonFactory.newObject(KNOWN_RAW_PAYLOAD_STR))
                .build();

        assertThatExceptionOfType(FeatureIdInvalidException.class)
                .isThrownBy(() -> SendFeatureMessage.of(THING_ID, FEATURE_ID, message, DITTO_HEADERS))
                .withMessage("The Message did not contain a feature ID at all! Expected was feature ID <%s>.",
                        FEATURE_ID)
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNonMatchingFeatureId() {
        final String expectedFeatureId = FEATURE_ID + "-nomatch";

        assertThatExceptionOfType(FeatureIdInvalidException.class)
                .isThrownBy(() -> SendFeatureMessage.of(THING_ID, expectedFeatureId, MESSAGE, DITTO_HEADERS))
                .withMessage("The Message contained feature ID <%s>. Expected was feature ID <%s>.", FEATURE_ID,
                        expectedFeatureId)
                .withNoCause();
    }

    @Test
    public void tryCreateValidMessage() {
        SendFeatureMessage.of(THING_ID, FEATURE_ID, MESSAGE, DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final SendFeatureMessage<?> underTest =
                SendFeatureMessage.of(THING_ID, FEATURE_ID, MESSAGE, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SendFeatureMessage<?> underTest =
                SendFeatureMessage.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getEntityId()).isEqualTo(THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(FEATURE_ID);
        assertThat(underTest.getMessageType()).isEqualTo(SendFeatureMessage.NAME);
        assertThat(underTest.getMessage()).isEqualTo(DESERIALIZED_MESSAGE);
    }

}
