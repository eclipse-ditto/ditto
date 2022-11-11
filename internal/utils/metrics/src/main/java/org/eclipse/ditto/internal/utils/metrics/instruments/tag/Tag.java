/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.metrics.instruments.tag;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * A tag which consists of a key and its associated value where both, key and
 * value is a string which is ensured to be neither {@code null} nor blank
 * (see {@link String#isBlank()}).
 */
@Immutable
public final class Tag {

    private final String key;
    private final String value;

    private Tag(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Returns a new instance of {@code Tag} for the specified key and value
     * arguments.
     *
     * @param key the key of the returned tag.
     * @param value the value of the returned tag.
     * @return the new {@code Tag}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code key} or {@code value} is
     * blank (see {@link String#isBlank()}).
     */
    public static Tag of(final String key, final String value) {
        return new Tag(validateString(key, "key"), validateString(value, "value"));
    }

    private static String validateString(final String s, final String argumentName) {
        return ConditionChecker.checkArgument(
                ConditionChecker.checkNotNull(s, argumentName),
                arg -> !arg.isBlank(),
                () -> MessageFormat.format("The {0} must not be blank.", argumentName)
        );
    }

    /**
     * Returns a new instance of {@code Tag} for the specified key and value
     * arguments.
     * This is a convenience alternative for {@link #of(String, String)} where
     * the value gets converted to String by {@link Boolean#toString(boolean)}.
     *
     * @param key the key of the returned tag.
     * @param value the value which is converted to String in the returned tag.
     * @return the new {@code Tag}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code key} is blank (see {@link String#isBlank()}).
     */
    public static Tag of(final String key, final boolean value) {
        return of(key, Boolean.toString(value));
    }

    /**
     * Returns a new instance of {@code Tag} for the specified key and value
     * arguments.
     * This is a convenience alternative for {@link #of(String, String)} where
     * the value gets converted to String by {@link Long#toString(long)}.
     *
     * @param key the key of the returned tag.
     * @param value the value which is converted to String in the returned tag.
     * @return the new {@code Tag}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code key} is blank (see {@link String#isBlank()}).
     */
    public static Tag of(final String key, final long value) {
        return of(key, Long.toString(value));
    }

    /**
     * Returns the key of this tag.
     *
     * @return the key.
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the value of this tag.
     *
     * @return the value.
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (Tag) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "key=" + key +
                ", value=" + value +
                "]";
    }

}
