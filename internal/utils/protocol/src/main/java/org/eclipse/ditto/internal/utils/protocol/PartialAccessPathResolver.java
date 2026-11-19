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
package org.eclipse.ditto.internal.utils.protocol;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Utility class for resolving accessible paths for a subscriber based on partial access paths header.
 * Contains common logic used by both Adaptable and JsonObject filtering.
 */
final class PartialAccessPathResolver {

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
         * Gets the set of accessible JsonPointer paths for the subscriber.
         *
         * @return the set of accessible JsonPointer paths for the subscriber
         */
        public Set<JsonPointer> getAccessiblePaths() {
            return accessiblePaths;
        }

        /**
         * Checks if the subscriber has unrestricted access (no filtering needed).
         *
         * @return true if the subscriber has unrestricted access (no filtering needed)
         */
        public boolean hasUnrestrictedAccess() {
            return hasUnrestrictedAccess;
        }

        /**
         * Checks if filtering should be applied.
         *
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
     * Resolves accessible paths for a subscriber based on partial access paths header and authorization context.
     * This is the core logic shared by both Adaptable and JsonObject filtering.
     *
     * @param partialAccessPaths the parsed partial access paths map (subject ID -> paths)
     * @param subscriberAuthContext the authorization context of the subscriber (may be null)
     * @param readGrantedSubjects the set of subjects with read granted permissions from headers
     * @return result containing accessible paths and filtering decision
     */
    static AccessiblePathsResult resolveAccessiblePaths(
            final Map<String, List<JsonPointer>> partialAccessPaths,
            @Nullable final AuthorizationContext subscriberAuthContext,
            final Set<AuthorizationSubject> readGrantedSubjects) {

        if (partialAccessPaths.isEmpty()) {
            return AccessiblePathsResult.unrestricted();
        }

        if (subscriberAuthContext == null ||
                subscriberAuthContext.getAuthorizationSubjects().isEmpty()) {
            return AccessiblePathsResult.unrestricted();
        }

        final Set<String> readGrantedSubjectIds = new LinkedHashSet<>();
        readGrantedSubjects.forEach(subject -> readGrantedSubjectIds.add(subject.getId()));

        final Set<String> subscriberSubjectIds = new LinkedHashSet<>();
        boolean hasUnrestrictedAccess = false;
        for (final AuthorizationSubject subject : subscriberAuthContext.getAuthorizationSubjects()) {
            final String subjectId = subject.getId();
            if (readGrantedSubjectIds.contains(subjectId)) {
                if (partialAccessPaths.containsKey(subjectId)) {
                    subscriberSubjectIds.add(subjectId);
                } else {
                    // Subject has read grant but is NOT in partial access paths → unrestricted
                    hasUnrestrictedAccess = true;
                }
            }
        }

        // If ANY subscriber subject has unrestricted access, return full payload immediately.
        // This must be checked before partial access filtering to avoid incorrectly restricting
        // subscribers that have both partial-access and unrestricted subjects.
        if (hasUnrestrictedAccess) {
            return AccessiblePathsResult.unrestricted();
        }

        if (subscriberSubjectIds.isEmpty()) {
            return AccessiblePathsResult.noAccess();
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
     * Resolves the thing-absolute paths a subscriber must not receive because READ on them is revoked for at least
     * one subject of its authorization context. Grants of the other subjects do not lift such a revoke.
     *
     * @param revokedAccessPaths the parsed revoked access paths (subject ID -> paths whose READ is revoked).
     * @param subscriberAuthContext the authorization context of the subscriber (may be null).
     * @return the union of the revoked paths of all subjects of the subscriber, empty if there are none.
     */
    static Set<JsonPointer> resolveRevokedPaths(final Map<String, List<JsonPointer>> revokedAccessPaths,
            @Nullable final AuthorizationContext subscriberAuthContext) {

        if (revokedAccessPaths.isEmpty() || subscriberAuthContext == null) {
            return Set.of();
        }
        final Set<JsonPointer> revokedPaths = new HashSet<>();
        for (final AuthorizationSubject subject : subscriberAuthContext.getAuthorizationSubjects()) {
            final List<JsonPointer> subjectPaths = revokedAccessPaths.get(subject.getId());
            if (subjectPaths != null) {
                revokedPaths.addAll(subjectPaths);
            }
        }
        return revokedPaths;
    }

}
