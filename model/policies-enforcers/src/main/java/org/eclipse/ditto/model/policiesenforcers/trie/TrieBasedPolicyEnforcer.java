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

import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;

/**
 * Holds Algorithms to build trie-based indices for a policy and to perform policy checks based on those indices.
 * <p>
 * Each policy is transformed into a trie (en.wikipedia.org/wiki/Trie) with {@link ResourceKey} as key type and with
 * each edge labeled by a {@link JsonKey}. For example, a policy with entries for {@code /attributes/A/B} and {@code
 * /attributes/A/C} is transformed to the following trie:
 * <pre>
 * '/attributes'
 *   |
 *   +--- '/attributes/A'
 *    A    |
 *         |
 *         +---- '/attributes/A/B'
 *         |  B
 *         |
 *         +---- '/attributes/A/C'
 *            C
 * </pre>
 * Each trie node has a {@link GrantRevokeIndex}, which maps each permission to the set of granted authorization
 * subjects and the set of revoked authorization subjects. The grant-revoke-indices are calculated in 4 different ways
 * to produce 4 tries of the same shape. 3 of those tries are used for policy enforcement. <ol> <li><em>Raw Trie:</em>
 * For each policy entry, pairs of permissions and their granted/revoked subjects are added to the trie node at the
 * resource location of the entry. </li> <li><em>Inherited Trie:</em> Encodes the hierarchical nature of policy entries,
 * namely that policy entries are valid also for sub-resources unless overridden. To build it, start from {@code
 * rawTrie}, push granted and revoked subjects from ancestors down to descendants. For each trie node, subjects from
 * ancestors are overridden by subjects defined at the corresponding node in {@code rawTrie}. </li> <li><em>Bottom Up
 * Grant Trie:</em> Supports partial permissions, e. g., a resource is considered readable also when 1 or more
 * sub-resources are readable in an authorization context. To build it, start from {@code inheritedTrie}, push granted
 * subjects from descendants up to ancestors. </li> <li><em>Bottom Up Revoke Trie:</em> Supports unrestricted
 * permissions, e. g., a resource is considered writable only if all sub-resources are writable, and any WRITE-revoked
 * resource make all its super-resources non-writable. To build it, start from {@code inheritedTrie}, push revoked
 * subjects from descendants up to ancestors. </li> </ol> See Javadoc of individual methods for more details.
 */
public final class TrieBasedPolicyEnforcer implements PolicyEnforcer {

    /**
     * PolicyTrie obtained by propagating grant & revoke sets down from ancestors to descendants.
     */
    private final PolicyTrie inheritedTrie;

    /**
     * PolicyTrie obtained from {@code this.inheritedTrie} by propagating grant sets up from descendants to ancestors.
     */
    private final PolicyTrie bottomUpGrantTrie;

    /**
     * PolicyTrie obtained from {@code this.inheritedTrie} by propagating revoke sets up from descendants to ancestors.
     */
    private final PolicyTrie bottomUpRevokeTrie;

    private TrieBasedPolicyEnforcer(final Iterable<PolicyEntry> policy) {
        final PolicyTrie rawTree = PolicyTrie.fromPolicy(policy);
        inheritedTrie = rawTree.getTransitiveClosure();
        bottomUpGrantTrie = inheritedTrie.getBottomUpGrantTrie();
        bottomUpRevokeTrie = inheritedTrie.getBottomUpRevokeTrie();
    }

