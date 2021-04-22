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

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * Represents a JSON object. A JSON object is a set of key-value-pairs with unique keys.
 * <p>
 * <em>Implementations of this interface are required to be immutable!</em>
 * </p>
 */
public interface JsonObject extends JsonValue, JsonValueContainer<JsonField> {

    /**
     * Creates a {@code JsonObject} from the given string.
     *
     * @param jsonObjectString the string that represents the JSON object.
     * @return the JSON object that has been created from the string.
     * @throws NullPointerException if {@code jsonObjectString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObjectString} is empty.
     * @throws JsonParseException if {@code jsonObjectString} does not represent a valid JSON object.
     */
    static JsonObject of(final String jsonObjectString) {
        return JsonFactory.newObject(jsonObjectString);
    }

    /**
     * Creates a {@code JsonObject} from the given byte array.
     *
     * @param jsonData the byte array that represents the JSON object.
     * @return the JSON object that has been created from the data.
     * @throws NullPointerException if {@code jsonData} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonData} is empty.
     * @throws JsonParseException if {@code jsonData} does not represent a valid JSON object.
     * @since 1.5.0
     */
    static JsonObject of(final byte[] jsonData) {
        return JsonFactory.newObject(jsonData);
    }

    /**
     * Returns a new mutable builder with a fluent API for a {@code JsonObject}.
     *
     * @return the builder.
     */
    static JsonObjectBuilder newBuilder() {
        return JsonFactory.newObjectBuilder();
    }

    /**
     * Returns an empty instance of {@code JsonObject}.
     *
     * @return the instance.
     */
    static JsonObject empty() {
        return JsonFactory.newObject();
    }

    /**
     * Returns a new mutable builder with a fluent API for a {@code JsonObject}. The returned builder is already
     * initialised with the data of the this JSON object. This method is useful if an existing JSON object should be
     * strongly modified but the amount of creating objects should be kept low at the same time.
     *
     * @return the builder which is initialised with the data of this JSON object instance.
     */
    default JsonObjectBuilder toBuilder() {
        return JsonFactory.newObjectBuilder(this);
    }

    /**
     * Creates a new JSON object by setting a new JSON field with the specified key and the JSON representation of the
     * specified {@code int} value to the new object. If this object previously contained a field with the same key,
     * the old field is replaced by the new field in the new object.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value of the field to be set.
     * @return a new JSON object with an association between {@code key} and {@code value} or this instance if it
     * already contains a field with {@code key} and {@code value}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     * @throws JsonKeyInvalidException if the JSON {@code key} was not valid.
     */
    JsonObject setValue(CharSequence key, int value);

    /**
     * Creates a new JSON object by setting a new JSON field with the specified key and the JSON representation of the
     * specified {@code long} value to the new object. If this object previously contained a field with the same key,
     * the old field is replaced by the new field in the new object.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value of the field to be set.
     * @return a new JSON object with an association between {@code key} and {@code value} or this instance if it
     * already contains a field with {@code key} and {@code value}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     * @throws JsonKeyInvalidException if the JSON {@code key} was not valid.
     */
    JsonObject setValue(CharSequence key, long value);

    /**
     * Creates a new JSON object by setting a new JSON field with the specified key and the JSON representation of the
     * specified {@code double} value to the new object. If this object previously contained a field with the same key,
     * the old field is replaced by the new field in the new object.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value of the field to be set.
     * @return a new JSON object with an association between {@code key} and {@code value} or this instance if it
     * already contains a field with {@code key} and {@code value}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     * @throws JsonKeyInvalidException if the JSON {@code key} was not valid.
     */
    JsonObject setValue(CharSequence key, double value);

    /**
     * Creates a new JSON object by setting a new JSON field with the specified key and the JSON representation of the
     * specified {@code boolean} value to the new object. If this object previously contained a field with the same
     * key, the old field is replaced by the new field in the new object.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value of the field to be set.
     * @return a new JSON object with an association between {@code key} and {@code value}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     * @throws JsonKeyInvalidException if the JSON {@code key} was not valid.
     */
    JsonObject setValue(CharSequence key, boolean value);

    /**
     * Creates a new JSON object by setting a new JSON field with the specified key and the JSON representation of the
     * specified {@code String} value to the new object. If this object previously contained a field with the same key,
     * the old field is replaced by the new field in the new object.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value of the field to be set.
     * @return a new JSON object with an association between {@code key} and {@code value}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     * @throws JsonKeyInvalidException if the JSON {@code key} was not valid.
     */
    JsonObject setValue(CharSequence key, String value);

