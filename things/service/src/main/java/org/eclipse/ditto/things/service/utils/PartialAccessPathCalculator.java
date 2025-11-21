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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * Utility class for calculating which JSON paths each subject with partial READ permissions can access.
 * This is used to enable partial change notifications for subjects with restricted READ permissions.
 */
public final class PartialAccessPathCalculator {

    private static final String SUBJECTS_KEY = "subjects";
    private static final String PATHS_KEY = "paths";

    private PartialAccessPathCalculator() {
        // No instantiation
    }

    /**
     * Calculates which JSON paths each subject with partial READ permissions can access for a given ThingEvent.
     *
     * @param thingEvent the ThingEvent to calculate paths for
     * @param thing the current Thing state (may be null for ThingDeleted)
     * @param policyEnforcer the PolicyEnforcer to use for permission checks
     * @return a map from subject ID to list of accessible JsonPointer paths
     */
    public static Map<String, List<JsonPointer>> calculatePartialAccessPaths(
            final ThingEvent<?> thingEvent,
            @Nullable final Thing thing,
            final PolicyEnforcer policyEnforcer) {

        if (thing == null) {
            return Map.of();
        }

        final var enforcer = policyEnforcer.getEnforcer();
        final var rootResourceKey = PoliciesResourceType.thingResource(JsonPointer.empty());

        final Set<AuthorizationSubject> subjectsWithPartialPermission =
                enforcer.getSubjectsWithPartialPermission(rootResourceKey, Permissions.newInstance(Permission.READ));

        if (subjectsWithPartialPermission.isEmpty()) {
            return Map.of();
        }

        final Map<String, List<JsonPointer>> result = new LinkedHashMap<>();

        final JsonObject thingJson = thing.toJson();

        for (final AuthorizationSubject subject : subjectsWithPartialPermission) {
            final List<JsonPointer> accessiblePaths = calculateAccessiblePathsForSubject(
                    subject, thingJson, enforcer);
            if (!accessiblePaths.isEmpty()) {
                result.put(subject.getId(), accessiblePaths);
            }
        }
        return result;
    }

    /**
     * Calculates which paths a specific subject can access.
     *
     * @param subject the subject to check
     * @param thingJson the Thing JSON
     * @param enforcer the Enforcer to use
     * @return list of accessible JsonPointer paths
     */
    private static List<JsonPointer> calculateAccessiblePathsForSubject(
            final AuthorizationSubject subject,
            final JsonObject thingJson,
            final Enforcer enforcer) {

        final Set<JsonPointer> accessiblePaths = enforcer.getAccessiblePaths(
                PoliciesResourceType.thingResource(JsonPointer.empty()),
                thingJson,
                AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        subject
                ),
                Permissions.newInstance(Permission.READ)
        );

        return new ArrayList<>(accessiblePaths);
    }

    /**
     * Converts a map of subject IDs to accessible paths into an indexed JSON object format.
     * This format uses indices to avoid repeating subject IDs, significantly reducing header size.
     *
     * Format:
     * {
     *   "subjects": ["subject1", "subject2", ...],
     *   "paths": {
     *     "/path1": [0, 1],
     *     "/path2": [1],
     *     ...
     *   }
     * }
     *
     * @param partialAccessPaths the map to convert
     * @return an indexed JSON object with subjects array and paths mapping to indices
     */
    public static JsonObject toIndexedJsonObject(final Map<String, List<JsonPointer>> partialAccessPaths) {
        if (partialAccessPaths.isEmpty()) {
            return JsonFactory.newObjectBuilder()
                    .set(SUBJECTS_KEY, JsonFactory.newArray())
                    .set(PATHS_KEY, JsonFactory.newObject())
                    .build();
        }

        final List<String> subjects = partialAccessPaths.keySet().stream()
                .sorted()
                .toList();

        final Map<String, Integer> subjectIndex = new LinkedHashMap<>();
        for (int i = 0; i < subjects.size(); i++) {
            subjectIndex.put(subjects.get(i), i);
        }

        final JsonArrayBuilder subjectsArray = JsonFactory.newArrayBuilder();
        subjects.forEach(subjectsArray::add);

        final Map<JsonPointer, Set<Integer>> pointerToSubjectIndexes = new LinkedHashMap<>();
        for (final Map.Entry<String, List<JsonPointer>> entry : partialAccessPaths.entrySet()) {
            final String subjectId = entry.getKey();
            final Integer idx = subjectIndex.get(subjectId);
            if (idx == null) {
                continue;
            }

            for (final JsonPointer path : entry.getValue()) {
                pointerToSubjectIndexes.computeIfAbsent(path, k -> new LinkedHashSet<>()).add(idx);
            }
        }

        final JsonObjectBuilder pathsBuilder = JsonFactory.newObjectBuilder();
        for (final Map.Entry<JsonPointer, Set<Integer>> entry : pointerToSubjectIndexes.entrySet()) {
            final JsonPointer pointer = entry.getKey();
            final JsonArrayBuilder idxArrayBuilder = JsonFactory.newArrayBuilder();
            entry.getValue().stream().sorted().forEach(idxArrayBuilder::add);
            final String pathString = pointer.toString();
            final String keyString = pathString.startsWith("/") ? pathString.substring(1) : pathString;
            pathsBuilder.set(JsonFactory.newKey(keyString), idxArrayBuilder.build());
        }

        return JsonFactory.newObjectBuilder()
                .set(SUBJECTS_KEY, subjectsArray.build())
                .set(PATHS_KEY, pathsBuilder.build())
                .build();
    }
}

