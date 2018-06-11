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

import java.io.IOException;
import java.io.Reader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.ParseException;

/**
 * The main entry point for the Eclipse Ditto JSON API. It provides a lot of convenience methods. Apart from
 * {@link #newArrayBuilder()} and {@link #newObjectBuilder()} all methods of this class return
 * <em>immutable objects.</em>
 */
@Immutable
public final class JsonFactory {

    private static final JsonObject EMPTY_JSON_OBJECT = ImmutableJsonObject.empty();
    private static final JsonArray EMPTY_JSON_ARRAY = ImmutableJsonArray.empty();
    private static final JsonObject NULL_JSON_OBJECT = ImmutableJsonObjectNull.newInstance();
    private static final JsonArray NULL_JSON_ARRAY = ImmutableJsonArrayNull.newInstance();
    private static final JsonValue NULL_LITERAL = ImmutableJsonNull.newInstance();

    /*
     * This utility class is not meant to be instantiated.
     */
    private JsonFactory() {
        throw new AssertionError();
    }

    /**
     * Returns JSON key for the given character sequence. If the given key value is already a JSON key, this is
     * immediately properly cast and returned.
     *
     * @param keyValue the character sequence value of the JSON key to be created.
     * @return a new JSON key with {@code keyValue} as its value.
     * @throws NullPointerException if {@code keyValue} is {@code null}.
     * @throws IllegalArgumentException if {@code keyValue} is empty.
     */
    public static JsonKey newKey(final CharSequence keyValue) {
        return ImmutableJsonKey.of(keyValue);
    }

    /**
     * Returns a JSON literal which represents {@code null}.
     *
     * @return the {@code null} JSON literal.
     */
    public static JsonValue nullLiteral() {
        return NULL_LITERAL;
    }

    /**
     * Tries to guess the associated JsonValue for the specified object.
     *
     * @param value the value to be converted.
     * @param <T> the type of {@code value}.
     * @return a JsonValue representation of {@code value}.
     * @throws JsonParseException if {@code value} is not defined for JSON.
     */
    static <T> JsonValue getAppropriateValue(@Nullable final T value) {
        final JsonValue result;

        if (null == value) {
            result = nullLiteral();
        } else if (value instanceof JsonValue) {
            result = ((JsonValue) value);
        } else if (value instanceof String || value instanceof CharSequence) {
            result = newValue(String.valueOf(value));
        } else {
            result = convert(tryToRead(String.valueOf(value)));
        }

        return result;
    }

    /**
     * Returns a JSON literal that represents the given {@code boolean} value.
     *
     * @param value the value to get a JSON literal for.
     * @return a JSON literal that represents the given boolean value.
     */
    public static JsonValue newValue(final boolean value) {
        return value ? ImmutableJsonLiteral.TRUE : ImmutableJsonLiteral.FALSE;
    }

    /**
     * Returns a JSON number that represents the given {@code int} value.
     *
     * @param value the value to get a JSON number for.
     * @return a JSON number that represents the given value.
     */
    public static JsonValue newValue(final int value) {
        return ImmutableJsonNumber.of(Json.value(value));
    }

    /**
     * Returns a JSON number that represents the given {@code long} value.
     *
     * @param value the value to get a JSON number for.
     * @return a JSON number that represents the given value.
     */
    public static JsonValue newValue(final long value) {
        return ImmutableJsonNumber.of(Json.value(value));
    }

    /**
     * Returns a JSON number that represents the given {@code double} value.
     *
     * @param value the value to get a JSON number for.
     * @return a JSON number that represents the given value.
     */
    public static JsonValue newValue(final double value) {
        return ImmutableJsonNumber.of(Json.value(value));
    }

    /**
     * Returns a JsonValue that represents the given Java string as JSON string. For example the Java string
     * {@code "foo"} would be {@code "\"foo\""} as JSON string.
     *
     * @param jsonString the string to get a JSON representation for.
     * @return a JSON value that represents the given string. If {@code jsonString} is {@code null}, a "null" object is
     * returned.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @see #nullLiteral()
     */
    public static JsonValue newValue(@Nullable final String jsonString) {
        final JsonValue result;

        if (null != jsonString) {
            result = ImmutableJsonString.of(Json.value(jsonString));
        } else {
            result = NULL_LITERAL;
        }

        return result;
    }

