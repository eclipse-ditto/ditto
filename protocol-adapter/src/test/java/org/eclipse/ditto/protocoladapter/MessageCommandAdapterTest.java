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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.protocoladapter.TestConstants.FEATURE_ID;
import static org.eclipse.ditto.protocoladapter.TestConstants.SUBJECT;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.KnownMessageSubjects;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link MessageCommandAdapter}.
 */
@RunWith(Parameterized.class)
public final class MessageCommandAdapterTest {

    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    private static final byte[] BINARY_PAYLOAD = "binary payload".getBytes(StandardCharsets.UTF_8);
    private static final String XML_PAYLOAD = "<xml>test</xml>";
    private MessageCommandAdapter underTest;

    @Parameterized.Parameters(name = "version={0}, direction={1}, type={2}, payload={3}")
    public static Collection<Object[]> data() {
        final Collection<TestPayload> payloads = Arrays.asList(
                TestPayload.of("text/plain", JsonValue.of("stringPayload"), "stringPayload", false),
                TestPayload.of(APPLICATION_JSON, JsonObject.newBuilder().set("hello", 42).set("foo", false).build()),
                TestPayload.of(APPLICATION_JSON, JsonValue.of(3.14159265359)),
                TestPayload.of(APPLICATION_JSON, JsonValue.of("payload")),
                TestPayload.of(APPLICATION_JSON, JsonValue.of(null)),
                TestPayload.of(APPLICATION_JSON, null),
                TestPayload.of(APPLICATION_OCTET_STREAM, binaryPayloadAsJson(), BINARY_PAYLOAD, true),
                TestPayload.of("text/xml", JsonValue.of(XML_PAYLOAD), XML_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                        true),
                TestPayload.of(null, null)
        );
        final Collection<String> messageTypes =
                Arrays.asList(SendThingMessage.TYPE, SendFeatureMessage.TYPE, SendClaimMessage.TYPE);

        final Collection<Object[]> params = new ArrayList<>();
        for (final JsonSchemaVersion version : JsonSchemaVersion.values()) {
            for (final MessageDirection direction : MessageDirection.values()) {
                for (final String messageType : messageTypes) {
                    for (final TestPayload payload : payloads) {
                        params.add(new Object[]{version, direction, messageType, payload});
                    }
                }
            }
        }
        return params;
    }

    private static JsonValue binaryPayloadAsJson() {
        return JsonValue.of(Base64.getEncoder().encodeToString(BINARY_PAYLOAD));
    }

    @Parameterized.Parameter
    public JsonSchemaVersion version;

    @Parameterized.Parameter(1)
    public MessageDirection direction;

    @Parameterized.Parameter(2)
    public String type;

    @Parameterized.Parameter(3)
    public TestPayload payload;

    @Before
    public void setUp() {
        underTest = MessageCommandAdapter.newInstance();
    }

    @Test
    public void testMessageFromAdaptable() {
        final String subject = subject();
        final String contentType = payload.contentType;
        final JsonPointer path = path(subject);
        final DittoHeaders theHeaders = expectedDittoHeaders(subject, contentType);

        // build expected message and message command
        final MessageHeaders messageHeaders = messageHeaders(subject, contentType);
        final Message<Object> expectedMessage = message(messageHeaders, payload.asObject);
        final MessageCommand expectedMessageCommand = messageCommand(type, expectedMessage, theHeaders);

        // build the adaptable that will be converted to a message command
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(payload.asJson)
                        .build())
                .withHeaders(theHeaders)
                .build();

        final MessageCommand actualMessageCommand = underTest.fromAdaptable(adaptable);

