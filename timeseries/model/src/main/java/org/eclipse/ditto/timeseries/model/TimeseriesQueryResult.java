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

import java.util.List;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Result of a single-Thing, single-path timeseries query.
 * <p>
 * Multi-path queries produce one {@code TimeseriesQueryResult} per path; the service-level
 * response composes them into the multi-series wire shape described in the concept document.
 *
 * @since 4.0.0
 */
public interface TimeseriesQueryResult extends Jsonifiable<JsonObject> {

    /**
     * Returns a new {@code TimeseriesQueryResult}.
     *
     * @param thingId the Thing the result belongs to.
     * @param path the path within the Thing this result corresponds to.
     * @param query the originating query.
     * @param meta metadata about the data array.
     * @param data the timeseries data values, in chronological order. May be empty.
     * @return the new result.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static TimeseriesQueryResult of(final ThingId thingId,
            final org.eclipse.ditto.json.JsonPointer path,
            final TimeseriesQuery query,
            final TimeseriesResultMeta meta,
            final List<TimeseriesDataValue> data) {

        return ImmutableTimeseriesQueryResult.of(thingId, path, query, meta, data);
    }

    /**
     * Parses a {@code TimeseriesQueryResult} from the given JSON object.
     *
     * @param jsonObject the JSON object.
     * @return the parsed result.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if a required field is missing.
     * @throws org.eclipse.ditto.json.JsonParseException if a field has an invalid value.
     */
    static TimeseriesQueryResult fromJson(final JsonObject jsonObject) {
        return ImmutableTimeseriesQueryResult.fromJson(jsonObject);
    }

    /**
     * @return the Thing the result belongs to.
     */
    ThingId getThingId();

    /**
     * @return the path within the Thing this result corresponds to.
     */
    org.eclipse.ditto.json.JsonPointer getPath();

    /**
     * @return the originating query.
     */
    TimeseriesQuery getQuery();

    /**
     * @return metadata about the data array.
     */
    TimeseriesResultMeta getMeta();

    /**
     * @return the timeseries data values, in chronological order. The returned list is unmodifiable.
     */
    List<TimeseriesDataValue> getData();

    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[] {JsonSchemaVersion.V_2};
    }

    /**
     * JSON field definitions for {@link TimeseriesQueryResult}.
     */
    final class JsonFields {

        /**
         * The Thing ID.
         */
        public static final JsonFieldDefinition<String> THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The path within the Thing.
         */
        public static final JsonFieldDefinition<String> PATH =
                JsonFactory.newStringFieldDefinition("path", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The originating query, as a JSON object.
         */
        public static final JsonFieldDefinition<JsonObject> QUERY =
                JsonFactory.newJsonObjectFieldDefinition("query", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The result metadata, as a JSON object — note the wire-level field is {@code result},
         * matching the concept document.
         */
        public static final JsonFieldDefinition<JsonObject> META =
                JsonFactory.newJsonObjectFieldDefinition("result", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The data array.
         */
        public static final JsonFieldDefinition<JsonArray> DATA =
                JsonFactory.newJsonArrayFieldDefinition("data", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