    /**
     * Reads the given string and creates a JSON value based on the read data. The actual type of this JSON value is
     * unknown but can be obtained by invoking the {@code is...} methods.
     *
     * @param json the JSON document to read.
     * @return a JSON value representing the read document. This value can be a JSON literal, a JSON object and so on.
     * @throws NullPointerException if {@code json} is empty.
     * @throws JsonParseException if {@code json} is empty or if it is no valid JSON.
     */
    public static JsonValue readFrom(final String json) {
        requireNonNull(json, "The JSON to read from must not be null!");
        if (json.isEmpty()) {
            throw new JsonParseException("The JSON to read from must not be empty!");
        }

        final com.eclipsesource.json.JsonValue minimalJsonValue = tryToRead(json);
        return convert(minimalJsonValue);
    }

    private static com.eclipsesource.json.JsonValue tryToRead(final String json) {
        try {
            return Json.parse(json);
        }
        catch (final ParseException | StackOverflowError e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to parse ''{0}''!", json))
                    .cause(e)
                    .build();
        }
    }

    /**
     * Reads the entire input stream from the specified reader and parses it as JSON value. The input stream is expected
     * to contain a valid JSON value with optional whitespace padding.
     * <p>
     * As characters are read in chunks and buffered internally it does <em>not</em> improve reading performance to wrap
     * an existing reader in a {@code BufferedReader}.
     * </p>
     *
     * @param reader the reader to read the JSON value from.
     * @return a JSON value which represents the read stream.
     * @throws NullPointerException if {@code reader} is {@code null}.
     * @throws JsonParseException if an I/O error occurred or if the input is no valid JSON.
     */
    public static JsonValue readFrom(final Reader reader) {
        requireNonNull(reader, "The reader must not be null!");
        final com.eclipsesource.json.JsonValue minimalJsonValue = tryToRead(reader);
        return convert(minimalJsonValue);
    }

    private static com.eclipsesource.json.JsonValue tryToRead(final Reader reader) {
        try {
            return Json.parse(reader);
        }
        catch (final ParseException | IOException | StackOverflowError e) {
            throw JsonParseException.newBuilder()
                    .message("Failed to parse JSON from reader!")
                    .cause(e)
                    .build();
        }
    }

    /**
     * Returns a new mutable builder for a {@code JsonObject}.
     *
     * @return a new JSON object builder.
     */
    public static JsonObjectBuilder newObjectBuilder() {
        return ImmutableJsonObjectBuilder.newInstance();
    }

    /**
     * Returns a new mutable builder for the specified {@code JsonField}s. The returned builder is already initialised
     * with the data of the provided JSON object. This method is useful if an existing JSON object should be strongly
     * modified but the amount of creating objects should be kept low at the same time.
     *
     * @param jsonFields are the initial data of the returned builder.
     * @return a new JSON object builder with pre-filled data of {@code jsonFields}.
     * @throws NullPointerException if {@code jsonFields} is {@code null}.
     */
    public static JsonObjectBuilder newObjectBuilder(final Iterable<JsonField> jsonFields) {
        requireNonNull(jsonFields, "The initial JSON fields must not be null!");

        final JsonObjectBuilder result = ImmutableJsonObjectBuilder.newInstance();
        result.setAll(jsonFields);
        return result;
    }

    /**
     * Returns an empty JSON object.
     *
     * @return an empty JSON object.
     */
    public static JsonObject newObject() {
        return EMPTY_JSON_OBJECT;
    }

