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

import java.util.Collections;
import java.util.Map;
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

        return ImmutableTimeseriesResultMeta.of(count, unit, dataType, Collections.emptyMap(), null, null);
    }

    /**
     * Returns a new {@code TimeseriesResultMeta} including the resolved tags and pagination metadata.
     *
     * @param count the number of data points returned.
     * @param unit the unit of the values, or {@code null} if not declared.
     * @param dataType a token describing the JSON type of values, e.g. {@code "number"}.
     * @param tags the resolved tags of the series (as stored with the points); may be empty but never
     * {@code null}. Empty for aggregated reads, which group away the per-point metadata.
     * @param hasMore whether more data matched the query than this page returned; {@code null} for a
     * non-paginated result (e.g. an aggregated read), in which case it is omitted from the wire form.
     * @param nextCursor an opaque cursor to fetch the next page, or {@code null} when the page is the
     * last one. Present exactly when {@code hasMore} is {@code true}.
     * @return the new meta.
     * @throws NullPointerException if {@code dataType} or {@code tags} is {@code null}.
     * @throws IllegalArgumentException if {@code count} is negative.
     */
    static TimeseriesResultMeta of(final int count,
            @Nullable final String unit,
            final String dataType,
            final Map<String, String> tags,
            @Nullable final Boolean hasMore,
            @Nullable final String nextCursor) {

        return ImmutableTimeseriesResultMeta.of(count, unit, dataType, tags, hasMore, nextCursor);
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

    /**
     * @return the resolved tags of the series, as stored with the data points. Always non-null; may
     * be empty (no declared tags, or an aggregated read). The returned map is unmodifiable.
     */
    Map<String, String> getTags();

    /**
     * @return whether more data matched the query than this page returned. Present only for
     * paginated (raw) reads: {@code true} means another page follows (see {@link #getNextCursor()}),
     * {@code false} means this is the last page. Empty for non-paginated results.
     */
    Optional<Boolean> getHasMore();

    /**
     * @return the opaque cursor for the next page if more data matched than this page returned, or
     * empty when this is the last page. Pass it back as the query's {@code cursor} to continue. See
     * {@link TimeseriesCursor}.
     */
    Optional<String> getNextCursor();

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

        /**
         * The resolved tags of the series. Optional; omitted when empty.
         */
        public static final JsonFieldDefinition<JsonObject> TAGS =
                JsonFactory.newJsonObjectFieldDefinition("tags", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * Whether more data is available. Optional; present only for paginated (raw) reads.
         */
        public static final JsonFieldDefinition<Boolean> HAS_MORE =
                JsonFactory.newBooleanFieldDefinition("hasMore", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The opaque cursor to fetch the next page. Optional; present only when more data is available.
         */
        public static final JsonFieldDefinition<String> NEXT_CURSOR =
                JsonFactory.newStringFieldDefinition("nextCursor", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
