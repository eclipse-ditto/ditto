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
package org.eclipse.ditto.model.connectivity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * A {@link Connection} target contains one address to publish to and several topics of Ditto signals for which to
 * subscribe in the Ditto cluster.
 */
public interface Target extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the address for the configured type of signals of this target
     */
    String getAddress();


    /**
     * @return the original address (before placeholders were resolved)
     */
    String getOriginalAddress();

    /**
     * Create a copy of this object with the target address replaced.
     *
     * @param newAddress new value for the address field.
     * @return a copy of this object retaining the class and all other fields.
     */
    Target withAddress(String newAddress);

    /**
     * @return set of topics that should be published via this target
     */
    Set<FilteredTopic> getTopics();

    /**
     * @return the optional qos value of this target - only applicable for certain {@link ConnectionType}s.
     */
    Optional<Integer> getQos();

    /**
     * Returns the Authorization Context of this {@code Target}. If an authorization context is set on a {@link Target}
     * it overrides the authorization context set on the enclosing {@link Connection}.
     *
     * @return the Authorization Context of this {@link Target}.
     */
    AuthorizationContext getAuthorizationContext();

    /**
     * Defines an optional header mapping e.g. to rename, combine etc. headers for outbound message. Mapping is
     * applied after payload mapping is applied. The mapping may contain {@code thing:*} and {@code header:*}
     * placeholders.
     *
     * @return the optional header mapping
     */
    Optional<HeaderMapping> getHeaderMapping();

    /**
     * The list of mappings that should be applied in this order for messages sent on this target. Each
     * mapping can produce multiple signals on its own that are then forwarded to the external system.
     *
     * @return list of mappings to execute
     */
    List<String> getMapping();

    /**
     * Returns all non hidden marked fields of this {@code Connection}.
     *
     * @return a JSON object representation of this Target including only non hidden marked fields
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
     * An enumeration of the known {@code JsonField}s of a {@code Target} configuration.
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
         * JSON field containing the {@code Target} address.
         */
        public static final JsonFieldDefinition<String> ADDRESS =
                JsonFactory.newStringFieldDefinition("address", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Target} topics.
         */
        public static final JsonFieldDefinition<JsonArray> TOPICS =
                JsonFactory.newJsonArrayFieldDefinition("topics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Target} qos.
         */
        public static final JsonFieldDefinition<Integer> QOS =
                JsonFactory.newIntFieldDefinition("qos", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Target} authorization context (list of authorization subjects).
         */
        public static final JsonFieldDefinition<JsonArray> AUTHORIZATION_CONTEXT =
                JsonFactory.newJsonArrayFieldDefinition("authorizationContext", FieldType.REGULAR,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Target} header mapping.
         */
        public static final JsonFieldDefinition<JsonObject> HEADER_MAPPING =
                JsonFactory.newJsonObjectFieldDefinition("headerMapping", FieldType.REGULAR,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Target} payload mapping.
         */
        public static final JsonFieldDefinition<JsonArray> MAPPING =
                JsonFactory.newJsonArrayFieldDefinition("mapping", FieldType.REGULAR,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        JsonFields() {
            throw new AssertionError();
        }
    }
}
