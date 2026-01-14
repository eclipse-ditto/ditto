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

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.Thing;

/**
 * Utility class for calculating which JSON paths each subject with partial READ permissions can access.
 * <p>
 * This class is used to enable partial change notifications for subjects with restricted READ permissions.
 * It calculates accessible paths for subjects that have partial (restricted) access to a Thing, excluding
 * subjects that have full access to the root resource.
 * </p>
 * <p>
 * The calculated paths are returned in an indexed format to reduce header size when serialized.
 * </p>
 *
 * @since 3.9.0
 */
public final class PartialAccessPathCalculator {

    /**
     * JSON field definition for the subjects array in the partial access paths structure.
     */
    public static final JsonFieldDefinition<JsonArray> SUBJECTS_FIELD_DEFINITION =
            JsonFactory.newJsonArrayFieldDefinition("subjects", FieldType.REGULAR);
    
    /**
     * JSON field definition for the paths object in the partial access paths structure.
     */
    public static final JsonFieldDefinition<JsonObject> PATHS_FIELD_DEFINITION =
            JsonFactory.newJsonObjectFieldDefinition("paths", FieldType.REGULAR);

    private static final JsonPointer ROOT_RESOURCE_POINTER = JsonPointer.empty();
    private static final Permissions READ_PERMISSIONS = Permissions.newInstance(Permission.READ);

    private PartialAccessPathCalculator() {
        throw new AssertionError("This class is not meant to be instantiated.");
    }

    /**
     * Calculates which JSON paths each subject with partial READ permissions can access.
     * <p>
     * This method identifies subjects with partial permissions and calculates their accessible paths,
     * excluding subjects that have full access to the root resource (as they don't need partial paths).
     * </p>
     *
     * @param thing the current Thing state (may be null for ThingDeleted events)
     * @param policyEnforcer the PolicyEnforcer to use for permission checks (must not be null)
     * @return a map from subject ID to list of accessible JsonPointer paths, empty map if no partial subjects exist
     * @throws NullPointerException if policyEnforcer is null
     */
    public static Map<String, List<JsonPointer>> calculatePartialAccessPaths(
            @Nullable final Thing thing,
            final PolicyEnforcer policyEnforcer) {

        if (thing == null) {
            return Map.of();
        }

        final Enforcer enforcer = policyEnforcer.getEnforcer();
        final var rootResourceKey = PoliciesResourceType.thingResource(ROOT_RESOURCE_POINTER);

        final Set<AuthorizationSubject> subjectsWithPartialPermission =
                enforcer.getSubjectsWithPartialPermission(rootResourceKey, READ_PERMISSIONS);

        if (subjectsWithPartialPermission.isEmpty()) {
            return Map.of();
        }

        final Set<AuthorizationSubject> subjectsWithRestrictedAccess =
                filterSubjectsWithRestrictedAccess(subjectsWithPartialPermission, enforcer, rootResourceKey);

        if (subjectsWithRestrictedAccess.isEmpty()) {
            return Map.of();
        }

        return calculateAccessiblePathsForSubjects(subjectsWithRestrictedAccess, thing, enforcer);
    }

