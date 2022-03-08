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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Abstract implementation of {@link TypedJsonObjectBuilder}.
 */
abstract class AbstractTypedJsonObjectBuilder<B extends TypedJsonObjectBuilder<B, T>, T extends JsonObject>
        implements TypedJsonObjectBuilder<B, T> {

    protected final B myself;
    protected final JsonObjectBuilder wrappedObjectBuilder;

    @SuppressWarnings("unchecked")
    protected AbstractTypedJsonObjectBuilder(final JsonObjectBuilder wrappedObjectBuilder, final Class<?> selfType) {
        myself = (B) selfType.cast(this);
        this.wrappedObjectBuilder = checkNotNull(wrappedObjectBuilder, "wrappedObjectBuilder");
    }

    @Override
    public B set(final CharSequence key, final int value, final Predicate<JsonField> predicate) {
        wrappedObjectBuilder.set(key, value, predicate);
        return myself;
    }

    @Override
    public B set(final CharSequence key, final long value, final Predicate<JsonField> predicate) {
        wrappedObjectBuilder.set(key, value, predicate);
        return myself;
    }

    @Override
    public B set(final CharSequence key, final double value, final Predicate<JsonField> predicate) {
        wrappedObjectBuilder.set(key, value, predicate);
        return myself;
    }

    @Override
    public B set(final CharSequence key, final boolean value, final Predicate<JsonField> predicate) {
        wrappedObjectBuilder.set(key, value, predicate);
        return myself;
    }

    @Override
    public B set(final CharSequence key, @Nullable final String value, final Predicate<JsonField> predicate) {
        wrappedObjectBuilder.set(key, value, predicate);
        return myself;
    }

    @Override
    public B set(final CharSequence key, final JsonValue value, final Predicate<JsonField> predicate) {
        wrappedObjectBuilder.set(key, value, predicate);
        return myself;
    }

    @Override
    public <J> B set(final JsonFieldDefinition<J> fieldDefinition, @Nullable final J value,
            final Predicate<JsonField> predicate) {
        wrappedObjectBuilder.set(fieldDefinition, value, predicate);
        return myself;
    }

    @Override
    public B set(final JsonField field, final Predicate<JsonField> predicate) {
        wrappedObjectBuilder.set(field, predicate);
        return myself;
    }

    @Override
    public B remove(final CharSequence key) {
        wrappedObjectBuilder.remove(key);
        return myself;
    }

    @Override
    public B remove(final JsonFieldDefinition<?> fieldDefinition) {
        wrappedObjectBuilder.remove(fieldDefinition);
        return myself;
    }

    @Override
    public B setAll(final Iterable<JsonField> fields, final Predicate<JsonField> predicate) {
        wrappedObjectBuilder.setAll(fields, predicate);
        return myself;
    }

    @Override
    public B removeAll() {
        wrappedObjectBuilder.removeAll();
        return myself;
    }

    @Override
    public Iterator<JsonField> iterator() {
        return wrappedObjectBuilder.iterator();
    }

    @Override
    public boolean isEmpty() {
        return wrappedObjectBuilder.isEmpty();
    }

    @Override
    public int getSize() {
        return wrappedObjectBuilder.getSize();
    }

    @Override
    public Stream<JsonField> stream() {
        return wrappedObjectBuilder.stream();
    }

    protected <J> void putValue(final JsonFieldDefinition<J> definition, @Nullable final J value) {
        final Optional<JsonKey> keyOpt = definition.getPointer().getRoot();
        if (keyOpt.isPresent()) {
            final JsonKey key = keyOpt.get();
            if (null != value) {
                checkNotNull(value, definition.getPointer().toString());
                wrappedObjectBuilder.remove(key);
                wrappedObjectBuilder.set(definition, value);
            } else {
                wrappedObjectBuilder.remove(key);
            }
        }
    }

}
