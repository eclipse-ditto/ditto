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
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link JsonIndex}.
 */
@Immutable
public final class ImmutableJsonIndex implements JsonIndex {

    private final CharSequence pointerOrKey;
    private final boolean isPointer;

    private ImmutableJsonIndex(final CharSequence thePointerOrKey, final boolean isPointer) {
        pointerOrKey = thePointerOrKey;
        this.isPointer = isPointer;
    }

    /**
     * Creates a new instance of {@code ImmutableJsonIndex} by parsing the specified char sequence.
     *
     * @param indexValue the char sequence to be parsed.
     * @return the instance.
     * @throws NullPointerException if {@code indexValue} is {@code null}.
     */
    public static ImmutableJsonIndex of(@Nonnull final CharSequence indexValue) {
        requireNonNull(indexValue, "The JSON index value to be parsed must not be null!");

        if (isPointer(indexValue)) {
            return new ImmutableJsonIndex(JsonFactory.newPointer(indexValue), true);
        }
        return new ImmutableJsonIndex(JsonFactory.newKey(indexValue), false);
    }

    private static boolean isPointer(final CharSequence indexValue) {
        return (0 == indexValue.length()) || ('/' == indexValue.charAt(0));
    }

    @Override
    public boolean isPointer() {
        return isPointer;
    }

    @Override
    public boolean isKey() {
        return !isPointer;
    }

    @Override
    public JsonPointer asPointer() {
        if (!isPointer()) {
            throw new IllegalStateException(MessageFormat.format("<{0}> is not a JSON pointer!", pointerOrKey));
        }
        //noinspection ConstantConditions
        return (JsonPointer) pointerOrKey;
    }

    @Override
    public JsonKey asKey() {
        if (!isKey()) {
            throw new IllegalStateException(MessageFormat.format("<{0}> is not a JSON key!", pointerOrKey));
        }
        //noinspection ConstantConditions
        return (JsonKey) pointerOrKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonIndex that = (ImmutableJsonIndex) o;
        return isPointer == that.isPointer &&
                Objects.equals(pointerOrKey, that.pointerOrKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pointerOrKey, isPointer);
    }

    @Override
    public int length() {
        return pointerOrKey.length();
    }

    @Override
    public char charAt(final int index) {
        return pointerOrKey.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return pointerOrKey.subSequence(start, end);
    }

    @Override
    public String toString() {
        return pointerOrKey.toString();
    }

}