    /**
     * Filters out subjects that have full access to the root resource.
     * Only subjects with truly restricted (partial) access are returned.
     *
     * @param subjectsWithPartialPermission all subjects with partial permission
     * @param enforcer the Enforcer to query
     * @param rootResourceKey the root resource key
     * @return set of subjects with restricted access (excluding those with full root access)
     */
    private static Set<AuthorizationSubject> filterSubjectsWithRestrictedAccess(
            final Set<AuthorizationSubject> subjectsWithPartialPermission,
            final Enforcer enforcer,
            final ResourceKey rootResourceKey) {

        final var effectedSubjects = enforcer.getSubjectsWithPermission(rootResourceKey, READ_PERMISSIONS);
        final Set<AuthorizationSubject> subjectsWithPermissionOnRoot = effectedSubjects.getGranted();

        final Set<AuthorizationSubject> subjectsWithUnrestrictedPermission =
                enforcer.getSubjectsWithUnrestrictedPermission(rootResourceKey, READ_PERMISSIONS);

        final Set<AuthorizationSubject> subjectsWithFullAccessOnRoot = new LinkedHashSet<>(subjectsWithPermissionOnRoot);
        subjectsWithFullAccessOnRoot.retainAll(subjectsWithUnrestrictedPermission);

        final Set<AuthorizationSubject> subjectsWithRestrictedAccess =
                new LinkedHashSet<>(subjectsWithPartialPermission);
        subjectsWithRestrictedAccess.removeAll(subjectsWithFullAccessOnRoot);

        return subjectsWithRestrictedAccess;
    }

