/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * A package internal helper construct to extend {@link JsonObjectBuilder}  with an additional type information.
 *
 * @param <B> the type of the JsonObjectBuilder.
 * @param <T> the type of the built JsonObjects.
 */
interface TypedJsonObjectBuilder<B extends JsonObjectBuilder, T extends JsonObject>
        extends JsonObjectBuilder {

    @Override
    B set(CharSequence key, int value, Predicate<JsonField> predicate);

    @Override
    default B set(final CharSequence key, final int value) {
        return set(key, value, field -> true);
    }

    @Override
    B set(CharSequence key, long value, Predicate<JsonField> predicate);

    @Override
    default B set(final CharSequence key, final long value) {
        return set(key, value, field -> true);
    }

    @Override
    B set(CharSequence key, double value, Predicate<JsonField> predicate);

    @Override
    default B set(final CharSequence key, final double value) {
        return set(key, value, field -> true);
    }

    @Override
    B set(CharSequence key, boolean value, Predicate<JsonField> predicate);

    @Override
    default B set(final CharSequence key, final boolean value) {
        return set(key, value, field -> true);
    }

    @Override
    B set(CharSequence key, @Nullable String value, Predicate<JsonField> predicate);

    @Override
    default B set(final CharSequence key, @Nullable final String value) {
        return set(key, value, field -> true);
    }

    @Override
    B set(CharSequence key, JsonValue value, Predicate<JsonField> predicate);

    @Override
    default B set(final CharSequence key, final JsonValue value) {
        return set(key, value, field -> true);
    }

    @Override
    <J> B set(JsonFieldDefinition<J> fieldDefinition, @Nullable J value, Predicate<JsonField> predicate);

    @Override
    default <J> B set(final JsonFieldDefinition<J> fieldDefinition, @Nullable final J value) {
        return set(fieldDefinition, value, jsonField -> true);
    }

    @Override
    B set(JsonField field, Predicate<JsonField> predicate);

    @Override
    default B set(final JsonField field) {
        return set(field, jsonField -> true);
    }

    @Override
    B remove(CharSequence key);

    @Override
    B remove(JsonFieldDefinition<?> fieldDefinition);

    @Override
    B setAll(Iterable<JsonField> fields, Predicate<JsonField> predicate);

    @Override
    default B setAll(final Iterable<JsonField> fields) {
        return setAll(fields, jsonField -> true);
    }

    @Override
    B removeAll();

    @Override
    T build();
}
