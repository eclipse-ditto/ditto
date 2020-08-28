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
package org.eclipse.ditto.signals.commands.messages;

import java.util.Optional;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageHeaders;

/**
 * Responsible for deserializing a {@link Message} from a given {@link JsonObject}.
 *
 * @since 1.2.0
 */
public final class MessageDeserializer {

    private MessageDeserializer() {
        // Empty because this is a utility class.
    }

    /**
     * Deserializes the {@link org.eclipse.ditto.model.messages.Message} from the JSON representation - the {@code rawPayload} is decoded with Base64.
     *
     * @param <T> the type of the message's payload.
     * @param messageObject the json representation of the {@link org.eclipse.ditto.model.messages.Message}.
     * @return the Message.
     */
    public static <T> Message<T> deserializeMessageFromJson(final JsonObject messageObject) {
        final JsonObject messageHeadersObject =
                messageObject.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS);
        final Optional<JsonValue> messagePayloadOptional =
                messageObject.getValue(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD);

        final MessageHeaders messageHeaders = MessageHeaders.of(messageHeadersObject);
        final MessageBuilder<T> messageBuilder = Message.newBuilder(messageHeaders);
        MessagePayloadSerializer.deserialize(messagePayloadOptional, messageBuilder,
                messageHeaders);
        return messageBuilder.build();
    }

}


