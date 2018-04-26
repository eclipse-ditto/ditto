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
package org.eclipse.ditto.model.connectivity;

import java.time.Instant;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Contains the {@link ConnectionStatus} and its details plus a message count related to a {@link Source}/{@link Target}
 * address.
 */
public interface AddressMetric extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the current status of the connection
     */
    ConnectionStatus getStatus();

    /**
     * @return the optional status details
     */
    Optional<String> getStatusDetails();

    /**
     * @return the current message count
     */
    long getMessageCount();

    /**
     * @return the timestamp when the last message was consumed/published.
     */
    Optional<Instant> getLastMessageAt();

    /**
     * Returns all non hidden marked fields of this {@code AddressMetric}.
     *
     * @return a JSON object representation of this Source including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@code JsonField}s of an {@code AddressMetric}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code JsonSchemaVersion}.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectionStatus} value.
         */
        public static final JsonFieldDefinition<String> STATUS =
                JsonFactory.newStringFieldDefinition("status", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectionStatus} details.
         */
        public static final JsonFieldDefinition<String> STATUS_DETAILS =
                JsonFactory.newStringFieldDefinition("statusDetails", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the amount of consumed/published messages.
         */
        public static final JsonFieldDefinition<Long> MESSAGE_COUNT =
                JsonFactory.newLongFieldDefinition("messageCount", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the timestamp when the last message was consumed/published.
         */
        public static final JsonFieldDefinition<String> LAST_MESSAGE_AT =
                JsonFactory.newStringFieldDefinition("lastMessageAt", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
