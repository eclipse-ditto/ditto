/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
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
 * Represents the status of a resource e.g. a client connection, a source or a target, defined in {@link ResourceType}.
 */
public interface ResourceStatus extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return type of the address
     */
    ImmutableResourceStatus.ResourceType getResourceType();

    /**
     * @return the resource identifier
     */
    Optional<String> getAddress();

    /**
     * @return the current status of the resource
     */
    String getStatus();

    /**
     * @return the optional status details
     */
    Optional<String> getStatusDetails();

    /**
     * @return the instant since the resource is in this state
     */
    Optional<Instant> getInStateSince();

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

    enum ResourceType {
        SOURCE,
        TARGET,
        CLIENT,
        UNKNOWN
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
         * JSON field containing the {@code ConnectionStatus} value.
         */
        public static final JsonFieldDefinition<String> ADDRESS =
                JsonFactory.newStringFieldDefinition("address", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectionStatus} details.
         */
        public static final JsonFieldDefinition<String> STATUS_DETAILS =
                JsonFactory.newStringFieldDefinition("statusDetails", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        /**
         * JSON field containing the instant since the resource is in the stater.
         */
        public static final JsonFieldDefinition<String> IN_STATE_SINCE =
                JsonFactory.newStringFieldDefinition("inStateSince", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
