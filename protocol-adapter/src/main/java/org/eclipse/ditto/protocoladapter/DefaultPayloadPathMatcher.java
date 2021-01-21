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

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Utility class for matching {@link Payload} path.
 *
 * @deprecated as of 1.6.0 please use an implementation of
 * {@link org.eclipse.ditto.signals.commands.common.AbstractPathMatcher} instead.
 */
@Deprecated
public final class DefaultPayloadPathMatcher implements PayloadPathMatcher {

    private final Map<String, Pattern> patterns;

    private DefaultPayloadPathMatcher(final Map<String, Pattern> patterns) {
        this.patterns = patterns;
    }

    /**
     * @param patterns the patterns supported by this path matcher instance
     * @return new path matcher instance created from the given map of patterns
     */
    public static PayloadPathMatcher from(final Map<String, Pattern> patterns) {
        return new DefaultPayloadPathMatcher(patterns);
    }

    /**
     * Creates a {@link PayloadPathMatcher} instance that does not match any pattern.
     *
     * @return empty path matcher instance
     */
    public static PayloadPathMatcher empty() {
        return new DefaultPayloadPathMatcher(Collections.emptyMap());
    }

    /**
     * Matches a given {@code path} against known schemes and returns the corresponding entity name.
     *
     * @param path the path to match.
     * @return the entity name which matched.
     * @throws UnknownPathException if {@code path} matched no known scheme.
     */
    @Override
    public String match(final JsonPointer path) {
        return patterns.entrySet()
                .stream()
                .filter(patternEntry -> pathMatchesPattern(patternEntry, path))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }

    private boolean pathMatchesPattern(final Map.Entry<String, Pattern> patternEntry, final JsonPointer path) {
        final Pattern pattern = patternEntry.getValue();
        final Matcher matcher = pattern.matcher(path);
        return matcher.matches();
    }
}
