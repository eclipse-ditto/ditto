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
package org.eclipse.ditto.gateway.service.security.authorization;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.LikeHelper;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Matches namespaces against allowed and blocked patterns using SQL-LIKE wildcard syntax
 * ({@code *} for any characters, {@code ?} for single character).
 * Blocked patterns take precedence over allowed patterns.
 * An empty allowed list means all namespaces are allowed (unless blocked).
 */
@Immutable
@AllValuesAreNonnullByDefault
final class NamespacePatternMatcher {

    private final List<Pattern> allowedPatterns;
    private final List<Pattern> blockedPatterns;

    NamespacePatternMatcher(final List<String> allowedNamespaces, final List<String> blockedNamespaces) {
        this.allowedPatterns = compilePatterns(allowedNamespaces);
        this.blockedPatterns = compilePatterns(blockedNamespaces);
    }

    private static List<Pattern> compilePatterns(final List<String> patterns) {
        return patterns.stream()
                .map(LikeHelper::convertToRegexSyntax)
                .filter(Objects::nonNull)
                .map(Pattern::compile)
                .collect(Collectors.toList());
    }

    boolean isBlocked(final String namespace) {
        return matchesAnyPattern(namespace, blockedPatterns);
    }

    boolean isAllowed(final String namespace) {
        return !isBlocked(namespace) && isAllowedByRule(namespace);
    }

    boolean isAllowedByRule(final String namespace) {
        if (allowedPatterns.isEmpty()) {
            return true;
        }
        return matchesAnyPattern(namespace, allowedPatterns);
    }

    private static boolean matchesAnyPattern(final String namespace, final List<Pattern> patterns) {
        return patterns.stream()
                .anyMatch(pattern -> pattern.matcher(namespace).matches());
    }

}