    /**
     * Creates a JSON object from the given string.
     *
     * @param jsonString the string that represents the JSON object.
     * @return the JSON object that has been created from the string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws JsonParseException if {@code jsonString} does not contain a valid JSON object.
     */
    public static JsonObject newObject(final String jsonString) {
        requireNonNull(jsonString, "The JSON string to create a JSON object from must not be null!");
        if (jsonString.isEmpty()) {
            throw new IllegalArgumentException("The JSON string to create a JSON object from must not be empty!");
        }

        if (Objects.equals(Json.NULL.toString(), jsonString)) {
            return NULL_JSON_OBJECT;
        } else {
            final com.eclipsesource.json.JsonObject jsonObject = tryToReadJsonObjectFrom(jsonString);
            return ImmutableJsonObject.of(toMap(jsonObject));
        }
    }

    /**
     * Creates a JSON object from the given key-value pairs aka fields.
     *
     * @param fields the fields of the JSON object to be created.
     * @return a new JSON object based on the provided fields.
     * @throws NullPointerException if {@code fields} is {@code null}.
     */
    public static JsonObject newObject(final Map<JsonKey, JsonValue> fields) {
        final Map<String, JsonField> jsonFields = new LinkedHashMap<>(fields.size());
        fields.forEach(((jsonKey, jsonValue) -> jsonFields.put(jsonKey.toString(), newField(jsonKey, jsonValue))));
        return ImmutableJsonObject.of(jsonFields);
    }

    /**
     * Returns a JSON NULL literal which is typed as JSON object.
     *
     * @return an object typed JSON NULL literal.
     */
    public static JsonObject nullObject() {
        return NULL_JSON_OBJECT;
    }

    private static com.eclipsesource.json.JsonObject tryToReadJsonObjectFrom(final String jsonString) {
        try {
            final com.eclipsesource.json.JsonValue parsedJsonString = Json.parse(jsonString);
            return parsedJsonString.asObject();
        }
        catch (final ParseException | UnsupportedOperationException | StackOverflowError e) {
            throw JsonParseException.newBuilder()
                    .message("Failed to create JSON object from string!")
                    .cause(e)
                    .build();
        }
    }

    private static Map<String, JsonField> toMap(final com.eclipsesource.json.JsonObject minimalJsonObject) {
        final Map<String, JsonField> result = new LinkedHashMap<>(minimalJsonObject.size());

        for (final com.eclipsesource.json.JsonObject.Member member : minimalJsonObject) {
            final JsonKey key = newKey(member.getName());
            final JsonValue value = convert(member.getValue());
            result.put(key.toString(), newField(key, value));
        }

        return result;
    }

    /**
     * Returns a new mutable builder for a {@code JsonArray}.
     *
     * @return a new JSON array builder.
     */
    public static JsonArrayBuilder newArrayBuilder() {
        return ImmutableJsonArrayBuilder.newInstance();
    }

    /**
     * Returns a new mutable builder for a {@code JsonArray} which is already initialised with the given values.
     *
     * @param values the values of the JSON array to be created. This might be an existing JSON array as well.
     * @return a new JSON array builder.
     * @throws NullPointerException if {@code values} is {@code null}.
     */
    public static JsonArrayBuilder newArrayBuilder(final Iterable<? extends JsonValue> values) {
        final JsonArrayBuilder result = ImmutableJsonArrayBuilder.newInstance();
        result.addAll(values);
        return result;
    }

    /**
     * Returns a new empty JSON array.
     *
     * @return a new empty JSON array.
     */
    public static JsonArray newArray() {
        return EMPTY_JSON_ARRAY;
    }

    /**
     * Creates a new JSON array from the given string.
     *
     * @param jsonString the string that represents the JSON array.
     * @return the JSON array that has been created from the string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws JsonParseException if {@code jsonString} does not contain a valid JSON array.
     */
    public static JsonArray newArray(final String jsonString) {
        requireNonNull(jsonString, "The JSON string to create JSON array from must not be null!");
        if (jsonString.isEmpty()) {
            throw new IllegalArgumentException("The JSON string to create a JSON array from must not be empty!");
        }
        if (jsonString.equals(Json.NULL.toString())) {
            return NULL_JSON_ARRAY;
        } else {
            final com.eclipsesource.json.JsonArray jsonArray = tryToReadMinimalJsonArrayFrom(jsonString);
            return ImmutableJsonArray.of(toList(jsonArray));
        }
    }

