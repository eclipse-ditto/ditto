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

import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * A JSON pointer defines a path within a JSON object. This path is hierarchical like a file tree. Thus each element of
 * this path is a sub level of it predecessor beginning at the root and ending at the leaf. For example, given the
 * following JSON object
 * <pre>
 *    {
 *       "thingId": "myThing",
 *       "attributes": {
 *          "location": {
 *             "latitude": 47.003215,
 *             "longitude": 9.0815
 *          }
 *       },
 *       "features": {}
 *    }
 * </pre>
 * The root of the JSON pointer {@code "/attributes/location/longitude"} is {@code "/attributes"} and the leaf is
 * {@code "longitude"}. This pointer points to the field with key {@code "longitude"} and value {@code 9.0815} of the
 * aforementioned JSON object.
 * <p>
 * <em>Implementations of this interface are required to be immutable!</em>
 * </p>
 */
public interface JsonPointer extends CharSequence, Iterable<JsonKey> {

    /**
     * Returns an empty JSON pointer. An empty pointer is represented by string {@code "/"}.
     *
     * @return the pointer.
     */
    @Nonnull
    static JsonPointer empty() {
        return JsonFactory.emptyPointer();
    }

    /**
     * Parses the given string to obtain a new JSON pointer. This method is the inverse of
     * {@link JsonPointer#toString()} with one exception: both strings {@code "/"} and {@code ""} lead to an empty
     * pointer while the string representation of an empty string is always {@code "/"}.
     * <p>
     * To support tildes in JsonKeys, they have to be escaped with {@code "~0"}. For example, parsing the string
     * {@code "/foo/~0dum~0die~0dum/baz"} would result in a JsonPointer consisting of the JsonKeys
     * <ol>
     * <li>{@code "foo"},</li>
     * <li>{@code "~dum~die~dum"} and</li>
     * <li>{@code "baz"}.</li>
     * </ol>
     *
     * @param slashDelimitedCharSequence slash-delimited string representing of a JSON pointer. The leading slash may
     * be omitted.
     * @return a new JSON pointer consisting of the JSON keys which were extracted from
     * {@code slashDelimitedCharSequence}.
     * @throws NullPointerException if {@code slashDelimitedCharSequence} is {@code null}.
     * @throws JsonPointerInvalidException if the passed {@code slashDelimitedCharSequence} contained double slashes.
     */
    static JsonPointer of(final CharSequence slashDelimitedCharSequence) {
        return JsonFactory.newPointer(slashDelimitedCharSequence);
    }

    /**
     * Creates a new JSON pointer by adding a level to this JSON pointer. For example, if this pointer is
     * {@code "/foo/bar"} and {@code addLevel()} is called with a JSON key {@code "baz"} then the new JSON pointer is
     * {@code "/foo/bar/baz"}.
     *
     * @param key the new level JSON key.
     * @return a <em>new</em> JSON pointer consisting of the old pointer extended by {@code jsonField}.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    JsonPointer addLeaf(JsonKey key);

    /**
     * Creates a new JSON pointer by appending the given pointer to this pointer.
     *
     * @param pointer the pointer to be appended to this pointer.
     * @return a new JSON pointer with this pointer as root followed by {@code pointer}.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     */
    JsonPointer append(JsonPointer pointer);

    /**
     * Returns the number of levels of this JSON pointer. For example, if the pointer is {@code "foo/bar/baz"} this
     * method will return the value {@literal 3}.
     *
     * @return the number of levels of this pointer.
     */
    int getLevelCount();

    /**
     * Indicates whether this pointer does contain any elements or not. If the level count is zero the pointer is
     * regarded to be empty.
     *
     * @return {@code true} if this pointer does <em>not</em> contain any elements, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns the JSON key at the specified level within this JSON pointer.
     *
     * @param level the level of the JSON key to return.
     * @return the JSON key at the specified level within this JSON pointer. If the level is outside the bounds of this
     * pointer the result is empty.
     */
    Optional<JsonKey> get(int level);

    /**
     * Returns the root JSON key of this pointer. If the level count of this pointer is {@code one} the root is the same
     * as the leaf.
     *
     * @return the root key if this pointer is not empty.
     */
    Optional<JsonKey> getRoot();

    /**
     * Returns the leaf JSON key of this pointer. If the level count of this pointer is {@code one} the leaf is the same
     * as the root.
     *
     * @return the leaf key if this pointer is not empty.
     */
    Optional<JsonKey> getLeaf();

    /**
     * Creates a new pointer by removing the leaf element of this pointer.
     *
     * @return a new pointer which does not contain the leaf element of this pointer. If this pointer is empty this
     * pointer instance is returned.
     */
    JsonPointer cutLeaf();

    /**
     * Goes to the next sub level of this pointer.
     *
     * @return a new pointer beginning with the element after the root of this pointer. If this pointer is empty this
     * pointer instance is returned.
     */
    JsonPointer nextLevel();

    /**
     * Returns a new JSON pointer including all JSON keys from to the passed level and upwards. For example, if this
     * pointer is {@code /foo/bar/baz} then calling this method with level {@literal 1} returns the pointer
     * {@code /bar/baz}.
     *
     * @param level the level from which (upwards) JSON keys should be included in the result. The key at the specified
     * level is part of the result.
     * @return the sub pointer starting from {@code level}. If the level is outside the bounds of this pointer the
     * result is empty.
     */
    Optional<JsonPointer> getSubPointer(int level);

    /**
     * Returns a new JSON pointer including all JSON keys from the root until the passed in level. For example, if this
     * pointer is {@code /foo/bar/baz} then calling this method with level {@literal 1} returns the pointer
     * {@code /foo/bar}.
     *
     * @param level the level from which (downwards) JSON keys should be included in the result. The key at the
     * specified level is part of the result.
     * @return the sub pointer starting from the root until {@code level}. If {@code level} is outside the bounds of
     * this pointer the result is empty.
     */
    Optional<JsonPointer> getPrefixPointer(int level);

    /**
     * Returns a new {@link JsonFieldSelector} containing this pointer as its only element.
     *
     * @return a new JSON field selector containing only this pointer.
     */
    JsonFieldSelector toFieldSelector();

    /**
     * The string representation of this pointer, i. e. all of its levels concatenated by {@code "/"}. For example,
     * if this pointer consists of the three levels {@code "foo"}, {@code "bar"} and {@code "baz"}, this method
     * will return the string {@code "/foo/bar/baz"}.
     *
     * @return the string representation of this pointer.
     */
    @Override
    String toString();

}
