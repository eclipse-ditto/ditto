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

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of a JSON object. Each call to a method which would alter the state of this object
 * returns a new JSON object with the altered state instead while the old JSON object remains unchanged. Care has to be
 * taken to assign the result of an altering method like {@code add} to a variable to have a handle to the new resp.
 * altered JSON object.
 */
@Immutable
final class ImmutableJsonObject extends AbstractImmutableJsonValue implements JsonObject {

    private static final JsonKey ROOT_KEY = JsonKey.newInstance("/");

    private final Map<String, JsonField> fields;

    private ImmutableJsonObject(final Map<String, JsonField> theFields) {
        requireNonNull(theFields, "The fields of JSON object must not be null!");

        fields = Collections.unmodifiableMap(new LinkedHashMap<>(theFields));
    }

    /**
     * Returns a new empty JSON object.
     *
     * @return a new empty JSON object.
     */
    public static ImmutableJsonObject empty() {
        return new ImmutableJsonObject(Collections.emptyMap());
    }

    /**
     * Returns a new {@code ImmutableJsonObject} instance which contains the given fields.
     *
     * @param fields the fields of the new JSON object.
     * @return a new JSON object containing the {@code fields}.
     * @throws NullPointerException if {@code fields} is {@code null}.
     */
    public static ImmutableJsonObject of(final Map<String, JsonField> fields) {
        return new ImmutableJsonObject(fields);
    }

    private static void checkPointer(final JsonPointer pointer) {
        requireNonNull(pointer, "The JSON pointer must not be null!");
    }

    private static void checkFieldDefinition(final JsonFieldDefinition fieldDefinition) {
        requireNonNull(fieldDefinition, "The JSON field definition which supplies the pointer must not be null!");
    }

    @Override
    public JsonObject setValue(final CharSequence key, final int value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final long value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final double value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final boolean value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final String value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final JsonValue value) {
        checkKey(key);

        JsonObject result = this;

        final JsonIndex jsonIndex = JsonFactory.newIndex(key);
        if (jsonIndex.isPointer()) {
            final JsonPointer pointer = jsonIndex.asPointer();
            final JsonKey leafKey = pointer.getLeaf().orElse(ROOT_KEY);
            final Optional<JsonFieldDefinition> keyDefinition = getDefinitionForKey(leafKey);
            result = setFieldInHierarchy(this, pointer,
                    JsonFactory.newField(leafKey, value, keyDefinition.orElse(null)));
        } else {
            final Optional<JsonFieldDefinition> keyDefinition = getDefinitionForKey(jsonIndex.asKey());
            final JsonField fieldToBeAdded = JsonFactory.newField(jsonIndex.asKey(), value, keyDefinition.orElse(null));
            final JsonField existingJsonField = fields.get(key.toString());
            if (!fieldToBeAdded.equals(existingJsonField)) {
                final Map<String, JsonField> fieldsCopy = copyFields();
                fieldsCopy.put(key.toString(), fieldToBeAdded);
                result = new ImmutableJsonObject(fieldsCopy);
            }
        }

        return result;
    }

    private static void checkKey(final CharSequence name) {
        final String pattern = "The key of the value to set, get or remove must not be {0}!";
        requireNonNull(name, MessageFormat.format(pattern, "null"));
        if (0 == name.length()) {
            throw new IllegalArgumentException(MessageFormat.format(pattern, "empty"));
        }
    }

    @Override
    public JsonObject set(final JsonFieldDefinition fieldDefinition, final JsonValue value) {
        requireNonNull(fieldDefinition, "The JSON field definition to set the value for must not be null!");

        final JsonPointer pointer = fieldDefinition.getPointer();

        final JsonKey leafKey = pointer.getLeaf().orElseThrow(() -> {
            final String msgTemplate = "The pointer of the field definition <{0}> must not be empty!";
            return new IllegalArgumentException(MessageFormat.format(msgTemplate, fieldDefinition));
        });
        final JsonField jsonField = JsonFactory.newField(leafKey, value, fieldDefinition);
        return setFieldInHierarchy(this, pointer, jsonField);
    }

    private static JsonObject setFieldInHierarchy(final JsonObject target, final JsonPointer pointer,
            final JsonField jsonField) {

        if (1 >= pointer.getLevelCount()) {
            return target.set(jsonField);
        }

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);

