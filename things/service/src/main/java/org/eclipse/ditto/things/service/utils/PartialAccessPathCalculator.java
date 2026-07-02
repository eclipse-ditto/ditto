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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
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
import org.eclipse.ditto.json.JsonKey;
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

        // Memoized on the PolicyEnforcer instance: paid once per policy revision, not per event.
        final SubjectClassification classification = policyEnforcer.getRootResourceReadClassification();

        // Common-case fast path: no subject has any READ grant on the root resource → nothing to do.
        if (classification.getPartialOnly().isEmpty() && classification.getUnrestricted().isEmpty()) {
            return Map.of();
        }

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

        return calculateAccessiblePathsForSubjects(subjectsWithRestrictedAccess, thingJson,
                policyEnforcer.getEnforcer());
    }

    /**
     * Computes a 64-bit hash of the Thing JSON's <em>structure</em> — the set of leaf paths — while
     * ignoring all scalar values. Two Thing JSONs with the same structure but different values hash
     * equally; any structural difference (added/removed/renamed field, scalar-vs-object,
     * empty-vs-populated object) changes the hash with overwhelming probability.
     * <p>
     * The output of {@link #calculatePartialAccessPaths} depends only on the policy and this structure,
     * so callers can memoize the result keyed on {@code (policy revision, structureHash)} and reuse it
     * across value-only Thing updates.
     * <p>
     * <strong>Coupling:</strong> the notion of "leaf" here mirrors
     * {@code TreeBasedPolicyEnforcer.collectFlatPointers} exactly — recurse into non-empty JSON objects;
     * treat scalars, arrays, and empty objects as leaves. Iteration follows {@link JsonObject} field
     * order, which is deterministic for a given structure. If the two ever diverge, hits would return a
     * stale structure result; the equivalence test in {@code PartialAccessPathCalculatorTest} guards this.
     *
     * @param thingJson the Thing JSON to hash.
     * @return a structure-only 64-bit hash.
     */
    public static long structureHash(final JsonObject thingJson) {
        return hashObject(thingJson, FNV_OFFSET_BASIS);
    }

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    // Distinct structural tokens folded into the hash to encode tree shape (so that e.g.
    // {a:{b}, c} and {a:{b, c}} — same key sequence, different nesting — hash differently).
    private static final int TOKEN_DESCEND = 0x01;
    private static final int TOKEN_ASCEND = 0x02;
    private static final int TOKEN_LEAF = 0x03;

    private static long hashObject(final JsonObject object, long hash) {
        for (final JsonField field : object) {
            hash = hashChars(field.getKey().toString(), hash);
            final JsonValue value = field.getValue();
            if (value.isObject() && !value.asObject().isEmpty()) {
                hash = fnv(hash, TOKEN_DESCEND);
                hash = hashObject(value.asObject(), hash);
                hash = fnv(hash, TOKEN_ASCEND);
            } else {
                hash = fnv(hash, TOKEN_LEAF);
            }
        }
        return hash;
    }

    private static long hashChars(final String string, long hash) {
        for (int i = 0; i < string.length(); i++) {
            final char c = string.charAt(i);
            hash = fnv(hash, c & 0xff);
            hash = fnv(hash, (c >> 8) & 0xff);
        }
        return fnv(hash, 0); // key terminator, so "ab","c" cannot collide with "a","bc"
    }

    private static long fnv(final long hash, final int nextByte) {
        return (hash ^ (nextByte & 0xff)) * FNV_PRIME;
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
        // Build a trie of the leaf segments once; navigating the trie alongside the JSON walk
        // replaces the per-field `leaves.contains(JsonPointer)` lookup (which hashed a freshly
        // allocated ImmutableJsonPointer per visit). A null trie node means "no leaf has this
        // prefix" → we can early-exit recursion without descending.
        final LeafTrie trie = LeafTrie.build(leaves);
        final List<JsonPointer> result = new ArrayList<>();
        final Deque<JsonKey> pathStack = new ArrayDeque<>();
        final boolean allAccessible = collapseRecursive(thingJson, trie, pathStack, result);
        if (allAccessible && result.isEmpty()) {
            // Every leaf in the Thing is accessible to this subject — emit the root pointer so the
            // consumer filter short-circuits to "unrestricted". The subject should normally have
            // been excluded upstream (via SubjectClassification), but a policy that grants the
            // union of all leaves without granting the root would otherwise be flattened here.
            result.add(ROOT_RESOURCE_POINTER);
        }
        return new LinkedHashSet<>(result);
    }

    /**
     * Walks the Thing JSON in lockstep with the leaf trie, accumulating into {@code result} the
     * minimal pointer set that still covers every accessible leaf when the consumer filter is
     * applied. A fully-accessible subtree collapses to a single ancestor pointer.
     * <p>
     * The current JSON path is tracked on {@code pathStack} as a mutable {@code Deque<JsonKey>};
     * a {@link JsonPointer} is materialized only at emission time, not per field visited.
     *
     * @return {@code true} iff every leaf descendant of {@code node} at the current path is
     * present in the trie — signalling to the caller that it may collapse further up.
     */
    private static boolean collapseRecursive(final JsonValue node, @Nullable final LeafTrie trieNode,
            final Deque<JsonKey> pathStack, final List<JsonPointer> result) {

        // No leaf in the trie has this prefix → nothing under this JSON subtree can match.
        if (trieNode == null) {
            return false;
        }
        if (!node.isObject() || node.isNull()) {
            if (trieNode.isLeaf) {
                result.add(materializePointer(pathStack));
                return true;
            }
            return false;
        }
        final JsonObject obj = node.asObject();
        if (obj.isEmpty()) {
            if (trieNode.isLeaf) {
                result.add(materializePointer(pathStack));
                return true;
            }
            return false;
        }

        // Track the size of `result` before descending so that, if every child is fully
        // accessible and we collapse to an ancestor pointer, we can roll back the children's
        // emissions cheaply via subList.clear() (avoids a per-call LinkedHashSet allocation).
        final int sizeBeforeChildren = result.size();
        boolean allChildrenAccessible = true;
        for (final JsonField field : obj) {
            final JsonKey key = field.getKey();
            final LeafTrie childTrie = trieNode.children.get(key.toString());
            pathStack.addLast(key);
            final boolean childAll = collapseRecursive(field.getValue(), childTrie, pathStack, result);
            pathStack.removeLast();
            if (!childAll) {
                allChildrenAccessible = false;
            }
        }
        if (allChildrenAccessible && !pathStack.isEmpty()) {
            // Collapse: discard child emissions and emit a single ancestor pointer.
            if (result.size() > sizeBeforeChildren) {
                result.subList(sizeBeforeChildren, result.size()).clear();
            }
            result.add(materializePointer(pathStack));
            return true;
        }
        return allChildrenAccessible;
    }

    private static JsonPointer materializePointer(final Deque<JsonKey> pathStack) {
        if (pathStack.isEmpty()) {
            return ROOT_RESOURCE_POINTER;
        }
        final Iterator<JsonKey> it = pathStack.iterator();
        final JsonKey first = it.next();
        if (!it.hasNext()) {
            return JsonPointer.empty().addLeaf(first);
        }
        final JsonKey[] rest = new JsonKey[pathStack.size() - 1];
        int i = 0;
        while (it.hasNext()) {
            rest[i++] = it.next();
        }
        return JsonFactory.newPointer(first, rest);
    }

    /**
     * Compact prefix tree of leaf pointers. Built once per {@link #collapseLeavesToAncestors}
     * invocation and walked in parallel with the Thing-JSON descent — replaces the original
     * O(leaves) per-node {@code Set<JsonPointer>.contains} lookup.
     */
    private static final class LeafTrie {

        final Map<String, LeafTrie> children = new HashMap<>();
        boolean isLeaf;

        static LeafTrie build(final Set<JsonPointer> leaves) {
            final LeafTrie root = new LeafTrie();
            for (final JsonPointer leaf : leaves) {
                LeafTrie node = root;
                for (final JsonKey key : leaf) {
                    node = node.children.computeIfAbsent(key.toString(), k -> new LeafTrie());
                }
                node.isLeaf = true;
            }
            return root;
        }
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