    /**
     * Creates a new JSON object by setting a new JSON field with the specified key and the specified JSON value to the
     * new object. If this object previously contained a field with the same key, the old field is replaced by the new
     * field in the new object.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value of the field to be set.
     * @return a new JSON object with an association between {@code key} and {@code value}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     * @throws JsonKeyInvalidException if the JSON {@code key} was not valid.
     */
    JsonObject setValue(CharSequence key, JsonValue value);

    /**
     * Sets the specified value to a field which is defined by the pointer of the given field definition on a copy of
     * this object.
     *
     * @param fieldDefinition the definition of the JSON field containing the value, i. e. the field with
     * {@link JsonPointer#getLeaf()} as key and {@code value} as value.
     * @param value the value to be set.
     * @param <T> the type of {@code value}.
     * @return a copy of this object with the value set at the pointer defined location.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @throws IllegalArgumentException if the pointer of {@code fieldDefinition} is empty.
     * @throws JsonKeyInvalidException if the JSON key in the passed {@code fieldDefinition} was not valid.
     */
    <T> JsonObject set(JsonFieldDefinition<T> fieldDefinition, @Nullable T value);

    /**
     * Sets the specified field to a copy of this object. A previous field with the same key is replaced.
     *
     * @param field the field to be set.
     * @return a copy of this object with the field set.
     * @throws NullPointerException if {@code field} is {@code null}.
     * @throws JsonKeyInvalidException if the JSON key in the passed {@code jsonField} was not valid.
     */
    JsonObject set(JsonField field);

    /**
     * Creates a new JSON object by setting the given JSON fields to this object. All previous fields with the same
     * key are replaced.
     *
     * @param jsonFields the fields to set.
     * @return a new JSON object extended by the specified fields.
     * @throws NullPointerException if {@code jsonFields} is null.
     * @throws JsonKeyInvalidException if a JSON key in the passed {@code jsonFields} was not valid.
     */
    JsonObject setAll(Iterable<JsonField> jsonFields);

    /**
     * Indicates whether this JSON object contains a field at the key defined location.
     *
     * @param key the JSON key or JSON pointer to be looked up.
     * @return {@code true} if this JSON object contains a field at {@code key}, {@code false} else.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    boolean contains(CharSequence key);

    /**
     * Returns a new JSON object containing the whole object hierarchy of the value which is defined by the given
     * pointer. If, for example, on the following JSON object
     * <pre>
     *    {
     *       "thingId": "myThing",
     *       "attributes": {
     *          "someAttr": {
     *             "subsel": 42
     *          },
     *          "anotherAttr": "baz"
     *       }
     *    }
     * </pre>
     * <p>
     * this method with the pointer {@code "attributes/someAttr/subsel"} is called the returned JSON object is
     * </p>
     * <pre>
     *    {
     *       "attributes": {
     *          "someAttr": {
     *             "subsel": 42
     *          }
     *       }
     *    }
     * </pre>
     *
     * @param pointer defines which value to get.
     * @return this object if the pointer is empty, a new hierarchical JSON object containing the pointer-defined
     * value or an empty object if the pointer refers to a non-existing value.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     */
    JsonObject get(JsonPointer pointer);

    /**
     * This is a convenience method which does the same as {@link #get(JsonPointer)}. The pointer is obtained from the
     * specified field definition.
     *
     * @param fieldDefinition supplies the JSON pointer to be used.
     * @return a new hierarchical JSON object containing the pointer-defined value or an empty object if the pointer
     * refers to a non-existing value.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @see #get(JsonPointer)
     */
    JsonObject get(JsonFieldDefinition<?> fieldDefinition);

