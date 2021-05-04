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

import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Holds data in order to apply enforcement of an {@code input} (e.g. a Thing ID or a source address) which must match
 * against the passed {@code filters} (which may contain placeholders like {@code {{ thing:id }}} etc.
 */
public interface Enforcement extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Retrieve the string to match against filters.
     *
     * @return the string that is supposed to match one of the filters.
     */
    String getInput();

    /**
     * Retrieve set of filters that are compared against the input string.
     * Filters contain placeholders ({@code {{ ... }}}).
     *
     * @return the filters.
     */
    Set<String> getFilters();

    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code Enforcement}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the input of the enforcement.
         */
        public static final JsonFieldDefinition<String> INPUT =
                JsonFactory.newStringFieldDefinition("input", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Enforcement} filters.
         */
        public static final JsonFieldDefinition<JsonArray> FILTERS =
                JsonFactory.newJsonArrayFieldDefinition("filters", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
