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

import java.net.URI;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTagKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TraceInformationGenerator generates a {@link TraceInformation} based on a specified path.
 * <p>
 * The purpose of this class is to minimize the amount of requests to be logged (e.g. by shortening requests),
 * in order to avoid the creation of too many Kamon traces (causing OutOfMemory).
 */
@Immutable
public final class TraceInformationGenerator implements Function<String, TraceInformation> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceInformationGenerator.class);

    static final String SHORTENED_PATH_SUFFIX = "/x";
    static final URI FALLBACK_URI = URI.create("/other");

    private static final int FIRST_API_VERSION = JsonSchemaVersion.V_2.toInt();
    private static final int LATEST_API_VERSION = JsonSchemaVersion.LATEST.toInt();
    private static final String API_VERSION_GROUP = "apiVersion";
    private static final String API_VERSIONS =
            "(?<" + API_VERSION_GROUP + ">[" + FIRST_API_VERSION + "-" + LATEST_API_VERSION + "])";
    private static final Set<String> SUB_PATHS_TO_SHORTEN = Set.of("things", "policies", "search/things");
    private static final String PATHS_TO_SHORTEN_GROUP = "shorten";
    private static final String PATHS_TO_SHORTEN_REGEX_TEMPLATE = "(?<" + PATHS_TO_SHORTEN_GROUP + ">^/(api)/" +
            API_VERSIONS +
            "/(?<entityType>{0}))(/(?<entityId>$|.*?))?(/(?<subEntityType>$|.*?))?(/(?<subEntityId>$|.*?))?($|/.*)";
    private static final String PATHS_EXACT_LENGTH_GROUP = "exact";
    private static final Set<String> PATHS_EXACT = Set.of(
            "ws/2",
            "health",
            "status",
            "status/health",
            "overall/status/health",
            "devops/logging",
            "devops/config"
    );
    private static final String PATHS_EXACT_REGEX_TEMPLATE = "(?<" + PATHS_EXACT_LENGTH_GROUP + ">^/({0}))/?$";
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("(.*/(inbox|outbox)/messages/.*)|(.*/inbox/claim)");
    private static final String MESSAGES_PATH_SUFFIX = "/messages";

    @Nullable private static TraceInformationGenerator instance = null;

    private final Pattern pathPattern;

    private TraceInformationGenerator(final Pattern pathPattern) {
        this.pathPattern = pathPattern;
    }

    /**
     * Returns an instance of {@code TraceInformationGenerator}.
     *
     * @return the instance.
     */
    public static TraceInformationGenerator getInstance() {
        var result = instance;
        if (null == result) {
            final var subPathsToShortenOrRegex = createOrRegex(SUB_PATHS_TO_SHORTEN, true);
            final var pathsToShortenRegex =
                    MessageFormat.format(PATHS_TO_SHORTEN_REGEX_TEMPLATE, subPathsToShortenOrRegex);

            final var pathsExactRegex =
                    MessageFormat.format(PATHS_EXACT_REGEX_TEMPLATE, createOrRegex(PATHS_EXACT, true));

            final var fullRegex = createOrRegex(
                    List.of(createRegexGroup(pathsToShortenRegex), createRegexGroup(pathsExactRegex)),
                    false
            );
            final var pattern = Pattern.compile(fullRegex);
            instance = new TraceInformationGenerator(pattern);
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
     * Generates a {@code TraceInformation} based on the given path String argument.
     *
     * @param path the path.
     * @return the TraceInformation.
     * @throws NullPointerException if {@code path} is {@code null}.
     * @throws IllegalArgumentException if for {@code path} no TraceInformation can be determined.
     */
    @Override
    public TraceInformation apply(final String path) {
        return extractTraceInformation(ConditionChecker.checkNotNull(path, "path"));
    }

    private TraceInformation extractTraceInformation(final String path) {
        final TraceInformation result;
        final var normalizedPath = TraceUtils.normalizePath(path);
        final var matcher = pathPattern.matcher(normalizedPath);
        if (matcher.matches()) {
            final URI traceUri;
            final URI sanitizedUri;
            final var pathToShorten = matcher.group(PATHS_TO_SHORTEN_GROUP);
            if (null != pathToShorten) {
                final var messageMatcher = MESSAGE_PATTERN.matcher(normalizedPath);
                if (messageMatcher.matches()) {
                    traceUri = URI.create(pathToShorten + MESSAGES_PATH_SUFFIX);
                    sanitizedUri = traceUri;
                } else {
                    traceUri = URI.create(pathToShorten + SHORTENED_PATH_SUFFIX);
                    sanitizedUri = getMatcherValue("subEntityType", matcher)
                            .map(s -> URI.create(traceUri + "/" + s + SHORTENED_PATH_SUFFIX))
                            .orElse(traceUri);
                }
            } else {
                final var pathFullLength = matcher.group(PATHS_EXACT_LENGTH_GROUP);
                if (null != pathFullLength) {
                    traceUri = URI.create(pathFullLength);
                    sanitizedUri = traceUri;
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
            result = TraceInformation.newInstance(
                    traceUri,
                    TagSet.ofTag(SpanTagKey.REQUEST_URI.getTagForValue(sanitizedUri))
            );
            LOGGER.debug("Generated trace information for <{}>: <{}>", path, result);
        } else {

            // return fallback trace information
            result = TraceInformation.newInstance(
                    FALLBACK_URI,
                    TagSet.ofTag(SpanTagKey.REQUEST_URI.getTagForValue(FALLBACK_URI))
            );
            LOGGER.debug("Returning fall-back trace information for <{}>: <{}>", path, result);
        }
        return result;
    }

    private static Optional<String> getMatcherValue(final String matchingGroup, final Matcher matcher) {
        return tryGetCapturingGroup(matcher, matchingGroup).filter(g -> !g.isEmpty());
    }

    private static Optional<String> tryGetCapturingGroup(final Matcher matcher, final String group) {
        try {
            return Optional.ofNullable(matcher.group(group));
        } catch (final IllegalArgumentException exception) {
            LOGGER.debug("Getting capturing group <{}> for pattern <{}> failed. This is expected to happen sometimes.",
                    group, matcher.pattern());
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (TraceInformationGenerator) o;
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
