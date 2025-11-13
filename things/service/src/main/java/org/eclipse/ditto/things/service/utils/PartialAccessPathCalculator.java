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
import org.eclipse.ditto.json.JsonField;
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

    private static final String THING_RESOURCE_TYPE = "thing";

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

        final JsonObject accessibleJson = enforcer.buildJsonView(
                thingJson,
                THING_RESOURCE_TYPE,
                AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        subject
                ),
                Permission.READ
        );

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();
        collectAccessiblePaths(accessibleJson, JsonPointer.empty(), accessiblePaths);

        return new ArrayList<>(accessiblePaths);
    }

    /**
     * Recursively collects all accessible paths from a JSON object.
     *
     * @param jsonObject the JSON object to traverse
     * @param currentPath the current path being traversed
     * @param accessiblePaths the set to collect paths into
     */
    private static void collectAccessiblePaths(
            final JsonObject jsonObject,
            final JsonPointer currentPath,
            final Set<JsonPointer> accessiblePaths) {

        for (final JsonField field : jsonObject) {
            final JsonPointer fieldPath = currentPath.isEmpty()
                    ? JsonPointer.of("/" + field.getKey())
                    : currentPath.append(JsonPointer.of("/" + field.getKey()));

            accessiblePaths.add(fieldPath);

            if (field.getValue().isObject()) {
                collectAccessiblePaths(field.getValue().asObject(), fieldPath, accessiblePaths);
            }
        }
    }

    /**
     * Converts a map of subject IDs to accessible paths into a JSON object.
     *
     * @param partialAccessPaths the map to convert
     * @return a JSON object with subject IDs as keys and arrays of path strings as values
     */
    public static JsonObject toJsonObject(final Map<String, List<JsonPointer>> partialAccessPaths) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        for (final Map.Entry<String, List<JsonPointer>> entry : partialAccessPaths.entrySet()) {
            final String subjectId = entry.getKey();
            final List<JsonPointer> paths = entry.getValue();

            final JsonArrayBuilder arrayBuilder = JsonFactory.newArrayBuilder();
            for (final JsonPointer path : paths) {
                arrayBuilder.add(path.toString());
            }

            builder.set(subjectId, arrayBuilder.build());
        }

        return builder.build();
    }
}

