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

import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessageHeadersBuilder;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link MessageCommandAdapter}.
 */
public class MessageCommandAdapterTest {

    private static final String APPLICATION_JSON = "application/json";
    private MessageCommandAdapter underTest;

    @Before
    public void setUp() throws Exception {
        underTest = MessageCommandAdapter.newInstance();
    }

    @Test
    public void sendThingMessagePlainTextFromAdaptable() {
        final MessageDirection messageDirection = MessageDirection.TO;
        final String subject = "foo";
        final String payload = "Huhu!";
        final String contentType = "text/plain";

        final Message expectedMessage = Message.newBuilder(
                MessageHeaders.newBuilder(messageDirection, TestConstants.THING_ID, subject)
                        .contentType(contentType)
                        .correlationId("correlationId")
                        .schemaVersion(JsonSchemaVersion.V_2)
                        .build()
        )
                .payload(payload)
                .build();
        final DittoHeaders expectedHeaders = buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                messageDirection, subject, contentType);
        final SendThingMessage expected = SendThingMessage.of(
                TestConstants.THING_ID,
                expectedMessage,
                expectedHeaders);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();

        final JsonPointer path = JsonPointer.of("/inbox/messages/" + subject);

        final DittoHeaders headers = TestConstants.HEADERS_V_2;
        final DittoHeaders theHeaders =
                buildMessageHeaders(headers.toBuilder(), messageDirection, subject, contentType);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonValue.of(payload))
                        .build())
                .withHeaders(theHeaders)
                .build();
        final MessageCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void sendThingMessageWithJsonObjectPayloadFromAdaptable() {
        final JsonObject payload = JsonObject.newBuilder().set("hello", 42).set("foo", false).build();
        sendThingMessageJsonFromAdaptable(payload, APPLICATION_JSON);
    }

    @Test
    public void sendThingMessageWithJsonStringPayloadFromAdaptable() {
        sendThingMessageJsonFromAdaptable(JsonValue.of("payload"), APPLICATION_JSON);
    }

    @Test
    public void sendThingMessageWithJsonNumberPayloadFromAdaptable() {
        sendThingMessageJsonFromAdaptable(JsonValue.of(3.14159265359), APPLICATION_JSON);
    }

    @Test
    public void sendThingMessageWithJsonBooleanPayloadFromAdaptable() {
        sendThingMessageJsonFromAdaptable(JsonValue.of(true), APPLICATION_JSON);
    }

    @Test
    public void sendThingMessageWithEmptyRawPayloadFromAdaptable() {
        sendThingMessageJsonFromAdaptable(null, null);
    }

    @Test
    public void sendThingMessageWithEmptyJsonPayloadFromAdaptable() {
        sendThingMessageJsonFromAdaptable(null, APPLICATION_JSON);
    }

    private void sendThingMessageJsonFromAdaptable(final JsonValue payload, final String contentType) {
        final MessageDirection messageDirection = MessageDirection.FROM;
        final String subject = "json-yeah";

        // build the expected message
        final MessageHeadersBuilder messageHeadersBuilder =
                MessageHeaders.newBuilder(messageDirection, TestConstants.THING_ID, subject)
                        .correlationId("correlationId")
                        .schemaVersion(JsonSchemaVersion.V_2);
        Optional.ofNullable(contentType).ifPresent(messageHeadersBuilder::contentType);
        final MessageBuilder<Object> messageBuilder = Message.newBuilder(messageHeadersBuilder.build());
        Optional.ofNullable(payload).ifPresent(messageBuilder::payload);
        final Message<Object> expectedMessage = messageBuilder.build();

        final DittoHeaders expectedHeaders = buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                messageDirection, subject, contentType);
        final SendThingMessage expected = SendThingMessage.of(
                TestConstants.THING_ID,
                expectedMessage,
                expectedHeaders);

        // build the adaptable that is vonverted to a message
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();

        final JsonPointer path = JsonPointer.of("/inbox/messages/" + subject);

        final DittoHeaders headers = TestConstants.HEADERS_V_2;
        final DittoHeaders theHeaders =
                buildMessageHeaders(headers.toBuilder(), messageDirection, subject, contentType);

        final AdaptableBuilder adaptableBuilder = Adaptable.newBuilder(topicPath);
        final PayloadBuilder payloadBuilder = Payload.newBuilder(path);
        Optional.ofNullable(payload).ifPresent(p -> payloadBuilder.withValue(payload));
        final Adaptable adaptable =
                adaptableBuilder.withHeaders(theHeaders).withPayload(payloadBuilder.build()).build();

        // test
        final MessageCommand actual = underTest.fromAdaptable(adaptable);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void sendThingMessageWithNumberToAdaptable() {
        sendThingMessageWithPayloadToAdaptable(JsonFactory.newValue(3.14159265359), APPLICATION_JSON);
    }

    @Test
    public void sendThingMessageWithBooelanToAdaptable() {
        sendThingMessageWithPayloadToAdaptable(JsonFactory.newValue(true), APPLICATION_JSON);
    }

    @Test
    public void sendThingMessageWithJsonObjectToAdaptable() {
        final JsonObject payload = JsonObject.newBuilder().set("hello", 42).set("foo", false).build();
        sendThingMessageWithPayloadToAdaptable(payload, APPLICATION_JSON);
    }

    @Test
    public void sendThingMessageWithJsonStringPayloadToAdaptable() {
        sendThingMessageWithPayloadToAdaptable(JsonFactory.newValue("payload"), APPLICATION_JSON);
    }

    @Test
    public void sendThingMessageWithEmptyRawPayloadToAdaptable() {
        sendThingMessageWithPayloadToAdaptable(null, null);
    }

    @Test
    public void sendThingMessageWithEmptyJsonPayloadToAdaptable() {
        sendThingMessageWithPayloadToAdaptable(null, APPLICATION_JSON);
    }

    private void sendThingMessageWithPayloadToAdaptable(JsonValue payload, final String contentType) {
        final MessageDirection messageDirection = MessageDirection.FROM;
        final String subject = "newMsg:stuff";

        // build expected adaptable
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();
        final JsonPointer path = JsonPointer.of("/outbox/messages/" + subject);

        final DittoHeaders expectedHeaders = buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                messageDirection, subject, contentType);
        final PayloadBuilder payloadBuilder = Payload.newBuilder(path);
        Optional.ofNullable(payload).ifPresent(payloadBuilder::withValue);
        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(payloadBuilder.build())
                .withHeaders(expectedHeaders)
                .build();

        // build the message that will be converted to an adaptable
        final MessageHeadersBuilder messageHeadersBuilder =
                MessageHeaders.newBuilder(messageDirection, TestConstants.THING_ID, subject);
        messageHeadersBuilder.correlationId("correlationId").schemaVersion(JsonSchemaVersion.V_2);
        Optional.ofNullable(contentType).ifPresent(messageHeadersBuilder::contentType);
        final MessageBuilder<Object> messageBuilder = Message.newBuilder(messageHeadersBuilder.build());
        Optional.ofNullable(payload).ifPresent(messageBuilder::payload);
        final Message<Object> theMessage = messageBuilder.build();
        final DittoHeaders theHeaders = buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                messageDirection, subject, contentType);

        final SendThingMessage sendThingMessage = SendThingMessage.of(
                TestConstants.THING_ID,
                theMessage,
                theHeaders);

        // test
        final Adaptable actual = underTest.toAdaptable(sendThingMessage);
        assertThat(actual).isEqualTo(expected);
    }

    private DittoHeaders buildMessageHeaders(final DittoHeadersBuilder headersBuilder,
            final MessageDirection messageDirection, final String subject, final String contentType) {
        headersBuilder.putHeader(MessageHeaderDefinition.THING_ID.getKey(), TestConstants.THING_ID);
        headersBuilder.putHeader(MessageHeaderDefinition.SUBJECT.getKey(), subject);
        headersBuilder.putHeader(MessageHeaderDefinition.DIRECTION.getKey(), messageDirection.name());
        if (contentType != null) {
            headersBuilder.putHeader(DittoHeaderDefinition.CONTENT_TYPE.getKey(), contentType);
        }
        return headersBuilder.build();
    }

}
