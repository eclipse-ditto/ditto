/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.things;

import static org.eclipse.ditto.protocol.TestConstants.CORRELATION_ID;
import static org.eclipse.ditto.protocol.TestConstants.FEATURE_ID;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.messages.model.KnownMessageSubjects;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaderDefinition;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.MessageHeadersBuilder;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link MessageCommandResponseAdapter}.
 */
@RunWith(Parameterized.class)
public final class MessageCommandResponseAdapterTest implements ProtocolAdapterTest {

    private MessageCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = MessageCommandResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Parameterized.Parameters(name = "type={0}")
    public static Collection<Object[]> data() {
        return Stream.of(SendThingMessageResponse.TYPE, SendFeatureMessageResponse.TYPE, SendClaimMessageResponse.TYPE)
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

        final HttpStatus httpStatus = HttpStatus.OK;

        final MessageHeadersBuilder messageHeadersBuilder =
                MessageHeaders.newBuilder(messageDirection, TestConstants.THING_ID, subject)
                        .contentType(contentType)
                        .correlationId(CORRELATION_ID)
                        .featureId(isFeatureResponse() ? FEATURE_ID : null)
                        .httpStatus(httpStatus)
                        .channel(TopicPath.Channel.LIVE.getName())
                        .schemaVersion(JsonSchemaVersion.V_2)
                        .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), TestConstants.THING_PREFIX + TestConstants.THING_ID);
        final DittoHeadersBuilder<?, ?> expectedHeadersBuilder = TestConstants.DITTO_HEADERS_V_2.toBuilder()
                .contentType(contentType)
                .channel(TopicPath.Channel.LIVE.getName());
        final Message<Object> expectedMessage = Message.newBuilder(messageHeadersBuilder.build())
                .payload(javaPayload)
                .rawPayload(ByteBuffer.wrap(jsonPayload.formatAsString().getBytes()))
                .build();
        final MessageCommandResponse<?, ?> expected =
                messageCommandResponse(expectedMessage, expectedHeadersBuilder.build());

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();

        final String box = messageDirection == MessageDirection.TO ? "inbox" : "outbox";
        final String preamble = isFeatureResponse() ? String.format("features/%s/%s", FEATURE_ID, box) : box;
        final JsonPointer path = JsonPointer.of(String.format("/%s/messages/%s", preamble, subject));

        final DittoHeaders headers = TestConstants.HEADERS_V_2;
        final DittoHeaders theHeaders =
                buildMessageHeaders(headers.toBuilder(), messageDirection, subject, contentType);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(httpStatus)
                        .withValue(jsonPayload)
                        .build())
                .withHeaders(theHeaders)
                .build();
        final MessageCommandResponse<?, ?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
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
        final HttpStatus httpStatus = HttpStatus.OK;

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.THING_ID)
                .live()
                .messages()
                .subject(subject)
                .build();
        final String box = "outbox";
        final String preamble = isFeatureResponse() ? String.format("features/%s/%s", FEATURE_ID, box) : box;
        final JsonPointer path = JsonPointer.of(String.format("/%s/messages/%s", preamble, subject));

        final DittoHeaders expectedHeaders = TestConstants.DITTO_HEADERS_V_2.toBuilder()
                .contentType(contentType)
                .build();
        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withStatus(httpStatus)
                        .withValue(payload)
                        .build())
                .withHeaders(expectedHeaders)
                .build();

        final Message<Object> theMessage = Message.newBuilder(
                MessageHeaders.newBuilder(messageDirection, TestConstants.THING_ID, subject)
                        .contentType(contentType)
                        .featureId(isFeatureResponse() ? FEATURE_ID : null)
                        .correlationId(CORRELATION_ID)
                        .httpStatus(httpStatus)
                        .schemaVersion(JsonSchemaVersion.V_2)
                        .build())
                .payload(payload)
                .build();
        final MessageCommandResponse<?, ?> messageCommandResponse = messageCommandResponse(theMessage, expectedHeaders);
        final Adaptable actual = underTest.toAdaptable(messageCommandResponse);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    private MessageCommandResponse<?, ?> messageCommandResponse(final Message<Object> message, final DittoHeaders headers) {
        switch (type) {
            case SendThingMessageResponse.TYPE:
                return SendThingMessageResponse.of(TestConstants.THING_ID, message, HttpStatus.OK, headers);
            case SendFeatureMessageResponse.TYPE:
                return SendFeatureMessageResponse.of(TestConstants.THING_ID, TestConstants.FEATURE_ID, message,
                        HttpStatus.OK, headers);
            case SendClaimMessageResponse.TYPE:
                return SendClaimMessageResponse.of(TestConstants.THING_ID, message, HttpStatus.OK, headers);
            default:
                throw new IllegalArgumentException(type + " not supported.");
        }
    }

    private DittoHeaders buildMessageHeaders(final DittoHeadersBuilder<?, ?> headersBuilder,
            final MessageDirection messageDirection,
            final CharSequence subject,
            final CharSequence contentType) {

        headersBuilder.putHeader(MessageHeaderDefinition.THING_ID.getKey(), TestConstants.THING_ID.toString());
        headersBuilder.putHeader(MessageHeaderDefinition.SUBJECT.getKey(), subject);
        headersBuilder.putHeader(MessageHeaderDefinition.DIRECTION.getKey(), messageDirection.name());
        headersBuilder.putHeader(DittoHeaderDefinition.CONTENT_TYPE.getKey(), contentType);
        if (isFeatureResponse()) {
            headersBuilder.putHeader(MessageHeaderDefinition.FEATURE_ID.getKey(), FEATURE_ID);
        }
        return headersBuilder.build();
    }

    private boolean isFeatureResponse() {
        return SendFeatureMessageResponse.TYPE.equals(type);
    }

}