    private static com.eclipsesource.json.JsonArray tryToReadMinimalJsonArrayFrom(final String jsonString) {
        try {
            final com.eclipsesource.json.JsonValue parsedJsonString = Json.parse(jsonString);
            return parsedJsonString.asArray();
        }
        catch (final ParseException | UnsupportedOperationException | StackOverflowError e) {
            throw JsonParseException.newBuilder()
                    .message("Failed to create JSON array from string!")
                    .cause(e)
                    .build();
        }
    }

    private static List<JsonValue> toList(final com.eclipsesource.json.JsonArray minimalJsonArray) {
        final List<JsonValue> result = new ArrayList<>(minimalJsonArray.size());

        for (final com.eclipsesource.json.JsonValue minimalJsonValue : minimalJsonArray) {
            result.add(convert(minimalJsonValue));
        }

        return result;
    }

    /**
     * Returns a JSON NULL literal which is typed as JSON array.
     *
     * @return an array typed JSON NULL literal.
     */
    public static JsonArray nullArray() {
        return NULL_JSON_ARRAY;
    }

    /**
     * Returns a new JSON field based on the specified key value pair.
     *
     * @param key the key of the field to be created.
     * @param value the value of the field to be created. {@code null} will be converted to the JSON NULL Literal.
     * @return a new JSON field containing the specified key value pair.
     * @throws NullPointerException if {@code key} is null;
     */
    public static JsonField newField(final JsonKey key, @Nullable final JsonValue value) {
        return ImmutableJsonField.newInstance(key, (null != value) ? value : NULL_LITERAL);
    }

    /**
     * Returns a new JSON field based on the specified key value pair and definition.
     *
     * @param key the key of the field to be created.
     * @param value the value of the field to be created. {@code null} will be converted to the JSON NULL Literal.
     * @param definition the definition of the field to be created.
     * @return a new JSON field containing the specified key value pair and definition.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    public static JsonField newField(final JsonKey key, @Nullable final JsonValue value,
            @Nullable final JsonFieldDefinition definition) {

        return ImmutableJsonField.newInstance(key, (null != value) ? value : NULL_LITERAL, definition);
    }

    /**
     * Returns a new JSON Patch which can be used to specify modifications on JSON Objects.
     *
     * @param operation the patch operation type
     * @param path a JSON Pointer specifying the path within the JSON Object on which the operation is defined
     * @param value the value to be used for the specified operation on the given path
     * @return the new JSON Patch.
     * @throws NullPointerException if {@code operation} or {@code path} is {@code null}.
     */
    public static JsonPatch newPatch(final JsonPatch.Operation operation, final JsonPointer path,
            @Nullable final JsonValue value) {

        return ImmutableJsonPatch.newInstance(operation, path, value);
    }

    /**
     * Returns a new JSON Patch created from the given string.
     *
     * @param jsonString the string representation of the JSON Patch object to be created.
     * @return the new JSON Patch.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws JsonParseException if {@code jsonString} does not contain a valid JSON Patch JSON object.
     * @throws JsonMissingFieldException if {@code jsonString} did not contain {@link JsonPatch.JsonFields#OPERATION} or
     * {@link JsonPatch.JsonFields#PATH}.
     */
    public static JsonPatch newPatch(final String jsonString) {
        return ImmutableJsonPatch.fromJson(jsonString);
    }

    /**
     * Returns an empty JSON pointer.
     *
     * @return JSON pointer containing no JSON keys.
     */
    public static JsonPointer emptyPointer() {
        return ImmutableJsonPointer.empty();
    }

