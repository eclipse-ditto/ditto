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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;

/**
 * An immutable implementation of {@link TimeseriesQueryResult}.
 */
@Immutable
final class ImmutableTimeseriesQueryResult implements TimeseriesQueryResult {

    private final ThingId thingId;
    private final JsonPointer path;
    private final TimeseriesQuery query;
    private final TimeseriesResultMeta meta;
    private final List<TimeseriesDataValue> data;

    private ImmutableTimeseriesQueryResult(final ThingId thingId,
            final JsonPointer path,
            final TimeseriesQuery query,
            final TimeseriesResultMeta meta,
            final List<TimeseriesDataValue> data) {

        this.thingId = thingId;
        this.path = path;
        this.query = query;
        this.meta = meta;
        this.data = data;
    }

    static TimeseriesQueryResult of(final ThingId thingId,
            final JsonPointer path,
            final TimeseriesQuery query,
            final TimeseriesResultMeta meta,
            final List<TimeseriesDataValue> data) {

        checkNotNull(thingId, "thingId");
        checkNotNull(path, "path");
        checkNotNull(query, "query");
        checkNotNull(meta, "meta");
        checkNotNull(data, "data");

        return new ImmutableTimeseriesQueryResult(
                thingId, path, query, meta,
                Collections.unmodifiableList(new ArrayList<>(data)));
    }

    static TimeseriesQueryResult fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(JsonFields.THING_ID));
        final JsonPointer path = JsonPointer.of(jsonObject.getValueOrThrow(JsonFields.PATH));
        final TimeseriesQuery query =
                TimeseriesQuery.fromJson(jsonObject.getValueOrThrow(JsonFields.QUERY));
        final TimeseriesResultMeta meta =
                TimeseriesResultMeta.fromJson(jsonObject.getValueOrThrow(JsonFields.META));
        final List<TimeseriesDataValue> data = dataFromJson(jsonObject.getValueOrThrow(JsonFields.DATA));

        return of(thingId, path, query, meta, data);
    }

    private static List<TimeseriesDataValue> dataFromJson(final JsonArray array) {
        final List<TimeseriesDataValue> result = new ArrayList<>(array.getSize());
        for (final JsonValue value : array) {
            if (!value.isObject()) {
                throw new DittoJsonException(JsonParseException.newBuilder()
                        .message("Element of <data> must be a JSON object but was: " + value)
                        .build());
            }
            result.add(TimeseriesDataValue.fromJson(value.asObject()));
        }
        return result;
    }

    @Override
    public ThingId getThingId() {
        return thingId;
    }

    @Override
    public JsonPointer getPath() {
        return path;
    }

    @Override
    public TimeseriesQuery getQuery() {
        return query;
    }

    @Override
    public TimeseriesResultMeta getMeta() {
        return meta;
    }

    @Override
    public List<TimeseriesDataValue> getData() {
        return data;
    }

    @Override
    public JsonObject toJson() {
        final JsonArrayBuilder dataBuilder = JsonFactory.newArrayBuilder();
        for (final TimeseriesDataValue value : data) {
            dataBuilder.add(value.toJson());
        }

        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.THING_ID, thingId.toString())
                .set(JsonFields.PATH, path.toString())
                .set(JsonFields.QUERY, query.toJson())
                .set(JsonFields.META, meta.toJson())
                .set(JsonFields.DATA, dataBuilder.build());

        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableTimeseriesQueryResult)) {
            return false;
        }
        final ImmutableTimeseriesQueryResult that = (ImmutableTimeseriesQueryResult) o;
        return Objects.equals(thingId, that.thingId) &&
                Objects.equals(path, that.path) &&
                Objects.equals(query, that.query) &&
                Objects.equals(meta, that.meta) &&
                Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, path, query, meta, data);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", path=" + path +
                ", query=" + query +
                ", meta=" + meta +
                ", data=" + data +
                "]";
    }
}
