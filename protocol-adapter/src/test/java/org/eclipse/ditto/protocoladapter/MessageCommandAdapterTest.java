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
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link MessageCommandAdapter}.
 */
public class MessageCommandAdapterTest {

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
        final DittoHeaders theHeaders = buildMessageHeaders(headers.toBuilder(), messageDirection, subject, contentType);

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
    public void sendThingMessageJsonFromAdaptable() {
        final MessageDirection messageDirection = MessageDirection.FROM;
        final String subject = "json-yeah";
        final JsonObject payload = JsonObject.newBuilder().set("hello", 42).set("foo", false).build();
        final String contentType = "application/json";

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
        final DittoHeaders theHeaders = buildMessageHeaders(headers.toBuilder(), messageDirection, subject, contentType);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(payload)
                        .build())
                .withHeaders(theHeaders)
                .build();
        final MessageCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void sendThingMessageToAdaptable() {
        final MessageDirection messageDirection = MessageDirection.FROM;
        final String subject = "newMsg:stuff";
        final JsonObject payload = JsonObject.newBuilder().set("hello", 42).set("foo", false).build();
        final String contentType = "application/json";

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();
        final JsonPointer path = JsonPointer.of("/outbox/messages/" + subject);

        final DittoHeaders expectedHeaders = buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                messageDirection, subject, contentType);
        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(payload)
                        .build())
                .withHeaders(expectedHeaders)
                .build();

        final Message theMessage = Message.newBuilder(
                MessageHeaders.newBuilder(messageDirection, TestConstants.THING_ID, subject)
                        .contentType(contentType)
                        .correlationId("correlationId")
                        .schemaVersion(JsonSchemaVersion.V_2)
                        .build()
        )
                .payload(payload)
                .build();
        final DittoHeaders theHeaders = buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                messageDirection, subject, contentType);
        final SendThingMessage sendThingMessage = SendThingMessage.of(
                TestConstants.THING_ID,
                theMessage,
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