    /**
     * Returns a new JSON pointer which consist of the specified hierarchical keys..
     *
     * @param rootLevel the JSON key which is the root level of the JSON pointer to create.
     * @param subLevels the JSON keys which form sub levels to the root level as well as to each other.
     * @return a new JSON pointer consisting of hierarchical JSON keys.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static JsonPointer newPointer(final JsonKey rootLevel, final JsonKey... subLevels) {
        return ImmutableJsonPointer.of(rootLevel, subLevels);
    }

    /**
     * Parses the given string to obtain a new JSON pointer. This method is the inverse of
     * {@link JsonPointer#toString()} with one exception: both strings {@code "/"} and {@code ""} lead to an empty
     * pointer while the string representation of an empty string is always {@code "/"}.
     * <p>
     * As a JsonPointer is a hierarchy of JsonKeys it has to support JsonKeys containing slashes. Because of this, a
     * JsonPointer string has to escape each slash of a JsonKey with {@code "~1"}. To support, tildes in JsonKeys,
     * too, they have to be escaped with {@code "~0"}. For example, parsing the string
     * {@code "/foo/~0dum~1~0die~1~0dum/baz"} would result in a JsonPointer consisting of the JsonKeys
     * <ol>
     *     <li>{@code "foo"},</li>
     *     <li>{@code "~dum/~die/~dum"} and</li>
     *     <li>{@code "baz"}.</li>
     * </ol>
     *
     * @param slashDelimitedCharSequence a string representing a JSON pointer.
     * @return a new JSON pointer consisting of the JSON keys which were extracted from {@code
     * slashDelimitedCharSequence}.
     * @throws NullPointerException if {@code slashDelimitedCharSequence} is {@code null}.
     */
    public static JsonPointer newPointer(final CharSequence slashDelimitedCharSequence) {
        return ImmutableJsonPointer.ofParsed(slashDelimitedCharSequence);
    }

    /**
     * Returns a new mutable builder for {@code JsonParseOptions}.
     *
     * @return the new JsonParseOptionsBuilder.
     */
    public static JsonParseOptionsBuilder newParseOptionsBuilder() {
        return ImmutableJsonParseOptionsBuilder.newInstance();
    }

    /**
     * Returns a new empty JSON field selector.
     *
     * @return a new empty JSON field selector.
     */
    public static JsonFieldSelector emptyFieldSelector() {
        return ImmutableJsonFieldSelector.empty();
    }

    /**
     * Returns a new JSON field selector by parsing the given string. If the JSON field selector string is {@code null}
     * or empty this means that no fields were selected thus this method returns an empty JSON field selector.
     * <p>
     * For example, the field selector string
     * </p>
     * <pre>
     * "thingId,attributes(acceleration,someData(foo,bar/baz)),acl,features/key"
     * </pre>
     * would lead to a JSON field selector which consists of the following JSON pointers:
     * <ul>
     * <li>{@code "thingId"},</li>
     * <li>{@code "attributes/acceleration"},</li>
     * <li>{@code "attributes/someData/foo"},</li>
     * <li>{@code "attributes/someData/bar/baz"},</li>
     * <li>{@code "acl"} and</li>
     * <li>{@code "features/key"}.</li>
     * </ul>
     *
     * @param fieldSelectorString string to be transformed into a JSON field selector object.
     * @param options the JsonParseOptions to apply when parsing the {@code fieldSelectorString}.
     * @return a new JSON field selector.
     * @throws JsonFieldSelectorInvalidException if {@code fieldSelectorString} is empty or if {@code
     * fieldSelectorString} does not contain a closing parenthesis ({@code )}) for each opening parenthesis ({@code
     * (}).
     * @throws IllegalStateException if {@code fieldSelectorString} cannot be decoded as UTF-8.
     */
    public static JsonFieldSelector newFieldSelector(@Nullable final String fieldSelectorString,
            final JsonParseOptions options) {

        final JsonFieldSelector result;

        if (null == fieldSelectorString || fieldSelectorString.isEmpty()) {
            result = ImmutableJsonFieldSelector.empty();
        } else {
            final ImmutableJsonFieldSelectorFactory jsonFieldSelectorFactory =
                    ImmutableJsonFieldSelectorFactory.newInstance(fieldSelectorString, options);
            result = jsonFieldSelectorFactory.newJsonFieldSelector();
        }

        return result;
    }

