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
package org.eclipse.ditto.things.service.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;

/**
 * The READ restrictions of the subjects of a policy for one Thing JSON structure, as carried by the
 * {@code ditto-partial-access-paths} and {@code ditto-revoked-access-paths} headers of a signal.
 *
 * @param partialAccessPaths the value of the {@code ditto-partial-access-paths} header, {@code null} for none.
 * @param revokedAccessPaths the value of the {@code ditto-revoked-access-paths} header, {@code null} for none.
 * @param subjectsWithoutReadableField the subjects with partial READ access which can read nothing of the Thing JSON;
 * they must not receive the signal, as a READ-granted subject missing from the partial header counts as unrestricted.
 * @since 3.9.9
 */
public record AccessRestrictions(@Nullable JsonObject partialAccessPaths,
                                 @Nullable JsonObject revokedAccessPaths,
                                 Set<String> subjectsWithoutReadableField) {

    /**
     * No READ restriction.
     */
    public static final AccessRestrictions NONE = new AccessRestrictions(null, null, Set.of());

    /**
     * Calculates the READ restrictions for a Thing JSON.
     *
     * @param policyEnforcer the policy enforcer, filtered for the Thing's namespace.
     * @param thingJson the Thing JSON, which may also be only a part of a Thing, e.g. the payload of a live signal.
     * @param withPartialAccessPaths whether to calculate the paths accessible for subjects with partial READ access.
     * @param collapseToAncestors whether fully accessible/revoked objects of {@code thingJson} are collapsed to their
     * path, which must not be done if the paths are also applied to JSON other than {@code thingJson}.
     * @return the restrictions.
     */
    public static AccessRestrictions calculate(final PolicyEnforcer policyEnforcer, final JsonObject thingJson,
            final boolean withPartialAccessPaths, final boolean collapseToAncestors) {

        JsonObject partialAccessPaths = null;
        Set<String> subjectsWithoutReadableField = Set.of();
        if (withPartialAccessPaths) {
            final Set<AuthorizationSubject> restricted =
                    PartialAccessPathCalculator.getSubjectsWithRestrictedAccess(policyEnforcer);
            if (!restricted.isEmpty()) {
                final Map<String, List<JsonPointer>> paths = PartialAccessPathCalculator.calculatePartialAccessPaths(
                        policyEnforcer, thingJson, collapseToAncestors);
                if (!paths.isEmpty()) {
                    partialAccessPaths = PartialAccessPathCalculator.toIndexedJsonObject(paths);
                }
                subjectsWithoutReadableField = restricted.stream()
                        .map(AuthorizationSubject::getId)
                        .filter(subjectId -> !paths.containsKey(subjectId))
                        .collect(Collectors.toUnmodifiableSet());
            }
        }

        JsonObject revokedAccessPaths = null;
        if (!policyEnforcer.getReadRevokedThingResourcePathsBySubject().isEmpty()) {
            final Map<String, List<JsonPointer>> paths =
                    RevokedAccessPathCalculator.calculateRevokedAccessPaths(thingJson, policyEnforcer,
                            collapseToAncestors);
            final Set<String> rootRevokedSubjects = RevokedAccessPathCalculator.getRootRevokedSubjects(policyEnforcer);
            if (!paths.isEmpty() || !rootRevokedSubjects.isEmpty()) {
                revokedAccessPaths = RevokedAccessPathCalculator.toHeaderValue(paths, rootRevokedSubjects);
            }
        }

        if (partialAccessPaths == null && revokedAccessPaths == null && subjectsWithoutReadableField.isEmpty()) {
            return NONE;
        }
        return new AccessRestrictions(partialAccessPaths, revokedAccessPaths, subjectsWithoutReadableField);
    }

    /**
     * Checks cheaply, without looking at any Thing JSON, whether {@link #calculate} can find any restriction.
     *
     * @param policyEnforcer the policy enforcer, filtered for the Thing's namespace.
     * @param withPartialAccessPaths whether partial READ access is to be considered.
     * @return {@code false} if READ is restricted for nobody.
     */
    public static boolean mayBeRestricted(final PolicyEnforcer policyEnforcer, final boolean withPartialAccessPaths) {
        return !policyEnforcer.getReadRevokedThingResourcePathsBySubject().isEmpty() ||
                withPartialAccessPaths && PartialAccessPathCalculator.hasSubjectsWithRestrictedAccess(policyEnforcer);
    }

    /**
     * Adds the restrictions to the headers of a signal and removes the subjects which can read nothing from its
     * {@code ditto-read-subjects}.
     *
     * @param signal the signal.
     * @param <T> the type of the signal.
     * @return the signal with the restrictions, or {@code signal} itself if there are none.
     */
    public <T extends DittoHeadersSettable<? extends T>> T applyTo(final T signal) {
        if (partialAccessPaths == null && revokedAccessPaths == null && subjectsWithoutReadableField.isEmpty()) {
            return signal;
        }
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final DittoHeadersBuilder<?, ?> builder = dittoHeaders.toBuilder();
        if (partialAccessPaths != null) {
            builder.putJsonHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessPaths);
        }
        if (revokedAccessPaths != null) {
            builder.putJsonHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(), revokedAccessPaths);
        }
        if (!subjectsWithoutReadableField.isEmpty()) {
            builder.readGrantedSubjects(dittoHeaders.getReadGrantedSubjects().stream()
                    .filter(subject -> !subjectsWithoutReadableField.contains(subject.getId()))
                    .toList());
        }
        return signal.setDittoHeaders(builder.build());
    }

}