        assertThat(actualMessageCommand).isEqualTo(expectedMessageCommand);
    }

    private MessageHeaders messageHeaders(final CharSequence subject, final CharSequence contentType) {
        return MessageHeaders.newBuilder(direction, TestConstants.THING_ID, subject)
                .correlationId(TestConstants.CORRELATION_ID)
                .schemaVersion(version)
                .contentType(contentType)
                .featureId(SendFeatureMessage.TYPE.equals(type) ? FEATURE_ID : null)
                .build();
    }

    @Test
    public void testMessageToAdaptable() {
        final String subject = subject();
        final String contentType = payload.contentType;
        final JsonPointer path = path(subject);

        // build expected adaptable
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();
        final DittoHeaders expectedHeaders = expectedDittoHeaders(subject, contentType);

        final PayloadBuilder payloadBuilder = Payload.newBuilder(path);
        if (payload.asJson != null) {
            payloadBuilder.withValue(payload.asJson);
        }

        final Adaptable expectedAdaptable = Adaptable.newBuilder(topicPath)
                .withPayload(payloadBuilder.build())
                .withHeaders(expectedHeaders)
                .build();

        // build the message that will be converted to an adaptable
        final MessageHeaders messageHeaders = messageHeaders(subject, contentType);
        final Message<Object> theMessage = message(messageHeaders, payload.asObject);
        final DittoHeaders theHeaders = dittoHeaders();
        final MessageCommand messageCommand = messageCommand(type, theMessage, theHeaders);

        // test
        final Adaptable actual = underTest.toAdaptable(messageCommand);
        assertThat(actual).isEqualTo(expectedAdaptable);
    }

    private Message<Object> message(final MessageHeaders messageHeaders, final Object thePayload) {

        final MessageBuilder<Object> messageBuilder = Message.newBuilder(messageHeaders);
        if (thePayload != null) {
            if (payload.raw) {
                messageBuilder.rawPayload(ByteBuffer.wrap((byte[]) thePayload));
            } else {
                messageBuilder.payload(thePayload);
            }
        }
        return messageBuilder.build();
    }

    private JsonPointer path(final CharSequence subject) {
        final JsonPointer path;
        if (SendFeatureMessage.TYPE.equals(type)) {
            path = JsonFactory.newPointer(JsonKey.of("features"), JsonKey.of(FEATURE_ID));
        } else {
            path = JsonPointer.empty();
        }
        final JsonKey directionKey = (direction == MessageDirection.FROM ? JsonKey.of("outbox") : JsonKey.of("inbox"));

        return path.addLeaf(directionKey).addLeaf(JsonKey.of("messages")).addLeaf(JsonKey.of(subject));
    }

    private DittoHeaders dittoHeaders() {
        final DittoHeadersBuilder headersBuilder = DittoHeaders.newBuilder();
        headersBuilder.correlationId(TestConstants.CORRELATION_ID);
        headersBuilder.schemaVersion(version);
        return headersBuilder.build();
    }

    private DittoHeaders expectedDittoHeaders(final CharSequence subject, final CharSequence contentType) {
        final DittoHeadersBuilder headersBuilder = DittoHeaders.newBuilder();
        headersBuilder.correlationId(TestConstants.CORRELATION_ID);
        headersBuilder.schemaVersion(version);
        headersBuilder.putHeader(MessageHeaderDefinition.THING_ID.getKey(), TestConstants.THING_ID);
        headersBuilder.putHeader(MessageHeaderDefinition.SUBJECT.getKey(), subject);
        headersBuilder.putHeader(MessageHeaderDefinition.DIRECTION.getKey(), direction.name());
        if (SendFeatureMessage.TYPE.equals(type)) {
            headersBuilder.putHeader(MessageHeaderDefinition.FEATURE_ID.getKey(), FEATURE_ID);
        }
        if (contentType != null) {
            headersBuilder.putHeader(DittoHeaderDefinition.CONTENT_TYPE.getKey(), contentType);
        }
        return headersBuilder.build();
    }


    private static MessageCommand messageCommand(final String type, final Message<Object> message,
            final DittoHeaders headers) {
        switch (type) {
            case SendThingMessage.TYPE:
                return SendThingMessage.of(TestConstants.THING_ID, message, headers);
            case SendFeatureMessage.TYPE:
                return SendFeatureMessage.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, message, headers);
            case SendClaimMessage.TYPE:
                return SendClaimMessage.of(TestConstants.THING_ID, message, headers);
            default:
                throw new IllegalArgumentException(type + " not supported.");
        }
    }

    private String subject() {
        return SendClaimMessage.TYPE.equals(type) ? KnownMessageSubjects.CLAIM_SUBJECT : SUBJECT;
    }

    private static final class TestPayload {

        private final String contentType;
        private final JsonValue asJson; // json representation (how the payload is represented in a message command)
        private final Object asObject; // java representation (how the payload is represented in an adaptable)
        private final boolean raw; // should the payload be interpreted as raw (base64)

        private TestPayload(final String contentType,
                final JsonValue asJson,
                final Object asObject,
                final boolean raw) {

            this.contentType = contentType;
            this.asJson = asJson;
            this.asObject = asObject;
            this.raw = raw;
        }

        private static TestPayload of(final String contentType, final JsonValue asJson) {
            return new TestPayload(contentType, asJson, asJson, false);
        }

        private static TestPayload of(final String contentType, final JsonValue asJson, final Object asObject,
                final boolean raw) {
            return new TestPayload(contentType, asJson, asObject, raw);
        }

        @Override
        public String toString() {
            return "TestPayload{" +
                    "contentType='" + contentType + '\'' +
                    ", asJson=" + asJson +
                    ", asObject=" + asObject +
                    ", raw=" + raw +
                    '}';
        }

    }

}