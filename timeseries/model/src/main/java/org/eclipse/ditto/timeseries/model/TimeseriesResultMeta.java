/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.model;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Metadata about the {@code data} array in a {@link TimeseriesQueryResult}: how many points were
 * returned, the unit (when available), and the JSON {@code dataType} of the values
 * ({@code "number"}, {@code "string"}, {@code "boolean"}, etc.).
 *
 * @since 4.0.0
 */
public interface TimeseriesResultMeta extends Jsonifiable<JsonObject> {

    /**
     * Returns a new {@code TimeseriesResultMeta}.
     *
     * @param count the number of data points returned.
     * @param unit the unit of the values, or {@code null} if not declared.
     * @param dataType a token describing the JSON type of values, e.g. {@code "number"}.
     * @return the new meta.
     * @throws NullPointerException if {@code dataType} is {@code null}.
     * @throws IllegalArgumentException if {@code count} is negative.
     */
    static TimeseriesResultMeta of(final int count,
            @Nullable final String unit,
            final String dataType) {

        return ImmutableTimeseriesResultMeta.of(count, unit, dataType);
    }

    /**
     * Parses a {@code TimeseriesResultMeta} from the given JSON object.
     *
     * @param jsonObject the JSON object.
     * @return the parsed meta.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if a required field is missing.
     */
    static TimeseriesResultMeta fromJson(final JsonObject jsonObject) {
        return ImmutableTimeseriesResultMeta.fromJson(jsonObject);
    }

    /**
     * @return the number of data points returned.
     */
    int getCount();

    /**
     * @return the unit string if present, or empty.
     */
    Optional<String> getUnit();

    /**
     * @return a token describing the JSON type of values, e.g. {@code "number"}.
     */
    String getDataType();

    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[] {JsonSchemaVersion.V_2};
    }

    /**
     * JSON field definitions for {@link TimeseriesResultMeta}.
     */
    final class JsonFields {

        /**
         * The count of returned data points.
         */
        public static final JsonFieldDefinition<Integer> COUNT =
                JsonFactory.newIntFieldDefinition("count", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The unit string. Optional.
         */
        public static final JsonFieldDefinition<String> UNIT =
                JsonFactory.newStringFieldDefinition("unit", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The data-type token.
         */
        public static final JsonFieldDefinition<String> DATA_TYPE =
                JsonFactory.newStringFieldDefinition("dataType", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
