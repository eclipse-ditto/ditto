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
package org.eclipse.ditto.model.connectivity;

import java.time.Instant;
import java.util.List;
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
 * Represents all logs of a connection.
 */
@Immutable
public interface ConnectionLogs extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return since when logging is enabled for the connection, or empty if it isn't enabled.
     */
    Optional<Instant> getEnabledSince();

    /**
     * @return until when logging is enabled for the connection, or empty if it isn't enabled.
     */
    Optional<Instant> getEnabledUntil();

    /**
     * @return the log entries for the connection.
     */
    List<LogEntry> getLogs();

    /**
     * Returns all non hidden marked fields of this {@code ConnectionLogs}.
     *
     * @return a JSON object representation of this Connection logs including only non hidden marked fields.
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
     * An enumeration of the known {@code JsonField}s of a {@code ConnectionLogs}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing when logging was enabled.
         */
        public static final JsonFieldDefinition<JsonObject> ENABLED_SINCE =
                JsonFactory.newJsonObjectFieldDefinition("enabledSince", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing when logging gets disabled.
         */
        public static final JsonFieldDefinition<JsonObject> ENABLED_UNTIL =
                JsonFactory.newJsonObjectFieldDefinition("enabledUntil", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the logs.
         */
        public static final JsonFieldDefinition<JsonObject> LOGS =
                JsonFactory.newJsonObjectFieldDefinition("logs", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
