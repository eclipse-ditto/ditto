/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policiesenforcers.trie;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.Subjects;

/**
 * Trie data structure for a policy optimized for policy enforcement.
 * <p>
 * A <em>trie</em> (en.wikipedia/wiki/Trie) is a map data structure. A {@code PolicyTrie} maps each {@link ResourceKey}
 * into a {@link GrantRevokeIndex}. Each trie node corresponds to a unique resource, say for example {@code
 * thing:/attributes/A/B}. Its parent is the immediate super-resource {@code thing:/attributes/A}, and its children are
 * immediate sub-resources, say {@code thing:/attributes/A/B/C} and {@code thing:/attributes/A/B/D}.
 */
@NotThreadSafe
final class PolicyTrie {

    private final GrantRevokeIndex grantRevokeIndex;
    private final Map<JsonKey, PolicyTrie> children;

    private PolicyTrie() {
        this(new GrantRevokeIndex(), new HashMap<>());
    }

    private PolicyTrie(final GrantRevokeIndex grantRevokeIndex, final Map<JsonKey, PolicyTrie> children) {
        this.grantRevokeIndex = grantRevokeIndex;
        this.children = children;
    }

    /**
     * Interprets a {@link org.eclipse.ditto.model.policies.Policy} as trie. For each policy entry, a map from
     * granted/revoked permissions to their corresponding subjects is added to a trie node at the exact location of the
     * resource of the policy entry.
     *
     * @param policy The policy data structure to interpret.
     * @return A trie optimized for enforcer operations.
     * @throws NullPointerException if {@code policy} is {@code null}.
     */
    static PolicyTrie fromPolicy(final Iterable<PolicyEntry> policy) {
        checkNotNull(policy, "policy to interpret");
        final PolicyTrie prototype = new PolicyTrie();
        policy.forEach(prototype::addPolicyEntry);
        return prototype;
    }

    /**
     * Converts a {@link ResourceKey} to an iterator of JSON keys by prepending the resource type to the resource path.
     *
     * @param resourceKey The resource key to convert.
     * @return The converted JSON key iterator.
     * @throws NullPointerException if {@code resourceKey} is {@code null}.
     */
    static Iterator<JsonKey> getJsonKeyIterator(final ResourceKey resourceKey) {
        checkNotNull(resourceKey, "resource key to convert");
        return JsonFactory.newPointer(resourceKey.getResourceType()).append(resourceKey.getResourcePath()).iterator();
    }

    /**
     * Returns the {@link GrantRevokeIndex} at this node.
     *
     * @return The grant-revoke-index at this node.
     */
    GrantRevokeIndex getGrantRevokeIndex() {
        return grantRevokeIndex;
    }

    /**
     * Returns a copy of this trie such that each trie node inherits all grants and revokes from its ancestors except
     * those that are overridden by more specific policy entries.
     *
     * @return A copy of this trie with grants and revokes pushed down from ancestors to descendants.
     */
    PolicyTrie getTransitiveClosure() {
        return computeTransitiveClosure(this, new GrantRevokeIndex());
    }

    private static PolicyTrie computeTransitiveClosure(final PolicyTrie thisTrie, final GrantRevokeIndex inherited) {
        final GrantRevokeIndex thisMap = inherited.copyWithDecrementedWeight().overrideBy(thisTrie.grantRevokeIndex);
        final Map<JsonKey, PolicyTrie> children = thisTrie.children.entrySet()
                .stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(),
                        computeTransitiveClosure(entry.getValue(), thisMap)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new PolicyTrie(thisMap, children);
    }

