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
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;

/**
 * A single timeseries data point — the unit of ingestion into a timeseries database.
 * <p>
 * Captures the value of a Thing property at a given moment in time, along with the resolved tags and
 * (optional) unit declared in the WoT ThingModel. Tags are resolved at ingestion time and stored
 * alongside the data point so that historical values are not affected by subsequent changes to the
 * Thing's attributes.
 *
 * @since 4.0.0
 */
public interface TimeseriesDataPoint extends Jsonifiable<JsonObject> {

    /**
     * Returns a new {@code TimeseriesDataPoint}.
     *
     * @param thingId the Thing whose property the data point belongs to.
     * @param path the Ditto Protocol path within the Thing (e.g.
     * {@code /features/env/properties/temperature}).
     * @param timestamp when the value was observed.
     * @param value the observed value as a {@code JsonValue}.
     * @param revision the Thing's revision counter at the time of observation.
     * @param tags resolved tag values (stored verbatim with the data point); may be empty but never
     * {@code null}.
     * @param unit the unit of the value as declared in the WoT property's {@code unit} field, with
     * any semantic prefix already stripped; may be {@code null} when not declared.
     * @return the new data point.
     * @throws NullPointerException if any non-{@code @Nullable} argument is {@code null}.
     */
    static TimeseriesDataPoint of(final ThingId thingId,
            final JsonPointer path,
            final Instant timestamp,
            final JsonValue value,
            final long revision,
            final Map<String, String> tags,
            @Nullable final String unit) {

        return ImmutableTimeseriesDataPoint.of(thingId, path, timestamp, value, revision, tags, unit);
    }

    /**
     * Parses a {@code TimeseriesDataPoint} from the given JSON object.
     *
     * @param jsonObject the JSON object representing a data point.
     * @return the parsed data point.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if a required field is missing.
     * @throws org.eclipse.ditto.json.JsonParseException if a field has the wrong type or an invalid
     * value (e.g. an unparseable timestamp).
     */
    static TimeseriesDataPoint fromJson(final JsonObject jsonObject) {
        return ImmutableTimeseriesDataPoint.fromJson(jsonObject);
    }

    /**
     * @return the Thing this data point belongs to.
     */
    ThingId getThingId();

    /**
     * @return the Ditto Protocol path within the Thing identifying the property whose value is
     * captured (e.g. {@code /features/env/properties/temperature}).
     */
    JsonPointer getPath();

    /**
     * @return the moment the value was observed.
     */
    Instant getTimestamp();

    /**
     * @return the observed value.
     */
    JsonValue getValue();

    /**
     * @return the Thing's revision counter at the time of observation.
     */
    long getRevision();

    /**
     * @return resolved tags stored with the data point. Always non-null; may be empty. The returned
     * map is unmodifiable.
     */
    Map<String, String> getTags();

    /**
     * @return the unit of the value (semantic prefix stripped) if one was declared in the WoT model,
     * or empty otherwise.
     */
    Optional<String> getUnit();

    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[] {JsonSchemaVersion.V_2};
    }

    /**
     * JSON field definitions for {@link TimeseriesDataPoint}.
     */
    final class JsonFields {

        /**
         * The Thing ID.
         */
        public static final JsonFieldDefinition<String> THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The Ditto Protocol path within the Thing.
         */
        public static final JsonFieldDefinition<String> PATH =
                JsonFactory.newStringFieldDefinition("path", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The ISO-8601 timestamp.
         */
        public static final JsonFieldDefinition<String> TIMESTAMP =
                JsonFactory.newStringFieldDefinition("timestamp", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The captured value as raw JSON.
         */
        public static final JsonFieldDefinition<JsonValue> VALUE =
                JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The Thing's revision counter.
         */
        public static final JsonFieldDefinition<Long> REVISION =
                JsonFactory.newLongFieldDefinition("revision", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * Resolved tags. Optional; omitted from the wire format when empty.
         */
        public static final JsonFieldDefinition<JsonObject> TAGS =
                JsonFactory.newJsonObjectFieldDefinition("tags", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * Unit string. Optional; omitted from the wire format when absent.
         */
        public static final JsonFieldDefinition<String> UNIT =
                JsonFactory.newStringFieldDefinition("unit", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
