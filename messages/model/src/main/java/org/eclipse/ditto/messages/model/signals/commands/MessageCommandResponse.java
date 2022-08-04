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

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.things.model.WithThingId;

/**
 * Base interface for all response messages to things and features.
 *
 * @param <P> the type of the message's payload.
 * @param <C> the type of the MessageCommandResponse.
 */
public interface MessageCommandResponse<P, C extends MessageCommandResponse<P, C>>
        extends CommandResponse<C>, SignalWithEntityId<C>, WithThingId, WithMessage<P> {

    /**
     * Type Prefix of Message commands.
     */
    String TYPE_PREFIX = "messages." + TYPE_QUALIFIER + ":";

    /**
     * Retrieves the type of the message to be delivered. This will be used as routingKey for delivering the message
     * over AMQP.
     *
     * @return the type of the message to be delivered
     */
    default String getMessageType() {
        return getName();
    }

    @Override
    default String getResourceType() {
        return MessageCommand.RESOURCE_TYPE;
    }

    @Override
    C setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default JsonPointer getResourcePath() {
        final Message<?> message = getMessage();
        final String box = message.getDirection() == MessageDirection.TO
                ? MessageCommand.INBOX_PREFIX
                : MessageCommand.OUTBOX_PREFIX;

        final JsonPointer pathSuffix =
                JsonFactory.newPointer(JsonKey.of(box), JsonKey.of(MessageCommand.MESSAGES_PREFIX),
                        JsonKey.of(message.getSubject()));

        final JsonPointer path = message.getFeatureId()
                .map(fId -> JsonFactory.newPointer(JsonKey.of(MessageCommand.FEATURES_PREFIX), JsonKey.of(fId)))
                .orElse(JsonPointer.empty());

        return path.append(pathSuffix);
    }

    /**
     * Indicates whether the specified signal argument is a {@link MessageCommandResponse}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code MessageCommandResponse}, {@code false} else.
     * @since 3.0.0
     */
    static boolean isMessageCommandResponse(@Nullable final WithType signal) {
        return WithType.hasTypePrefix(signal, MessageCommandResponse.TYPE_PREFIX);
    }

    /**
     * This class contains definitions for all specific fields of a {@code MessageCommandResponse}'s JSON
     * representation.
     *
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the MessageCommandResponse's thingId.
         */
        public static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the MessageCommandResponse's Message.
         */
        public static final JsonFieldDefinition<JsonObject> JSON_MESSAGE =
                JsonFactory.newJsonObjectFieldDefinition("message", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the MessageCommandResponse's Message headers.
         */
        public static final JsonFieldDefinition<JsonObject> JSON_MESSAGE_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("headers", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the MessageCommandResponse's Message payload.
         */
        public static final JsonFieldDefinition<String> JSON_MESSAGE_PAYLOAD =
                JsonFactory.newStringFieldDefinition("payload", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

    }

}
