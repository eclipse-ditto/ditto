/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.json.JsonFactory.newPointer;
import static org.eclipse.ditto.json.JsonFactory.newValue;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Builder for creating instances of {@link ImmutableJsonObject}. This builder does not allow duplicate JSON fields; i.
 * e. there is at most one association per JSON key in the resulting JSON object.
 */
@NotThreadSafe
final class ImmutableJsonObjectBuilder implements JsonObjectBuilder {

    private static final JsonKey ROOT_KEY = JsonKey.newInstance("/");

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

    private static <T> boolean testPredicate(final Predicate<T> predicate, final T value) {
        checkPredicate(predicate);
        return predicate.test(value);
    }

    private static void checkPredicate(final Predicate<?> predicate) {
        requireNonNull(predicate, "The predicate must not be null!");
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
    public ImmutableJsonObjectBuilder set(final CharSequence key, final String value,
            final Predicate<JsonField> predicate) {

        return set(key, JsonFactory.newValue(value), predicate);
    }

    @Override
    public ImmutableJsonObjectBuilder set(final CharSequence key, final JsonValue value,
            final Predicate<JsonField> predicate) {

        checkKey(key);
        checkValue(value);

        final JsonIndex jsonIndex = JsonFactory.newIndex(key);
        if (jsonIndex.isPointer()) {
            return setValueForPointer(jsonIndex.asPointer(), value, predicate);
        }
        setValueForKey(jsonIndex.asKey(), value, predicate);

        return this;
    }

    private static void checkKey(final CharSequence key) {
        final String pattern = "The key of the value must not be {0}!";
        requireNonNull(key, () -> MessageFormat.format(pattern, "null"));
    }

    private static void checkValue(final Object value) {
        requireNonNull(value, "The value must not be null!");
    }

    private ImmutableJsonObjectBuilder setValueForPointer(final JsonPointer pointer, final JsonValue value,
            final Predicate<JsonField> predicate) {

        if (pointer.isEmpty()) {
            setValueForKey(ROOT_KEY, value, predicate);
        } else if (1 == pointer.getLevelCount()) {
            setValueForKey(pointer.getRoot().orElse(ROOT_KEY), value, predicate);
        } else {
            final JsonKey leafKey = getLeafKey(pointer);
            final JsonField jsonField = JsonFactory.newField(leafKey, value);
            if (predicate.test(jsonField)) {
                return setFieldInHierarchy(this, pointer, jsonField);
            }
        }

        return this;
    }

    private static JsonKey getLeafKey(final JsonPointer pointer) {
        return pointer.getLeaf().orElseThrow(() -> new IllegalArgumentException("The pointer must not be empty!"));
    }

    private static ImmutableJsonObjectBuilder setFieldInHierarchy(final ImmutableJsonObjectBuilder target,
            final JsonPointer pointer, final JsonField field) {

        if (1 >= pointer.getLevelCount()) {
            return (ImmutableJsonObjectBuilder) target.set(field);
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
        return (ImmutableJsonObjectBuilder) target.set(rootKey, jsonObject);
    }

    private void setValueForKey(final JsonKey key, final JsonValue value, final Predicate<JsonField> predicate) {
        final JsonField jsonField = JsonFactory.newField(key, value);
        if (predicate.test(jsonField)) {
            fields.put(jsonField.getKeyName(), jsonField);
        }
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
    public JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final boolean value,
            final Predicate<JsonField> predicate) {

        return set(fieldDefinition, JsonFactory.newValue(value), predicate);
    }

    @Override
    public JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final int value,
            final Predicate<JsonField> predicate) {

        return set(fieldDefinition, JsonFactory.newValue(value), predicate);
    }

    @Override
    public JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final long value,
            final Predicate<JsonField> predicate) {

        return set(fieldDefinition, newValue(value), predicate);
    }

    @Override
    public JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final double value,
            final Predicate<JsonField> predicate) {

        return set(fieldDefinition, JsonFactory.newValue(value), predicate);
    }

    @Override
    public JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final String value,
            final Predicate<JsonField> predicate) {

        return set(fieldDefinition, newValue(value), predicate);
    }

    @Override
    public JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final JsonValue value,
            final Predicate<JsonField> predicate) {

        requireNonNull(fieldDefinition, "The definition of the JSON field to set the value for must not be null!");
        checkValue(value);
        final JsonKey leafKey = getLeafKey(fieldDefinition.getPointer());
        final JsonField jsonField = JsonFactory.newField(leafKey, value, fieldDefinition);

        if (predicate.test(jsonField)) {
            return setFieldInHierarchy(this, fieldDefinition.getPointer(), jsonField);
        }
        return this;
    }

    @Override
    public JsonObjectBuilder remove(final CharSequence key) {
        checkKey(key);

        return remove(newPointer(key));
    }

    private JsonObjectBuilder remove(final JsonPointer pointer) {
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
    public JsonObjectBuilder remove(final JsonFieldDefinition fieldDefinition) {
        requireNonNull(fieldDefinition, "The field definition must not be null!");
        return remove(fieldDefinition.getPointer());
    }

    @Override
    public JsonObjectBuilder setAll(final Iterable<JsonField> fields, final Predicate<JsonField> predicate) {
        requireNonNull(fields, "The JSON fields to be set must not be null!");
        checkPredicate(predicate);

        StreamSupport.stream(fields.spliterator(), false)
                .filter(field -> !field.getDefinition().isPresent() || predicate.test(field))
                .forEach(fieldToBeSet -> this.fields.put(fieldToBeSet.getKeyName(), fieldToBeSet));

        return this;
    }

    @Override
    public JsonObjectBuilder setAll(final Iterable<JsonField> fields) {
        requireNonNull(fields, "The JSON fields to be set must not be null!");

        for (final JsonField jsonField : fields) {
            this.fields.put(jsonField.getKeyName(), jsonField);
        }

        return this;
    }

    @Override
    public JsonObjectBuilder removeAll() {
        fields.clear();
        return this;
    }

    @Override
    public Iterator<JsonField> iterator() {
        return fields.values().iterator();
    }

    @Override
    public JsonObject build() {
        return ImmutableJsonObject.of(fields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "fields=" + fields.values() + "]";
    }

}
