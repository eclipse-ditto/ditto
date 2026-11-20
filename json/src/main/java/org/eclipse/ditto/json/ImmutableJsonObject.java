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

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An immutable implementation of a JSON object.
 * Each call to a method which would alter the state of this object returns a new JSON object with the altered state
 * instead while the old JSON object remains unchanged.
 * Care has to be taken to assign the result of an altering method like {@code add} to a variable to have a handle to
 * the new resp. altered JSON object.
 */
@Immutable
final class ImmutableJsonObject extends AbstractJsonValue implements JsonObject {

    private static final JsonKey ROOT_KEY = JsonKey.of("/");

    @Nullable private static ImmutableJsonObject emptyInstance = null;

    private final SoftReferencedFieldMap fieldMap;

    ImmutableJsonObject(final SoftReferencedFieldMap theFieldMap) {
        fieldMap = theFieldMap;
    }

    /**
     * Returns an empty JSON object.
     *
     * @return an empty JSON object.
     */
    public static ImmutableJsonObject empty() {
        ImmutableJsonObject result = emptyInstance;
        if (null == result) {
            result = new ImmutableJsonObject(SoftReferencedFieldMap.empty());
            emptyInstance = result;
        }
        return result;
    }

    /**
     * Returns a new {@code ImmutableJsonObject} instance which contains the given fields.
     *
     * @param fields the fields of the new JSON object.
     * @return a new JSON object containing the {@code fields}.
     * @throws NullPointerException if {@code fields} is {@code null}.
     */
    public static ImmutableJsonObject of(final Map<String, JsonField> fields) {
        return new ImmutableJsonObject(SoftReferencedFieldMap.of(fields));
    }

    /**
     * Like {@link #of(Map, String)}, but takes over the passed map instead of copying it - for parsers which build
     * the map exclusively for the new object. The caller must neither modify the map nor keep a reference to it.
     *
     * @param fields the fields of the JSON object, taken over.
     * @param stringRepresentation the already known string representation of the returned object or {@code null}.
     * @return the JSON object.
     */
    static ImmutableJsonObject ofTakingOwnership(final Map<String, JsonField> fields,
            @Nullable final String stringRepresentation) {
        return new ImmutableJsonObject(SoftReferencedFieldMap.ofTakingOwnership(fields, stringRepresentation));
    }

    /**
     * Creates a JSON object from the fields a JSON string parser collected for it.
     *
     * @param fields the fields in parsing order.
     * @param stringRepresentation the already known string representation of the returned object or {@code null}.
     * @return the JSON object.
     */
    static ImmutableJsonObject ofParsedFields(final List<JsonField> fields,
            @Nullable final String stringRepresentation) {
        return new ImmutableJsonObject(SoftReferencedFieldMap.ofParsedFields(fields, stringRepresentation));
    }

    /**
     * Creates a JSON object parsed from CBOR, taking over the passed fields array and referencing its CBOR
     * representation as slice of {@code cborBytes}, which must never be modified - neither by the caller nor by
     * anyone the array is shared with.
     *
     * @param ownedFields the fields in parsing order, taken over.
     * @param cborBytes the bytes containing the CBOR representation.
     * @param cborOffset the offset of the CBOR representation in {@code cborBytes}.
     * @param cborLength the length of the CBOR representation.
     * @return the JSON object.
     */
    static ImmutableJsonObject ofCborSlice(final JsonField[] ownedFields, final byte[] cborBytes,
            final int cborOffset, final int cborLength) {
        return new ImmutableJsonObject(
                SoftReferencedFieldMap.ofCborSlice(ownedFields, cborBytes, cborOffset, cborLength));
    }

    /**
     * Returns a new {@code ImmutableJsonObject} instance which contains the given fields.
     *
     * @param fields the fields of the new JSON object.
     * @param stringRepresentation the already known string representation of the returned object or {@code null}.
     * @return a new JSON object containing the {@code fields}.
     * @throws NullPointerException if {@code fields} is {@code null}.
     */
    public static ImmutableJsonObject of(final Map<String, JsonField> fields,
            @Nullable final String stringRepresentation) {

        return new ImmutableJsonObject(SoftReferencedFieldMap.of(fields, stringRepresentation));
    }

