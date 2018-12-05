/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

import com.eclipsesource.json.Json;

/**
 * Immutable implementation of {@link AddressMetric}.
 */
@Immutable
final class ImmutableAddressMetric implements AddressMetric {

    public static final JsonKey SUCCESS_KEY = JsonFactory.newKey("success");
    public static final JsonKey FAILURE_KEY = JsonFactory.newKey("failure");
    private final Set<Measurement> measurements;

    private ImmutableAddressMetric(final Set<Measurement> measurements) {
        this.measurements = Collections.unmodifiableSet(new HashSet<>(measurements));
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
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);

        for (final Measurement measurement : measurements) {
            final JsonPointer pointer = JsonFactory.newPointer(
                    JsonFactory.newKey(measurement.getType()),
                    measurement.isSuccess() ? SUCCESS_KEY : FAILURE_KEY);
            jsonObjectBuilder.set(pointer, measurement.toJson().getValue(pointer).orElse(JsonFactory.newObject()));
        }
        return jsonObjectBuilder.build();
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
        final Set<Measurement> readMeasurements = new HashSet<>();
        jsonObject.stream()
                .filter(field -> field.getValue().isObject())
                .forEach(f -> Stream.of(SUCCESS_KEY, FAILURE_KEY)
                        .map(key -> JsonFactory.newPointer(JsonFactory.newKey(f.getKeyName()), key))
                        .map(jsonObject::get)
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .map(ImmutableMeasurement::fromJson)
                        .forEach(readMeasurements::add));
        return ImmutableAddressMetric.of(readMeasurements);
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (!(o instanceof ImmutableAddressMetric)) {return false;}
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
