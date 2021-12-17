/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.rql.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

/**
 * Holds a String representing a {@code Placeholder} in the form: {@code prefix:name}.
 * The supported placeholder prefixes are defined in the {@code RqlPredicateParser}.
 */
@Immutable
public final class ParsedPlaceholder implements CharSequence {

    private static final String PREFIX_GROUP = "prefix";
    private static final String NAME_GROUP = "name";
    private static final String COLON = ":";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(?<" + PREFIX_GROUP + ">[a-z]+)" + COLON +
            "(?<" + NAME_GROUP + ">.+)");

    private final String value;
    private final String prefix;
    private final String name;

    private ParsedPlaceholder(final String prefix, final String name) {
        this.value = String.format("%s:%s", prefix, name);
        this.prefix = prefix;
        this.name = name;
    }

    /**
     * Creates a new ParsedPlaceholder from the passed char sequence ensuring that it is in the format {@code <prefix>:<name>}.
     *
     * @param placeholderWithPrefix the char sequence containing the placeholder in format {@code <prefix>:<name>}.
     * @return the ParsedPlaceholder instance.
     * @throws NullPointerException if the passed {@code placeholderWithPrefix} was {@code null}.
     * @throws IllegalArgumentException if the passed {@code placeholderWithPrefix} was not in the expected format.
     */
    public static ParsedPlaceholder of(final CharSequence placeholderWithPrefix) {
        if (null == placeholderWithPrefix) {
            throw new NullPointerException("The placeholderWithPrefix must not be null!");
        }
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(placeholderWithPrefix);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Placeholder was not in expected format <prefix:name>!");
        }

        return new ParsedPlaceholder(matcher.group(PREFIX_GROUP), matcher.group(NAME_GROUP));
    }

    /**
     * Returns the placeholder prefix.
     *
     * @return the placeholder prefix.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns the placeholder name.
     *
     * @return the placeholder name.
     */
    public String getName() {
        return name;
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public char charAt(final int index) {
        return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return value.subSequence(start, end);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof CharSequence) {
            return Objects.equals(value, other.toString());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
