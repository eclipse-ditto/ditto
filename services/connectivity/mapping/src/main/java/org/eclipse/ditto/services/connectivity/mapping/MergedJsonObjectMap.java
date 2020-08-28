/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Merge 2 JSON objects and present them as a map.
 */
@Immutable
public final class MergedJsonObjectMap implements Map<CharSequence, JsonValue> {

    private final JsonObject jsonObject;
    private final JsonObject fallbackObject;

    private MergedJsonObjectMap(final JsonObject jsonObject, final JsonObject fallbackObject) {
        this.jsonObject = jsonObject;
        this.fallbackObject = fallbackObject;
    }

    static MergedJsonObjectMap of(final JsonObject jsonObject, final JsonObject fallbackObject) {
        return new MergedJsonObjectMap(jsonObject, fallbackObject);
    }

    // size is just a hint
    @Override
    public int size() {
        return Math.max(jsonObject.getSize(), fallbackObject.getSize());
    }

    @Override
    public boolean isEmpty() {
        return jsonObject.isEmpty() && fallbackObject.isEmpty();
    }

    @Override
    public boolean containsKey(final Object o) {
        if (!(o instanceof CharSequence)) {
            return false;
        }
        final CharSequence key = (CharSequence) o;
        return jsonObject.contains(key) || fallbackObject.contains(key);
    }

    @Override
    public boolean containsValue(final Object o) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    @Nullable
    public JsonValue get(final Object o) {
        if (!(o instanceof CharSequence)) {
            return null;
        }
        final CharSequence key = (CharSequence) o;
        return jsonObject.getValue(key).orElseGet(() -> fallbackObject.getValue(key).orElse(null));
    }

    @Override
    public JsonValue put(final CharSequence charSequence, final JsonValue jsonValue) {
        throw new UnsupportedOperationException("This object is immutable");
    }

    @Override
    public JsonValue remove(final Object o) {
        throw new UnsupportedOperationException("This object is immutable");
    }

    @Override
    public void putAll(final Map<? extends CharSequence, ? extends JsonValue> map) {
        throw new UnsupportedOperationException("This object is immutable");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("This object is immutable");
    }

    @Override
    public Set<CharSequence> keySet() {
        return Stream.concat(jsonObject.getKeys().stream(), fallbackObject.getKeys().stream())
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<JsonValue> values() {
        return Stream.concat(jsonObject.stream(), fallbackObject.stream())
                .map(JsonField::getValue)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Entry<CharSequence, JsonValue>> entrySet() {
        return Stream.concat(jsonObject.stream(), fallbackObject.stream())
                .map(field -> new AbstractMap.SimpleEntry<CharSequence, JsonValue>(field.getKey(), field.getValue()))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final MergedJsonObjectMap that = (MergedJsonObjectMap) o;
        return Objects.equals(jsonObject, that.jsonObject) && Objects.equals(fallbackObject, that.fallbackObject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonObject, fallbackObject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[jsonObject=" + jsonObject +
                ",fallbackObject=" + fallbackObject +
                "]";
    }
}
