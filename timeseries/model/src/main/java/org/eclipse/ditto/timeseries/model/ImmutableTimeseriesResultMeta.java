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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * An immutable implementation of {@link TimeseriesResultMeta}.
 */
@Immutable
final class ImmutableTimeseriesResultMeta implements TimeseriesResultMeta {

    private final int count;
    @Nullable private final String unit;
    private final String dataType;

    private ImmutableTimeseriesResultMeta(final int count,
            @Nullable final String unit,
            final String dataType) {

        this.count = count;
        this.unit = unit;
        this.dataType = dataType;
    }

    static TimeseriesResultMeta of(final int count,
            @Nullable final String unit,
            final String dataType) {

        checkNotNull(dataType, "dataType");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative but was: " + count);
        }
        return new ImmutableTimeseriesResultMeta(count, unit, dataType);
    }

    static TimeseriesResultMeta fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        final int count = jsonObject.getValueOrThrow(JsonFields.COUNT);
        final String unit = jsonObject.getValue(JsonFields.UNIT).orElse(null);
        final String dataType = jsonObject.getValueOrThrow(JsonFields.DATA_TYPE);

        return of(count, unit, dataType);
    }

    @Override
    public int getCount() {
        return count;
    }

    @Override
    public Optional<String> getUnit() {
        return Optional.ofNullable(unit);
    }

    @Override
    public String getDataType() {
        return dataType;
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.COUNT, count)
                .set(JsonFields.DATA_TYPE, dataType);

        if (unit != null) {
            builder.set(JsonFields.UNIT, unit);
        }

        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableTimeseriesResultMeta)) {
            return false;
        }
        final ImmutableTimeseriesResultMeta that = (ImmutableTimeseriesResultMeta) o;
        return count == that.count &&
                Objects.equals(unit, that.unit) &&
                Objects.equals(dataType, that.dataType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, unit, dataType);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "count=" + count +
                ", unit=" + unit +
                ", dataType=" + dataType +
                "]";
    }
}
