/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Builder for creating instances of {@link ImmutableJsonObject}.
 * This builder does not allow duplicate JSON fields; i. e. there is at most one association per JSON key in the
 * resulting JSON object.
 */
@NotThreadSafe
final class ImmutableJsonObjectBuilder implements JsonObjectBuilder {

    private static final JsonKey ROOT_KEY = JsonFactory.newKey("/");

    private final Map<String, JsonField> fields;

    private ImmutableJsonObjectBuilder() {
        fields = new LinkedHashMap<>();
    }

    /**
     * Returns a new instance of {@code ImmutableJsonObjectBuilder}.
     *
     * @return a new builder for an immutable {@link JsonObject}.
     */
    public static ImmutableJsonObjectBuilder newInstance() {
        return new ImmutableJsonObjectBuilder();
    }

    @Override
    public ImmutableJsonObjectBuilder set(final CharSequence key, final int value,
            final Predicate<JsonField> predicate) {

        return set(key, JsonFactory.newValue(value), predicate);
    }

    @Override
    public ImmutableJsonObjectBuilder set(final CharSequence key, final long value,
            final Predicate<JsonField> predicate) {

        return set(key, JsonFactory.newValue(value), predicate);
    }

    @Override
    public ImmutableJsonObjectBuilder set(final CharSequence key, final double value,
            final Predicate<JsonField> predicate) {

        return set(key, JsonFactory.newValue(value), predicate);
    }

    @Override
    public ImmutableJsonObjectBuilder set(final CharSequence key, final boolean value,
            final Predicate<JsonField> predicate) {

        return set(key, JsonFactory.newValue(value), predicate);
    }

    @Override
    public ImmutableJsonObjectBuilder set(final CharSequence key, @Nullable final String value,
            final Predicate<JsonField> predicate) {

        return set(key, JsonFactory.newValue(value), predicate);
    }

    @Override
    public ImmutableJsonObjectBuilder set(final CharSequence key, final JsonValue value,
            final Predicate<JsonField> predicate) {

        final JsonPointer pointer = JsonFactory.getNonEmptyPointer(key);
        checkValue(value);
        checkPredicate(predicate);

        pointer.getLeaf()
                .map(leafKey -> JsonFactory.newField(leafKey, value))
                .filter(predicate)
                .ifPresent(jsonField -> setFieldInHierarchy(this, pointer, jsonField));

        return this;
    }

    @Override
    public <T> JsonObjectBuilder set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value,
            final Predicate<JsonField> predicate) {

        requireNonNull(fieldDefinition, "The definition of the JSON field to set the value for must not be null!");
        checkPredicate(predicate);

        final JsonPointer pointer = fieldDefinition.getPointer();
        pointer.getLeaf()
                .map(leafKey -> JsonFactory.newField(leafKey, JsonFactory.getAppropriateValue(value), fieldDefinition))
                .filter(predicate)
                .ifPresent(jsonField -> setFieldInHierarchy(this, pointer, jsonField));

        return this;
    }

    private static void checkValue(final Object value) {
        requireNonNull(value, "The value must not be null!");
    }

    private static void checkPredicate(final Predicate<JsonField> predicate) {
        requireNonNull(predicate, "The predicate must not be null!");
    }

    private static JsonObjectBuilder setFieldInHierarchy(final ImmutableJsonObjectBuilder target,
            final JsonPointer pointer, final JsonField field) {

        if (1 >= pointer.getLevelCount()) {
            return target.set(field);
        }

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);

        final ImmutableJsonObjectBuilder newTarget = newInstance();
        final JsonField rootJsonField = target.fields.get(rootKey.toString());
        if (null != rootJsonField) {
            final JsonValue rootValue = rootJsonField.getValue();
            if (rootValue.isObject() && !rootValue.isNull()) {
                newTarget.setAll(rootValue.asObject());
            }
        }

        // let the recursion begin >:-(
        final JsonObject jsonObject = setFieldInHierarchy(newTarget, pointer.nextLevel(), field).build();
        return target.set(rootKey, jsonObject);
    }

    @Override
    public ImmutableJsonObjectBuilder set(final JsonField field, final Predicate<JsonField> predicate) {
        requireNonNull(field, "The field to be set must not be null!");

        if (predicate.test(field)) {
            fields.put(field.getKeyName(), field);
        }
        return this;
    }

    @Override
    public ImmutableJsonObjectBuilder remove(final CharSequence key) {
        return remove(JsonFactory.newPointer(key));
    }

    private ImmutableJsonObjectBuilder remove(final JsonPointer pointer) {
        pointer.getRoot()
                .map(JsonKey::toString)
                .map(fields::get)
                .ifPresent(jsonField -> {
                    final JsonValue rootValue = jsonField.getValue();
                    final JsonPointer nextPointerLevel = pointer.nextLevel();
                    if (rootValue.isObject() && !nextPointerLevel.isEmpty()) {
                        JsonObject rootObject = rootValue.asObject();
                        rootObject = rootObject.remove(nextPointerLevel);
                        set(JsonFactory.newField(jsonField.getKey(), rootObject,
                                jsonField.getDefinition().orElse(null)));
                    } else {
                        fields.remove(jsonField.getKeyName());
                    }
                });

        return this;
    }

    @Override
    public ImmutableJsonObjectBuilder remove(final JsonFieldDefinition<?> fieldDefinition) {
        requireNonNull(fieldDefinition, "The field definition must not be null!");
        return remove(fieldDefinition.getPointer());
    }

    @Override
    public ImmutableJsonObjectBuilder setAll(final Iterable<JsonField> fields, final Predicate<JsonField> predicate) {
        requireNonNull(fields, "The JSON fields to be set must not be null!");
        checkPredicate(predicate);

        StreamSupport.stream(fields.spliterator(), false)
                .filter(field -> !field.getDefinition().isPresent() || predicate.test(field))
                .forEach(fieldToBeSet -> this.fields.put(fieldToBeSet.getKeyName(), fieldToBeSet));

        return this;
    }

    @Override
    public ImmutableJsonObjectBuilder setAll(final Iterable<JsonField> fields) {
        requireNonNull(fields, "The JSON fields to be set must not be null!");

        for (final JsonField jsonField : fields) {
            this.fields.put(jsonField.getKeyName(), jsonField);
        }

        return this;
    }

    @Override
    public ImmutableJsonObjectBuilder removeAll() {
        fields.clear();
        return this;
    }

    @Override
    public Iterator<JsonField> iterator() {
        return fields.values().iterator();
    }

    @Override
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    @Override
    public int getSize() {
        return fields.size();
    }

    @Override
    public Stream<JsonField> stream() {
        return fields.values().stream();
    }

    @Override
    public JsonObject build() {
        return ImmutableJsonObject.of(fields);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonObjectBuilder that = (ImmutableJsonObjectBuilder) o;
        return Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "fields=" + fields.values() + "]";
    }

}