    /**
     * Returns a new JSON field selector which is based on the given set of {@link JsonPointer}s. If the set of JSON
     * pointers string is empty this means that no fields were selected thus this method returns an empty JSON field
     * selector.
     *
     * @param pointers the JSON pointers of the field selector to be created.
     * @return a new JSON field selector.
     * @throws NullPointerException if {@code pointers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointers} is empty.
     */
    public static JsonFieldSelector newFieldSelector(final Iterable<JsonPointer> pointers) {
        requireNonNull(pointers, "In order to create a JSON field selector the JSON pointers must not be null!");

        return ImmutableJsonFieldSelector.of(pointers);
    }

    /**
     * Returns a new JSON field selector which is based on the given {@link JsonPointer}(s).
     *
     * @param pointer a JSON pointer of the field selector to be created.
     * @param furtherPointers additional JSON pointers to form the field selector to be created by this method.
     * @return a new JSON field selector.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static JsonFieldSelector newFieldSelector(final JsonPointer pointer, final JsonPointer... furtherPointers) {
        requireNonNull(pointer, "The JSON pointer must not be null!");
        requireNonNull(furtherPointers, "The optional JSON keys must not be null!");

        final Collection<JsonPointer> jsonPointers = new LinkedHashSet<>(1 + furtherPointers.length);
        jsonPointers.add(pointer);
        Collections.addAll(jsonPointers, furtherPointers);

        return ImmutableJsonFieldSelector.of(jsonPointers);
    }

    /**
     * Returns a new JSON field selector which is based on the given {@link JsonPointer}(s).
     *
     * @param pointerString a JSON pointer of the field selector to be created.
     * @param furtherPointerStrings additional JSON pointers to form the field selector to be created by this method.
     * @return a new JSON field selector.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static JsonFieldSelector newFieldSelector(final CharSequence pointerString,
            final CharSequence... furtherPointerStrings) {

        requireNonNull(pointerString, "The JSON pointer string must not be null!");
        requireNonNull(furtherPointerStrings, "The optional JSON keys must not be null!");

        final Collection<JsonPointer> jsonPointers = new LinkedHashSet<>(1 + furtherPointerStrings.length);
        jsonPointers.add(newPointer(pointerString));
        for (final CharSequence furtherPointerString : furtherPointerStrings) {
            jsonPointers.add(newPointer(furtherPointerString));
        }

        return ImmutableJsonFieldSelector.of(jsonPointers);
    }

    /**
     * Returns a new JSON field selector which is based on pointers of the given {@link JsonFieldDefinition}(s).
     *
     * @param fieldDefinition the JSON field definition of the returned field selector.
     * @param furtherFieldDefinitions additional JSON field definitions of the returned field selector.
     * @return a new JSON field selector.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static JsonFieldSelector newFieldSelector(final JsonFieldDefinition fieldDefinition,
            final JsonFieldDefinition... furtherFieldDefinitions) {

        requireNonNull(fieldDefinition, "The JSON field definition must not be null!");
        requireNonNull(furtherFieldDefinitions, "The optional JSON field definitions must not be null!");

        final Collection<JsonPointer> pointers = new LinkedHashSet<>(1 + furtherFieldDefinitions.length);
        pointers.add(fieldDefinition.getPointer());
        for (final JsonFieldDefinition furtherFieldDefinition : furtherFieldDefinitions) {
            pointers.add(furtherFieldDefinition.getPointer());
        }

        return newFieldSelector(pointers);
    }

    /**
     * Returns a new JSON field selector builder for building JSON field selectors.
     *
     * @return the JSON field selector builder.
     */
    public static JsonFieldSelectorBuilder newFieldSelectorBuilder() {
        return ImmutableJsonFieldSelectorBuilder.newInstance();
    }

