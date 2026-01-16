/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Utility class for validating that all prefixed terms (CURIEs) used in a WoT ThingModel or
 * ThingDescription are properly defined in the {@code @context}.
 * <p>
 * A prefixed term (CURIE - Compact URI) has the format {@code prefix:localPart}, for example
 * {@code ditto:category} or {@code ace:ACESecurityScheme}.
 * </p>
 *
 * @since 3.9.0
 */
public final class AtContextPrefixValidator {

    /**
     * Pattern for detecting CURIEs (Compact URIs).
     * Matches strings like "prefix:localPart" where:
     * - prefix starts with a letter and can contain letters, numbers, underscores, or hyphens
     * - localPart starts with a letter (to distinguish from time strings like "12:30")
     * Does NOT match absolute URIs (containing "://").
     */
    private static final Pattern CURIE_PATTERN =
            Pattern.compile("^([a-zA-Z][a-zA-Z0-9_-]*):([a-zA-Z].*)$");

    /**
     * Pattern to detect absolute URIs which should not be treated as CURIEs.
     * This includes http://, https://, and urn: style URIs.
     */
    private static final Pattern ABSOLUTE_URI_PATTERN = Pattern.compile("^(.*://.*|urn:.*)$");

    /**
     * The JSON-LD @context key which should be excluded from prefix scanning.
     */
    private static final String AT_CONTEXT = "@context";

    private AtContextPrefixValidator() {
        throw new AssertionError();
    }

    /**
     * Validates that all prefixed terms (CURIEs) used in the given ThingSkeleton have their
     * prefixes defined in the {@code @context}.
     *
     * @param thingSkeleton the ThingModel or ThingDescription to validate.
     * @throws WotValidationException if undefined prefixes are found.
     * @throws NullPointerException if {@code thingSkeleton} is {@code null}.
     */
    public static void validatePrefixes(final ThingSkeleton<?> thingSkeleton) throws WotValidationException {
        final JsonObject jsonObject = thingSkeleton.toJson();
        final AtContext atContext = thingSkeleton.getAtContext();

        final Set<String> definedPrefixes = extractDefinedPrefixes(atContext);
        final Set<String> usedPrefixes = collectUsedPrefixes(jsonObject);

        final Set<String> undefinedPrefixes = usedPrefixes.stream()
                .filter(prefix -> !definedPrefixes.contains(prefix))
                .filter(prefix -> !WotStandardContextPrefixes.isStandardPrefix(prefix))
                .collect(Collectors.toSet());

        if (!undefinedPrefixes.isEmpty()) {
            throw WotValidationException.newBuilderForUndefinedPrefixes(undefinedPrefixes).build();
        }
    }

    /**
     * Collects all prefixes used in prefixed terms (CURIEs) within the given JSON object.
     * This recursively scans all field keys and string values.
     *
     * @param jsonObject the JSON object to scan.
     * @return a set of prefix strings (without the colon).
     */
    static Set<String> collectUsedPrefixes(final JsonObject jsonObject) {
        final Set<String> prefixes = new HashSet<>();
        collectUsedPrefixesRecursive(jsonObject, prefixes, false);
        return prefixes;
    }

    private static void collectUsedPrefixesRecursive(final JsonValue jsonValue,
            final Set<String> prefixes,
            final boolean isInContext) {
        if (jsonValue.isObject()) {
            final JsonObject obj = jsonValue.asObject();
            obj.forEach(field -> {
                final String key = field.getKeyName();

                // Skip scanning inside @context
                if (AT_CONTEXT.equals(key)) {
                    collectUsedPrefixesRecursive(field.getValue(), prefixes, true);
                    return;
                }

                // Don't extract prefixes from @context definitions
                if (!isInContext) {
                    extractPrefix(key).ifPresent(prefixes::add);
                }

                collectUsedPrefixesRecursive(field.getValue(), prefixes, isInContext);
            });
        } else if (jsonValue.isArray()) {
            final JsonArray arr = jsonValue.asArray();
            arr.forEach(element -> collectUsedPrefixesRecursive(element, prefixes, isInContext));
        } else if (jsonValue.isString() && !isInContext) {
            // Check string values for CURIEs (like "scheme": "ace:ACESecurityScheme")
            final String stringValue = jsonValue.asString();
            if (isCurie(stringValue)) {
                extractPrefix(stringValue).ifPresent(prefixes::add);
            }
        }
    }

    /**
     * Extracts all defined prefixes from the given {@code @context}.
     *
     * @param atContext the AtContext to extract prefixes from.
     * @return a set of defined prefix strings.
     */
    static Set<String> extractDefinedPrefixes(final AtContext atContext) {
        final Set<String> prefixes = new HashSet<>();

        if (atContext instanceof MultipleAtContext) {
            final MultipleAtContext multipleAtContext = (MultipleAtContext) atContext;
            StreamSupport.stream(multipleAtContext.spliterator(), false)
                    .filter(SinglePrefixedAtContext.class::isInstance)
                    .map(SinglePrefixedAtContext.class::cast)
                    .map(SinglePrefixedAtContext::getPrefix)
                    .forEach(prefixes::add);
        }
        // SingleUriAtContext does not define custom prefixes

        return prefixes;
    }

    /**
     * Checks if a string value is a CURIE (Compact URI) and not an absolute URI.
     *
     * @param value the string value to check.
     * @return {@code true} if the value is a CURIE, {@code false} otherwise.
     */
    static boolean isCurie(final String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        // Exclude absolute URIs
        if (ABSOLUTE_URI_PATTERN.matcher(value).matches()) {
            return false;
        }
        return CURIE_PATTERN.matcher(value).matches();
    }

    /**
     * Extracts the prefix from a prefixed term (CURIE).
     *
     * @param prefixedTerm the prefixed term (e.g., "ditto:category").
     * @return an Optional containing the prefix if the term is a valid CURIE, empty otherwise.
     */
    static Optional<String> extractPrefix(final String prefixedTerm) {
        if (prefixedTerm == null || prefixedTerm.isEmpty()) {
            return Optional.empty();
        }
        // Exclude absolute URIs
        if (ABSOLUTE_URI_PATTERN.matcher(prefixedTerm).matches()) {
            return Optional.empty();
        }
        final Matcher matcher = CURIE_PATTERN.matcher(prefixedTerm);
        if (matcher.matches()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }
}
