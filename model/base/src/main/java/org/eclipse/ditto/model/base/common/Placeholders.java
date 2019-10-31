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
package org.eclipse.ditto.model.base.common;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * Supports substitution of placeholders in the format {@code {{ prefix:key }}}
 * or the legacy-format {@code ${prefix.key}}.
 */
@Immutable
public final class Placeholders {

    private static final String PLACEHOLDER_GROUP_NAME = "p";
    private static final String LEGACY_PLACEHOLDER_GROUP_NAME = "l";

    private static final String PLACEHOLDER_START = Pattern.quote("{{");
    private static final String PLACEHOLDER_END = Pattern.quote("}}");

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

    private static final String LEGACY_REQUEST_SUBJECT_ID =
            "(?<" + LEGACY_PLACEHOLDER_GROUP_NAME + ">" + Pattern.quote("request.subjectId") + ")";
    private static final String LEGACY_REQUEST_SUBJECT_ID_REGEX =
            LEGACY_PLACEHOLDER_START + LEGACY_REQUEST_SUBJECT_ID + LEGACY_PLACEHOLDER_END;
    private static final Pattern LEGACY_REQUEST_SUBJECT_ID_PATTERN = Pattern.compile(LEGACY_REQUEST_SUBJECT_ID_REGEX);

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
        return Arrays.asList(PLACEHOLDER_GROUP_NAME, LEGACY_PLACEHOLDER_GROUP_NAME);
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

    /**
     * Checks whether the given {@code input} contains legacy request subject id placeholder.
     *
     * @param input the input.
     * @return {@code} true, if the input contains a placeholder.
     */
    private static boolean containsLegacyRequestSubjectIdPlaceholder(final CharSequence input) {
        requireNonNull(input);
        return LEGACY_REQUEST_SUBJECT_ID_PATTERN.matcher(input).find();
    }

    /**
     * Substitutes any placeholder contained in the input.
     *
     * @param input the input.
     * @param placeholderReplacerFunction a function defining how a placeholder will be replaced. It must not return
     * null, instead it should throw a specific exception if a placeholder cannot be replaced.
     * @param unresolvedInputHandler exception handler providing a exception which is thrown when placeholders
     * remain unresolved, e.g. when brackets have the wrong order.
     * @return the replaced input, if the input contains placeholders; the (same) input object, if no placeholders were
     * contained in the input.
     * @throws IllegalStateException if {@code placeholderReplacerFunction} returns null
     * @throws DittoRuntimeException if {@code allowUnresolved} is set to {@code false}, the passed in
     * {@code unresolvedInputHandler} will be used in order to throw the DittoRuntimeException which was defined by the
     * caller
     */
    public static String substitute(final String input,
            final Function<String, Optional<String>> placeholderReplacerFunction,
            final Function<String, DittoRuntimeException> unresolvedInputHandler) {
        requireNonNull(input);
        requireNonNull(placeholderReplacerFunction);
        requireNonNull(unresolvedInputHandler);

        final String substitutedStandardPlaceholder = substituteStandardPlaceholder(input, placeholderReplacerFunction,
                unresolvedInputHandler);
        return substituteLegacyPlaceholder(substitutedStandardPlaceholder, placeholderReplacerFunction,
                unresolvedInputHandler);
    }

    private static String substituteLegacyPlaceholder(final String input,
            final Function<String, Optional<String>> placeholderReplacerFunction,
            final Function<String, DittoRuntimeException> unresolvedInputHandler) {
        if (containsLegacyPlaceholder(input)) {
            // for legacy placeholder we only allow request.subjectId, all other placeholders are unresolved
            if (containsLegacyRequestSubjectIdPlaceholder(input)) {
                final String substituted =
                        substitute(input, LEGACY_REQUEST_SUBJECT_ID_PATTERN, LEGACY_PLACEHOLDER_GROUP_NAME,
                                placeholderReplacerFunction);
                if (containsLegacyPlaceholder(substituted)) {
                    throw unresolvedInputHandler.apply(substituted);
                } else {
                    return substituted;
                }
            } else {
                throw unresolvedInputHandler.apply(input);
            }
        }
        return input;
    }

    private static String substituteStandardPlaceholder(final String input,
            final Function<String, Optional<String>> placeholderReplacerFunction,
            final Function<String, DittoRuntimeException> unresolvedInputHandler) {

        if (containsPlaceholder(input)) {
            final String substituted = substitute(input, PLACEHOLDER_PATTERN, PLACEHOLDER_GROUP_NAME,
                    placeholderReplacerFunction);
            if (containsPlaceholder(substituted)) {
                throw unresolvedInputHandler.apply(substituted);
            }
            return substituted;
        } else {
            return input;
        }
    }

    private static String substitute(final String input, final Pattern pattern,
            final String groupName,
            final Function<String, Optional<String>> replacerFunction) {
        final Matcher matcher = pattern.matcher(input);
        // replace with StringBuilder with JDK9
        final AtomicReference<StringBuffer> bufferReference = new AtomicReference<>();
        while (matcher.find()) {
            final String placeholder = matcher.group(groupName).trim();
            replacerFunction.apply(placeholder)
                    .map(Matcher::quoteReplacement)
                    .ifPresent(replacement -> matcher.appendReplacement(lazyGet(bufferReference, StringBuffer::new),
                            replacement));
        }

        if (bufferReference.get() == null) { // no match -> return original string
            return input;
        } else { // there was at least one match -> append tail of original string
            matcher.appendTail(bufferReference.get());
            return bufferReference.get().toString();
        }
    }

    /**
     * Lazily initializes the given reference using the given initializer.
     *
     * @param reference the reference to the actual instance of type T
     * @param initializer a supplier that initializes a new instance of T
     * @param <T> the type of the instance
     * @return the instance of type T, that is initialized on first access
     */
    private static <T> T lazyGet(final AtomicReference<T> reference, final Supplier<T> initializer) {
        T result = reference.get();
        if (result == null) {
            result = initializer.get();
            if (!reference.compareAndSet(null, result)) {
                return reference.get();
            }
        }
        return result;
    }
}