    /**
     * Returns a new definition of a JSON field which contains a String value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static JsonFieldDefinition<String> newStringFieldDefinition(final CharSequence pointer,
            final JsonFieldMarker... markers) {

        return JavaValueFieldDefinition.newInstance(pointer, String.class, JsonValue::isString, JsonValue::asString,
                markers);
    }

    /**
     * Returns a new definition of a JSON field which contains a boolean value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if any argument but {@code markers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static JsonFieldDefinition<Boolean> newBooleanFieldDefinition(final CharSequence pointer,
            final JsonFieldMarker... markers) {

        return JavaValueFieldDefinition.newInstance(pointer, Boolean.class, JsonValue::isBoolean,
                JsonValue::asBoolean, markers);
    }

    /**
     * Returns a new definition of a JSON field which contains an int value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if any argument but {@code markers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static JsonFieldDefinition<Integer> newIntFieldDefinition(final CharSequence pointer,
            final JsonFieldMarker... markers) {

        return JavaValueFieldDefinition.newInstance(pointer, Integer.class, JsonValue::isNumber, JsonValue::asInt,
                markers);
    }

    /**
     * Returns a new definition of a JSON field which contains an long value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if any argument but {@code markers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static JsonFieldDefinition<Long> newLongFieldDefinition(final CharSequence pointer,
            final JsonFieldMarker... markers) {

        return JavaValueFieldDefinition.newInstance(pointer, Long.class, JsonValue::isNumber, JsonValue::asLong,
                markers);
    }

    /**
     * Returns a new definition of a JSON field which contains an double value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if any argument but {@code markers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static JsonFieldDefinition<Double> newDoubleFieldDefinition(final CharSequence pointer,
            final JsonFieldMarker... markers) {

        return JavaValueFieldDefinition.newInstance(pointer, Double.class, JsonValue::isNumber,
                JsonValue::asDouble, markers);
    }

    /**
     * Returns a new definition of a JSON field which contains an {@link JsonArray} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if any argument but {@code markers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static JsonFieldDefinition<JsonArray> newJsonArrayFieldDefinition(final CharSequence pointer,
            final JsonFieldMarker... markers) {

        return JsonValueFieldDefinition.newInstance(pointer, JsonArray.class, JsonValue::isArray, JsonValue::asArray,
                markers);
    }

    /**
     * Returns a new definition of a JSON field which contains an {@link JsonObject} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if any argument but {@code markers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static JsonFieldDefinition<JsonObject> newJsonObjectFieldDefinition(final CharSequence pointer,
            final JsonFieldMarker... markers) {

        return JsonValueFieldDefinition.newInstance(pointer, JsonObject.class, JsonValue::isObject,
                JsonValue::asObject, markers);
    }

    /**
     * Returns a new definition of a JSON field which contains an {@link JsonValue} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if any argument but {@code markers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static JsonFieldDefinition<JsonValue> newJsonValueFieldDefinition(final CharSequence pointer,
            final JsonFieldMarker... markers) {

        return JsonValueFieldDefinition.newInstance(pointer, JsonValue.class, jsonValue -> true, Function.identity(),
                markers);
    }

    /**
     * Converts the specified JSON value of the Minimal JSON library to a matching object of our own JSON
     * implementation.
     *
     * @param minimalJsonValue the value to be converted.
     * @return a {@link JsonValue} which matches {@code minimalJsonValue} or {@code null} if {@code minimalJsonValue} is
     * {@code null}.
     * @throws IllegalStateException if {@code minimalJsonValue} cannot be converted for some reason.
     */
    @SuppressWarnings({"squid:MethodCyclomaticComplexity",
            "checkstyle:com.puppycrawl.tools.checkstyle.checks.metrics.CyclomaticComplexityCheck"})
    @Nullable
    static JsonValue convert(@Nullable final com.eclipsesource.json.JsonValue minimalJsonValue) {
        final JsonValue result;

        if (null == minimalJsonValue) {
            result = null;
        } else if (minimalJsonValue.isObject()) {
            final Map<String, JsonField> jsonFields = toMap(minimalJsonValue.asObject());
            result = ImmutableJsonObject.of(jsonFields);
        } else if (minimalJsonValue.isString()) {
            result = ImmutableJsonString.of(minimalJsonValue);
        } else if (minimalJsonValue.isArray()) {
            final List<JsonValue> jsonValues = toList(minimalJsonValue.asArray());
            result = ImmutableJsonArray.of(jsonValues);
        } else if (minimalJsonValue.isBoolean()) {
            result = newValue(minimalJsonValue.asBoolean());
        } else if (minimalJsonValue.isNull()) {
            result = NULL_LITERAL;
        } else if (minimalJsonValue.isNumber()) {
            result = ImmutableJsonNumber.of(minimalJsonValue);
        } else {
            throw new IllegalStateException(
                    MessageFormat.format("Failed to convert {0} to JsonValue!", minimalJsonValue));
        }

        return result;
    }

