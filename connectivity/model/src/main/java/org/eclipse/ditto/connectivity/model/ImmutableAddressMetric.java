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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link AddressMetric}.
 */
@Immutable
final class ImmutableAddressMetric implements AddressMetric {

    private static final JsonKey SUCCESS_KEY = JsonFactory.newKey("success");
    private static final JsonKey FAILURE_KEY = JsonFactory.newKey("failure");

    private final Set<Measurement> measurements;

    private ImmutableAddressMetric(final Set<Measurement> measurements) {
        this.measurements = Collections.unmodifiableSet(new LinkedHashSet<>(measurements));
    }

    /**
     * Creates a new {@code ImmutableAddressMetric} instance.
     *
     * @param measurements set of measurements for this address
     * @return a new instance of ImmutableAddressMetric
     */
    public static ImmutableAddressMetric of(final Set<Measurement> measurements) {
        return new ImmutableAddressMetric(ConditionChecker.checkNotNull(measurements, "measurements"));
    }

    @Override
    public Set<Measurement> getMeasurements() {
        return measurements;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        if (measurements.isEmpty()) {
            return JsonFactory.nullObject();
        } else {
            final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
            final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
            final List<Measurement> sortedMeasurements = new ArrayList<>(measurements);
            sortedMeasurements.sort(getMeasurementComparator());
            for (final Measurement measurement : sortedMeasurements) {
                final JsonPointer pointer = JsonFactory.newPointer(
                        JsonFactory.newKey(measurement.getMetricType().getName()),
                        measurement.isSuccess() ? SUCCESS_KEY : FAILURE_KEY);
                jsonObjectBuilder.set(pointer,
                        measurement.toJson(predicate).getValue(pointer).orElse(JsonFactory.newObject()));
            }
            return jsonObjectBuilder.build();
        }
    }

    private static Comparator<Measurement> getMeasurementComparator() {
        final List<MetricType> sortedTypes = Arrays.asList(MetricType.values());
        Collections.sort(sortedTypes);

        return (m1, m2) -> {
            if (m1.equals(m2)) {
                return 0;
            } else {
                return calculateComparatorScore(sortedTypes, m1, m2);
            }
        };
    }

    private static int calculateComparatorScore(final List<MetricType> sortedTypes, final Measurement m1,
            final Measurement m2) {
        final int idx1 = sortedTypes.indexOf(m1.getMetricType());
        final int idx2 = sortedTypes.indexOf(m2.getMetricType());

        final int score;
        if (m1.isSuccess() && m2.isSuccess()) {
            score = 1;
        } else if (m1.isSuccess()) {
            score = 2;
        } else {
            score = 3;
        }

        if (idx1 < idx2) {
            return -score;
        } else if (idx1 == idx2) {
            return m1.isSuccess() ? -1 : 1;
        } else {
            return score;
        }
    }

    /**
     * Creates a new {@code AddressMetric} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the AddressMetric to be created.
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static AddressMetric fromJson(final JsonObject jsonObject) {
        final Set<Measurement> readMeasurements = new LinkedHashSet<>();
        jsonObject.stream()
                .filter(field -> field.getValue().isObject())
                .forEach(f -> Stream.of(SUCCESS_KEY, FAILURE_KEY)
                        .map(key -> JsonFactory.newPointer(JsonFactory.newKey(f.getKeyName()), key))
                        .map(jsonObject::get)
                        .filter(JsonValue::isObject)
                        .filter(o -> !o.isEmpty())
                        .map(JsonValue::asObject)
                        .map(ImmutableMeasurement::fromJson)
                        .forEach(readMeasurements::add));
        return ImmutableAddressMetric.of(readMeasurements);
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableAddressMetric)) {
            return false;
        }
        final ImmutableAddressMetric that = (ImmutableAddressMetric) o;
        return Objects.equals(measurements, that.measurements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(measurements);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "measurements=" + measurements +
                "]";
    }

    /**
     * Convert an indexed address metric into a JSON field.
     *
     * @param key Key of the address metric; may be empty.
     * @param metric the address metric to convert.
     * @return a valid JSON field for the address metric.
     */
    static JsonField toJsonField(final String key, final AddressMetric metric) {
        final String nonemptyKey = key.isEmpty() ? "<empty>" : key;
        return JsonFactory.newField(JsonKey.of(nonemptyKey), metric.toJson());
    }

}
