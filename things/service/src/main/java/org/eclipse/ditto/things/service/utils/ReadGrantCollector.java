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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.SubjectClassification;
import org.eclipse.ditto.things.model.Thing;

/**
 * Utility class for collecting read grants from a PolicyEnforcer.
 * Determines which subjects can read which paths for a given set of fields.
 */
public final class ReadGrantCollector {

    private ReadGrantCollector() {
        // No instantiation
    }

    /**
     * Collects read grants for the specified fields from the enforcer.
     *
     * @param fields the fields to collect grants for
     * @param thing the Thing entity
     * @param policyEnforcer the PolicyEnforcer to query
     * @return ReadGrant mapping paths to sets of subject IDs
     */
    public static ReadGrant collect(
            final JsonFieldSelector fields,
            final Thing thing,
            final PolicyEnforcer policyEnforcer
    ) {
        return collect(fields, thing.toJson(), policyEnforcer);
    }

    /**
     * Collects read grants for the specified fields from the enforcer, using a pre-computed {@code thingJson}
     * to avoid redundant serialization.
     *
     * @param fields the fields to collect grants for
     * @param thingJson the pre-computed JSON representation of the Thing
     * @param policyEnforcer the PolicyEnforcer to query
     * @return ReadGrant mapping paths to sets of subject IDs
     */
    public static ReadGrant collect(
            final JsonFieldSelector fields,
            final JsonObject thingJson,
            final PolicyEnforcer policyEnforcer
    ) {
        final Enforcer enforcer = policyEnforcer.getEnforcer();
        final Map<JsonPointer, Set<String>> pointerToSubjects = new LinkedHashMap<>();

        for (final JsonPointer pointer : fields.getPointers()) {
            final Map<JsonPointer, Set<String>> pathsForPointer =
                    collectSubjectsForPointer(pointer, thingJson, enforcer);

            for (final Map.Entry<JsonPointer, Set<String>> entry : pathsForPointer.entrySet()) {
                pointerToSubjects.merge(entry.getKey(), entry.getValue(), (existing, newSet) -> {
                    final Set<String> merged = new LinkedHashSet<>(existing);
                    merged.addAll(newSet);
                    return merged;
                });
            }
        }

        return new ReadGrant(pointerToSubjects);
    }

    /**
     * Collects all subject IDs and their accessible nested paths for the specified pointer.
     * Uses {@code classifySubjects()} for a single tree walk and batch path calculation.
     *
     * @param pointer the pointer to check
     * @param thingJson the Thing JSON representation
     * @param enforcer the Enforcer to query
     * @return map of JsonPointer paths to sets of subject IDs
     */
    private static Map<JsonPointer, Set<String>> collectSubjectsForPointer(
            final JsonPointer pointer,
            final JsonObject thingJson,
            final Enforcer enforcer
    ) {
        final ResourceKey resourceKey = PoliciesResourceType.thingResource(pointer);
        final Permissions readPermissions = Permissions.newInstance(Permission.READ);

        final SubjectClassification classification = enforcer.classifySubjects(resourceKey, readPermissions);
        final Set<AuthorizationSubject> subjectsWithUnrestrictedPermission = classification.getUnrestricted();
        final Set<AuthorizationSubject> partialOnly = classification.getPartialOnly();

        final Map<JsonPointer, Set<String>> result = new LinkedHashMap<>();

        final Set<String> unrestrictedIds = subjectsWithUnrestrictedPermission.stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unrestrictedIds.isEmpty()) {
            result.put(pointer, unrestrictedIds);
        }

        if (!partialOnly.isEmpty()) {
            final JsonValue pointerValue = thingJson.getValue(pointer).orElse(null);

            if (pointerValue != null && pointerValue.isObject()) {
                final JsonObject pointerObject = pointerValue.asObject();

                final Map<AuthorizationSubject, Set<JsonPointer>> batchResult =
                        enforcer.getAccessiblePathsForSubjects(resourceKey, pointerObject,
                                partialOnly, readPermissions);
                for (final Map.Entry<AuthorizationSubject, Set<JsonPointer>> entry : batchResult.entrySet()) {
                    for (final JsonPointer accessiblePath : entry.getValue()) {
                        result.computeIfAbsent(accessiblePath, k -> new LinkedHashSet<>())
                                .add(entry.getKey().getId());
                    }
                }
            }
        }

        return result;
    }


}