    /**
     * Returns a copy of this trie such that each trie node contains grants from all its descendants.
     *
     * @return A copy of this trie with grants pushed up from descendants to ancestors.
     */
    PolicyTrie getBottomUpGrantTrie() {
        final Map<JsonKey, PolicyTrie> newChildren = children.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getBottomUpGrantTrie()));
        final PermissionSubjectsMap newGrantMap = grantRevokeIndex.getGranted().copy();
        newChildren.values().forEach(child ->
                newGrantMap.addAllEntriesFrom(child.getGrantRevokeIndex().getGranted().copyWithIncrementedWeight()));
        final PermissionSubjectsMap newRevokeMap = grantRevokeIndex.getRevoked().copy();
        newRevokeMap.removeAllEntriesFrom(newGrantMap);
        final GrantRevokeIndex newGrantRevokeMap = new GrantRevokeIndex(newGrantMap, newRevokeMap);
        return new PolicyTrie(newGrantRevokeMap, newChildren);
    }

    /**
     * Returns a copy of this trie such that each trie node contains revokes from all its descendants.
     *
     * @return A copy of this trie with revokes pushed up from descendants to ancestors.
     */
    PolicyTrie getBottomUpRevokeTrie() {
        final Map<JsonKey, PolicyTrie> newChildren = children.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getBottomUpRevokeTrie()));
        final PermissionSubjectsMap newRevokeMap = grantRevokeIndex.getRevoked().copy();
        newChildren.values().forEach(child ->
                newRevokeMap.addAllEntriesFrom(child.getGrantRevokeIndex().getRevoked().copyWithIncrementedWeight()));
        final PermissionSubjectsMap newGrantMap = grantRevokeIndex.getGranted().copy();
        final GrantRevokeIndex newGrantRevokeMap = new GrantRevokeIndex(newGrantMap, newRevokeMap);
        return new PolicyTrie(newGrantRevokeMap, newChildren);
    }

    /**
     * Returns whether a child exists for the given key.
     *
     * @param childKey Key of the child to check.
     * @return {@code true} if a child with the given key exists, {@code false} otherwise.
     */
    boolean hasChild(final JsonKey childKey) {
        return children.containsKey(childKey);
    }

    JsonObject buildJsonView(final Iterable<JsonField> jsonFields,
            final Set<String> subjectIds, final Permissions permissions) {

        // converts jsonFields into a JsonObject
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonFields.forEach(jsonObjectBuilder::set);

        return buildJsonObjectView(jsonObjectBuilder.build(), subjectIds, permissions);
    }

    private JsonObject buildJsonObjectView(final JsonObject inputObject, final Set<String> subjectIds,
            final Permissions permissions) {

        final JsonObjectBuilder outputObjectBuilder = JsonFactory.newObjectBuilder();

        final PolicyTrie defaultPolicyTrie = new PolicyTrie(grantRevokeIndex, Collections.emptyMap());

        inputObject.forEach(jsonField -> {
            final JsonValue fieldValue = jsonField.getValue();
            final PolicyTrie relevantTrie = children.getOrDefault(jsonField.getKey(), defaultPolicyTrie);
            relevantTrie.buildPossiblyEmptyViewForJsonObjectsArraysAndScalars(fieldValue, subjectIds, permissions)
                    .ifPresent(value -> {
                        if (jsonField.getDefinition().isPresent()) {
                            outputObjectBuilder.set(jsonField.getDefinition().get(), value);
                        } else {
                            outputObjectBuilder.set(jsonField.getKey(), value);
                        }
                    });
        });

        return outputObjectBuilder.build();
    }

    private Optional<JsonValue> buildPossiblyEmptyViewForJsonObjectsArraysAndScalars(
            final JsonValue jsonValue,
            final Set<String> subjectIds,
            final Permissions permissions) {

        if (jsonValue.isObject()) {
            final JsonObject candidate = buildJsonObjectView(jsonValue.asObject(), subjectIds, permissions);
            if (!candidate.isEmpty() || grantRevokeIndex.hasPermissions(subjectIds, permissions)) {
                return Optional.of(candidate);
            } else {
                return Optional.empty();
            }
        } else if (jsonValue.isArray()) {
            final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
            jsonValue.asArray().forEach(element ->
                    buildPossiblyEmptyViewForJsonObjectsArraysAndScalars(element, subjectIds,
                            permissions).ifPresent(
                            jsonArrayBuilder::add));
            final JsonArray candidate = jsonArrayBuilder.build();
            if (!candidate.isEmpty() || grantRevokeIndex.hasPermissions(subjectIds, permissions)) {
                return Optional.of(candidate);
            } else {
                return Optional.empty();
            }
        } else {
            return grantRevokeIndex.hasPermissions(subjectIds, permissions) ? Optional.of(jsonValue) : Optional.empty();
        }
    }

    private void addPolicyEntry(final PolicyEntry policyEntry) {
        final Collection<String> subjectIds = getSubjectIds(policyEntry.getSubjects());
        policyEntry.getResources().forEach(resource -> {
            final PolicyTrie target = seekOrCreate(getJsonKeyIterator(resource.getResourceKey()));
            final EffectedPermissions effectedPermissions = resource.getEffectedPermissions();
            target.grant(subjectIds, effectedPermissions.getGrantedPermissions());
            target.revoke(subjectIds, effectedPermissions.getRevokedPermissions());
        });
    }

    private static Collection<String> getSubjectIds(final Subjects subjects) {
        return subjects.stream()
                .map(Subject::getId)
                .map(SubjectId::toString)
                .collect(Collectors.toSet());
    }

    /**
     * Seek to a trie node matching the given path exactly, or create it and all needed if they did not exist. Analogous
     * to {@code mkdir -p}.
     *
     * @param path The resource path to seek/create a node for.
     * @return The node whose path from root matches {@code path} exactly.
     */
    private PolicyTrie seekOrCreate(final Iterator<JsonKey> path) {
        final Supplier<PolicyTrie> computeForChild = () -> {
            final PolicyTrie childNode = compute(path.next(), Function.identity());
            return childNode.seekOrCreate(path);
        };
        return path.hasNext() ? computeForChild.get() : this;
    }

    // wrapper of Map.compute
    private PolicyTrie compute(final JsonKey childKey, final Function<PolicyTrie, PolicyTrie> childUpdate) {
        return children.compute(childKey,
                (key, childTrie) -> childTrie == null ? new PolicyTrie() : childUpdate.apply(childTrie));
    }

    private void grant(final Collection<String> subjectIds, final Iterable<String> permissions) {
        grantRevokeIndex.getGranted().addTotalRelationOfWeightZero(permissions, subjectIds);
    }

    private void revoke(final Collection<String> subjectIds, final Iterable<String> permissions) {
        grantRevokeIndex.getRevoked().addTotalRelationOfWeightZero(permissions, subjectIds);
    }

    /**
     * Seek to a trie node whose path from root matches {@code path} as much as possible.
     *
     * @param path The path key to match.
     * @return The best matched node.
     */
    PolicyTrie seekToLeastAncestor(final Iterator<JsonKey> path) {
        return seek(path, Function.identity(), Function.identity());
    }

    /**
     * Seek to the trie node whose path from root matches {@code path} exactly.
     *
     * @param path The resource path to match.
     * @return The exactly matched trie node, or {@code Optional.empty()} if no trie node matches {@code path} exactly.
     */
    Optional<PolicyTrie> seekToExactNode(final Iterator<JsonKey> path) {
        return seek(path, Optional::of, ancestor -> Optional.empty());
    }

    /**
     * Traverse along a resource path as far as possible, then call 1 of the 2 given functions according to whether the
     * resource path is fully traversed (i. e., exact match found) or whether a leaf of the trie is reached without
     * exhausting the resource path.
     *
     * @param path The resource path to traverse.
     * @param endOfPath What to do when the path ends (i. e., exact match found).
     * @param endOfTrie What to do when a leaf is reached without exhausting the path.
     * @param <T> Type of the result.
     * @return Result of {@code endOfPath} or {@code endOfTrie}.
     */
    private <T> T seek(final Iterator<JsonKey> path, final Function<PolicyTrie, T> endOfPath,
            final Function<PolicyTrie, T> endOfTrie) {
        if (path.hasNext()) {
            final JsonKey key = path.next();
            return children.containsKey(key)
                    ? children.get(key).seek(path, endOfPath, endOfTrie)
                    : endOfTrie.apply(this);
        } else {
            return endOfPath.apply(this);
        }
    }

}
