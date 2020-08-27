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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
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
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.junit.Before;
import org.junit.Test;

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
    public void mapMessageWithoutPayloadWithoutContentType() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final Message<Object> messageWithoutPayload = messageBuilder(null).build();
        final Signal<?> sendThingMessage = SendThingMessage.of(THING_ID, messageWithoutPayload, dittoHeaders);
        final List<ExternalMessage> result = underTest.map(ADAPTER.toAdaptable(sendThingMessage));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBytePayload()).isEmpty();
        assertThat(result.get(0).getTextPayload()).isEmpty();
    }

    @Test
    public void mapMessageWithTextPayload() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final Message<Object> messageWithoutPayload = messageBuilder("application/vnd.eclipse.ditto+json")
                .payload("hello world")
                .build();
        final Signal<?> sendThingMessage = SendThingMessage.of(THING_ID, messageWithoutPayload, dittoHeaders);
        final List<ExternalMessage> result = underTest.map(ADAPTER.toAdaptable(sendThingMessage));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBytePayload()).isEmpty();
        assertThat(result.get(0).getTextPayload()).contains("hello world");
    }

    @Test
    public void mapMessageWithBinaryPayload() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final Message<Object> messageWithoutPayload = messageBuilder("application/whatever")
                .rawPayload(ByteBuffer.wrap(new byte[]{1, 2, 3, 4}))
                .build();
        final Signal<?> sendThingMessage = SendThingMessage.of(THING_ID, messageWithoutPayload, dittoHeaders);
        final List<ExternalMessage> result = underTest.map(ADAPTER.toAdaptable(sendThingMessage));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBytePayload()).satisfies(byteBufferOptional -> {
            assertThat(byteBufferOptional).isNotEmpty();
            assertThat(ByteString.copyFrom(byteBufferOptional.get()))
                    .isEqualTo(ByteString.copyFrom(new byte[]{1, 2, 3, 4}));
        });
        assertThat(result.get(0).getTextPayload()).isEmpty();
    }

    @Test
    public void mapJsonMessageWithBinaryContentType() {
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
    public void mapTextMessageWithBinaryContentType() {
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

    private MessageBuilder<Object> messageBuilder(@Nullable final String contentType) {
        return MessagesModelFactory.newMessageBuilder(
                MessagesModelFactory.newHeadersBuilder(MessageDirection.TO, THING_ID, "subject")
                        .contentType(contentType)
                        .build()
        );
    }
}