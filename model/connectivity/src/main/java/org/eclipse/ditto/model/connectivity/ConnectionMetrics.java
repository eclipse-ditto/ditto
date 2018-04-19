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
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Connection Metrics represent the current (and not the persisted/desired) connection status and information of this
 * connection like amount of consumed/published messages, etc.
 */
@Immutable
public interface ConnectionMetrics extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the current ConnectionStatus of the related {@link Connection}.
     */
    ConnectionStatus getConnectionStatus();

    /**
     * @return the optional details of the ConnectionStatus of the related {@link Connection}.
     */
    Optional<String> getConnectionStatusDetails();

    /**
     * @return the Instant since when the connection is in its current {@link #getConnectionStatus()}.
     */
    Instant getInConnectionStatusSince();

    /**
     * @return in which state the client handling the {@link Connection} currently is.
     */
    String getClientState();

    /**
     * @return the metrics of all Connection {@link Source}s.
     */
    List<SourceMetrics> getSourcesMetrics();

    /**
     *
     * @return the metrics of all Connection {@link Target}s.
     */
    List<TargetMetrics> getTargetsMetrics();

    /**
     * Returns all non hidden marked fields of this {@code Connection}.
     *
     * @return a JSON object representation of this Connection including only non hidden marked fields.
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
     * An enumeration of the known {@code JsonField}s of a {@code ConnectionMetrics}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code ConnectionStatus} value.
         */
        public static final JsonFieldDefinition<String> CONNECTION_STATUS =
                JsonFactory.newStringFieldDefinition("connectionStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectionStatus} details.
         */
        public static final JsonFieldDefinition<String> CONNECTION_STATUS_DETAILS =
                JsonFactory.newStringFieldDefinition("connectionStatusDetails", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the client state.
         */
        public static final JsonFieldDefinition<String> CLIENT_STATE =
                JsonFactory.newStringFieldDefinition("clientState", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing since when the client is in its current state.
         */
        public static final JsonFieldDefinition<String> IN_CONNECTION_STATUS_SINCE =
                JsonFactory.newStringFieldDefinition("inConnectionStatusSince", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the sources metrics.
         */
        public static final JsonFieldDefinition<JsonArray> SOURCES_METRICS =
                JsonFactory.newJsonArrayFieldDefinition("sourcesMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the targets metrics.
         */
        public static final JsonFieldDefinition<JsonArray> TARGETS_METRICS =
                JsonFactory.newJsonArrayFieldDefinition("targetsMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
