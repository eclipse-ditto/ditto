/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.protocol;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Utility class for resolving accessible paths for a subscriber based on partial access paths header.
 * Contains common logic used by both Adaptable and JsonObject filtering.
 */
public final class PartialAccessPathResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartialAccessPathResolver.class);

    // TODO check if needed to be configurable
    private static final int MAX_CACHE_SIZE = 5000;
    private static final Duration CACHE_EXPIRE_AFTER_ACCESS = Duration.ofMinutes(15);
    private static final Duration CACHE_EXPIRE_AFTER_WRITE = Duration.ofMinutes(15);
    private static final com.github.benmanes.caffeine.cache.Cache<String, IndexedPartialAccessPaths> PARSED_HEADER_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(MAX_CACHE_SIZE)
                    .expireAfterWrite(CACHE_EXPIRE_AFTER_WRITE)
                    .expireAfterAccess(CACHE_EXPIRE_AFTER_ACCESS)
                    .build();

    private PartialAccessPathResolver() {
        // No instantiation
    }

    /**
     * Result of resolving accessible paths for a subscriber.
     */
    public static final class AccessiblePathsResult {
        private final Set<JsonPointer> accessiblePaths;
        private final boolean hasUnrestrictedAccess;
        private final boolean shouldFilter;

        private AccessiblePathsResult(final Set<JsonPointer> accessiblePaths,
                final boolean hasUnrestrictedAccess,
                final boolean shouldFilter) {
            this.accessiblePaths = accessiblePaths;
            this.hasUnrestrictedAccess = hasUnrestrictedAccess;
            this.shouldFilter = shouldFilter;
        }

        /**
         * @return the set of accessible JsonPointer paths for the subscriber
         */
        public Set<JsonPointer> getAccessiblePaths() {
            return accessiblePaths;
        }

        /**
         * @return true if the subscriber has unrestricted access (no filtering needed)
         */
        public boolean hasUnrestrictedAccess() {
            return hasUnrestrictedAccess;
        }

        /**
         * @return true if filtering should be applied, false if subscriber has no access
         */
        public boolean shouldFilter() {
            return shouldFilter;
        }

        static AccessiblePathsResult unrestricted() {
            return new AccessiblePathsResult(Set.of(), true, false);
        }

        static AccessiblePathsResult noAccess() {
            return new AccessiblePathsResult(Set.of(), false, false);
        }

        static AccessiblePathsResult filtered(final Set<JsonPointer> accessiblePaths) {
            return new AccessiblePathsResult(accessiblePaths, false, true);
        }
    }

    /**
     * Parses and caches the partial access paths from the header.
     *
     * @param partialAccessPathsHeader the header value containing partial access paths JSON
     * @return IndexedPartialAccessPaths model, or EMPTY if header is null/empty
     */
    public static IndexedPartialAccessPaths parseAndCachePartialAccessPaths(
            @Nullable final String partialAccessPathsHeader) {

        if (partialAccessPathsHeader == null || partialAccessPathsHeader.isEmpty()) {
            return IndexedPartialAccessPaths.EMPTY;
        }

        return PARSED_HEADER_CACHE.get(
                partialAccessPathsHeader,
                header -> {
                    try {
                        final JsonObject partialAccessPathsJson = JsonFactory.readFrom(header).asObject();
                        return JsonPartialAccessFilter.parseIndexedPartialAccessPaths(partialAccessPathsJson);
                    } catch (final JsonParseException e) {
                        LOGGER.warn("Failed to parse partial access paths header as JSON: {}", e.getMessage(), e);
                        return IndexedPartialAccessPaths.EMPTY;
                    }
                });
    }

    /**
     * Parses and caches the partial access paths from the header, returning as a map.
     * This is a convenience method for backwards compatibility.
     *
     * @param partialAccessPathsHeader the header value containing partial access paths JSON
     * @return map of subject IDs to their accessible paths, or empty map if header is null/empty
     */
    public static Map<String, List<JsonPointer>> parseAndCachePartialAccessPathsAsMap(
            @Nullable final String partialAccessPathsHeader) {
        final IndexedPartialAccessPaths indexed = parseAndCachePartialAccessPaths(partialAccessPathsHeader);
        return JsonPartialAccessFilter.expandIndexed(indexed);
    }

    /**
     * Resolves accessible paths for a subscriber based on partial access paths header and authorization context.
     * This is the core logic shared by both Adaptable and JsonObject filtering.
     *
     * @param partialAccessPaths the parsed partial access paths map (subject ID -> paths)
     * @param subscriberAuthContext the authorization context of the subscriber (may be null)
     * @param readGrantedSubjects the set of subjects with read granted permissions from headers
     * @return result containing accessible paths and filtering decision
     */
    public static AccessiblePathsResult resolveAccessiblePaths(
            final Map<String, List<JsonPointer>> partialAccessPaths,
            @Nullable final AuthorizationContext subscriberAuthContext,
            final Set<AuthorizationSubject> readGrantedSubjects) {

        if (partialAccessPaths.isEmpty()) {
            return AccessiblePathsResult.unrestricted();
        }

        final Set<String> subscriberSubjectIds = new LinkedHashSet<>();
        final Set<String> allSubscriberSubjectIds = new LinkedHashSet<>();

        if (subscriberAuthContext != null && !subscriberAuthContext.getAuthorizationSubjects().isEmpty()) {
            subscriberAuthContext.getAuthorizationSubjects().forEach(subject -> {
                final String subjectId = subject.getId();
                allSubscriberSubjectIds.add(subjectId);
                if (partialAccessPaths.containsKey(subjectId)) {
                    subscriberSubjectIds.add(subjectId);
                }
            });
        } else {
            return AccessiblePathsResult.noAccess();
        }

        final Set<String> readGrantedSubjectIds = new LinkedHashSet<>();
        readGrantedSubjects.forEach(subject -> readGrantedSubjectIds.add(subject.getId()));

        if (subscriberSubjectIds.isEmpty()) {
            // Check if subscriber has unrestricted access (in readGrantedSubjects but not in partialAccessPaths)
            final boolean hasUnrestrictedAccess = allSubscriberSubjectIds.stream()
                    .anyMatch(subjectId ->
                            readGrantedSubjectIds.contains(subjectId) &&
                                    !partialAccessPaths.containsKey(subjectId)
                    );

            if (hasUnrestrictedAccess) {
                return AccessiblePathsResult.unrestricted();
            } else {
                return AccessiblePathsResult.noAccess();
            }
        }

        final Set<JsonPointer> accessiblePaths = new HashSet<>();
        for (final String subjectId : subscriberSubjectIds) {
            final List<JsonPointer> subjectPaths = partialAccessPaths.get(subjectId);
            if (subjectPaths != null && !subjectPaths.isEmpty()) {
                accessiblePaths.addAll(subjectPaths);
            }
        }

        if (accessiblePaths.isEmpty()) {
            return AccessiblePathsResult.noAccess();
        }

        return AccessiblePathsResult.filtered(accessiblePaths);
    }

    /**
     * Convenience method that combines parsing and resolution.
     *
     * @param partialAccessPathsHeader the header value containing partial access paths JSON
     * @param subscriberAuthContext the authorization context of the subscriber (may be null)
     * @param readGrantedSubjects the set of subjects with read granted permissions from headers
     * @return result containing accessible paths and filtering decision
     */
    public static AccessiblePathsResult resolveAccessiblePathsFromHeader(
            @Nullable final String partialAccessPathsHeader,
            @Nullable final AuthorizationContext subscriberAuthContext,
            final Set<AuthorizationSubject> readGrantedSubjects) {

        final Map<String, List<JsonPointer>> partialAccessPaths = 
                parseAndCachePartialAccessPathsAsMap(partialAccessPathsHeader);
        return resolveAccessiblePaths(partialAccessPaths, subscriberAuthContext, readGrantedSubjects);
    }

    /**
     * Convenience method that extracts readGrantedSubjects from DittoHeaders.
     *
     * @param partialAccessPathsHeader the header value containing partial access paths JSON
     * @param subscriberAuthContext the authorization context of the subscriber (may be null)
     * @param dittoHeaders the DittoHeaders containing readGrantedSubjects
     * @return result containing accessible paths and filtering decision
     */
    public static AccessiblePathsResult resolveAccessiblePathsFromHeader(
            @Nullable final String partialAccessPathsHeader,
            @Nullable final AuthorizationContext subscriberAuthContext,
            final DittoHeaders dittoHeaders) {

        return resolveAccessiblePathsFromHeader(
                partialAccessPathsHeader,
                subscriberAuthContext,
                dittoHeaders.getReadGrantedSubjects());
    }
}

