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

import java.util.Map;

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
     *     <li><pre>org.eclipse.ditto.services.connectivity.mapping.mapper.javascript.JavaScriptMessageMapperRhino</pre></li>
     * </ul>
     * @return the mapping engine name
     */
    String getMappingEngine();

    /**
     * All configuration options for mapping engine instantiation.
     * @return the options
     */
    Map<String, String> getOptions();

    /**
     * Returns all non hidden marked fields of this {@code MappingContext}.
     *
     * @return a JSON object representation of this MappingContext including only non hidden marked fields.
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
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the options for the mapping.
         */
        public static final JsonFieldDefinition<JsonObject> OPTIONS =
                JsonFactory.newJsonObjectFieldDefinition("options", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