    /**
     * Converts the specified JSON value to a matching {@link com.eclipsesource.json.JsonValue} object.
     *
     * @param jsonValue the value to be converted.
     * @return a {@link com.eclipsesource.json.JsonValue} which matches {@code jsonValue} or {@code null} if {@code
     * jsonValue} is {@code null}.
     * @throws IllegalStateException if {@code jsonValue} cannot be converted for some reason.
     */
    @SuppressWarnings({"checkstyle:com.puppycrawl.tools.checkstyle.checks.metrics.CyclomaticComplexityCheck",
            "squid:MethodCyclomaticComplexity"})
    @Nullable
    static com.eclipsesource.json.JsonValue convert(@Nullable final JsonValue jsonValue) {
        final com.eclipsesource.json.JsonValue result;

        if (null == jsonValue) {
            result = null;
        } else if (jsonValue.isNull()) {
            result = Json.NULL;
        } else if (jsonValue.isString()) {
            result = Json.value(jsonValue.asString());
        } else if (jsonValue.isBoolean()) {
            result = Json.value(jsonValue.asBoolean());
        } else if (jsonValue.isNumber()) {
            final Double doubleValue = jsonValue.asDouble();
            if (doubleValue.intValue() == doubleValue) {
                result = Json.value(doubleValue.intValue());
            } else if (doubleValue.longValue() == doubleValue) {
                result = Json.value(doubleValue.longValue());
            } else {
                result = Json.value(doubleValue);
            }
        } else if (jsonValue.isObject()) {
            result = Json.parse(jsonValue.toString());
        } else if (jsonValue.isArray()) {
            result = Json.parse(jsonValue.toString());
        } else {
            throw new IllegalStateException(
                    MessageFormat.format("Failed to convert {0} to JsonValue of Minimal JSON!", jsonValue));
        }

        return result;
    }

    /**
     * Converts the specified char sequence to a {@link JsonPointer} which is guaranteed to be not empty.
     * @param keyOrPointer a string representation of a JSON pointer or a JsonKey.
     * @return the pointer.
     * @throws NullPointerException if {@code keyOrPointer} is {@code null}.
     * @throws IllegalArgumentException if {@code keyOrPointer} would lead to an empty JsonPointer.
     */
    static JsonPointer getNonEmptyPointer(final CharSequence keyOrPointer) {
        requireNonNull(keyOrPointer, "The key or pointer char sequence must not be null!");

        final JsonPointer result;

        if (isPointer(keyOrPointer)) {
            result = JsonFactory.newPointer(keyOrPointer);
        } else {
            final JsonKey jsonKey = JsonFactory.newKey(keyOrPointer);
            result = jsonKey.asPointer();
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("The key or pointer must not be empty!");
        }

        return result;
    }

    private static boolean isPointer(@Nullable final CharSequence charSequence) {
        return null != charSequence &&
                !JsonKey.class.isAssignableFrom(charSequence.getClass()) &&
                (JsonPointer.class.isAssignableFrom(charSequence.getClass()) ||
                        (0 == charSequence.length()) ||
                        ('/' == charSequence.charAt(0))
                );
    }

}
