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
package org.eclipse.ditto.things.service.utils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
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
        if (fields == null || thing == null || policyEnforcer == null) {
            return ReadGrant.empty();
        }

        final Enforcer enforcer = policyEnforcer.getEnforcer();
        final Map<JsonPointer, Set<String>> pointerToSubjects = new LinkedHashMap<>();

        for (final JsonPointer pointer : fields.getPointers()) {
            // Collect all paths and subjects for this pointer (including nested paths from partial subjects)
            final Map<JsonPointer, Set<String>> pathsForPointer = collectSubjectsForPointer(pointer, thing, enforcer);
            
            // Merge into result
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
     * For partial subjects, collects all nested paths they can access.
     *
     * @param pointer the pointer to check
     * @param thing the Thing entity
     * @param enforcer the Enforcer to query
     * @return map of JsonPointer paths to sets of subject IDs
     */
    private static Map<JsonPointer, Set<String>> collectSubjectsForPointer(
            final JsonPointer pointer,
            final Thing thing,
            final Enforcer enforcer
    ) {
        final Set<AuthorizationSubject> subjectsWithUnrestrictedPermission =
                enforcer.getSubjectsWithUnrestrictedPermission(
                        PoliciesResourceType.thingResource(pointer),
                        Permissions.newInstance(Permission.READ)
                );

        final Set<AuthorizationSubject> subjectsWithPartialPermission =
                enforcer.getSubjectsWithPartialPermission(
                        PoliciesResourceType.thingResource(pointer),
                        Permissions.newInstance(Permission.READ)
                );

        final Map<JsonPointer, Set<String>> result = new LinkedHashMap<>();

        final Set<String> unrestrictedIds = subjectsWithUnrestrictedPermission.stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unrestrictedIds.isEmpty()) {
            result.put(pointer, unrestrictedIds);
        }

        if (!subjectsWithPartialPermission.equals(subjectsWithUnrestrictedPermission)) {
            final Set<AuthorizationSubject> partialOnly = new HashSet<>(subjectsWithPartialPermission);
            partialOnly.removeAll(subjectsWithUnrestrictedPermission);

            final JsonObject thingJson = thing.toJson();
            final JsonValue pointerValue = thingJson.getValue(pointer).orElse(null);
            
            if (pointerValue != null && pointerValue.isObject()) {
                final JsonObject pointerObject = pointerValue.asObject();
                
                for (final AuthorizationSubject subject : partialOnly) {
                    final ResourceKey resourceKey = PoliciesResourceType.thingResource(pointer);
                    final Set<JsonPointer> accessiblePaths = enforcer.getAccessiblePaths(
                            resourceKey,
                            pointerObject,
                            AuthorizationContext.newInstance(
                                    DittoAuthorizationContextType.UNSPECIFIED,
                                    subject
                            ),
                            Permissions.newInstance(Permission.READ)
                    );

                    for (final JsonPointer accessiblePath : accessiblePaths) {
                        result.computeIfAbsent(accessiblePath, k -> new LinkedHashSet<>()).add(subject.getId());
                    }
                }
            }
        }

        return result;
    }


}

