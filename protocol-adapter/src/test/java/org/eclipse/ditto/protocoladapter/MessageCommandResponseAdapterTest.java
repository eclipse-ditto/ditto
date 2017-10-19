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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendThingMessageResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link MessageCommandResponseAdapter}.
 */
public class MessageCommandResponseAdapterTest {

    private MessageCommandResponseAdapter underTest;

    @Before
    public void setUp() throws Exception {
        underTest = MessageCommandResponseAdapter.newInstance();
    }

    @Test
    public void sendThingMessageResponsePlainTextFromAdaptable() {
        final MessageDirection messageDirection = MessageDirection.TO;
        final String subject = "foo";
        final String payload = "Huhu!";
        final String contentType = "text/plain";
        final HttpStatusCode statusCode = HttpStatusCode.UNPROCESSABLE_ENTITY;

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
        final SendThingMessageResponse expected = SendThingMessageResponse.of(
                TestConstants.THING_ID,
                expectedMessage,
                statusCode,
                expectedHeaders);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();

        final JsonPointer path = JsonPointer.of("/inbox/messages/" + subject);

        final DittoHeaders headers = TestConstants.HEADERS_V_2;
        final DittoHeaders theHeaders = buildMessageHeaders(headers.toBuilder(), messageDirection, subject, contentType);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(statusCode)
                        .withValue(JsonValue.of(payload))
                        .build())
                .withHeaders(theHeaders)
                .build();
        final MessageCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void sendThingMessageResponseJsonFromAdaptable() {
        final MessageDirection messageDirection = MessageDirection.FROM;
        final String subject = "json-yeah";
        final JsonObject payload = JsonObject.newBuilder().set("hello", 42).set("foo", false).build();
        final String contentType = "application/json";
        final HttpStatusCode statusCode = HttpStatusCode.OK;

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
        final SendThingMessageResponse expected = SendThingMessageResponse.of(
                TestConstants.THING_ID,
                expectedMessage,
                statusCode,
                expectedHeaders);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();

        final JsonPointer path = JsonPointer.of("/inbox/messages/" + subject);

        final DittoHeaders headers = TestConstants.HEADERS_V_2;
        final DittoHeaders theHeaders = buildMessageHeaders(headers.toBuilder(), messageDirection, subject, contentType);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(statusCode)
                        .withValue(payload)
                        .build())
                .withHeaders(theHeaders)
                .build();
        final MessageCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void sendThingMessageResponseToAdaptable() {
        final MessageDirection messageDirection = MessageDirection.FROM;
        final String subject = "newMsg:stuff";
        final JsonObject payload = JsonObject.newBuilder().set("hello", 42).set("foo", false).build();
        final String contentType = "application/json";
        final HttpStatusCode statusCode = HttpStatusCode.NOT_ACCEPTABLE;

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();
        final JsonPointer path = JsonPointer.of("/outbox/messages/" + subject);

        final DittoHeaders expectedHeaders = MessageHeaders.of(buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                messageDirection, subject, contentType)).toBuilder().statusCode(statusCode).build();
        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(statusCode)
                        .withValue(payload)
                        .build())
                .withHeaders(expectedHeaders)
                .build();

        final Message theMessage = Message.newBuilder(
                MessageHeaders.newBuilder(messageDirection, TestConstants.THING_ID, subject)
                        .contentType(contentType)
                        .correlationId("correlationId")
                        .statusCode(statusCode)
                        .schemaVersion(JsonSchemaVersion.V_2)
                        .build()
        )
                .payload(payload)
                .build();
        final DittoHeaders theHeaders = buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                messageDirection, subject, contentType);
        final SendThingMessageResponse sendThingMessage = SendThingMessageResponse.of(
                TestConstants.THING_ID,
                theMessage,
                statusCode,
                theHeaders);

        final Adaptable actual = underTest.toAdaptable(sendThingMessage);

        assertThat(actual).isEqualTo(expected);
    }

    private DittoHeaders buildMessageHeaders(final DittoHeadersBuilder headersBuilder,
            final MessageDirection messageDirection, final String subject, final String contentType) {
        headersBuilder.putHeader(MessageHeaderDefinition.THING_ID.getKey(), TestConstants.THING_ID);
        headersBuilder.putHeader(MessageHeaderDefinition.SUBJECT.getKey(), subject);
        headersBuilder.putHeader(MessageHeaderDefinition.DIRECTION.getKey(), messageDirection.name());
        headersBuilder.putHeader(DittoHeaderDefinition.CONTENT_TYPE.getKey(), contentType);
        return headersBuilder.build();
    }

}
