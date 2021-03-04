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

/**
 * Represents a JSON key. A JSON key can be used to access values of a JSON document. For example, in the following JSON
 * document
 * <pre>
 *    {
 *       "foo": 1,
 *       "bar": 1337,
 *       "baz": "Some other value"
 *    }
 * </pre>
 * <p>
 * {@code "foo"}, {@code "bar"} and {@code "baz"} are JSON keys which can be represented by this interface.
 * </p>
 * <p>
 * <em>Implementations of this interface are required to be immutable.</em>
 * </p>
 */
public interface JsonKey extends CharSequence {

    /**
     * Returns JSON key for the given character sequence. If the given key value is already a JSON key, this is
     * immediately properly cast and returned.
     *
     * @param keyValue the character sequence value of the JSON key to be created.
     * @return a new JSON key with {@code keyValue} as its value.
     * @throws NullPointerException if {@code keyValue} is {@code null}.
     * @throws IllegalArgumentException if {@code keyValue} is empty.
     */
    static JsonKey of(final CharSequence keyValue) {
        return JsonFactory.newKey(keyValue);
    }

    /**
     * Returns this key as {@link JsonPointer} with one level. If, for example, this key is {@code "foo"} the returned
     * pointer is {@code "/foo"}.
     *
     * @return this key as JSON pointer.
     */
    JsonPointer asPointer();

}
