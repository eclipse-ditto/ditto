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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link TimeseriesDataValue}.
 */
@Immutable
final class ImmutableTimeseriesDataValue implements TimeseriesDataValue {

    private final Instant timestamp;
    @Nullable private final JsonValue value;
    private final boolean isGap;

    private ImmutableTimeseriesDataValue(final Instant timestamp,
            @Nullable final JsonValue value,
            final boolean isGap) {

        this.timestamp = timestamp;
        this.value = value;
        this.isGap = isGap;
    }

    static TimeseriesDataValue of(final Instant timestamp,
            @Nullable final JsonValue value,
            final boolean isGap) {

        checkNotNull(timestamp, "timestamp");
        if (!isGap) {
            checkNotNull(value, "value");
        }
        return new ImmutableTimeseriesDataValue(timestamp, value, isGap);
    }

    static TimeseriesDataValue fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        final Instant timestamp = parseTimestamp(jsonObject.getValueOrThrow(JsonFields.TIMESTAMP));
        final boolean isGap = jsonObject.getValue(JsonFields.GAP).orElse(false);
        final JsonValue value = jsonObject.getValue(JsonFields.VALUE)
                .map(v -> v.isNull() ? null : v)
                .orElse(null);

        return new ImmutableTimeseriesDataValue(timestamp, value, isGap);
    }

    private static Instant parseTimestamp(final String raw) {
        try {
            return Instant.parse(raw);
        } catch (final DateTimeParseException e) {
            throw new DittoJsonException(JsonParseException.newBuilder()
                    .message("Field <t> is not a valid ISO-8601 instant: <" + raw + ">.")
                    .description("Expected an ISO-8601 instant, e.g. \"2026-01-15T10:30:00Z\".")
                    .cause(e)
                    .build());
        }
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public Optional<JsonValue> getValue() {
        return Optional.ofNullable(value);
    }

    @Override
    public boolean isGap() {
        return isGap;
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.TIMESTAMP, timestamp.toString());

        if (value != null) {
            builder.set(JsonFields.VALUE, value);
        } else if (isGap) {
            // Gap with FillStrategy.NULL — emit explicit JSON null so the timestamp slot is visible.
            builder.set(JsonFields.VALUE, JsonValue.nullLiteral());
        }
        if (isGap) {
            builder.set(JsonFields.GAP, true);
        }

        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableTimeseriesDataValue)) {
            return false;
        }
        final ImmutableTimeseriesDataValue that = (ImmutableTimeseriesDataValue) o;
        return isGap == that.isGap &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, value, isGap);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "timestamp=" + timestamp +
                ", value=" + value +
                ", isGap=" + isGap +
                "]";
    }
}
