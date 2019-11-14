/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Utility class for matching {@link Payload} path.
 * TODO test
 */
final class PathMatcher {

    private final Map<String, Pattern> patterns;

    PathMatcher(final Map<String, Pattern> patterns) {
        this.patterns = patterns;
    }

    /**
     * Matches a given {@code path} against known schemes and returns the corresponding entity name.
     *
     * @param path the path to match.
     * @return the entity name which matched.
     * @throws UnknownPathException if {@code path} matched no known scheme.
     */
    String match(final JsonPointer path) {
        final Predicate<Map.Entry<String, Pattern>> pathMatchesPattern = entry -> {
            final Pattern pattern = entry.getValue();
            final Matcher matcher = pattern.matcher(path);
            return matcher.matches();
        };

        return patterns.entrySet()
                .stream()
                .filter(pathMatchesPattern)
                .findFirst()
                .map(Map.Entry::getKey)
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }
}
