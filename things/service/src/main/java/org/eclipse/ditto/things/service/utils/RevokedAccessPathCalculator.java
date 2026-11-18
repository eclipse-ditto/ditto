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
import java.util.TreeSet;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;

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

    /**
     * Optional field of the {@code ditto-revoked-access-paths} header: the indexes of the subjects for which READ on
     * the Thing root itself is revoked.
     */
    public static final JsonFieldDefinition<JsonArray> ROOT_FIELD_DEFINITION =
            JsonFactory.newJsonArrayFieldDefinition("root", FieldType.REGULAR);

    private static final Permissions READ_PERMISSIONS = Permissions.newInstance(Permission.READ);

    private RevokedAccessPathCalculator() {
        throw new AssertionError();
    }

    /**
     * Calculates the Thing paths whose READ permission is revoked for each subject.
     *
     * @param thingJson the Thing JSON, which may also be only a part of a Thing, e.g. the payload of a live event.
     * @param policyEnforcer the policy enforcer, filtered for the Thing's namespace.
     * @param collapseToAncestors whether fully revoked objects of {@code thingJson} are collapsed to their path, which
     * must not be done if the paths are also applied to JSON other than {@code thingJson}.
     * @return map of subject ID to the revoked paths; empty if READ is revoked nowhere in the Thing.
     */
    public static Map<String, List<JsonPointer>> calculateRevokedAccessPaths(final JsonObject thingJson,
            final PolicyEnforcer policyEnforcer, final boolean collapseToAncestors) {

        final Map<AuthorizationSubject, Set<JsonPointer>> revokedResourcePathsBySubject =
                policyEnforcer.getReadRevokedThingResourcePathsBySubject();
        if (revokedResourcePathsBySubject.isEmpty()) {
            return Map.of();
        }

        final Map<AuthorizationSubject, Set<JsonPointer>> candidateLeavesBySubject = new LinkedHashMap<>();
        final Set<JsonPointer> allRevokedResourcePaths = new LinkedHashSet<>();
        revokedResourcePathsBySubject.forEach((subject, revokedResourcePaths) -> {
            final Set<JsonPointer> candidateLeaves = new LinkedHashSet<>();
            for (final JsonPointer revokedResourcePath : revokedResourcePaths) {
                thingJson.getValue(revokedResourcePath).ifPresent(value -> {
                    collectLeaves(revokedResourcePath, value, candidateLeaves);
                    allRevokedResourcePaths.add(revokedResourcePath);
                });
            }
            if (!candidateLeaves.isEmpty()) {
                candidateLeavesBySubject.put(subject, candidateLeaves);
            }
        });
        if (candidateLeavesBySubject.isEmpty()) {
            return Map.of();
        }

        final Map<AuthorizationSubject, Set<JsonPointer>> accessibleLeavesBySubject =
                policyEnforcer.getEnforcer().getAccessiblePathsForSubjects(
                        PoliciesResourceType.thingResource(JsonPointer.empty()),
                        restrictTo(thingJson, allRevokedResourcePaths),
                        candidateLeavesBySubject.keySet(),
                        READ_PERMISSIONS);

        final Map<String, List<JsonPointer>> result = new LinkedHashMap<>();
        candidateLeavesBySubject.forEach((subject, candidateLeaves) -> {
            final Set<JsonPointer> revokedLeaves = new LinkedHashSet<>(candidateLeaves);
            revokedLeaves.removeAll(accessibleLeavesBySubject.getOrDefault(subject, Set.of()));
            if (!revokedLeaves.isEmpty()) {
                final Set<JsonPointer> paths = collapseToAncestors
                        ? PartialAccessPathCalculator.collapseLeavesToAncestors(revokedLeaves, thingJson)
                        : revokedLeaves;
                result.put(subject.getId(), new ArrayList<>(paths));
            }
        });
        return result;
    }

    /**
     * Returns the IDs of the subjects for which READ on the Thing root is revoked, which no grant of the subject can
     * outweigh at the root.
     *
     * @param policyEnforcer the policy enforcer, filtered for the Thing's namespace.
     * @return the subject IDs.
     */
    public static Set<String> getRootRevokedSubjects(final PolicyEnforcer policyEnforcer) {
        final Set<String> result = new TreeSet<>();
        policyEnforcer.getReadRevokedThingResourcePathsBySubject().forEach((subject, revokedResourcePaths) -> {
            if (revokedResourcePaths.contains(JsonPointer.empty())) {
                result.add(subject.getId());
            }
        });
        return result;
    }

    /**
     * Builds the value of the {@code ditto-revoked-access-paths} header in the indexed format of
     * {@link PartialAccessPathCalculator#toIndexedJsonObject(Map)}, plus the optional {@code root} field listing the
     * indexes of the {@code rootRevokedSubjects}.
     *
     * @param revokedAccessPaths the revoked paths per subject ID.
     * @param rootRevokedSubjects the IDs of the subjects for which READ on the Thing root is revoked.
     * @return the header value; its {@code subjects} are empty if nothing is revoked.
     */
    public static JsonObject toHeaderValue(final Map<String, List<JsonPointer>> revokedAccessPaths,
            final Set<String> rootRevokedSubjects) {

        if (rootRevokedSubjects.isEmpty()) {
            return PartialAccessPathCalculator.toIndexedJsonObject(revokedAccessPaths);
        }
        final Map<String, List<JsonPointer>> withRootRevokedSubjects = new LinkedHashMap<>(revokedAccessPaths);
        rootRevokedSubjects.forEach(subject -> withRootRevokedSubjects.putIfAbsent(subject, List.of()));
        final JsonObject indexed = PartialAccessPathCalculator.toIndexedJsonObject(withRootRevokedSubjects);
        final JsonArray subjects = indexed.getValueOrThrow(PartialAccessPathCalculator.SUBJECTS_FIELD_DEFINITION);
        final JsonArrayBuilder rootIndexes = JsonFactory.newArrayBuilder();
        for (int i = 0; i < subjects.getSize(); i++) {
            if (rootRevokedSubjects.contains(subjects.get(i).map(JsonValue::asString).orElse(""))) {
                rootIndexes.add(i);
            }
        }
        return indexed.set(ROOT_FIELD_DEFINITION, rootIndexes.build());
    }

    private static JsonObject restrictTo(final JsonObject thingJson, final Set<JsonPointer> paths) {
        if (paths.contains(JsonPointer.empty())) {
            return thingJson;
        }
        JsonObject result = JsonObject.empty();
        for (final JsonPointer path : paths) {
            final JsonValue value = thingJson.getValue(path).orElse(null);
            if (value != null) {
                result = result.setValue(path, value);
            }
        }
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
