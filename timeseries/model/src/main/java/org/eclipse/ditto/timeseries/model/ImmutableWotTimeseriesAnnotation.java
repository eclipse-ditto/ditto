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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link WotTimeseriesAnnotation}.
 */
@Immutable
final class ImmutableWotTimeseriesAnnotation implements WotTimeseriesAnnotation {

    private final Ingest ingest;
    private final Map<String, String> tags;

    private ImmutableWotTimeseriesAnnotation(final Ingest ingest, final Map<String, String> tags) {
        this.ingest = ingest;
        this.tags = tags;
    }

    static WotTimeseriesAnnotation of(final Ingest ingest, final Map<String, String> tags) {
        checkNotNull(ingest, "ingest");
        checkNotNull(tags, "tags");
        return new ImmutableWotTimeseriesAnnotation(
                ingest,
                Collections.unmodifiableMap(new LinkedHashMap<>(tags)));
    }

    static WotTimeseriesAnnotation fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        final String rawIngest = jsonObject.getValueOrThrow(JsonFields.INGEST);
        final Ingest ingest = Ingest.forName(rawIngest)
                .orElseThrow(() -> new DittoJsonException(JsonParseException.newBuilder()
                        .message("Field <" + JsonFields.INGEST.getPointer() +
                                "> has an unknown value: <" + rawIngest + ">.")
                        .description("Expected one of: ALL, NONE.")
                        .build()));

        final Map<String, String> tags = jsonObject.getValue(JsonFields.TAGS)
                .map(ImmutableWotTimeseriesAnnotation::tagsFromJson)
                .orElseGet(Collections::emptyMap);

        return new ImmutableWotTimeseriesAnnotation(
                ingest, Collections.unmodifiableMap(new LinkedHashMap<>(tags)));
    }

    static Optional<WotTimeseriesAnnotation> findInProperty(final JsonObject propertySchema) {
        checkNotNull(propertySchema, "propertySchema");
        return propertySchema.getValue(EXTENSION_KEY)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ImmutableWotTimeseriesAnnotation::fromJson);
    }

    private static Map<String, String> tagsFromJson(final JsonObject tagsJson) {
        final Map<String, String> result = new LinkedHashMap<>(tagsJson.getSize());
        for (final JsonField field : tagsJson) {
            final JsonValue value = field.getValue();
            if (!value.isString()) {
                throw new DittoJsonException(JsonParseException.newBuilder()
                        .message("Tag value for key <" + field.getKeyName() +
                                "> must be a JSON string.")
                        .build());
            }
            result.put(field.getKeyName(), value.asString());
        }
        return result;
    }

    @Override
    public Ingest getIngest() {
        return ingest;
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.INGEST, ingest.getName());

        if (!tags.isEmpty()) {
            final JsonObjectBuilder tagsBuilder = JsonFactory.newObjectBuilder();
            for (final Map.Entry<String, String> entry : tags.entrySet()) {
                tagsBuilder.set(entry.getKey(), entry.getValue());
            }
            builder.set(JsonFields.TAGS, tagsBuilder.build());
        }

        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableWotTimeseriesAnnotation)) {
            return false;
        }
        final ImmutableWotTimeseriesAnnotation that = (ImmutableWotTimeseriesAnnotation) o;
        return ingest == that.ingest && Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ingest, tags);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "ingest=" + ingest +
                ", tags=" + tags +
                "]";
    }
}
