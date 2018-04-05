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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TraceUriGenerator generates a trace uri based on a specified path.
 * <p>
 * The purpose of this class is to minimize the amount of requests to be logged (e. g. by shortening requests),
 * in order to avoid the creation of too many Kamon traces (causing OutOfMemory).
 */
@Immutable
final class TraceUriGenerator implements Function<String, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceUriGenerator.class);

    static final String SHORTENED_PATH_SUFFIX = "/x";
    static final String MESSAGES_PATH_SUFFIX = "/messages";
    static final String FALLBACK_PATH = "/other";

    private static final String SLASH = "/";

    private static final int FIRST_API_VERSION = JsonSchemaVersion.V_1.toInt();
    private static final int LATEST_API_VERSION = JsonSchemaVersion.LATEST.toInt();
    private static final String API_VERSIONS = "[" + FIRST_API_VERSION + "-" + LATEST_API_VERSION + "]";
    private static final List<String> SUB_PATHS_TO_SHORTEN =
            Arrays.asList("things", "policies", "search/things");
    private static final String PATHS_TO_SHORTEN_GROUP = "shorten";
    private static final String PATHS_TO_SHORTEN_REGEX_TEMPLATE = "(?<" + PATHS_TO_SHORTEN_GROUP + ">^/(api)/" +
            API_VERSIONS + "/({0}))($|/.*)";
    private static final String PATHS_EXACT_LENGTH_GROUP = "exact";
    private static final List<String> PATHS_EXACT = Arrays.asList("status", "status/health");
    private static final String PATHS_EXACT_REGEX_TEMPLATE = "(?<" + PATHS_EXACT_LENGTH_GROUP + ">^/({0}))/?$";
    private static final Pattern DUPLICATE_SLASH_PATTERN = Pattern.compile("\\/+");
    private static final Pattern messagePattern = Pattern.compile("(.*/messages/.*)|(.*/claim)");

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
    static TraceUriGenerator getInstance() {
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
    public String apply(final String path) {
        requireNonNull(path, "The path must not be null!");

        return generateTraceUri(path);
    }

    private String generateTraceUri(final String path) {
        final String normalizedPath = normalizePath(path);
        final Matcher messageMatcher = messagePattern.matcher(normalizedPath);
        final Matcher matcher = pathPattern.matcher(normalizedPath);

        if (matcher.matches()) {
            final String traceUri;

            final String pathToShorten = matcher.group(PATHS_TO_SHORTEN_GROUP);
            if (pathToShorten != null) {
                if (messageMatcher.matches()) {
                    traceUri = pathToShorten + MESSAGES_PATH_SUFFIX;
                } else {
                    traceUri = pathToShorten + SHORTENED_PATH_SUFFIX;
                }
            } else {
                final String pathFullLength = matcher.group(PATHS_EXACT_LENGTH_GROUP);
                if (pathFullLength != null) {
                    traceUri = pathFullLength;
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

            LOGGER.debug("Generated traceUri for '{}': '{}'", path, traceUri);
            return traceUri;
        } else {
            // return fallback trace URI
            LOGGER.debug("Returning fallback traceUri for '{}': '{}'", path, FALLBACK_PATH);
            return FALLBACK_PATH;
        }
    }

    private static String normalizePath(final String path) {
        if (path.isEmpty()) {
            return SLASH;
        }

        // remove duplicate slashes
        String normalized = DUPLICATE_SLASH_PATTERN.matcher(path).replaceAll(SLASH);

        // strip trailing slash if necessary
        if (normalized.length() > 1 && normalized.endsWith(SLASH)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // add leading slash if necessary
        if (!normalized.startsWith(SLASH)) {
            normalized = SLASH + normalized;
        }

        return normalized;
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
