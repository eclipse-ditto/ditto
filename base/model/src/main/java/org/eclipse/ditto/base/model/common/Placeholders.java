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
package org.eclipse.ditto.base.model.common;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

/**
 * Supports checking for presence of placeholders in the format {@code {{ prefix:key }}}
 * or the legacy-format {@code ${prefix.key}}.
 */
@Immutable
public final class Placeholders {

    private static final String PLACEHOLDER_GROUP_NAME = "p";
    private static final String LEGACY_PLACEHOLDER_GROUP_NAME = "l";
    private static final String LEGACY_PLACEHOLDER_SUBJECT_ID_GROUP_NAME = "s";

    private static final String PLACEHOLDER_START = Pattern.quote("{{");
    private static final String PLACEHOLDER_END = Pattern.quote("}}");

    /*
     * Caution: If you adapt this regex, make sure to adapt it also in org.eclipse.ditto.json.ImmutableJsonFieldSelectorFactory.
     * It had to be duplicated because it couldn't be used here due to dependency cycles.
     */
    private static final String PLACEHOLDER_GROUP = "(?<" + PLACEHOLDER_GROUP_NAME + ">((}[^}]|[^}])*+))";
    private static final String LEGACY_PLACEHOLDER_GROUP = "(?<" + LEGACY_PLACEHOLDER_GROUP_NAME + ">([^}]*+))";
    private static final String ANY_NUMBER_OF_SPACES = "\\s*+";
    private static final String PLACEHOLDER_REGEX = PLACEHOLDER_START
            + ANY_NUMBER_OF_SPACES // allow arbitrary number of spaces
            + PLACEHOLDER_GROUP // the content of the placeholder
            + ANY_NUMBER_OF_SPACES  // allow arbitrary number of spaces
            + PLACEHOLDER_END; // end of placeholder

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(PLACEHOLDER_REGEX);

    private static final String LEGACY_PLACEHOLDER_START = Pattern.quote("${");
    private static final String LEGACY_PLACEHOLDER_END = Pattern.quote("}");
    private static final String LEGACY_PLACEHOLDER_REGEX =
            LEGACY_PLACEHOLDER_START + LEGACY_PLACEHOLDER_GROUP + LEGACY_PLACEHOLDER_END;
    private static final Pattern LEGACY_PLACEHOLDER_PATTERN = Pattern.compile(LEGACY_PLACEHOLDER_REGEX);

    private static final Pattern ANY_PLACEHOLDER_PATTERN =
            Pattern.compile("(?:" + PLACEHOLDER_REGEX + "|" + LEGACY_PLACEHOLDER_REGEX + ")");

    private Placeholders() {
        throw new AssertionError();
    }

    /**
     * Get the pattern for any placeholder.
     *
     * @return the pattern.
     */
    public static Pattern pattern() {
        return ANY_PLACEHOLDER_PATTERN;
    }

    /**
     * Get the group names of the placeholder in a match.
     *
     * @return the group name.
     */
    public static List<String> groupNames() {
        return Arrays.asList(PLACEHOLDER_GROUP_NAME, LEGACY_PLACEHOLDER_GROUP_NAME,
                LEGACY_PLACEHOLDER_SUBJECT_ID_GROUP_NAME);
    }

    /**
     * Checks whether the given {@code input} contains any placeholder.
     *
     * @param input the input.
     * @return {@code} true, if the input contains a placeholder.
     */
    public static boolean containsAnyPlaceholder(final CharSequence input) {
        requireNonNull(input);
        return containsPlaceholder(input) || containsLegacyPlaceholder(input);
    }

    /**
     * Checks whether the given {@code input} contains any placeholder.
     *
     * @param input the input.
     * @return {@code} true, if the input contains a placeholder.
     */
    private static boolean containsPlaceholder(final CharSequence input) {
        requireNonNull(input);
        return PLACEHOLDER_PATTERN.matcher(input).find();
    }

    /**
     * Checks whether the given {@code input} contains any legacy placeholder.
     *
     * @param input the input.
     * @return {@code} true, if the input contains a placeholder.
     */
    private static boolean containsLegacyPlaceholder(final CharSequence input) {
        requireNonNull(input);
        return LEGACY_PLACEHOLDER_PATTERN.matcher(input).find();
    }

}
