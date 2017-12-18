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
import static org.eclipse.ditto.protocoladapter.TestConstants.CORRELATION_ID;
import static org.eclipse.ditto.protocoladapter.TestConstants.FEATURE_ID;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.KnownMessageSubjects;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessageHeadersBuilder;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;
import org.eclipse.ditto.signals.commands.messages.SendThingMessageResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link MessageCommandResponseAdapter}.
 */
@RunWith(Parameterized.class)
public final class MessageCommandResponseAdapterTest {

    private MessageCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = MessageCommandResponseAdapter.newInstance();
    }

    @Parameterized.Parameters(name = "type={0}")
    public static Collection<Object[]> data() {
        return Stream.of(SendThingMessageResponse.TYPE, SendFeatureMessageResponse.TYPE, SendClaimMessageResponse.TYPE,
                SendMessageAcceptedResponse.TYPE)
                .map(type -> new Object[]{type})
                .collect(Collectors.toList());
    }

    @Parameterized.Parameter
    public String type;

    @Test
    public void sendMessageResponsePlainTextFromAdaptable() {
        final MessageDirection messageDirection = MessageDirection.TO;
        final String subject = subject();
        final String payload = "Huhu!";
        final String contentType = "text/plain";

        sendMessageResponseFromAdaptable(messageDirection, subject, JsonValue.of(payload), payload, contentType);
    }

    @Test
    public void sendMessageResponseJsonFromAdaptable() {
        final MessageDirection messageDirection = MessageDirection.FROM;
        final String subject = subject();
        final JsonObject payload = JsonObject.newBuilder().set("hello", 42).set("foo", false).build();
        final String contentType = "application/json";

        sendMessageResponseFromAdaptable(messageDirection, subject, payload, payload, contentType);
    }

    private void sendMessageResponseFromAdaptable(final MessageDirection messageDirection,
            final String subject,
            final JsonValue jsonPayload,
            final Object javaPayload,
            final CharSequence contentType) {

        final HttpStatusCode statusCode = HttpStatusCode.OK;

        final MessageHeadersBuilder messageHeadersBuilder =
                MessageHeaders.newBuilder(messageDirection, TestConstants.THING_ID, subject)
                        .contentType(contentType)
                        .correlationId(CORRELATION_ID)
                        .featureId(isFeatureResponse() ? FEATURE_ID : null)
                        .schemaVersion(JsonSchemaVersion.V_2);
        if (isAcceptedResponse()) {
            messageHeadersBuilder.responseRequired(false);
        }
        final Message<Object> expectedMessage = Message.newBuilder(messageHeadersBuilder.build())
                .payload(javaPayload)
                .build();
        final DittoHeaders expectedHeaders = buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                messageDirection, subject, contentType);
        final MessageCommandResponse messageCommandResponse = messageCommandResponse(expectedMessage, expectedHeaders);

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
                        .withStatus(statusCode)
                        .withValue(jsonPayload)
                        .build())
                .withHeaders(theHeaders)
                .build();
        final MessageCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(messageCommandResponse);
    }

    private String subject() {
        return SendClaimMessageResponse.TYPE.equals(type) ? KnownMessageSubjects.CLAIM_SUBJECT : "json-yeah";
    }

    @Test
    public void sendMessageResponseToAdaptable() {
        final MessageDirection messageDirection = MessageDirection.FROM;
        final String subject = "newMsg:stuff";
        final JsonObject payload = JsonObject.newBuilder().set("hello", 42).set("foo", false).build();
        final String contentType = "application/json";
        final HttpStatusCode statusCode = HttpStatusCode.OK;

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();
        final JsonPointer path = JsonPointer.of("/outbox/messages/" + subject);

        final DittoHeaders expectedHeaders =
                MessageHeaders.of(buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(),
                        messageDirection, subject, contentType)).toBuilder().statusCode(statusCode).build();
        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(statusCode)
                        .withValue(isAcceptedResponse() ? null : payload)
                        .build())
                .withHeaders(expectedHeaders)
                .build();

        final Message<Object> theMessage = Message.newBuilder(
                MessageHeaders.newBuilder(messageDirection, TestConstants.THING_ID, subject)
                        .contentType(contentType)
                        .correlationId(CORRELATION_ID)
                        .statusCode(statusCode)
                        .schemaVersion(JsonSchemaVersion.V_2)
                        .build())
                .payload(payload)
                .build();
        final DittoHeaders theHeaders =
                buildMessageHeaders(TestConstants.DITTO_HEADERS_V_2.toBuilder(), messageDirection, subject,
                        contentType);
        final MessageCommandResponse messageCommandResponse = messageCommandResponse(theMessage, theHeaders);

        final Adaptable actual = underTest.toAdaptable(messageCommandResponse);

        assertThat(actual).isEqualTo(expected);
    }

    private MessageCommandResponse messageCommandResponse(final Message<Object> message, final DittoHeaders headers) {
        switch (type) {
            case SendThingMessageResponse.TYPE:
                return SendThingMessageResponse.of(TestConstants.THING_ID, message, HttpStatusCode.OK, headers);
            case SendFeatureMessageResponse.TYPE:
                return SendFeatureMessageResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, message,
                        HttpStatusCode.OK, headers);
            case SendClaimMessageResponse.TYPE:
                return SendClaimMessageResponse.of(TestConstants.THING_ID, message, HttpStatusCode.OK, headers);
            case SendMessageAcceptedResponse.TYPE:
                return SendMessageAcceptedResponse.newInstance(TestConstants.THING_ID, message.getHeaders(),
                        HttpStatusCode.OK, headers);
            default:
                throw new IllegalArgumentException(type + " not supported.");
        }
    }

    private DittoHeaders buildMessageHeaders(final DittoHeadersBuilder headersBuilder,
            final MessageDirection messageDirection,
            final CharSequence subject,
            final CharSequence contentType) {

        headersBuilder.putHeader(MessageHeaderDefinition.THING_ID.getKey(), TestConstants.THING_ID);
        headersBuilder.putHeader(MessageHeaderDefinition.SUBJECT.getKey(), subject);
        headersBuilder.putHeader(MessageHeaderDefinition.DIRECTION.getKey(), messageDirection.name());
        headersBuilder.putHeader(DittoHeaderDefinition.CONTENT_TYPE.getKey(), contentType);
        if (isFeatureResponse()) {
            headersBuilder.putHeader(MessageHeaderDefinition.FEATURE_ID.getKey(), FEATURE_ID);
        }
        if (isAcceptedResponse()) {
            headersBuilder.putHeader(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), "false");
        }
        return headersBuilder.build();
    }

    private boolean isFeatureResponse() {
        return SendFeatureMessageResponse.TYPE.equals(type);
    }

    private boolean isAcceptedResponse() {
        return SendMessageAcceptedResponse.TYPE.equals(type);
    }

}
