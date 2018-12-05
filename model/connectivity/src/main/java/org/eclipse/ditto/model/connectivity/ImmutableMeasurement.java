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

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link Measurement}.
 */
public final class ImmutableMeasurement implements Measurement {

    private static final String SUCCESS_FIELD_NAME = "success";
    private static final String FAILURE_FIELD_NAME = "failure";
    private final Map<Duration, Long> values;
    private final Instant lastMessageAt;
    private final String counterType;
    private final boolean success;

    public ImmutableMeasurement(final String counterType, final boolean success,
            final Map<Duration, Long> values, final Instant lastMessageAt) {
        this.counterType = counterType;
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
        this.success = success;
        this.lastMessageAt = lastMessageAt;
    }

    @Override
    public String getType() {
        return counterType;
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public Map<Duration, Long> getCounts() {
        return values;
    }

    @Override
    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final JsonObject counts = JsonFactory.newObject(values.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> JsonFactory.newKey(e.getKey().toString()),
                        e -> JsonFactory.newValue(e.getValue())))
        );

        return JsonFactory.newObjectBuilder()
                .set(counterType,
                        JsonFactory.newObjectBuilder()
                                .set(getSuccessFieldName(success),
                                        JsonFactory.newObjectBuilder(counts)
                                                .set(JsonFields.LAST_MESSAGE_AT, getLastMessageAt().toString()
                                                ).build()
                                ).build()
                ).build();
    }

    private static String getSuccessFieldName(final boolean success) {
        return success ? SUCCESS_FIELD_NAME : FAILURE_FIELD_NAME;
    }

    private static boolean fromSuccessFieldName(final String success) {
        switch (success) {
            case SUCCESS_FIELD_NAME:
                return true;
            case FAILURE_FIELD_NAME:
                return false;
            default:
                throw new JsonParseException("Unknown key " + success + ", expected '" + SUCCESS_FIELD_NAME + "' " +
                        "or '" + FAILURE_FIELD_NAME + ".");
        }
    }

    /**
     * Creates a new {@code Measurement} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the AddressMetric to be created.
     * @return a new Measurement which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Measurement fromJson(final JsonObject jsonObject) {

        final JsonField type = unwrap(jsonObject);
        final JsonField success = unwrap(type.getValue());
        final JsonObject counterJson = success.getValue().asObject();

        final Map<Duration, Long> readCounterMap =
                counterJson.stream()
                        .filter(f -> f.getValue().isNumber())
                        .collect(Collectors.toMap(f -> Duration.parse(f.getKeyName()), f -> f.getValue().asLong()));

        final Instant readLastMessageAt = counterJson
                .getValue(JsonFields.LAST_MESSAGE_AT)
                .map(Instant::parse)
                .orElse(Instant.EPOCH);

        return new ImmutableMeasurement(type.getKeyName(), fromSuccessFieldName(success.getKeyName()), readCounterMap,
                readLastMessageAt);
    }

    private static JsonField unwrap(JsonValue jsonValue) {
        if (jsonValue.isObject() && jsonValue.asObject().getSize() > 0) {
            final JsonObject jsonObject = jsonValue.asObject();
            final String inner = jsonObject.getKeys().get(0).toString();
            return JsonField.newInstance(inner,
                    jsonObject.getValue(inner)
                            .orElseThrow(
                                    () -> JsonParseException.newBuilder().message("No inner object found.").build()));
        } else {
            throw JsonParseException.newBuilder().message("No inner object found.").build();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableMeasurement that = (ImmutableMeasurement) o;
        return success == that.success &&
                Objects.equals(values, that.values) &&
                Objects.equals(lastMessageAt, that.lastMessageAt) &&
                Objects.equals(counterType, that.counterType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, counterType, lastMessageAt, success);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "values=" + values +
                ", lastMessageAt=" + lastMessageAt +
                ", counterType=" + counterType +
                ", success=" + success +
                "]";
    }
}