    /**
     * Returns a new JSON object which is composed from parts of this object. The parts to use are defined by the given
     * {@link JsonFieldSelector}. The order of the fields defined in the field selector is maintained in the result. For
     * example, if on the following JSON object
     * <pre>
     *    {
     *       "thingId": "0x1337",
     *       "foo": {
     *          "bar": {
     *             "baz": 23,
     *             "oogle": "boogle"
     *          },
     *          "yo": 10
     *       },
     *       "isOn": false
     *    }
     * </pre>
     * <p>
     * this method is called with the field selector {@code "foo(bar/baz,yo),thingId"} the returned JSON object is
     * </p>
     * <pre>
     *    {
     *    "foo": {
     *       "bar": {
     *          "baz": 23
     *       },
     *       "yo": 10
     *    },
     *    "thingId": "0x1337"
     *    }
     * </pre>
     * This also works with arbitrarily nested levels.
     *
     * @param fieldSelector the JSON field selector which defines what the returned object should contain.
     * @return if the field selector is empty or contains only pointers to non-existing values an empty JSON object;
     * otherwise a new JSON object containing the field selector-defined parts of this object.
     * @throws NullPointerException if {@code field selector} is {@code null}.
     */
    JsonObject get(JsonFieldSelector fieldSelector);

    /**
     * Returns the value which is associated with the specified key. This method is similar to {@link #get(JsonPointer)}
     * however it does not maintain any hierarchy but returns simply the value. If, for example, on the following JSON
     * object
     * <pre>
     *    {
     *       "thingId": "myThing",
     *       "attributes": {
     *          "someAttr": {
     *             "subsel": 42
     *          },
     *          "anotherAttr": "baz"
     *       }
     *    }
     * </pre>
     * this method is called with key {@code "attributes/someAttr/subsel"} an empty Optional is returned. Is the key
     * {@code "thingId"} used instead the returned Optional would contain {@code "myThing"}.
     * If the specified key is empty or {@code "/"} this object reference is returned within the result.
     *
     * @param key defines which value to get.
     * @return the JSON value at the key-defined location within this object.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    Optional<JsonValue> getValue(CharSequence key);

    /**
     * Returns the plain Java typed value of the field whose location is defined by the JsonPointer of the specified
     * JsonFieldDefinition. The expected Java type is the value type of the JsonFieldDefinition. If this JsonObject
     * does not contain a value at the defined location an empty Optional is returned.
     *
     * @param fieldDefinition supplies the JsonPointer to and expected value type of the desired result value.
     * @param <T> the expected type of the result.
     * @return the JSON value at the defined location within this object or an empty Optional if this JsonObject did not
     * contain a field at the location.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @throws JsonParseException if this JsonObject contained a value at the defined location with an unexpected type.
     */
    <T> Optional<T> getValue(JsonFieldDefinition<T> fieldDefinition);

    /**
     * Returns the plain Java typed value of the field whose location is defined by the JsonPointer of the specified
     * JsonFieldDefinition. The expected Java type is the value type of the JsonFieldDefinition. If this JsonObject
     * does not contain a value at the defined location a {@link JsonMissingFieldException} is thrown.
     *
     * @param fieldDefinition supplies the JsonPointer to and expected value type of the desired result value.
     * @param <T> the expected type of the result.
     * @return the JSON value at the defined location within this object.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @throws JsonMissingFieldException if this JsonObject did not contain a value at all at the defined location.
     * @throws JsonParseException if this JsonObject contained a value at the defined location with a type which is
     * different from {@code T}.
     */
    <T> T getValueOrThrow(JsonFieldDefinition<T> fieldDefinition);

    /**
     * Removes the JSON field to which the given pointer points to. The pointer's leaf is the key of the field to be
     * removed. For example, if on the following JSON object
     * <pre>
     *    {
     *       "someObjectAttribute": {
     *          "someKey": {
     *             "someNestedKey": 42
     *          }
     *       }
     *    }
     * </pre>
     * this method is called with the pointer {@code "/someObjectAttribute/someKey/someNestedKey"} the returned
     * object is
     * <pre>
     *    {
     *       "someObjectAttribute": {
     *          "someKey": {}
     *       }
     *    }
     * </pre>
     *
     * @param key the key or JSON pointer which denotes the field to be removed.
     * @return a new JSON object without the JSON field which is defined by the pointer.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    JsonObject remove(CharSequence key);

    /**
     * Returns a list of the keys in this JSON object in document order. The returned list cannot be used to modify this
     * JSON object.
     *
     * @return an unmodifiable list of the keys in this JSON object;
     */
    List<JsonKey> getKeys();

    /**
     * Returns the JsonField which contains both JsonKey and JsonValue for the passed key.
     *
     * @param key the JSON key or JSON pointer which denotes the field to be get.
     * @return the JSON field containing key and value.
     * @throws NullPointerException if {@code index} is {@code null}.
     */
    Optional<JsonField> getField(CharSequence key);

}
