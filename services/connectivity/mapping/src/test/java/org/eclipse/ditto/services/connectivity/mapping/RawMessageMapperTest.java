/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageFormatInvalidException;
import org.eclipse.ditto.model.messages.MessagesModelFactory;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.protobuf.ByteString;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.mapping.RawMessageMapper}.
 */
public final class RawMessageMapperTest {

    private static final ThingId THING_ID = ThingId.of("thing:id");
    private static final ProtocolAdapter ADAPTER = DittoProtocolAdapter.newInstance();

    private MessageMapper underTest;

    @Before
    public void setUp() {
        underTest = new RawMessageMapper();
    }

    @Test
    public void mapFromMessageWithoutPayloadWithoutContentType() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final Message<Object> message = messageBuilder(null).build();
        final Signal<?> sendThingMessage = SendThingMessage.of(THING_ID, message, dittoHeaders);
        final List<ExternalMessage> result = underTest.map(ADAPTER.toAdaptable(sendThingMessage));
        assertThat(result).hasSize(1);
        final ExternalMessage externalMessage = result.get(0);
        assertThat(externalMessage.getBytePayload()).isEmpty();
        assertThat(externalMessage.getTextPayload()).isEmpty();
        assertThat(externalMessage.getHeaders()).containsAllEntriesOf(message.getHeaders());
    }

    @Test
    public void mapFromMessageWithTextPayload() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final Message<Object> message = messageBuilder("text/plain")
                .payload("hello world")
                .build();
        final Signal<?> sendThingMessage = SendThingMessage.of(THING_ID, message, dittoHeaders);
        final List<ExternalMessage> result = underTest.map(ADAPTER.toAdaptable(sendThingMessage));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBytePayload()).isEmpty();
        assertThat(result.get(0).getTextPayload()).contains("hello world");
        assertThat(result.get(0).getHeaders()).containsExactlyEntriesOf(message.getHeaders());
    }

    @Test
    public void mapFromMessageWithBinaryPayload() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final Message<Object> message = messageBuilder("application/whatever")
                .rawPayload(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6}))
                .build();
        final Signal<?> sendThingMessage = SendThingMessage.of(THING_ID, message, dittoHeaders);
        final List<ExternalMessage> result = underTest.map(ADAPTER.toAdaptable(sendThingMessage));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBytePayload()).satisfies(byteBufferOptional -> {
            assertThat(byteBufferOptional).isNotEmpty();
            assertThat(ByteString.copyFrom(byteBufferOptional.get()))
                    .isEqualTo(ByteString.copyFrom(new byte[]{1, 2, 3, 4, 5, 6}));
        });
        assertThat(result.get(0).getTextPayload()).isEmpty();
        assertThat(result.get(0).getHeaders()).containsAllEntriesOf(message.getHeaders());
    }

    @Test
    public void mapFromJsonMessageWithBinaryContentType() {
        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(JsonObject.of("{\n" +
                "  \"topic\": \"x/1/things/live/messages/subject\",\n" +
                "  \"headers\": {\n" +
                "    \"content-type\": \"application/octet-stream\"\n" +
                "  },\n" +
                "  \"path\": \"/inbox/messages/subject\",\n" +
                "  \"value\": {\n" +
                "    \"jsonKey\": \"jsonValue\"\n" +
                "  }\n" +
                "}"));
        assertThatExceptionOfType(MessageFormatInvalidException.class).isThrownBy(() -> underTest.map(adaptable));
    }

    @Test
    public void mapFromTextMessageWithBinaryContentType() {
        final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(JsonObject.of("{\n" +
                "  \"topic\": \"x/1/things/live/messages/subject\",\n" +
                "  \"headers\": {\n" +
                "    \"content-type\": \"application/octet-stream\"\n" +
                "  },\n" +
                "  \"path\": \"/inbox/messages/subject\",\n" +
                "  \"value\": \"This is not a base64-encoded octet stream.\"" +
                "}"));
        assertThatExceptionOfType(MessageFormatInvalidException.class).isThrownBy(() -> underTest.map(adaptable));
    }

    @Test
    public void mapFromNonMessageCommand() {
        final Signal<?> signal = ThingDeleted.of(ThingId.of("thing:id"), 25L, DittoHeaders.empty());
        final Adaptable adaptable = ADAPTER.toAdaptable(signal);
        final List<ExternalMessage> actualExternalMessages = underTest.map(adaptable);
        final List<ExternalMessage> expectedExternalMessages = new DittoMessageMapper().map(adaptable);
        assertThat(actualExternalMessages).isEqualTo(expectedExternalMessages);
    }

    @Test
    public void mapFromMessageWithDittoProtocolContentType() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .randomCorrelationId()
                .contentType("application/vnd.eclipse.ditto+json")
                .build();
        final Message<Object> messageWithoutPayload = messageBuilder(null).payload("hello world").build();
        final Signal<?> sendThingMessage = SendThingMessage.of(THING_ID, messageWithoutPayload, dittoHeaders);
        final Adaptable adaptable = ADAPTER.toAdaptable(sendThingMessage);
        final List<ExternalMessage> result = underTest.map(adaptable);
        final List<ExternalMessage> expectedResult = new DittoMessageMapper().map(adaptable);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void mapToMessageWithoutHeadersOrConfig() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(Map.of()).build())
        );
    }

    @Test
    public void mapToMessageWithoutThingId() {
        final Map<String, String> headers = Map.of(
                "ditto-message-subject", "hello/world"
        );
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers).build())
        );
    }

    @Test
    public void mapToBinaryMessagePerDefault() {
        final Map<String, String> headers = Map.of(
                "ditto-message-subject", "hello/world",
                "ditto-message-thing-id", "thing:id"
        );
        final ByteString payload = ByteString.copyFrom(new byte[]{1, 2, 3, 4, 5, 6});
        final List<Adaptable> adaptables =
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers)
                        .withBytes(payload.toByteArray())
                        .build());
        assertThat(adaptables).hasSize(1);
        assertThat(adaptables.get(0).getPayload().getValue()).contains(JsonValue.of("AQIDBAUG"));
        final Signal<?> signal = ADAPTER.fromAdaptable(adaptables.get(0));
        assertThat(signal).isInstanceOf(SendThingMessage.class);
        final SendThingMessage<?> sendThingMessage = (SendThingMessage<?>) signal;
        assertThat(sendThingMessage.getEntityId().toString()).isEqualTo("thing:id");
        assertThat(ByteString.copyFrom(sendThingMessage.getMessage().getRawPayload().orElseThrow())).isEqualTo(payload);
    }

    @Test
    public void mapToTextMessage() {
        final Map<String, String> headers = Map.of(
                "content-type", "text/plain",
                "ditto-message-subject", "hello/world",
                "ditto-message-thing-id", "thing:id"
        );
        final String payload = "lorem ipsum dolor sit amet";
        final List<Adaptable> adaptables =
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers)
                        .withText(payload)
                        .build());
        assertThat(adaptables).hasSize(1);
        final Signal<?> signal = ADAPTER.fromAdaptable(adaptables.get(0));
        assertThat(signal).isInstanceOf(SendThingMessage.class);
        final SendThingMessage<?> sendThingMessage = (SendThingMessage<?>) signal;
        assertThat(sendThingMessage.getEntityId().toString()).isEqualTo("thing:id");
        assertThat(sendThingMessage.getMessage().getPayload().orElseThrow()).isEqualTo(payload);
    }

    @Test
    public void mapToTextMessageWithContentTypeOverride() {
        final Map<String, String> headers = Map.of(
                "content-type", "application/octet-stream",
                "ditto-message-subject", "hello/world",
                "ditto-message-thing-id", "thing:id"
        );
        final String payload = "lorem ipsum dolor sit amet";
        underTest.configure(DefaultMappingConfig.of(ConfigFactory.empty()),
                DefaultMessageMapperConfiguration.of("RawMessage",
                        Map.of("incomingMessageHeaders", JsonObject.newBuilder()
                                .set("content-type", "text/plain")
                                .build()
                        ), Map.of(), Map.of()));
        final List<Adaptable> adaptables =
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers)
                        .withBytes(payload.getBytes())
                        .build());
        assertThat(adaptables).hasSize(1);
        final Signal<?> signal = ADAPTER.fromAdaptable(adaptables.get(0));
        assertThat(signal).isInstanceOf(SendThingMessage.class);
        final SendThingMessage<?> sendThingMessage = (SendThingMessage<?>) signal;
        assertThat(sendThingMessage.getEntityId().toString()).isEqualTo("thing:id");
        assertThat(sendThingMessage.getMessage().getPayload().orElseThrow()).isEqualTo(payload);
    }

    @Test
    public void mapToBinaryMessageWithContentTypeOverride() {
        final Map<String, String> headers = Map.of(
                "content-type", "text/plain",
                "ditto-message-subject", "hello/world",
                "ditto-message-thing-id", "thing:id"
        );
        final String payload = "lorem ipsum dolor sit amet";
        underTest.configure(DefaultMappingConfig.of(ConfigFactory.empty()),
                DefaultMessageMapperConfiguration.of("RawMessage",
                        Map.of("incomingMessageHeaders", JsonObject.newBuilder()
                                .set("content-type", "application/octet-stream")
                                .build()
                        ), Map.of(), Map.of()));
        final List<Adaptable> adaptables =
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers)
                        .withText(payload)
                        .build());
        assertThat(adaptables).hasSize(1);
        final Signal<?> signal = ADAPTER.fromAdaptable(adaptables.get(0));
        assertThat(signal).isInstanceOf(SendThingMessage.class);
        final SendThingMessage<?> sendThingMessage = (SendThingMessage<?>) signal;
        assertThat(sendThingMessage.getEntityId().toString()).isEqualTo("thing:id");
        assertThat(sendThingMessage.getMessage().getPayload().orElseThrow())
                .isEqualTo(ByteBuffer.wrap(payload.getBytes()));
    }

    @Test
    public void mapToJsonMessage() {
        final Map<String, String> headers = Map.of(
                "content-type", "application/vnd.hello.world+json",
                "ditto-message-subject", "hello/world",
                "ditto-message-thing-id", "thing:id"
        );
        final String payload = "{\"lorem\":\"ipsum\"}";
        final List<Adaptable> adaptables =
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers)
                        .withText(payload)
                        .build());
        assertThat(adaptables).hasSize(1);
        assertThat(adaptables.get(0).getPayload().getValue()).contains(JsonObject.of(payload));
        final Signal<?> signal = ADAPTER.fromAdaptable(adaptables.get(0));
        assertThat(signal).isInstanceOf(SendThingMessage.class);
        final SendThingMessage<?> sendThingMessage = (SendThingMessage<?>) signal;
        assertThat(sendThingMessage.getEntityId().toString()).isEqualTo("thing:id");
        assertThat(sendThingMessage.getMessage().getPayload().orElseThrow()).isEqualTo(JsonObject.of(payload));
    }

    @Test
    public void mapToDittoProtocolMessage() {
        final Map<String, String> headers = Map.of(
                "content-type", "application/vnd.eclipse.ditto+json",
                "ditto-message-subject", "hello/world",
                "ditto-message-thing-id", "thing:id"
        );
        final String payload = "{\n" +
                "  \"topic\": \"com.acme/xdk_53/things/twin/commands/delete\",\n" +
                "  \"headers\": {},\n" +
                "  \"path\": \"/\",\n" +
                "  \"status\": 204\n" +
                "}";
        final List<Adaptable> adaptables =
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers)
                        .withText(payload)
                        .build());
        assertThat(adaptables).hasSize(1);
        final Signal<?> signal = ADAPTER.fromAdaptable(adaptables.get(0));
        assertThat(signal).isInstanceOf(DeleteThingResponse.class);
    }

    @Test
    public void mapToSendFeatureMessageResponse() {
        final Map<String, String> headers = Map.of(
                "content-type", "application/json",
                "status", "418",
                "ditto-message-subject", "hello/world",
                "ditto-message-thing-id", "thing:id",
                "ditto-message-feature-id", "accelerometer"
        );
        final String payload = "{\"lorem\":\"ipsum\"}";
        final List<Adaptable> adaptables =
                underTest.map(ExternalMessageFactory.newExternalMessageBuilder(headers)
                        .withText(payload)
                        .build());
        assertThat(adaptables).hasSize(1);
        final Signal<?> signal = ADAPTER.fromAdaptable(adaptables.get(0));
        assertThat(signal).isInstanceOf(SendFeatureMessageResponse.class);
        final SendFeatureMessageResponse<?> response = (SendFeatureMessageResponse<?>) signal;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.IM_A_TEAPOT);
        assertThat(response.getEntityId().toString()).isEqualTo("thing:id");
        assertThat(response.getFeatureId()).isEqualTo("accelerometer");
        assertThat(response.getMessage().getPayload().orElseThrow()).isEqualTo(JsonObject.of(payload));
    }

    @Test
    public void mapToNonMessageCommandWithDittoProtocolContentType() {
        final Signal<?> signal = ThingDeleted.of(ThingId.of("thing:id"), 25L, DittoHeaders.empty());
        final Adaptable adaptable = ADAPTER.toAdaptable(signal);
        final ExternalMessage externalMessage = new DittoMessageMapper().map(adaptable).get(0)
                .withHeader("content-type", "application/vnd.eclipse.ditto+json");
        final List<Adaptable> mapped = underTest.map(externalMessage);
        assertThat(mapped).hasSize(1);
        assertThat(mapped.get(0)).isEqualTo(adaptable);
    }

    private MessageBuilder<Object> messageBuilder(@Nullable final String contentType) {
        return MessagesModelFactory.newMessageBuilder(
                MessagesModelFactory.newHeadersBuilder(MessageDirection.TO, THING_ID, "subject")
                        .contentType(contentType)
                        .build()
        );
    }
}