        final JsonObject newTarget = target.getValue(rootKey)
                .filter(JsonValue::isObject)
                .filter(jsonValue -> !jsonValue.isNull())
                .map(JsonValue::asObject)
                .orElseGet(ImmutableJsonObject::empty);

        // let the recursion begin ]:-)
        return target.setValue(rootKey, setFieldInHierarchy(newTarget, pointer.nextLevel(), jsonField));
    }

    @Override
    public ImmutableJsonObject set(final JsonField field) {
        requireNonNull(field, "The JSON field to be set must not be null!");

        ImmutableJsonObject result = this;

        final JsonField existingField = fields.get(field.getKeyName());
        if (!field.equals(existingField)) {
            final Map<String, JsonField> fieldsCopy = copyFields();
            fieldsCopy.put(field.getKeyName(), field);
            result = new ImmutableJsonObject(fieldsCopy);
        }

        return result;
    }

    private Map<String, JsonField> copyFields() {
        return new LinkedHashMap<>(fields);
    }

    @Override
    public JsonObject setAll(final Iterable<JsonField> fields) {
        requireNonNull(fields, "The JSON fields to add must not be null!");

        final JsonObject result;

        if (isEmpty(fields)) {
            result = this;
        } else {
            final Map<String, JsonField> fieldsCopy = copyFields();
            fields.forEach(jsonField -> fieldsCopy.put(jsonField.getKeyName(), jsonField));
            result = new ImmutableJsonObject(fieldsCopy);
        }

        return result;
    }

    private static boolean isEmpty(final Iterable<?> iterable) {
        final Iterator<?> iterator = iterable.iterator();
        return !iterator.hasNext();
    }

    @Override
    public boolean contains(final CharSequence key) {
        requireNonNull(key, "The key to check the existence of a value for must not be null!");

        final boolean result;

        final JsonIndex jsonIndex = JsonFactory.newIndex(key);
        if (jsonIndex.isPointer()) {
            final JsonPointer pointer = jsonIndex.asPointer();
            final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);
            if (1 >= pointer.getLevelCount()) {
                result = containsKey(rootKey);
            } else {
                result = getValueForKey(rootKey)
                        .map(jsonValue -> !jsonValue.isObject() ||
                                jsonValue.asObject().contains(pointer.nextLevel()) // Recursion
                        )
                        .orElse(false);
            }

        } else {
            result = containsKey(key);
        }

        return result;
    }

    private Optional<JsonValue> getValueForKey(final CharSequence key) {
        final JsonField jsonField = fields.get(key.toString());
        return null != jsonField ? Optional.of(jsonField.getValue()) : Optional.empty();
    }

    private Optional<JsonFieldDefinition> getDefinitionForKey(final CharSequence key) {
        final JsonField jsonField = fields.get(key.toString());
        return null != jsonField ? jsonField.getDefinition() : Optional.empty();
    }

    @Override
    public Optional<JsonValue> getValue(final CharSequence key) {
        checkKey(key);

        return getValueForIndex(JsonFactory.newIndex(key));
    }

    private Optional<JsonValue> getValueForIndex(final JsonIndex index) {
        if (index.isPointer()) {
            return getValueForPointer(index.asPointer());
        }
        final JsonField jsonField = fields.get(index.toString());
        if (null != jsonField) {
            return Optional.of(jsonField.getValue());
        }
        return Optional.empty();
    }

    private Optional<JsonValue> getValueForPointer(final JsonPointer pointer) {
        final Optional<JsonValue> result;

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);
        if (pointer.isEmpty() || 1 == pointer.getLevelCount()) {
            // same as getting a value for a key
            result = getValueForKey(rootKey);
        } else {
            final Optional<JsonValue> jsonValue = getValueForKey(rootKey);
            final boolean isArray = jsonValue.filter(JsonValue::isArray).isPresent();

            if (isArray) {
                result = jsonValue
                        .map(JsonValue::asArray)
                        .map(JsonValueContainer::stream)
                        .map(jsonValueStream -> jsonValueStream
                                .map(JsonValue::asObject)
                                .map(jsonObject -> jsonObject.getValue(pointer.nextLevel()))
                                .map(Optional::get)
                                .collect(JsonCollectors.valuesToArray()));
            } else {
                result = jsonValue.filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .flatMap(jsonObject -> jsonObject.getValue(pointer.nextLevel()));
            }
        }

        return result;
    }

    @Override
    public Optional<JsonValue> getValue(final JsonFieldDefinition fieldDefinition) {
        checkFieldDefinition(fieldDefinition);
        return getValueForPointer(fieldDefinition.getPointer());
    }

    @Override
    public JsonObject get(final JsonPointer pointer) {
        checkPointer(pointer);

        final JsonObject result;

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);
        final Optional<JsonValue> rootKeyValue = getValueForKey(rootKey);
        final Optional<JsonFieldDefinition> rootKeyDefinition = getDefinitionForKey(rootKey);
        if (pointer.isEmpty() || 1 == pointer.getLevelCount()) {
            result = rootKeyValue.map(
                    jsonValue -> JsonFactory.newField(rootKey, jsonValue, rootKeyDefinition.orElse(null)))
                    .map(jsonField -> Collections.singletonMap(jsonField.getKeyName(), jsonField))
                    .map(ImmutableJsonObject::of)
                    .orElseGet(ImmutableJsonObject::empty);
        } else {

            // The pointer has more than one level; therefore build result recursively.
            final JsonPointer nextPointerLevel = pointer.nextLevel();
            final Predicate<JsonObject> containsNextLevelRootKey = jsonObject -> nextPointerLevel.getRoot()
                    .filter(jsonObject::contains)
                    .isPresent();

            result = rootKeyValue.map(jsonValue -> {
                if (jsonValue.isObject()) {
                    if (containsNextLevelRootKey.test(jsonValue.asObject())) {
                        return jsonValue.asObject().get(nextPointerLevel); // Recursion
                    } else {
                        return null;
                    }
                } else {
                    return jsonValue;
                }
            })
                    .map(jsonValue -> JsonFactory.newField(rootKey, jsonValue, rootKeyDefinition.orElse(null)))
                    .map(jsonField -> Collections.singletonMap(jsonField.getKeyName(), jsonField))
                    .map(ImmutableJsonObject::of)
                    .orElseGet(ImmutableJsonObject::empty);
        }

        return result;
    }

    private boolean containsKey(final CharSequence key) {
        return fields.containsKey(key.toString());
    }

    @Override
    public JsonObject get(final JsonFieldDefinition fieldDefinition) {
        checkFieldDefinition(fieldDefinition);
        return get(fieldDefinition.getPointer());
    }

    @Override
    public JsonObject get(final JsonFieldSelector fieldSelector) {
        requireNonNull(fieldSelector, "The JSON field selector must not be null!");

        if (isEmpty()) {
            return this;
        }

        final Map<JsonKey, JsonValue> jsonValues = new LinkedHashMap<>();
        for (final JsonPointer jsonPointer : fieldSelector) {
            if (contains(jsonPointer)) {
                final JsonObject objectForPointer = get(jsonPointer);
                final JsonKey rootKey = jsonPointer.getRoot().orElse(ROOT_KEY);
                if (jsonValues.containsKey(rootKey)) {
                    jsonValues.put(rootKey, mergeIfJsonObject(jsonValues.get(rootKey), jsonPointer, objectForPointer));
                } else {
                    objectForPointer.getValue(rootKey).ifPresent(jsonObject -> jsonValues.put(rootKey, jsonObject));
                }
            }
        }

        final Map<String, JsonField> newJsonFields = jsonValues.entrySet()
                .stream()
                .map(entry -> JsonFactory.newField(entry.getKey(), entry.getValue(),
                        Optional.ofNullable(fields.get(entry.getKey().toString()))
                                .flatMap(JsonField::getDefinition)
                                .orElse(null)))
                .collect(Collectors.toMap(JsonField::getKeyName, Function.identity(), (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                }, LinkedHashMap::new));

        return of(newJsonFields);
    }

    private static JsonValue mergeIfJsonObject(final JsonValue jsonValue, final JsonPointer pointer,
            final JsonObject toBeMerged) {

        final JsonValue result;
        if (jsonValue.isObject()) {
            final JsonObject alreadyKnownObject = jsonValue.asObject();
            result = toBeMerged.getValue(pointer)
                    .map(value -> alreadyKnownObject.setValue(pointer.nextLevel(), value))
                    .orElse(alreadyKnownObject);
        } else {
            result = jsonValue;
        }

        return result;
    }

    @Override
    public JsonObject remove(final CharSequence index) {
        checkKey(index);

        return removeForIndex(JsonFactory.newIndex(index));
    }

    private JsonObject removeForIndex(final JsonIndex index) {
        if (index.isPointer()) {
            return removeForPointer(index.asPointer());
        }
        return removeValueForKey(index);
    }

    private JsonObject removeForPointer(final JsonPointer pointer) {
        final JsonObject result;

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);
        final Optional<JsonFieldDefinition> rootKeyDefinition = getDefinitionForKey(rootKey);
        if (pointer.isEmpty()) {
            result = this;
        } else if (1 == pointer.getLevelCount()) {
            result = removeValueForKey(rootKey);
        } else {
            final JsonPointer nextPointerLevel = pointer.nextLevel();

            final Predicate<JsonObject> containsNextLevelRootKey = jsonObject -> nextPointerLevel.getRoot()
                    .filter(jsonObject::contains)
                    .isPresent();

            result = getValueForKey(rootKey)
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .filter(containsNextLevelRootKey)
                    .map(jsonObject -> jsonObject.remove(nextPointerLevel)) // Recursion
                    .map(withoutValue -> JsonFactory.newField(rootKey, withoutValue, rootKeyDefinition.orElse(null)))
                    .map(this::set)
                    .orElse(this);
        }

        return result;
    }

    private JsonObject removeValueForKey(final CharSequence key) {
        JsonObject result = this;

        if (containsKey(key)) {
            final Map<String, JsonField> fieldsCopy = copyFields();
            fieldsCopy.remove(key.toString());
            result = new ImmutableJsonObject(fieldsCopy);
        }

        return result;
    }

    @Override
    public List<JsonKey> getKeys() {
        final List<JsonKey> keys = getKeysAsStream()
                .collect(Collectors.toList());

        return Collections.unmodifiableList(keys);
    }

    @Override
    public Optional<JsonField> getField(final CharSequence index) {
        checkKey(index);

        final Optional<JsonField> result;
        final JsonIndex jsonIndex = JsonFactory.newIndex(index);
        if (jsonIndex.isPointer()) {
            result = getField(jsonIndex.asPointer());
        } else {
            result = getFieldForKey(index);
        }

        return result;
    }

    private Optional<JsonField> getField(final JsonPointer pointer) {
        final Optional<JsonField> result;

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);
        if (1 >= pointer.getLevelCount()) {
            // same as getting a field for a key
            result = getFieldForKey(rootKey);
        } else {
            result = getFieldForKey(rootKey)
                    .map(JsonField::getValue)
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .flatMap(jsonObject -> jsonObject.getField(pointer.nextLevel()));
        }

        return result;
    }

    private Optional<JsonField> getFieldForKey(final CharSequence key) {
        return Optional.ofNullable(fields.get(key.toString()));
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public JsonObject asObject() {
        return this;
    }

    @Override
    protected String createStringRepresentation() {
        final com.eclipsesource.json.JsonObject minJsonObject = new com.eclipsesource.json.JsonObject();
        fields.values().forEach(field -> minJsonObject.add(field.getKeyName(), JsonFactory.convert(field.getValue())));
        return minJsonObject.toString();
    }

    /**
     * {@inheritDoc} Removing JSON fields through the returned iterator has no effect on this JSON object.
     *
     * @return an iterator for the JSON fields of this JSON object.
     */
    @Override
    public Iterator<JsonField> iterator() {
        return fields.values().iterator();
    }

    @Override
    public Stream<JsonField> stream() {
        return fields.values().stream();
    }

    @Override
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    @Override
    public int getSize() {
        return fields.size();
    }

    @SuppressWarnings({"checkstyle:com.puppycrawl.tools.checkstyle.checks.metrics.CyclomaticComplexityCheck",
            "squid:MethodCyclomaticComplexity"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonObject that = (ImmutableJsonObject) o;

        return fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }

    private Stream<JsonKey> getKeysAsStream() {
        return fields.values()
                .stream()
                .map(JsonField::getKey);
    }

}
