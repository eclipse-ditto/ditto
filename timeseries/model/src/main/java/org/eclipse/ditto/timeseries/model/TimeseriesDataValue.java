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

import java.time.Instant;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * A single timestamped value in a timeseries query result. Either a real observation
 * (value present, {@link #isGap()} is {@code false}) or a gap-filled placeholder produced by a
 * {@link FillStrategy} during downsampling (value may be {@code null} if the strategy is
 * {@link FillStrategy#NULL}, and {@link #isGap()} is {@code true}).
 *
 * @since 4.0.0
 */
public interface TimeseriesDataValue extends Jsonifiable<JsonObject> {

    /**
     * Returns a non-gap data value.
     *
     * @param timestamp the time of the observation.
     * @param value the observed value.
     * @return the data value.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static TimeseriesDataValue of(final Instant timestamp, final JsonValue value) {
        return ImmutableTimeseriesDataValue.of(timestamp, value, false);
    }

    /**
     * Returns a data value at the given timestamp marked as a gap (produced by a fill strategy).
     *
     * @param timestamp the bucket timestamp.
     * @param value the value supplied by the fill strategy, or {@code null} for
     * {@link FillStrategy#NULL}.
     * @return the gap data value.
     * @throws NullPointerException if {@code timestamp} is {@code null}.
     */
    static TimeseriesDataValue gap(final Instant timestamp, @Nullable final JsonValue value) {
        return ImmutableTimeseriesDataValue.of(timestamp, value, true);
    }

    /**
     * Parses a {@code TimeseriesDataValue} from the given JSON object. The JSON shape uses short
     * field names: {@code t} for timestamp (ISO-8601), {@code v} for value, and the optional
     * {@code _gap} flag.
     *
     * @param jsonObject the JSON object.
     * @return the parsed data value.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code t} is missing.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code t} is not a valid ISO-8601 instant.
     */
    static TimeseriesDataValue fromJson(final JsonObject jsonObject) {
        return ImmutableTimeseriesDataValue.fromJson(jsonObject);
    }

    /**
     * @return the timestamp of this value.
     */
    Instant getTimestamp();

    /**
     * @return the observed value if present, or empty when the value is {@code null} (e.g. a
     * {@link FillStrategy#NULL} gap).
     */
    Optional<JsonValue> getValue();

    /**
     * @return {@code true} if this entry was produced by a fill strategy and does not represent an
     * actual observation; {@code false} for real observations.
     */
    boolean isGap();

    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[] {JsonSchemaVersion.V_2};
    }

    /**
     * JSON field definitions for {@link TimeseriesDataValue}.
     */
    final class JsonFields {

        /**
         * The timestamp ({@code t}).
         */
        public static final JsonFieldDefinition<String> TIMESTAMP =
                JsonFactory.newStringFieldDefinition("t", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The value ({@code v}).
         */
        public static final JsonFieldDefinition<JsonValue> VALUE =
                JsonFactory.newJsonValueFieldDefinition("v", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The gap flag ({@code _gap}). Optional; default {@code false}.
         */
        public static final JsonFieldDefinition<Boolean> GAP =
                JsonFactory.newBooleanFieldDefinition("_gap", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
