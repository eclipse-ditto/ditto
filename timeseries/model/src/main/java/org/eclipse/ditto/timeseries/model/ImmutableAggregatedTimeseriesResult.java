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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link AggregatedTimeseriesResult}.
 *
 * @since 4.0.0
 */
@Immutable
final class ImmutableAggregatedTimeseriesResult implements AggregatedTimeseriesResult {

    private final Map<String, String> group;
    private final JsonPointer path;
    private final TimeseriesResultMeta meta;
    private final List<TimeseriesDataValue> data;

    private ImmutableAggregatedTimeseriesResult(final Map<String, String> group,
            final JsonPointer path,
            final TimeseriesResultMeta meta,
            final List<TimeseriesDataValue> data) {

        this.group = Collections.unmodifiableMap(new LinkedHashMap<>(group));
        this.path = path;
        this.meta = meta;
        this.data = Collections.unmodifiableList(new ArrayList<>(data));
    }

    static AggregatedTimeseriesResult of(final Map<String, String> group,
            final JsonPointer path,
            final TimeseriesResultMeta meta,
            final List<TimeseriesDataValue> data) {

        checkNotNull(group, "group");
        checkNotNull(path, "path");
        checkNotNull(meta, "meta");
        checkNotNull(data, "data");
        return new ImmutableAggregatedTimeseriesResult(group, path, meta, data);
    }

    static AggregatedTimeseriesResult fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        final Map<String, String> group = jsonObject.getValue(JsonFields.GROUP)
                .map(ImmutableAggregatedTimeseriesResult::groupFromJson)
                .orElseGet(Collections::emptyMap);
        final JsonPointer path = JsonPointer.of(jsonObject.getValueOrThrow(JsonFields.PATH));
        final TimeseriesResultMeta meta =
                TimeseriesResultMeta.fromJson(jsonObject.getValueOrThrow(JsonFields.META));
        final List<TimeseriesDataValue> data = dataFromJson(jsonObject.getValueOrThrow(JsonFields.DATA));

        return of(group, path, meta, data);
    }

    private static Map<String, String> groupFromJson(final JsonObject groupJson) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final JsonField field : groupJson) {
            final JsonValue value = field.getValue();
            result.put(field.getKeyName(), value.isString() ? value.asString() : value.formatAsString());
        }
        return result;
    }

    private static List<TimeseriesDataValue> dataFromJson(final JsonArray array) {
        final List<TimeseriesDataValue> result = new ArrayList<>(array.getSize());
        for (final JsonValue value : array) {
            result.add(TimeseriesDataValue.fromJson(value.asObject()));
        }
        return result;
    }

    @Override
    public Map<String, String> getGroup() {
        return group;
    }

    @Override
    public JsonPointer getPath() {
        return path;
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
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        if (!group.isEmpty()) {
            final JsonObjectBuilder groupBuilder = JsonFactory.newObjectBuilder();
            for (final Map.Entry<String, String> entry : group.entrySet()) {
                groupBuilder.set(JsonKey.of(entry.getKey()), entry.getValue());
            }
            builder.set(JsonFields.GROUP, groupBuilder.build());
        }
        return builder
                .set(JsonFields.PATH, path.toString())
                .set(JsonFields.META, meta.toJson())
                .set(JsonFields.DATA, data.stream()
                        .map(TimeseriesDataValue::toJson)
                        .map(JsonValue.class::cast)
                        .collect(JsonCollectors.valuesToArray()))
                .build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAggregatedTimeseriesResult that = (ImmutableAggregatedTimeseriesResult) o;
        return Objects.equals(group, that.group)
                && Objects.equals(path, that.path)
                && Objects.equals(meta, that.meta)
                && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, path, meta, data);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [group=" + group
                + ", path=" + path
                + ", meta=" + meta
                + ", data=" + data
                + "]";
    }

    /**
     * JSON field definitions of an {@code AggregatedTimeseriesResult}.
     */
    static final class JsonFields {

        static final JsonFieldDefinition<JsonObject> GROUP =
                JsonFactory.newJsonObjectFieldDefinition("group", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> PATH =
                JsonFactory.newStringFieldDefinition("path", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonObject> META =
                JsonFactory.newJsonObjectFieldDefinition("result", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonArray> DATA =
                JsonFactory.newJsonArrayFieldDefinition("data", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
