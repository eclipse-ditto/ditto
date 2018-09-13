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
package org.eclipse.ditto.model.base.common;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

/**
 * Supports substitution of placeholders in the format {@code {{ prefix:key }}}
 * or the legacy-format {@code ${prefix.key}}.
 *
 */
@Immutable
public final class Placeholders {

    private static final String PLACEHOLDER_GROUP_NAME = "ph";

    private static final String PLACEHOLDER_START = "{{";
    private static final String PLACEHOLDER_END = "}}";

    private static final String PLACEHOLDER_REGEX =
            Pattern.quote(PLACEHOLDER_START) + " (?<" + PLACEHOLDER_GROUP_NAME + ">\\S+) " +
                    Pattern.quote(PLACEHOLDER_END);


    private static final String LEGACY_PLACEHOLDER_START = "${";
    private static final String LEGACY_PLACEHOLDER_END = "}";
    private static final String LEGACY_PLACEHOLDER_REGEX =
            Pattern.quote(LEGACY_PLACEHOLDER_START) + "(?<" + PLACEHOLDER_GROUP_NAME +  ">\\S+)" +
                    Pattern.quote(LEGACY_PLACEHOLDER_END);

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(PLACEHOLDER_REGEX);
    private static final Pattern LEGACY_PLACEHOLDER_PATTERN = Pattern.compile(LEGACY_PLACEHOLDER_REGEX);

    private static final Function<String, String> TO_LEGACY_PLACEHOLDER_CONVERTER =
            inputPlaceholder -> inputPlaceholder.replaceAll(Pattern.quote("."), ":");

    private Placeholders() {
        throw new AssertionError();
    }

    /**
     * Checks whether the given {@code input} contains any placeholder.
     * @param input the input.
     * @return {@code} true, if the input contains a placeholder.
     */
    public static boolean containsAnyPlaceholder(final CharSequence input) {
        requireNonNull(input);
        return PLACEHOLDER_PATTERN.matcher(input).find() ||
                LEGACY_PLACEHOLDER_PATTERN.matcher(input).find();
    }

    /**
     * Substitutes any placeholder contained in the input.
     *
     * @param input the input.
     * @param placeholderReplacerFunction a function defining how a placeholder will be replaced. It must not return
     * null, instead it should throw a specific exception if a placeholder cannot be replaced.
     * @return the replaced input, if the input contains placeholders; the (same) input object, if no placeholders were
     * contained in the input.
     * @throws IllegalStateException if {@code placeholderReplacerFunction} returns null
     */
    public static String substitute(final String input, final Function<String, String> placeholderReplacerFunction) {
        final Function<String, String> legacyPlaceholderReplacerFunction =
                TO_LEGACY_PLACEHOLDER_CONVERTER.andThen(placeholderReplacerFunction);

        String maybeSubstituted =
                substitute(input, PLACEHOLDER_PATTERN, placeholderReplacerFunction);
        maybeSubstituted =
                substitute(maybeSubstituted, LEGACY_PLACEHOLDER_PATTERN, legacyPlaceholderReplacerFunction);

        return maybeSubstituted;
    }

    private static String substitute(final String input, final Pattern pattern,
            final Function<String, String> replacerFunction) {

        final Matcher matcher = pattern.matcher(input);
        StringBuilder substituted = null;
        int previousMatchEnd = 0;
        while (matcher.find()) {
            if (substituted == null) {
                substituted = new StringBuilder(input.length());
            }
            final int start = matcher.start();
            final int end = matcher.end();

            final String previousText = input.substring(previousMatchEnd, start);
            substituted.append(previousText);

            final String placeholder = matcher.group(PLACEHOLDER_GROUP_NAME);
            final String replacement = replacerFunction.apply(placeholder);
            if (replacement == null) {
                throw new IllegalStateException("Null values must not be returned by replacerFunction");
            }
            substituted.append(replacement);

            previousMatchEnd = end;
        }

        if (substituted != null && previousMatchEnd < input.length()) {
            final String remainingText = input.substring(previousMatchEnd);
            substituted.append(remainingText);
        }

        if (substituted == null) {
            return input;
        } else {
            return substituted.toString();
        }
    }
}
