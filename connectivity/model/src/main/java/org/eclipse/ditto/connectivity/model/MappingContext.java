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
package org.eclipse.ditto.connectivity.model;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * Defines all information necessary to instantiate a mapper.
 */
@Immutable
public interface MappingContext extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Fully qualified classname of a mapping engine which can map messages of the contexts content-type or one of the
     * supported built-in mapping-engines aliases.
     * E.g.:
     * <ul>
     *     <li>JavaScript</li>
     *     <li><pre>org.eclipse.ditto.connectivity.service.mapping.mapper.javascript.JavaScriptMessageMapperRhino</pre></li>
     * </ul>
     *
     * @return the mapping engine name
     */
    String getMappingEngine();

    /**
     * All configuration options for mapping engine instantiation.
     *
     * @return the options
     * @since 1.3.0
     */
    JsonObject getOptionsAsJson();

    /**
     * All conditions to be validated before mapping incoming messages.
     *
     * @return the conditions
     * @since 1.3.0
     */
    Map<String, String> getIncomingConditions();

    /**
     * All conditions to be validated before mapping outgoing messages.
     *
     * @return the conditions
     * @since 1.3.0
     */
    Map<String, String> getOutgoingConditions();

    /**
     * Returns all non-hidden marked fields of this {@code MappingContext}.
     *
     * @return a JSON object representation of this MappingContext including only non-hidden marked fields.
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
     * An enumeration of the known {@code JsonField}s of a {@code MappingContext}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code mappingEngine} to use in order to map messages.
         */
        public static final JsonFieldDefinition<String> MAPPING_ENGINE =
                JsonFactory.newStringFieldDefinition("mappingEngine", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the options for the mapping.
         */
        public static final JsonFieldDefinition<JsonObject> OPTIONS =
                JsonFactory.newJsonObjectFieldDefinition("options", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code conditions} to check before mapping incoming messages.
         *
         * @since 1.3.0
         */
        public static final JsonFieldDefinition<JsonObject> INCOMING_CONDITIONS =
                JsonFactory.newJsonObjectFieldDefinition("incomingConditions", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code conditions} to check before mapping outgoing messages.
         *
         * @since 1.3.0
         */
        public static final JsonFieldDefinition<JsonObject> OUTGOING_CONDITIONS =
                JsonFactory.newJsonObjectFieldDefinition("outgoingConditions", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
