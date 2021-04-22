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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
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

        final boolean result;

        final JsonPointer pointer = JsonPointer.of(key);

        if (1 >= pointer.getLevelCount()) {
            result = pointer.getRoot().map(this::containsKey).orElse(false);
        } else {
            result = pointer.getRoot()
                    .flatMap(this::getValueForKey)
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(jsonObject -> jsonObject.contains(pointer.nextLevel()))
                    .orElse(false);
        }

        return result;
    }

    private boolean containsKey(final CharSequence key) {
        return fieldMap.containsKey(key.toString());
    }

    @Override
    public Optional<JsonValue> getValue(final CharSequence key) {
        requireNonNull(key, "The key or pointer of the value to be retrieved must not be null!");
        return getValueForPointer(JsonPointer.of(key));
    }

    private Optional<JsonValue> getValueForPointer(final JsonPointer pointer) {
        final Optional<JsonValue> result;

        final JsonKey rootKey = pointer.getRoot().orElse(ROOT_KEY);
        final int levelCount = pointer.getLevelCount();
        if (0 == levelCount) {
            result = Optional.of(this);
        } else if (1 == levelCount) {
            // same as getting a value for a key
            result = getValueForKey(rootKey);
        } else {
            result = getValueForKey(rootKey)
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .flatMap(jsonObject -> jsonObject.getValue(pointer.nextLevel()));
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

        return getValueForPointer(fieldDefinition.getPointer()).map(fieldDefinition::mapValue);
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
                .filter(this::contains)
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
                final JsonValue filteredChildValue = childValue.isObject()
                        ? filterByTrie(childValue.asObject(), trie.descend(key))
                        : childValue;
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
                    .map(withoutValue -> JsonField.newInstance(rootKey, withoutValue, getDefinitionForKey(rootKey).orElse(null)))
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

        private String jsonObjectStringRepresentation;
        private byte[] cborObjectRepresentation;
        private int hashCode;
        private SoftReference<Map<String, JsonField>> fieldsReference;

        private SoftReferencedFieldMap(final Map<String, JsonField> jsonFieldMap,
                @Nullable final String stringRepresentation, @Nullable final byte[] cborObjectRepresentation) {

            requireNonNull(jsonFieldMap, "The fields of JSON object must not be null!");
            fieldsReference = new SoftReference<>(Collections.unmodifiableMap(new LinkedHashMap<>(jsonFieldMap)));
            jsonObjectStringRepresentation = stringRepresentation;
            this.cborObjectRepresentation = cborObjectRepresentation;
            if (jsonObjectStringRepresentation == null && cborObjectRepresentation == null) {
                if (CBOR_FACTORY.isCborAvailable()) {
                    try {
                        this.cborObjectRepresentation = CBOR_FACTORY.createCborRepresentation(jsonFieldMap,
                                        guessSerializedSize());
                    } catch (final IOException e) {
                        assert false; // this should not happen, so assertions will throw during testing
                        jsonObjectStringRepresentation = createStringRepresentation(jsonFieldMap);
                    }
                } else {
                    jsonObjectStringRepresentation = createStringRepresentation(jsonFieldMap);
                }
            }
            hashCode = 0;
        }

        static SoftReferencedFieldMap empty() {
            return of(Collections.emptyMap(), "{}", new byte[]{(byte) 0xA0});
        }

        static SoftReferencedFieldMap of(final Map<String, JsonField> fieldMap) {
            return new SoftReferencedFieldMap(fieldMap, null, null);
        }

        static SoftReferencedFieldMap of(final Map<String, JsonField> jsonFieldMap,
                @Nullable final String stringRepresentation) {
            return new SoftReferencedFieldMap(jsonFieldMap, stringRepresentation, null);
        }

        static SoftReferencedFieldMap of(final Map<String, JsonField> jsonFieldMap,
                @Nullable final byte[] cborObjectRepresentation) {
            return new SoftReferencedFieldMap(jsonFieldMap, null, cborObjectRepresentation);
        }

        static SoftReferencedFieldMap of(final Map<String, JsonField> jsonFieldMap,
                @Nullable final String stringRepresentation,
                @Nullable final byte[] cborObjectRepresentation) {
            return new SoftReferencedFieldMap(jsonFieldMap, stringRepresentation, cborObjectRepresentation);
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

        private Map<String, JsonField> fields() {
            Map<String, JsonField> result = fieldsReference.get();
            if (null == result) {
                result = recoverFields();
                fieldsReference = new SoftReference<>(result);
            }
            return result;
        }

        private Map<String, JsonField> recoverFields() {
            if (CBOR_FACTORY.isCborAvailable() && cborObjectRepresentation != null) {
                return parseToMap(cborObjectRepresentation);
            }
            if (jsonObjectStringRepresentation != null) {
                return parseToMap(jsonObjectStringRepresentation);
            }
            throw new IllegalStateException("Fatal cache miss on JsonObject");
        }

        private static Map<String, JsonField> parseToMap(final String jsonObjectString) {
            final FieldMapJsonHandler jsonHandler = new FieldMapJsonHandler();
            JsonValueParser.fromString(jsonHandler).accept(jsonObjectString);
            return jsonHandler.getValue();
        }

        private static Map<String, JsonField> parseToMap(final byte[] cborObjectRepresentation) {
            final JsonValue jsonObject = CBOR_FACTORY.readFrom(cborObjectRepresentation);
            final Map<String, JsonField> map = new LinkedHashMap<>();
            for (final JsonField jsonValue : jsonObject.asObject()) {
                map.put(jsonValue.getKey().toString(), jsonValue);
            }
            return map;
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
            if (cborObjectRepresentation != null && that.cborObjectRepresentation != null &&
                    Arrays.equals(cborObjectRepresentation, that.cborObjectRepresentation)) {
                return true;
            }
            return Objects.equals(fields(), that.fields());
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
                jsonObjectStringRepresentation = createStringRepresentation(this.fields());
            }
            return jsonObjectStringRepresentation;
        }

        void writeValue(final SerializationContext serializationContext) throws IOException {
            if (CBOR_FACTORY.isCborAvailable() && cborObjectRepresentation == null) {
                cborObjectRepresentation = CBOR_FACTORY.createCborRepresentation(this.fields(), guessSerializedSize());
            }
            serializationContext.writeCachedElement(cborObjectRepresentation);
        }

        private int guessSerializedSize() {
            // This function currently overestimates for CBOR and underestimates for JSON, but it should be better than a static guess.
            if (jsonObjectStringRepresentation != null) {
                return jsonObjectStringRepresentation.length();
            }
            if (cborObjectRepresentation != null) {
                return cborObjectRepresentation.length;
            }
            return 512;
        }

        public long upperBoundForStringSize() {
            if (jsonObjectStringRepresentation != null) {
                return jsonObjectStringRepresentation.length();
            }
            if (cborObjectRepresentation != null) {
                return cborObjectRepresentation.length * CBOR_MAX_COMPRESSION_RATIO;
            }
            assert false; // this should never happen
            return Long.MAX_VALUE;
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
