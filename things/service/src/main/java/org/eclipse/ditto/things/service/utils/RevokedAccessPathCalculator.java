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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;

/**
 * Calculates, per subject, the Thing paths whose READ permission is revoked for that subject.
 * <p>
 * Event subscribers are authorized per subject, so a subscriber whose authorization context contains a subject
 * granted READ and another subject with READ revoked would otherwise receive the revoked paths. The subscriber side
 * removes every path revoked for any of its subjects.
 * </p>
 *
 * @since 3.9.9
 */
public final class RevokedAccessPathCalculator {

    private static final Permissions READ_PERMISSIONS = Permissions.newInstance(Permission.READ);

    private RevokedAccessPathCalculator() {
        throw new AssertionError();
    }

    /**
     * Calculates the Thing paths whose READ permission is revoked for each subject, i.e. where a revoke of the subject
     * outweighs its grants.
     *
     * @param thingJson the Thing JSON, which may also be only a part of a Thing, e.g. the payload of a live event.
     * @param policyEnforcer the policy enforcer.
     * @return map of subject ID to the revoked paths, collapsed to the topmost fully revoked object; empty if READ is
     * revoked nowhere in the Thing.
     */
    public static Map<String, List<JsonPointer>> calculateRevokedAccessPaths(final JsonObject thingJson,
            final PolicyEnforcer policyEnforcer) {

        final Set<JsonPointer> readRevokedResourcePaths = policyEnforcer.getReadRevokedThingResourcePaths();
        if (readRevokedResourcePaths.isEmpty()) {
            return Map.of();
        }
        final Set<JsonPointer> candidateLeaves = new LinkedHashSet<>();
        for (final JsonPointer revokedResourcePath : readRevokedResourcePaths) {
            thingJson.getValue(revokedResourcePath)
                    .ifPresent(value -> collectLeaves(revokedResourcePath, value, candidateLeaves));
        }

        final Enforcer enforcer = policyEnforcer.getEnforcer();
        final Map<AuthorizationSubject, Set<JsonPointer>> revokedLeavesPerSubject = new LinkedHashMap<>();
        for (final JsonPointer leaf : candidateLeaves) {
            for (final AuthorizationSubject subject : enforcer.getSubjectsWithPermission(
                    PoliciesResourceType.thingResource(leaf), READ_PERMISSIONS).getRevoked()) {
                revokedLeavesPerSubject.computeIfAbsent(subject, s -> new LinkedHashSet<>()).add(leaf);
            }
        }

        final Map<String, List<JsonPointer>> result = new LinkedHashMap<>();
        revokedLeavesPerSubject.forEach((subject, revokedLeaves) -> result.put(subject.getId(),
                new ArrayList<>(PartialAccessPathCalculator.collapseLeavesToAncestors(revokedLeaves, thingJson))));
        return result;
    }

    private static void collectLeaves(final JsonPointer path, final JsonValue value, final Set<JsonPointer> leaves) {
        if (value.isObject() && !value.isNull() && !value.asObject().isEmpty()) {
            for (final JsonField field : value.asObject()) {
                collectLeaves(path.addLeaf(field.getKey()), field.getValue(), leaves);
            }
        } else if (!path.isEmpty()) {
            leaves.add(path);
        }
    }

}
