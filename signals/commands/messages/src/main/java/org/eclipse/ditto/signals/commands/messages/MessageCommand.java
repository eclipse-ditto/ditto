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
package org.eclipse.ditto.signals.commands.messages;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Base interface for all commands to send messages to things and features.
 *
 * @param <T> the type of the message's payload.
 * @param <C> the type of the MessageCommand.
 */
public interface MessageCommand<T, C extends MessageCommand> extends Command<C>, WithThingId {

    /**
     * Type Prefix of Message commands.
     */
    String TYPE_PREFIX = "messages." + TYPE_QUALIFIER + ":";

    /**
     * The "inbox" direction keyword used in HTTP route / policy.
     */
    String INBOX_PREFIX = "inbox";

    /**
     * The "outbox" direction keyword used in HTTP route / policy.
     */
    String OUTBOX_PREFIX = "outbox";

    /**
     * The "features" keyword used in HTTP route / policy.
     */
    String FEATURES_PREFIX = "features";

    /**
     * The "messages" keyword used in HTTP route / policy.
     */
    String MESSAGES_PREFIX = "messages";

    /**
     * Message resource type.
     */
    String RESOURCE_TYPE = "message";

    /**
     * Retrieves the Message to be delivered.
     *
     * @return the Message to be delivered.
     */
    Message<T> getMessage();

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

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
    default String getId() {
        return getThingId();
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    C setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default JsonPointer getResourcePath() {
        final Message<?> message = getMessage();
        final String box = message.getDirection() == MessageDirection.TO ? INBOX_PREFIX : OUTBOX_PREFIX;
        final JsonPointer pathSuffix = JsonPointer.empty()
                .addLeaf(JsonKey.of(box))
                .addLeaf(JsonKey.of(MESSAGES_PREFIX))
                .addLeaf(JsonKey.of(message.getSubject()));

        final JsonPointer path = message.getFeatureId().map(fId -> JsonPointer.empty()
                .addLeaf(JsonKey.of(FEATURES_PREFIX))
                .addLeaf(JsonKey.of(fId)))
                .orElse(JsonPointer.empty());
        return path.append(pathSuffix);
    }


    /**
     * This class contains definitions for all specific fields of a {@code MessageCommand}'s JSON representation.
     *
     */
    class JsonFields extends Command.JsonFields {

        /**
         * JSON field containing the MessageCommand's thingId.
         */
        public static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the MessageCommand's Message.
         */
        public static final JsonFieldDefinition<JsonObject> JSON_MESSAGE =
                JsonFactory.newJsonObjectFieldDefinition("message", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the MessageCommand's Message headers.
         */
        public static final JsonFieldDefinition<JsonObject> JSON_MESSAGE_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("headers", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the MessageCommand's Message payload.
         */
        public static final JsonFieldDefinition<JsonValue> JSON_MESSAGE_PAYLOAD =
                JsonFactory.newJsonValueFieldDefinition("payload", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }

}