    /**
     * Calculates accessible paths for a set of subjects with restricted access.
     *
     * @param subjectsWithRestrictedAccess subjects to calculate paths for
     * @param thing the Thing entity
     * @param enforcer the Enforcer to use
     * @return map of subject ID to their accessible paths
     */
    private static Map<String, List<JsonPointer>> calculateAccessiblePathsForSubjects(
            final Set<AuthorizationSubject> subjectsWithRestrictedAccess,
            final Thing thing,
            final Enforcer enforcer) {

        final Map<String, List<JsonPointer>> result = new LinkedHashMap<>();
        final JsonObject thingJson = thing.toJson();

        for (final AuthorizationSubject subject : subjectsWithRestrictedAccess) {
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
     * @param thingJson the Thing JSON representation
     * @param enforcer the Enforcer to use for permission checks
     * @return list of accessible JsonPointer paths for the subject
     */
    private static List<JsonPointer> calculateAccessiblePathsForSubject(
            final AuthorizationSubject subject,
            final JsonObject thingJson,
            final Enforcer enforcer) {

        final Set<JsonPointer> accessiblePaths = enforcer.getAccessiblePaths(
                PoliciesResourceType.thingResource(ROOT_RESOURCE_POINTER),
                thingJson,
                subject,
                READ_PERMISSIONS
        );

        return new ArrayList<>(accessiblePaths);
    }

    /**
     * Converts a map of subject IDs to accessible paths into an indexed JSON object format.
     * <p>
     * This format uses integer indices to reference subjects, significantly reducing header size
     * when there are many subjects or paths. Instead of repeating subject IDs multiple times,
     * each subject is assigned an index and paths reference these indices.
     * </p>
     * <p>
     * Example transformation:
     * </p>
     * <pre>{@code
     * // Input:
     * {
     *   "subject1": ["/attributes/foo", "/features/A/properties/baz"],
     *   "subject2": ["/attributes/foo"]
     * }
     *
     * // Output:
     * {
     *   "subjects": ["subject1", "subject2"],
     *   "paths": {
     *     "attributes/foo": [0, 1],
     *     "features/A/properties/baz": [0]
     *   }
     * }
     * }</pre>
     *
     * @param partialAccessPaths the map to convert (subject ID -> list of accessible paths)
     * @return an indexed JSON object with subjects array and paths mapping to subject indices
     */
    public static JsonObject toIndexedJsonObject(final Map<String, List<JsonPointer>> partialAccessPaths) {
        if (partialAccessPaths.isEmpty()) {
            return createEmptyIndexedJsonObject();
        }

        final List<String> sortedSubjects = extractAndSortSubjects(partialAccessPaths);
        final Map<String, Integer> subjectToIndex = buildSubjectIndex(sortedSubjects);
        final Map<JsonPointer, Set<Integer>> pointerToSubjectIndexes = buildPointerToIndexMap(
                partialAccessPaths, subjectToIndex);

        return buildIndexedJsonObject(sortedSubjects, pointerToSubjectIndexes);
    }

    /**
     * Creates an empty indexed JSON object with the standard structure.
     *
     * @return empty JSON object with subjects array and paths object
     */
    private static JsonObject createEmptyIndexedJsonObject() {
        return JsonFactory.newObjectBuilder()
                .set(SUBJECTS_FIELD_DEFINITION, JsonFactory.newArray())
                .set(PATHS_FIELD_DEFINITION, JsonFactory.newObject())
                .build();
    }

    /**
     * Extracts and sorts subject IDs from the partial access paths map.
     *
     * @param partialAccessPaths the map containing subject IDs
     * @return sorted list of subject IDs
     */
    private static List<String> extractAndSortSubjects(
            final Map<String, List<JsonPointer>> partialAccessPaths) {
        return partialAccessPaths.keySet().stream()
                .sorted()
                .toList();
    }

    /**
     * Builds a map from subject ID to its index in the sorted subjects list.
     *
     * @param sortedSubjects the sorted list of subject IDs
     * @return map from subject ID to index
     */
    private static Map<String, Integer> buildSubjectIndex(final List<String> sortedSubjects) {
        final Map<String, Integer> subjectToIndex = new LinkedHashMap<>();
        for (int i = 0; i < sortedSubjects.size(); i++) {
            subjectToIndex.put(sortedSubjects.get(i), i);
        }
        return subjectToIndex;
    }

    /**
     * Builds a map from JsonPointer paths to sets of subject indices that can access each path.
     *
     * @param partialAccessPaths the original map of subject IDs to paths
     * @param subjectToIndex the mapping from subject ID to index
     * @return map from JsonPointer to set of subject indices
     */
    private static Map<JsonPointer, Set<Integer>> buildPointerToIndexMap(
            final Map<String, List<JsonPointer>> partialAccessPaths,
            final Map<String, Integer> subjectToIndex) {

        final Map<JsonPointer, Set<Integer>> pointerToSubjectIndexes = new LinkedHashMap<>();
        
        for (final Map.Entry<String, List<JsonPointer>> entry : partialAccessPaths.entrySet()) {
            final String subjectId = entry.getKey();
            final Integer subjectIndex = subjectToIndex.get(subjectId);

            for (final JsonPointer path : entry.getValue()) {
                pointerToSubjectIndexes.computeIfAbsent(path, k -> new LinkedHashSet<>())
                        .add(subjectIndex);
            }
        }
        
        return pointerToSubjectIndexes;
    }

    /**
     * Builds the final indexed JSON object from sorted subjects and the pointer-to-index map.
     *
     * @param sortedSubjects the sorted list of subject IDs
     * @param pointerToSubjectIndexes the map from paths to subject indices
     * @return the complete indexed JSON object
     */
    private static JsonObject buildIndexedJsonObject(
            final List<String> sortedSubjects,
            final Map<JsonPointer, Set<Integer>> pointerToSubjectIndexes) {

        final JsonArrayBuilder subjectsArray = JsonFactory.newArrayBuilder();
        sortedSubjects.forEach(subjectsArray::add);

        final JsonObjectBuilder pathsBuilder = JsonFactory.newObjectBuilder();
        for (final Map.Entry<JsonPointer, Set<Integer>> entry : pointerToSubjectIndexes.entrySet()) {
            final JsonPointer pointer = entry.getKey();
            final JsonArrayBuilder indexArrayBuilder = JsonFactory.newArrayBuilder();
            entry.getValue().stream()
                    .sorted()
                    .forEach(indexArrayBuilder::add);
            
            final String pathKey = PointerUtils.toStringWithoutLeadingSlash(pointer);
            pathsBuilder.set(JsonFactory.newKey(pathKey), indexArrayBuilder.build());
        }

        return JsonFactory.newObjectBuilder()
                .set(SUBJECTS_FIELD_DEFINITION, subjectsArray.build())
                .set(PATHS_FIELD_DEFINITION, pathsBuilder.build())
                .build();
    }
}