    /**
     * Constructs a trie-based policy enforcer from a policy.
     *
     * @param policy The policy to interpret.
     * @return The policy enforcer.
     * @throws NullPointerException if {@code policy} is {@code null}.
     */
    public static TrieBasedPolicyEnforcer newInstance(final Policy policy) {
        return new TrieBasedPolicyEnforcer(checkNotNull(policy, "policy to interpret"));
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the given resource (or one of its sub-resources) are mentioned in the policy, then use the trie {@code
     * bottomUpRevokeTrie} to detect whether any permission is <em>revoked</em> on a sub-resource. If the given resource
     * is not mentioned in the policy, then its grants/revokes are inherited from a super-resource, and we use {@code
     * inheritedTrie} to locate the inherited grants/revokes.
     */
    @Override
    public boolean hasUnrestrictedPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext,
            final Permissions permissions) {

        final PolicyTrie policyTrie = seekWithFallback(resourceKey, bottomUpRevokeTrie, inheritedTrie);
        final GrantRevokeIndex grantRevokeIndex = policyTrie.getGrantRevokeIndex();
        final Set<String> subjectIds = getSubjectIds(authorizationContext);

        return grantRevokeIndex.hasPermissions(subjectIds, permissions);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the given resource (or one of its sub-resources) are mentioned in the policy, then use the trie {@code
     * bottomUpGrantTrie} to detect whether any permission is <em>granted</em> on a sub-resource. If the given resource
     * is not mentioned in the policy, then its grants/revokes are inherited from a super-resource, and we use {@code
     * inheritedTrie} to locate the inherited grants/revokes.
     */
    @Override
    public boolean hasPartialPermissions(final ResourceKey resourceKey, final AuthorizationContext authorizationContext,
            final Permissions permissions) {

        final PolicyTrie policyTrie = seekWithFallback(resourceKey, bottomUpGrantTrie, inheritedTrie);
        final GrantRevokeIndex grantRevokeIndex = policyTrie.getGrantRevokeIndex();
        final Set<String> subjectIds = getSubjectIds(authorizationContext);

        return grantRevokeIndex.hasPermissions(subjectIds, permissions);
    }

    @Override
    public EffectedSubjectIds getSubjectIdsWithPermission(final ResourceKey resourceKey,
            final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkPermissions(permissions);
        return inheritedTrie.seekToLeastAncestor(PolicyTrie.getJsonKeyIterator(resourceKey))
                .getGrantRevokeIndex()
                .getEffectedSubjectIds(permissions);
    }

    private static void checkResourceKey(final ResourceKey resourceKey) {
        checkNotNull(resourceKey, "resource key");
    }

    private static void checkPermissions(final Permissions permissions) {
        checkNotNull(permissions, "permissions to check");
    }

    @Override
    public Set<String> getSubjectIdsWithPartialPermission(final ResourceKey resourceKey,
            final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkPermissions(permissions);
        final PolicyTrie policyTrie = seekWithFallback(resourceKey, bottomUpGrantTrie, inheritedTrie);
        final GrantRevokeIndex grantRevokeIndex = policyTrie.getGrantRevokeIndex();
        return grantRevokeIndex.getGrantedSubjectIds(permissions);
    }

    @Override
    public JsonObject buildJsonView(final ResourceKey resourceKey,
            final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext,
            final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkNotNull(jsonFields, "JSON fields");
        checkPermissions(permissions);

        final Set<String> subjectIds = getSubjectIds(authorizationContext);

        final JsonKey typeKey = JsonKey.of(resourceKey.getResourceType());

        if (inheritedTrie.hasChild(typeKey)) {
            final PolicyTrie start = inheritedTrie.seekToLeastAncestor(PolicyTrie.getJsonKeyIterator(resourceKey));
            return start.buildJsonView(jsonFields, subjectIds, permissions);
        } else {
            return JsonFactory.newObject();
        }
    }

    /**
     * Extracts all subject IDs from an authorization context as a set of strings.
     *
     * @param authorizationContext The authorization context.
     * @return The set of subject IDs.
     */
    private static Set<String> getSubjectIds(final AuthorizationContext authorizationContext) {
        checkNotNull(authorizationContext, "Authorization Context");
        return authorizationContext.stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Returns a node in the trie {@code firstTry} whose path from root matches the given resource key exactly if it
     * exists, otherwise seek to the node in the trie {@code fallback} whose path from root matches the resource key the
     * best.
     *
     * @param resourceKey Pointer to a resource.
     * @param firstTry The policy trie to attempt an exact match.
     * @param fallback The policy trie to traverse if no exact match is found in {@code firstTry}.
     * @return The result trie node.
     */
    private static PolicyTrie seekWithFallback(final ResourceKey resourceKey, final PolicyTrie firstTry,
            final PolicyTrie fallback) {

        return firstTry.seekToExactNode(PolicyTrie.getJsonKeyIterator(resourceKey))
                .orElseGet(() -> fallback.seekToLeastAncestor(PolicyTrie.getJsonKeyIterator(resourceKey)));
    }

}
