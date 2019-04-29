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

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link JsonKey}.
 */
@Immutable
final class ImmutableJsonKey implements JsonKey {

    private final String keyValue;

    private ImmutableJsonKey(final String theKeyValue) {
        keyValue = theKeyValue;
    }

    /**
     * Returns a new JSON Key based on the provided string.
     *
     * @param keyValue the character sequence forming the keyValue's value.
     * @return a new JSON Key.
     * @throws NullPointerException if {@code keyValue} is {@code null}.
     * @throws IllegalArgumentException if {@code keyValue} is empty.
     */
    public static JsonKey of(final CharSequence keyValue) {
        requireNonNull(keyValue, "The key string must not be null!");

        if (JsonKey.class.isAssignableFrom(keyValue.getClass())) {
            return ((JsonKey) keyValue);
        } else  if (0 == keyValue.length()) {
            throw new IllegalArgumentException("The key string must not be empty!");
        }

        return new ImmutableJsonKey(keyValue.toString());
    }

    @Override
    public JsonPointer asPointer() {
        return JsonFactory.newPointer(this);
    }

    @Override
    public int length() {
        return keyValue.length();
    }

    @Override
    public char charAt(final int index) {
        return keyValue.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return keyValue.subSequence(start, end);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonKey that = (ImmutableJsonKey) o;
        return Objects.equals(keyValue, that.keyValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyValue);
    }

    @Override
    @Nonnull
    public String toString() {
        return keyValue;
    }

}
