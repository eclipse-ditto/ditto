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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.RegexPatterns;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;

/**
 * Utility methods for the policy entry namespace pattern syntax.
 * <p>
 * Supported patterns are exact namespaces like {@code com.acme} and prefix patterns like
 * {@code com.acme.*}. An empty list means that a policy entry applies to all namespaces.
 *
 * @since 3.9.0
 */
@Immutable
public final class PolicyEntryNamespaces {

    private PolicyEntryNamespaces() {
        throw new AssertionError();
    }

    /**
     * Parses namespace patterns from a JSON array and validates both element types and pattern syntax.
     *
     * @param jsonArray the JSON array containing namespace patterns.
     * @return the parsed namespace patterns.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     * @throws PolicyEntryInvalidException if the array contains non-string values or invalid patterns.
     */
    public static List<String> fromJsonArray(final JsonArray jsonArray) {
        checkNotNull(jsonArray, "jsonArray");

        final List<String> result = new ArrayList<>(jsonArray.getSize());
        for (final JsonValue element : jsonArray) {
            if (!element.isString()) {
                throw PolicyEntryInvalidException.newBuilder()
                        .description("The 'namespaces' array must only contain string values, " +
                                "but contained: " + element + ".")
                        .build();
            }
            result.add(element.asString());
        }

        validate(result);
        return Collections.unmodifiableList(result);
    }

    /**
     * Validates the policy entry namespace patterns.
     *
     * @param namespaces the namespace patterns to validate.
     * @throws NullPointerException if {@code namespaces} or one of its elements is {@code null}.
     * @throws PolicyEntryInvalidException if any pattern is invalid.
     */
    public static void validate(final Iterable<String> namespaces) {
        checkNotNull(namespaces, "namespaces");
        for (final String namespace : namespaces) {
            validatePattern(namespace);
        }
    }

    /**
     * Returns whether the given policy entry namespace patterns apply to the provided namespace.
     *
     * @param namespacePatterns the namespace patterns restricting the policy entry.
     * @param namespace the concrete namespace to check.
     * @return {@code true} if the patterns match the namespace or if the pattern list is empty.
     * @throws NullPointerException if {@code namespacePatterns} or {@code namespace} is {@code null}.
     */
    public static boolean matches(final Iterable<String> namespacePatterns, final String namespace) {
        checkNotNull(namespacePatterns, "namespacePatterns");
        checkNotNull(namespace, "namespace");

        boolean hasPatterns = false;
        for (final String pattern : namespacePatterns) {
            hasPatterns = true;
            if (matchesPattern(pattern, namespace)) {
                return true;
            }
        }
        return !hasPatterns;
    }

    /**
     * Returns whether a single namespace pattern matches a concrete namespace.
     *
     * @param pattern the policy entry namespace pattern.
     * @param namespace the concrete namespace.
     * @return whether the pattern matches the namespace.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static boolean matchesPattern(final String pattern, final String namespace) {
        checkNotNull(pattern, "pattern");
        checkNotNull(namespace, "namespace");

        if (pattern.endsWith(".*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2);
            return namespace.startsWith(prefix + ".");
        }
        return namespace.equals(pattern);
    }

    private static void validatePattern(final String value) {
        checkNotNull(value, "namespace pattern");
        if (!isValidPattern(value)) {
            throw PolicyEntryInvalidException.newBuilder()
                    .description("The value '" + value + "' is not a valid namespace pattern. " +
                            "Valid patterns are namespaces like 'com.acme' or wildcard patterns like " +
                            "'com.acme.*'.")
                    .build();
        }
    }

    private static boolean isValidPattern(final String value) {
        if (value.isEmpty()) {
            return false;
        }
        if (value.endsWith(".*")) {
            final String namespacePrefix = value.substring(0, value.length() - 2);
            return !namespacePrefix.isEmpty() && RegexPatterns.NAMESPACE_PATTERN.matcher(namespacePrefix).matches();
        }
        return RegexPatterns.NAMESPACE_PATTERN.matcher(value).matches();
    }

}