    /**
     * Returns a new {@code ImmutableJsonObject} instance which contains the given fields.
     *
     * @param fields the fields of the new JSON object.
     * @param cborRepresentation the already known CBOR representation of the returned object or {@code null}.
     * @return a new JSON object containing the {@code fields}.
     * @throws NullPointerException if {@code fields} is {@code null}.
     * @since 1.1.0
     */
    public static ImmutableJsonObject of(final Map<String, JsonField> fields,
            @Nullable final byte[] cborRepresentation) {

        return new ImmutableJsonObject(SoftReferencedFieldMap.of(
                fields,
                cborRepresentation != null ? cborRepresentation.clone() : null
        ));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final int value) {
        return setValue(key, JsonValue.of(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final long value) {
        return setValue(key, JsonValue.of(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final double value) {
        return setValue(key, JsonValue.of(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final boolean value) {
        return setValue(key, JsonValue.of(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final String value) {
        return setValue(key, JsonValue.of(value));
    }

    @Override
    public JsonObject setValue(final CharSequence key, final JsonValue value) {
        final JsonPointer pointer = JsonFactory.getNonEmptyPointer(key);
        final JsonKey leafKey = pointer.getLeaf().orElse(ROOT_KEY);
        return setFieldInHierarchy(this, pointer, JsonField.newInstance(leafKey, value,
                getDefinitionForKey(leafKey).orElse(null)));
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    private Optional<JsonFieldDefinition> getDefinitionForKey(final CharSequence key) {
        return getField(key).flatMap(JsonField::getDefinition);
    }

    @Override
    public <T> JsonObject set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value) {
        requireNonNull(fieldDefinition, "The JSON field definition to set the value for must not be null!");

        final JsonPointer pointer = fieldDefinition.getPointer();

        final JsonKey leafKey = pointer.getLeaf().orElseThrow(() -> {
            final String msgTemplate = "The pointer of the field definition <{0}> must not be empty!";
            return new IllegalArgumentException(MessageFormat.format(msgTemplate, fieldDefinition));
        });
        final JsonField field = JsonField.newInstance(leafKey, JsonValue.of(value), fieldDefinition);
        return setFieldInHierarchy(this, pointer, field);
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

        final JsonField existingField = fieldMap.getOrNull(field.getKeyName());
        if (!field.equals(existingField)) {
            result = new ImmutableJsonObject(fieldMap.put(field.getKeyName(), field));
        }

        return result;
    }

    @Override
    public JsonObject setAll(final Iterable<JsonField> fields) {
        requireNonNull(fields, "The JSON fields to add must not be null!");

        final JsonObject result;

        if (isEmpty(fields)) {
            result = this;
        } else {
            result = new ImmutableJsonObject(fieldMap.putAll(fields));
        }

        return result;
    }

    private static boolean isEmpty(final Iterable<?> iterable) {
        final Iterator<?> iterator = iterable.iterator();
        return !iterator.hasNext();
    }

    @Override
    public boolean contains(final CharSequence key) {
        requireNonNull(key, "The key or pointer to check the existence of a value for must not be null!");
        return internalContains(key, false);
    }

    @Override
    public boolean containsFlatteningArrays(final CharSequence key) {
        requireNonNull(key, "The key or pointer to check the existence of a value for must not be null!");
        return internalContains(key, true);
    }

    boolean internalContains(final CharSequence key, final boolean flatteningArrays) {
        final boolean result;

        final JsonPointer pointer = JsonPointer.of(key);

        if (1 >= pointer.getLevelCount()) {
            result = pointer.getRoot().map(this::containsKey).orElse(false);
        } else {
            result = containsPointer(pointer, flatteningArrays);
        }

        return result;
    }

    private Boolean containsPointer(final JsonPointer pointer, final boolean flatteningArrays) {
        return pointer.getRoot()
                .flatMap(this::getValueForKey)
                .filter(val -> val.isObject() || (flatteningArrays && val.isArray()))
                .map(val -> val.isObject() ? Stream.of(val.asObject()) :
                        val.asArray().stream().filter(JsonValue::isObject).map(JsonValue::asObject)
                )
                .map(stream -> stream.anyMatch(jsonObject -> flatteningArrays ?
                        jsonObject.containsFlatteningArrays(pointer.nextLevel()) :
                        jsonObject.contains(pointer.nextLevel()))
                )
                .orElse(false);
    }

    private boolean containsKey(final CharSequence key) {
        return fieldMap.containsKey(key.toString());
    }

    @Override
    public Optional<JsonValue> getValue(final CharSequence key) {
        requireNonNull(key, "The key or pointer of the value to be retrieved must not be null!");
        return getValueForPointer(JsonPointer.of(key), false);
    }

    @Override
    public Optional<JsonValue> getValueFlatteningArrays(final CharSequence key) {
        requireNonNull(key, "The key or pointer of the value to be retrieved must not be null!");
        return getValueForPointer(JsonPointer.of(key), true);
    }

    private Optional<JsonValue> getValueForPointer(final JsonPointer pointer, final boolean flatteningArrays) {
        final Optional<JsonValue> result;

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);
        final int levelCount = pointer.getLevelCount();
        if (0 == levelCount) {
            result = Optional.of(this);
        } else if (1 == levelCount) {
            // same as getting a value for a key
            result = getValueForKey(rootKey);
        } else {
            final AtomicReference<Boolean> valueIsArray = new AtomicReference<>(false);
            final List<JsonValue> collected = getValueForKey(rootKey).map(Stream::of).orElse(Stream.empty())
                    .filter(val -> val.isObject() || (flatteningArrays && val.isArray()))
                    .flatMap(val -> {
                        if (val.isObject()) {
                            return Stream.of(val.asObject());
                        } else {
                            valueIsArray.set(true);
                            return val.asArray().stream().filter(JsonValue::isObject).map(JsonValue::asObject);
                        }
                    })
                    .flatMap(jsonObject -> flatteningArrays ?
                            jsonObject.getValueFlatteningArrays(pointer.nextLevel())
                                    .map(Stream::of).orElseGet(Stream::empty) :
                            jsonObject.getValue(pointer.nextLevel())
                                    .map(Stream::of).orElseGet(Stream::empty)
                    ).collect(Collectors.toList());

            if (collected.isEmpty()) {
                result = Optional.empty();
            } else if (Boolean.TRUE.equals(valueIsArray.get())) {
                result = Optional.of(collected.stream().collect(JsonCollectors.valuesToArray()));
            } else {
                result = Optional.of(collected.get(0));
            }
        }

        return result;
    }

    private Optional<JsonValue> getValueForKey(final CharSequence key) {
        final JsonField jsonField = fieldMap.getOrNull(key.toString());
        return null != jsonField ? Optional.of(jsonField.getValue()) : Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(final JsonFieldDefinition<T> fieldDefinition) {
        checkFieldDefinition(fieldDefinition);

        return getValueForPointer(fieldDefinition.getPointer(), false).map(fieldDefinition::mapValue);
    }

    private static void checkFieldDefinition(final JsonFieldDefinition<?> fieldDefinition) {
        requireNonNull(fieldDefinition, "The JSON field definition which supplies the pointer must not be null!");
    }

    @Override
    public <T> T getValueOrThrow(final JsonFieldDefinition<T> fieldDefinition) {
        return getValue(fieldDefinition).orElseThrow(() -> new JsonMissingFieldException(fieldDefinition));
    }

    @Override
    public JsonObject get(final JsonPointer pointer) {
        checkPointer(pointer);

        if (pointer.isEmpty()) {
            return this;
        }

        final JsonObject result;

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);
        final Optional<JsonValue> rootKeyValue = getValueForKey(rootKey);
        final Optional<JsonFieldDefinition> rootKeyDefinition = getDefinitionForKey(rootKey);
        if (1 >= pointer.getLevelCount()) {
            result = rootKeyValue.map(
                            jsonValue -> JsonField.newInstance(rootKey, jsonValue, rootKeyDefinition.orElse(null)))
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
                    .map(jsonValue -> JsonField.newInstance(rootKey, jsonValue, rootKeyDefinition.orElse(null)))
                    .map(jsonField -> Collections.singletonMap(jsonField.getKeyName(), jsonField))
                    .map(ImmutableJsonObject::of)
                    .orElseGet(ImmutableJsonObject::empty);
        }

        return result;
    }

    private static void checkPointer(final JsonPointer pointer) {
        requireNonNull(pointer, "The JSON pointer must not be null!");
    }

    @Override
    public JsonObject get(final JsonFieldDefinition<?> fieldDefinition) {
        checkFieldDefinition(fieldDefinition);
        return get(fieldDefinition.getPointer());
    }

    @Override
    public JsonObject get(final JsonFieldSelector fieldSelector) {
        requireNonNull(fieldSelector, "The JSON field selector must not be null!");

        if (isEmpty()) {
            return this;
        }

        final List<JsonPointer> pointersContainedInThis = fieldSelector.getPointers()
                .stream()
                .filter(this::containsFlatteningArrays)
                .collect(Collectors.toList());

        if (pointersContainedInThis.isEmpty()) {
            return empty();
        } else {
            return filterByTrie(this, JsonFieldSelectorTrie.of(pointersContainedInThis));
        }
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    private static JsonObject filterByTrie(final JsonObject self, final JsonFieldSelectorTrie trie) {
        if (trie.isEmpty()) {
            return self;
        }

        final JsonObjectBuilder builder = JsonObject.newBuilder();

        for (final JsonKey key : trie.getKeys()) {
            self.getField(key).ifPresent(child -> {
                final JsonValue childValue = child.getValue();
                final JsonValue filteredChildValue;
                if (childValue.isObject()) {
                    filteredChildValue = filterByTrie(childValue.asObject(), trie.descend(key)); // recurse!
                } else if (childValue.isArray()) {
                    filteredChildValue = childValue.asArray().stream()
                            .map(value -> value.isObject() ? filterByTrie(value.asObject(), trie.descend(key)) // recurse!
                                    : value)
                            .collect(JsonCollectors.valuesToArray());
                } else {
                    filteredChildValue = childValue;
                }
                final Optional<JsonFieldDefinition> childFieldDefinition = child.getDefinition();
                if (childFieldDefinition.isPresent()) {
                    builder.set(childFieldDefinition.get(), filteredChildValue);
                } else {
                    builder.set(key, filteredChildValue);
                }
            });
        }

        return builder.build();
    }

    @Override
    public JsonObject remove(final CharSequence key) {
        requireNonNull(key, "The key or pointer of the field to be removed must not be null!");
        return removeForPointer(JsonPointer.of(key));
    }

    private JsonObject removeForPointer(final JsonPointer pointer) {
        final JsonObject result;

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);
        if (pointer.isEmpty()) {
            result = this;
        } else if (1 == pointer.getLevelCount()) {
            result = removeValueForKey(rootKey);
        } else {
            final JsonPointer nextPointerLevel = pointer.nextLevel();

            final Predicate<JsonObject> containsNextLevelRootKey = jsonObject -> nextPointerLevel.getRoot()
                    .map(jsonObject::contains)
                    .orElse(false);

            result = getValueForKey(rootKey)
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .filter(containsNextLevelRootKey)
                    .map(jsonObject -> jsonObject.remove(nextPointerLevel)) // Recursion
                    .map(withoutValue -> JsonField.newInstance(rootKey, withoutValue,
                            getDefinitionForKey(rootKey).orElse(null)))
                    .map(this::set)
                    .orElse(this);
        }

        return result;
    }

    private JsonObject removeValueForKey(final CharSequence key) {
        JsonObject result = this;

        if (containsKey(key)) {
            result = new ImmutableJsonObject(fieldMap.remove(key.toString()));
        }

        return result;
    }

    @Override
    public List<JsonKey> getKeys() {
        final List<JsonKey> keys = fieldMap.getStream()
                .map(JsonField::getKey)
                .collect(Collectors.toList());

        return Collections.unmodifiableList(keys);
    }

    @Override
    public Optional<JsonField> getField(final CharSequence key) {
        requireNonNull(key, "The key or pointer of the field to be retrieved must not be null!");

        final JsonPointer pointer = JsonPointer.of(key);

        Optional<JsonField> result = pointer.getRoot()
                .map(JsonKey::toString)
                .map(fieldMap::getOrNull);

        if (1 < pointer.getLevelCount()) {
            result = result.map(JsonField::getValue)
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .flatMap(jsonObject -> jsonObject.getField(pointer.nextLevel())); // Recursion
        }

        return result;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public JsonObject asObject() {
        return this;
    }

    /**
     * {@inheritDoc} Removing JSON fields through the returned iterator has no effect on this JSON object.
     *
     * @return an iterator for the JSON fields of this JSON object.
     */
    @Override
    public Iterator<JsonField> iterator() {
        return fieldMap.getIterator();
    }

    @Override
    public Stream<JsonField> stream() {
        return fieldMap.getStream();
    }

    @Override
    public boolean isEmpty() {
        return fieldMap.isEmpty();
    }

    @Override
    public int getSize() {
        return fieldMap.getSize();
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

        return Objects.equals(fieldMap, that.fieldMap);
    }

    @Override
    public int hashCode() {
        return fieldMap.hashCode();
    }

    @Override
    public String toString() {
        return fieldMap.asJsonObjectString();
    }

    @Override
    public void writeValue(final SerializationContext serializationContext) throws IOException {
        fieldMap.writeValue(serializationContext);
    }

    @Override
    public long getUpperBoundForStringSize() {
        return fieldMap.upperBoundForStringSize();
    }

    @Immutable
    static final class SoftReferencedFieldMap {

        private static final long CBOR_MAX_COMPRESSION_RATIO = 5; // "false" compressed to one byte
        private static final CborFactory CBOR_FACTORY;

        static {
            final ServiceLoader<CborFactory> sl = ServiceLoader.load(CborFactory.class);
            CBOR_FACTORY = StreamSupport.stream(sl.spliterator(), false)
                    .findFirst()
                    .orElseGet(NoopCborFactory::new); // when no Service could be found -> CBOR not available
        }


        // Lazy-encoding invariant: whenever fieldsRef holds a SoftReference, at least one representation must be
        // non-null so recoverFields() can rebuild the map after a soft-clear. The CBOR representation is final (set
        // only on construction); the string representation is volatile as it is created lazily, so a thread which
        // observes the SoftReference also observes the representation written before it.
        private volatile String jsonObjectStringRepresentation;
        // The CBOR representation is a slice of a byte array which may be shared with the enclosing and nested
        // values parsed from the same CBOR message: each message is copied once instead of once per nesting level.
        @Nullable private final byte[] cborBytes;
        private final int cborOffset;
        private final int cborLength;
        private int hashCode;
        // Either a CompactFieldMap held strongly (when no serialised representation exists to recover it from) or a
        // SoftReference<CompactFieldMap> once a representation is available. {@link #fields()} unwraps both cases.
        private volatile Object fieldsRef;

        private SoftReferencedFieldMap(final CompactFieldMap fields, @Nullable final String stringRepresentation,
                @Nullable final byte[] cborBytes, final int cborOffset, final int cborLength) {

            this.jsonObjectStringRepresentation = stringRepresentation;
            this.cborBytes = cborBytes;
            this.cborOffset = cborOffset;
            this.cborLength = cborLength;
            // Soft-reference the field map only when a serialised representation already
            // exists; the representation is what {@link #recoverFields()} parses back when
            // the soft reference is cleared under GC pressure. Without one, we must hold
            // the map strongly to avoid data loss.
            if (stringRepresentation != null || cborBytes != null) {
                this.fieldsRef = new SoftReference<>(fields);
            } else {
                this.fieldsRef = fields;
            }
            hashCode = 0;
        }

        private static CompactFieldMap compact(final Map<String, JsonField> jsonFieldMap) {
            requireNonNull(jsonFieldMap, "The fields of JSON object must not be null!");
            return CompactFieldMap.of(jsonFieldMap);
        }

        static SoftReferencedFieldMap empty() {
            return new SoftReferencedFieldMap(CompactFieldMap.EMPTY, "{}", new byte[]{(byte) 0xA0}, 0, 1);
        }

        static SoftReferencedFieldMap of(final Map<String, JsonField> fieldMap) {
            return new SoftReferencedFieldMap(compact(fieldMap), null, null, 0, 0);
        }

        static SoftReferencedFieldMap ofTakingOwnership(final Map<String, JsonField> fieldMap,
                @Nullable final String stringRepresentation) {
            return new SoftReferencedFieldMap(compact(fieldMap), stringRepresentation, null, 0, 0);
        }

        static SoftReferencedFieldMap ofParsedFields(final List<JsonField> fields,
                @Nullable final String stringRepresentation) {
            return new SoftReferencedFieldMap(CompactFieldMap.of(fields), stringRepresentation, null, 0, 0);
        }

        static SoftReferencedFieldMap ofCborSlice(final JsonField[] ownedFields, final byte[] cborBytes,
                final int cborOffset, final int cborLength) {
            return new SoftReferencedFieldMap(CompactFieldMap.ofOwnedArray(ownedFields), null, cborBytes, cborOffset,
                    cborLength);
        }

        static SoftReferencedFieldMap of(final Map<String, JsonField> jsonFieldMap,
                @Nullable final String stringRepresentation) {
            return new SoftReferencedFieldMap(compact(jsonFieldMap), stringRepresentation, null, 0, 0);
        }

        static SoftReferencedFieldMap of(final Map<String, JsonField> jsonFieldMap,
                @Nullable final byte[] cborObjectRepresentation) {
            return of(jsonFieldMap, null, cborObjectRepresentation);
        }

        static SoftReferencedFieldMap of(final Map<String, JsonField> jsonFieldMap,
                @Nullable final String stringRepresentation,
                @Nullable final byte[] cborObjectRepresentation) {
            return new SoftReferencedFieldMap(compact(jsonFieldMap), stringRepresentation, cborObjectRepresentation,
                    0, null != cborObjectRepresentation ? cborObjectRepresentation.length : 0);
        }

        private String createStringRepresentation(final Map<String, JsonField> jsonFieldMap) {
            final StringBuilder stringBuilder = new StringBuilder(guessSerializedSize());
            stringBuilder.append('{');
            String delimiter = "";
            for (final JsonField jsonField : jsonFieldMap.values()) {
                stringBuilder.append(delimiter);
                stringBuilder.append(jsonField);
                delimiter = ",";
            }
            stringBuilder.append('}');

            return stringBuilder.toString();
        }

        int getSize() {
            return fields().size();
        }

        boolean isEmpty() {
            return fields().isEmpty();
        }

        boolean containsKey(final String key) {
            return fields().containsKey(key);
        }

        @Nullable
        JsonField getOrNull(final String key) {
            return fields().get(key);
        }

        SoftReferencedFieldMap put(final String key, final JsonField value) {
            final Map<String, JsonField> fieldsCopy = copyFields();
            fieldsCopy.put(key, value);
            return of(fieldsCopy);
        }

        private Map<String, JsonField> copyFields() {
            return new LinkedHashMap<>(fields());
        }

        SoftReferencedFieldMap putAll(final Iterable<JsonField> jsonFields) {
            final Map<String, JsonField> fieldsCopy = copyFields();
            jsonFields.forEach(jsonField -> fieldsCopy.put(jsonField.getKeyName(), jsonField));
            return of(fieldsCopy);
        }

        SoftReferencedFieldMap remove(final String key) {
            final Map<String, JsonField> fieldsCopy = copyFields();
            fieldsCopy.remove(key);
            return of(fieldsCopy);
        }

        Stream<JsonField> getStream() {
            return fields().values().stream();
        }

        Iterator<JsonField> getIterator() {
            return fields().values().iterator();
        }

        private CompactFieldMap fields() {
            final Object ref = fieldsRef;
            if (ref instanceof SoftReference) {
                @SuppressWarnings("unchecked")
                final SoftReference<CompactFieldMap> softRef = (SoftReference<CompactFieldMap>) ref;
                CompactFieldMap result = softRef.get();
                if (null == result) {
                    result = recoverFields();
                    fieldsRef = new SoftReference<>(result);
                }
                return result;
            }
            return (CompactFieldMap) ref;
        }

        private void softenFieldsRef(final CompactFieldMap fields) {
            if (!(fieldsRef instanceof SoftReference)) {
                fieldsRef = new SoftReference<>(fields);
            }
        }

        private CompactFieldMap recoverFields() {
            final byte[] bytes = cborBytes;
            if (CBOR_FACTORY.isCborAvailable() && bytes != null) {
                // the slice is immutable and owned by this object: parse it in place, the nested values slice it
                final JsonValue jsonObject = CBOR_FACTORY.readFromOwnedBytes(bytes, cborOffset, cborLength);
                return ((ImmutableJsonObject) jsonObject).fieldMap.fields();
            } else if (jsonObjectStringRepresentation != null) {
                return CompactFieldMap.of(parseToMap(jsonObjectStringRepresentation));
            } else {
                throw new IllegalStateException("Fatal cache miss on JsonObject");
            }
        }

        private static Map<String, JsonField> parseToMap(final String jsonObjectString) {
            final FieldMapJsonHandler jsonHandler = new FieldMapJsonHandler();
            JsonValueParser.fromString(jsonHandler).accept(jsonObjectString);
            return jsonHandler.getValue();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SoftReferencedFieldMap that = (SoftReferencedFieldMap) o;

            if (jsonObjectStringRepresentation != null && that.jsonObjectStringRepresentation != null) {
                if (jsonObjectStringRepresentation.equals(that.jsonObjectStringRepresentation)) {
                    return true;
                } else if (jsonObjectStringRepresentation.length() == that.jsonObjectStringRepresentation.length()) {
                    return Objects.equals(fields(), that.fields());
                }
                return false;
            }
            if (cborBytes != null && that.cborBytes != null &&
                    CompactFieldMap.rangeEquals(cborBytes, cborOffset, cborLength,
                            that.cborBytes, that.cborOffset, that.cborLength)) {
                return true;
            }
            // Compared structurally, regardless of field order, and without rendering both objects to JSON strings:
            // values of different classes which render the same JSON are equal, e.g. NullAttributes and
            // JsonValue.nullLiteral() or FeatureProperties and a JsonObject with the same fields.
            return fieldsEqual(fields(), that.fields());
        }

        private static boolean fieldsEqual(final CompactFieldMap fields, final CompactFieldMap otherFields) {
            if (fields.size() != otherFields.size()) {
                return false;
            }
            for (final JsonField field : fields.fieldList()) {
                final JsonField otherField = otherFields.get(field.getKeyName());
                if (null == otherField || !jsonValuesEqual(field.getValue(), otherField.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean jsonValuesEqual(final JsonValue value, final JsonValue otherValue) {
            if (value == otherValue) {
                return true;
            }
            if (value.isNull() || otherValue.isNull()) {
                return value.isNull() && otherValue.isNull();
            }
            if (value.isObject()) {
                if (!otherValue.isObject()) {
                    return false;
                }
                if (value instanceof ImmutableJsonObject && otherValue instanceof ImmutableJsonObject) {
                    return value.equals(otherValue);
                }
                return objectsEqual(value.asObject(), otherValue.asObject());
            }
            if (value.isArray()) {
                return otherValue.isArray() && arraysEqual(value.asArray(), otherValue.asArray());
            }
            return value.equals(otherValue);
        }

        // for JsonObjects of other classes, e.g. wrappers of a JsonObject
        private static boolean objectsEqual(final JsonObject jsonObject, final JsonObject otherJsonObject) {
            if (jsonObject.getSize() != otherJsonObject.getSize()) {
                return false;
            }
            final Map<String, JsonValue> otherValues = new HashMap<>();
            for (final JsonField otherField : otherJsonObject) {
                otherValues.put(otherField.getKeyName(), otherField.getValue());
            }
            for (final JsonField field : jsonObject) {
                final JsonValue otherValue = otherValues.get(field.getKeyName());
                if (null == otherValue || !jsonValuesEqual(field.getValue(), otherValue)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean arraysEqual(final JsonArray jsonArray, final JsonArray otherJsonArray) {
            if (jsonArray.getSize() != otherJsonArray.getSize()) {
                return false;
            }
            final Iterator<JsonValue> otherValues = otherJsonArray.iterator();
            for (final JsonValue value : jsonArray) {
                if (!otherValues.hasNext() || !jsonValuesEqual(value, otherValues.next())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = hashCode;
            if (0 == result) {
                result = fields().hashCode();
                hashCode = result;
            }
            return result;
        }

        String asJsonObjectString() {
            if (jsonObjectStringRepresentation == null) {
                final CompactFieldMap currentFields = fields();
                jsonObjectStringRepresentation = createStringRepresentation(currentFields);
                softenFieldsRef(currentFields);
            }
            return jsonObjectStringRepresentation;
        }

        void writeValue(final SerializationContext serializationContext) throws IOException {
            final byte[] bytes = cborBytes;
            if (null != bytes) {
                serializationContext.writeCachedElement(bytes, cborOffset, cborLength);
            } else {
                // Write the fields as part of the context's output instead of creating (and keeping) a CBOR
                // representation of this object first: done on every nesting level, that encoded and copied each
                // byte once per level and kept a copy of it in every nested object.
                CBOR_FACTORY.writeObject(fields(), serializationContext);
            }
        }

        private int guessSerializedSize() {
            // This function currently overestimates for CBOR and underestimates for JSON, but it should be better than a static guess.
            if (jsonObjectStringRepresentation != null) {
                return jsonObjectStringRepresentation.length();
            }
            if (cborBytes != null) {
                return cborLength;
            }
            return 512;
        }

        public long upperBoundForStringSize() {
            // Callers (size-validation code paths in commands and CBOR sizing) need a real
            // bound. Materialise the string representation lazily here when neither form
            // exists; subsequent calls and the eventual serialization will reuse the cache.
            if (jsonObjectStringRepresentation == null && cborBytes == null) {
                asJsonObjectString();
            }
            long max = 0L;
            if (jsonObjectStringRepresentation != null) {
                max = jsonObjectStringRepresentation.length();
            }
            if (cborBytes != null) {
                final long cborSize = cborLength * CBOR_MAX_COMPRESSION_RATIO;
                if (cborSize > max) {
                    max = cborSize;
                }
            }
            return max;
        }
    }

    /**
     * This JsonHandler creates a Map instead of a JsonObject as Map is the internal structure of ImmutableJsonObject.
     * All method calls which do not affect JSON object creation are delegated to {@link DefaultDittoJsonHandler}.
     * JSON object creation has to be split because only the base level should be represented as a Map, all nested
     * JSON objects should be of type {@link JsonObject}.
     * <p>
     * <em>This handler is only usable for parsing JSON object strings.</em>
     * </p>
     */
    @NotThreadSafe
    static final class FieldMapJsonHandler
            extends DittoJsonHandler<List<JsonValue>, List<JsonField>, Map<String, JsonField>> {

        private final DefaultDittoJsonHandler defaultHandler;
        private Map<String, JsonField> value;
        private final Deque<List<JsonField>> jsonObjectBuilders; // for nested JsonObjects
        private int level;

        /**
         * Constructs a new {@code FieldMapJsonHandler} object.
         */
        FieldMapJsonHandler() {
            defaultHandler = DefaultDittoJsonHandler.newInstance();
            value = null;
            jsonObjectBuilders = new ArrayDeque<>();
            level = 0;
        }

        @Override
        public List<JsonValue> startArray() {
            return defaultHandler.startArray();
        }

        @Override
        public List<JsonField> startObject() {
            if (0 < level) {
                jsonObjectBuilders.push(defaultHandler.startObject());
            }
            final List<JsonField> result = new ArrayList<>();
            level++;
            return result;
        }

        @Override
        public void endNull() {
            defaultHandler.endNull();
        }

        @Override
        public void endBoolean(final boolean value) {
            defaultHandler.endBoolean(value);
        }

        @Override
        public void endString(final String string) {
            defaultHandler.endString(string);
        }

        @Override
        public void endNumber(final String string) {
            defaultHandler.endNumber(string);
        }

        @Override
        public void endArray(final List<JsonValue> jsonValues) {
            defaultHandler.endArray(jsonValues);
        }

        @Override
        public void endArrayValue(final List<JsonValue> jsonValues) {
            defaultHandler.endArrayValue(jsonValues);
        }

        @Override
        public void endObjectValue(final List<JsonField> jsonFields, final String name) {
            final List<JsonField> jsonObjectBuilder = jsonObjectBuilders.peek();
            if (null != jsonObjectBuilder) {
                defaultHandler.endObjectValue(jsonObjectBuilder, name);
            } else {
                jsonFields.add(JsonField.newInstance(name, defaultHandler.getValue()));
            }
        }

        @Override
        public void endObject(final List<JsonField> jsonFields) {
            final List<JsonField> jsonObjectBuilder = jsonObjectBuilders.poll();
            if (null != jsonObjectBuilder) {
                defaultHandler.endObject(jsonObjectBuilder);
            } else {
                final Map<String, JsonField> linkedHashMap = new LinkedHashMap<>(jsonFields.size());
                for (final JsonField jsonField : jsonFields) {
                    linkedHashMap.put(jsonField.getKeyName(), jsonField);
                }
                value = linkedHashMap;
            }
            level--;
        }

        @Override
        protected Map<String, JsonField> getValue() {
            return value;
        }

    }

}
