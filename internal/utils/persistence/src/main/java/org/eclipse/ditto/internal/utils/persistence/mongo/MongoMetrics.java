/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Holds metrics for current MongoDB maximum roundtrip timers, in which resolution they were reported and from which
 * reporter.
 */
public final class MongoMetrics implements Jsonifiable<JsonObject> {

    private final String reporter;
    private final Duration resolution;
    private final List<Long> maxTimerNanos;

    private MongoMetrics(final String reporter, final Duration resolution, final List<Long> maxTimerNanos) {
        this.reporter = reporter;
        this.resolution = resolution;
        this.maxTimerNanos = Collections.unmodifiableList(new ArrayList<>(maxTimerNanos));
    }

    /**
     * Returns a new {@code MongoMetrics} instance.
     *
     * @param reporter the reporter who reports this metrics.
     * @param resolution the resolution in which the metrics were reported.
     * @param maxTimerNanos a list of maximum nanoseconds encountered on MongoDB insert times.
     * @return the new MongoMetrics instance.
     */
    public static MongoMetrics of(final String reporter, final Duration resolution,
            final Iterable<Long> maxTimerNanos) {

        final List<Long> target = new ArrayList<>();
        maxTimerNanos.forEach(target::add);
        return new MongoMetrics(reporter, resolution, target);
    }

    /**
     * Creates a new {@code MongoMetrics} from a JSON string.
     *
     * @param jsonString the JSON string of which a new MongoMetrics is to be created.
     * @return the MongoMetrics which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonString} was not in the
     * expected 'MongoMetrics' format.
     */
    public static MongoMetrics fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@code MongoMetrics} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new MongoMetrics is to be created.
     * @return the MongoMetrics which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected 'MongoMetrics' format.
     */
    public static MongoMetrics fromJson(final JsonObject jsonObject) {
        final String extractedReporter = jsonObject.getValueOrThrow(JsonFields.REPORTER);
        final Duration extractedResolution = Duration.parse(jsonObject.getValueOrThrow(JsonFields.RESOLUTION));
        final List<Long> extractedMaxTimerNanos = jsonObject.getValue(JsonFields.MAX_TIMER_NANOS)
                .orElse(JsonArray.empty())
                .stream()
                .filter(JsonValue::isLong)
                .map(JsonValue::asLong)
                .toList();

        return of(extractedReporter, extractedResolution, extractedMaxTimerNanos);
    }

    /**
     * @return the reporter who reported this metrics.
     */
    public String getReporter() {
        return reporter;
    }

    /**
     * @return the resolution in which the metrics were reported.
     */
    public Duration getResolution() {
        return resolution;
    }

    /**
     * @return the list of maximum nanoseconds encountered on MongoDB insert times.
     */
    public List<Long> getMaxTimerNanos() {
        return maxTimerNanos;
    }

    @Override
    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.REPORTER, reporter)
                .set(JsonFields.RESOLUTION, resolution.toString())
                .set(JsonFields.MAX_TIMER_NANOS, maxTimerNanos.stream()
                        .map(JsonFactory::newValue)
                        .collect(JsonCollectors.valuesToArray())
                )
                .build();
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a MongoMetrics.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field of the reporter value.
         */
        static final JsonFieldDefinition<String> REPORTER =
                JsonFactory.newStringFieldDefinition("reporter");

        /**
         * JSON field of the resolution value.
         */
        static final JsonFieldDefinition<String> RESOLUTION =
                JsonFactory.newStringFieldDefinition("resolution");

        /**
         * JSON field of the max-timer-nanos array.
         */
        static final JsonFieldDefinition<JsonArray> MAX_TIMER_NANOS =
                JsonFactory.newJsonArrayFieldDefinition("maxTimerNanos");

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
