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

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
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

        return calculatePartialAccessPaths(thing, policyEnforcer, thing.toJson());
    }

    /**
     * Calculates which JSON paths each subject with partial READ permissions can access,
     * using a pre-computed {@code thingJson} to avoid redundant serialization.
     *
     * @param thing the current Thing state (may be null for ThingDeleted events)
     * @param policyEnforcer the PolicyEnforcer to use for permission checks (must not be null)
     * @param thingJson the pre-computed JSON representation of the Thing
     * @return a map from subject ID to list of accessible JsonPointer paths, empty map if no partial subjects exist
     * @throws NullPointerException if policyEnforcer is null
     */
    public static Map<String, List<JsonPointer>> calculatePartialAccessPaths(
            @Nullable final Thing thing,
            final PolicyEnforcer policyEnforcer,
            final JsonObject thingJson) {

        if (thing == null) {
            return Map.of();
        }

        final Enforcer enforcer = policyEnforcer.getEnforcer();
        final ResourceKey rootResourceKey = PoliciesResourceType.thingResource(ROOT_RESOURCE_POINTER);

        final SubjectClassification classification = enforcer.classifySubjects(rootResourceKey, READ_PERMISSIONS);

        // Reconstruct "restricted" = partial - (effectedGranted ∩ unrestricted)
        final Set<AuthorizationSubject> allPartial = new LinkedHashSet<>(classification.getPartialOnly());
        allPartial.addAll(classification.getUnrestricted());
        final Set<AuthorizationSubject> fullAccessOnRoot = new LinkedHashSet<>(classification.getEffectedGranted());
        fullAccessOnRoot.retainAll(classification.getUnrestricted());
        final Set<AuthorizationSubject> subjectsWithRestrictedAccess = new LinkedHashSet<>(allPartial);
        subjectsWithRestrictedAccess.removeAll(fullAccessOnRoot);

        if (subjectsWithRestrictedAccess.isEmpty()) {
            return Map.of();
        }

        return calculateAccessiblePathsForSubjects(subjectsWithRestrictedAccess, thingJson, enforcer);
    }

    /**
     * Calculates accessible paths for a set of subjects with restricted access using a batch call.
     * <p>
     * The {@link Enforcer#getAccessiblePathsForSubjects} call returns a per-subject set of
     * <em>leaf</em> JsonPointers obtained by flattening the Thing JSON. For Things with
     * high-cardinality features (hundreds of child entries, each potentially with several
     * properties), that leaf set can reach several thousand entries per subject. Serialized
     * into the {@code ditto-partial-access-paths} header, the resulting JSON easily exceeds
     * the Pekko Artery remoting frame size, causing {@code BufferOverflowException} in
     * {@code PublishSignal} serialization and silently dropping every {@code ThingEvent} for
     * the affected Thing cluster-wide.
     * <p>
     * This method therefore post-processes the enforcer output by collapsing any subtree of
     * the Thing JSON whose leaves are <em>all</em> accessible to the subject into a single
     * ancestor pointer. This mirrors Ditto's native policy semantics (a READ grant on an
     * ancestor implies read on every descendant) and the consumer-side filter
     *
     * @param subjectsWithRestrictedAccess subjects to calculate paths for
     * @param thingJson the Thing JSON representation
     * @param enforcer the Enforcer to use
     * @return map of subject ID to their accessible paths (ancestor-collapsed)
     */
    private static Map<String, List<JsonPointer>> calculateAccessiblePathsForSubjects(
            final Set<AuthorizationSubject> subjectsWithRestrictedAccess,
            final JsonObject thingJson,
            final Enforcer enforcer) {

        final ResourceKey resourceKey = PoliciesResourceType.thingResource(ROOT_RESOURCE_POINTER);
        final Map<AuthorizationSubject, Set<JsonPointer>> batchResult =
                enforcer.getAccessiblePathsForSubjects(resourceKey, thingJson,
                        subjectsWithRestrictedAccess, READ_PERMISSIONS);

        final Map<String, List<JsonPointer>> result = new LinkedHashMap<>();
        for (final Map.Entry<AuthorizationSubject, Set<JsonPointer>> entry : batchResult.entrySet()) {
            final Set<JsonPointer> collapsed = collapseLeavesToAncestors(entry.getValue(), thingJson);
            result.put(entry.getKey().getId(), new ArrayList<>(collapsed));
        }
        return result;
    }

    /**
     * Collapses a flat set of leaf pointers into the minimal set of pointers that still covers
     * the same accessible fields when the consumer filter is applied.
     * <p>
     * The algorithm walks the Thing JSON top-down: at each non-empty object node, if every
     * descendant leaf of that node is in {@code leaves}, the entire subtree is replaced by a
     * single entry for the node's path; otherwise recursion continues into the children. Leaves
     * outside {@code leaves} are naturally omitted.
     * <p>
     * Empty-object nodes (e.g. {@code "properties": {}}) are treated as leaves themselves to
     * match how the enforcer emits them.
     *
     * @param leaves the leaf-level accessible pointers returned by the enforcer
     * @param thingJson the Thing JSON representation to walk
     * @return the ancestor-collapsed accessible pointer set
     * @since 3.9.0
     */
    static Set<JsonPointer> collapseLeavesToAncestors(final Set<JsonPointer> leaves,
            final JsonObject thingJson) {

        if (leaves.isEmpty()) {
            return Set.of();
        }
        final Set<JsonPointer> result = new LinkedHashSet<>();
        final boolean allAccessible = collapseRecursive(thingJson, ROOT_RESOURCE_POINTER, leaves, result);
        if (allAccessible && result.isEmpty()) {
            // Every leaf in the Thing is accessible to this subject — emit the root pointer so the
            // consumer filter short-circuits to "unrestricted". The subject should normally have
            // been excluded upstream (via SubjectClassification), but a policy that grants the
            // union of all leaves without granting the root would otherwise be flattened here.
            result.add(ROOT_RESOURCE_POINTER);
        }
        return result;
    }

    /**
     * Walks the Thing JSON and emits into {@code result} the minimal pointer set that still
     * covers every leaf of {@code leaves} when the consumer filter is applied. A fully-accessible
     * subtree collapses to a single ancestor pointer.
     *
     * @return {@code true} iff every leaf descendant of {@code node} at {@code currentPath} is
     * present in {@code leaves} — signalling to the caller that it may collapse further up.
     */
    private static boolean collapseRecursive(final JsonValue node, final JsonPointer currentPath,
            final Set<JsonPointer> leaves, final Set<JsonPointer> result) {

        if (!node.isObject() || node.isNull()) {
            if (leaves.contains(currentPath)) {
                result.add(currentPath);
                return true;
            }
            return false;
        }
        final JsonObject obj = node.asObject();
        if (obj.isEmpty()) {
            if (leaves.contains(currentPath)) {
                result.add(currentPath);
                return true;
            }
            return false;
        }

        final Set<JsonPointer> childEmissions = new LinkedHashSet<>();
        boolean allChildrenAccessible = true;
        for (final JsonField field : obj) {
            final JsonPointer childPath = currentPath.addLeaf(field.getKey());
            final boolean childAll = collapseRecursive(field.getValue(), childPath, leaves, childEmissions);
            if (!childAll) {
                allChildrenAccessible = false;
            }
        }
        if (allChildrenAccessible && !currentPath.isEmpty()) {
            // Collapse: replace all descendant emissions with a single ancestor pointer.
            result.add(currentPath);
            return true;
        }
        result.addAll(childEmissions);
        return allChildrenAccessible;
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
