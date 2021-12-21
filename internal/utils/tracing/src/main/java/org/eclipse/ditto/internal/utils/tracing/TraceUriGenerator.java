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
package org.eclipse.ditto.internal.utils.tracing;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TraceUriGenerator generates a trace uri based on a specified path.
 * <p>
 * The purpose of this class is to minimize the amount of requests to be logged (e.g. by shortening requests),
 * in order to avoid the creation of too many Kamon traces (causing OutOfMemory).
 */
@Immutable
public final class TraceUriGenerator implements Function<String, TraceInformation> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceUriGenerator.class);

    static final String SHORTENED_PATH_SUFFIX = "/x";
    static final String MESSAGES_PATH_SUFFIX = "/messages";
    static final String FALLBACK_PATH = "/other";


    private static final int FIRST_API_VERSION = JsonSchemaVersion.V_2.toInt();
    private static final int LATEST_API_VERSION = JsonSchemaVersion.LATEST.toInt();
    private static final String API_VERSION_GROUP = "apiVersion";
    private static final String API_VERSIONS =
            "(?<" + API_VERSION_GROUP + ">[" + FIRST_API_VERSION + "-" + LATEST_API_VERSION + "])";
    private static final List<String> SUB_PATHS_TO_SHORTEN =
            Arrays.asList("things", "policies", "search/things");
    private static final String PATHS_TO_SHORTEN_GROUP = "shorten";
    private static final String PATHS_TO_SHORTEN_REGEX_TEMPLATE = "(?<" + PATHS_TO_SHORTEN_GROUP + ">^/(api)/" +
            API_VERSIONS +
            "/(?<entityType>{0}))(/(?<entityId>$|.*?))?(/(?<subEntityType>$|.*?))?(/(?<subEntityId>$|.*?))?($|/.*)";
    private static final String PATHS_EXACT_LENGTH_GROUP = "exact";
    private static final List<String> PATHS_EXACT = Arrays.asList("ws/2", "health", "status", "status/health",
            "overall/status/health", "devops/logging", "devops/config");
    private static final String PATHS_EXACT_REGEX_TEMPLATE = "(?<" + PATHS_EXACT_LENGTH_GROUP + ">^/({0}))/?$";
    private static final Pattern messagePattern = Pattern.compile("(.*/(inbox|outbox)/messages/.*)|(.*/inbox/claim)");

    @Nullable private static TraceUriGenerator instance = null;

    private final Pattern pathPattern;

    private TraceUriGenerator(final Pattern pathPattern) {
        this.pathPattern = pathPattern;
    }

    /**
     * Returns an instance of {@code TraceUriGenerator}.
     *
     * @return the instance.
     */
    public static TraceUriGenerator getInstance() {
        final TraceUriGenerator result;

        if (null != instance) {
            result = instance;
        } else {
            final String subPathsToShortenOrRegex = createOrRegex(SUB_PATHS_TO_SHORTEN, true);
            final String pathsToShortenRegex =
                    MessageFormat.format(PATHS_TO_SHORTEN_REGEX_TEMPLATE, subPathsToShortenOrRegex);

            final String pathsExactOrRegex = createOrRegex(PATHS_EXACT, true);
            final String pathsExactRegex = MessageFormat.format(PATHS_EXACT_REGEX_TEMPLATE, pathsExactOrRegex);

            final String fullRegex = createOrRegex(
                    Arrays.asList(createRegexGroup(pathsToShortenRegex), createRegexGroup(pathsExactRegex)), false);
            final Pattern pattern = Pattern.compile(fullRegex);
            instance = new TraceUriGenerator(pattern);
            result = instance;
        }

        return result;
    }

    private static String createOrRegex(final Collection<String> expressions, final boolean quote) {
        return expressions.stream()
                .map(expr -> quote ? Pattern.quote(expr) : expr)
                .collect(Collectors.joining("|"));
    }

    private static String createRegexGroup(final String expression) {
        return "(" + expression + ")";
    }

    /**
     * Generates a trace URI based on the given {@code path}.
     *
     * @param path the path.
     * @return the trace URI.
     * @throws NullPointerException if {@code path} is {@code null}.
     */
    @Override
    public TraceInformation apply(final String path) {
        requireNonNull(path, "The path must not be null!");

        return extractTraceInformation(path);
    }

    private TraceInformation extractTraceInformation(final String path) {
        final String normalizedPath = TraceUtils.normalizePath(path);
        final Matcher messageMatcher = messagePattern.matcher(normalizedPath);
        final Matcher matcher = pathPattern.matcher(normalizedPath);

        final Map<String, String> tags = new HashMap<>();
        if (matcher.matches()) {
            final String traceUri;
            final String sanitizedPath;
            final String pathToShorten = matcher.group(PATHS_TO_SHORTEN_GROUP);
            if (pathToShorten != null) {
                final Optional<String> subEntityType = getMatcherValue("subEntityType", matcher);
                if (messageMatcher.matches()) {
                    traceUri = pathToShorten + MESSAGES_PATH_SUFFIX;
                    sanitizedPath = traceUri;
                } else {
                    traceUri = pathToShorten + SHORTENED_PATH_SUFFIX;
                    sanitizedPath = subEntityType.map(s -> traceUri + "/" + s + SHORTENED_PATH_SUFFIX).orElse(traceUri);
                }
            } else {
                final String pathFullLength = matcher.group(PATHS_EXACT_LENGTH_GROUP);
                if (pathFullLength != null) {
                    traceUri = pathFullLength;
                    sanitizedPath = traceUri;
                } else {
                    // This branch is impossible:
                    // - (pathsToShortenRegex | pathsExactRegex) matches,
                    // - pathsToShortenRegex does not match, and
                    // - pathsExactRegex does not match.
                    //
                    // Entering this branch implies a bug in the regex.
                    throw new IllegalStateException();
                }
            }
            tags.put(TracingTags.REQUEST_PATH, sanitizedPath);
            final TraceInformation info = new TraceInformation(traceUri, tags);
            LOGGER.debug("Generated traceUri for '{}': '{}'", path, info);
            return info;
        } else {
            // return fallback trace URI
            tags.put(TracingTags.REQUEST_PATH, FALLBACK_PATH);
            final TraceInformation fallback = new TraceInformation(FALLBACK_PATH, tags);
            LOGGER.debug("Returning fallback traceUri for '{}': '{}'", path, fallback);
            return fallback;
        }
    }

    private Optional<String> getMatcherValue(final String matchingGroup, final Matcher matcher) {
        return tryGetCapturingGroup(matcher, matchingGroup)
                .filter(Objects::nonNull)
                .filter(g -> !g.isEmpty());
    }

    private Optional<String> tryGetCapturingGroup(final Matcher matcher, final String group) {
        try {
            return Optional.ofNullable(matcher.group(group));
        } catch (final IllegalArgumentException exception) {
            LOGGER.debug("Getting capturing group <{}> for pattern <{}> failed. This is expected to happen sometimes.",
                    group, matcher.pattern());
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceUriGenerator that = (TraceUriGenerator) o;
        return Objects.equals(pathPattern, that.pathPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathPattern);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "pathPattern=" + pathPattern +
                "]";
    }

}
