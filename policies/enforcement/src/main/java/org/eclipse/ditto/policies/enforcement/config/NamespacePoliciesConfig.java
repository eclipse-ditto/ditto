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
package org.eclipse.ditto.policies.enforcement.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Configuration mapping namespace patterns to a set of "namespace root" policy IDs whose implicit
 * entries are automatically merged into every policy belonging to a matching namespace during enforcer
 * resolution.
 * <p>
 * This allows operators to define tenant/namespace-wide access policies without requiring individual
 * policies to declare explicit imports. The injection happens transparently at enforcer-build time, so
 * the stored policy's {@code imports} field is never modified.
 * </p>
 * <p>
 * Namespace patterns support the following syntax:
 * <ul>
 *   <li>{@code *} — matches every namespace</li>
 *   <li>{@code org.example.*} — matches any namespace whose name starts with {@code org.example.}</li>
 *   <li>exact string — matches only that exact namespace</li>
 * </ul>
 * Matching namespace roots are applied in deterministic precedence order:
 * exact match first, then prefix wildcards ordered from most specific to least specific, and finally
 * the catch-all pattern {@code *}.
 * </p>
 *
 * @since 3.9.0
 */
@Immutable
public interface NamespacePoliciesConfig {

    /**
     * Returns the full mapping of namespace patterns to list of namespace root policy IDs.
     * Map keys may be exact namespaces or wildcard patterns (e.g. {@code org.example.*}).
     *
     * @return immutable map of namespace pattern to list of PolicyIds.
     */
    Map<String, List<PolicyId>> getNamespacePolicies();

    /**
     * Returns the combined list of namespace root policy IDs whose patterns match the given
     * {@code namespace}. Returns an empty list if no pattern matches.
     * Matching patterns are resolved in deterministic precedence order: exact match first, then
     * prefix wildcards ordered from most specific to least specific, and finally {@code *}.
     *
     * @param namespace the concrete namespace to look up (not a pattern).
     * @return list of PolicyIds acting as namespace roots for the given namespace, never null.
     */
    List<PolicyId> getRootPoliciesForNamespace(String namespace);

    /**
     * Returns all policy IDs that are configured as namespace root policies across all patterns.
     * Used to short-circuit cache invalidation checks.
     *
     * @return set of all namespace root PolicyIds.
     */
    Set<PolicyId> getAllNamespaceRootPolicyIds();

    /**
     * Returns the set of namespace patterns that the given {@code rootPolicyId} covers.
     * Patterns may be exact namespaces or wildcards (e.g. {@code org.example.*} or {@code *}).
     * Used during cache invalidation: when a namespace root policy changes, all cached policies
     * whose namespace matches any returned pattern must be invalidated.
     *
     * @param rootPolicyId the policy ID of a namespace root policy.
     * @return set of namespace patterns covered by the given root policy, empty if none.
     */
    Set<String> getNamespacesForRootPolicy(PolicyId rootPolicyId);

    /**
     * Returns whether no namespace policies are configured at all.
     *
     * @return {@code true} if the config is empty.
     */
    boolean isEmpty();

    /**
     * Returns whether the given concrete {@code namespace} matches the given {@code pattern}.
     * <ul>
     *   <li>{@code *} matches any namespace.</li>
     *   <li>{@code org.example.*} matches any namespace starting with {@code org.example.}</li>
     *   <li>Any other value is treated as an exact match.</li>
     * </ul>
     *
     * @param namespace the concrete namespace string to test.
     * @param pattern the pattern from the config (exact, prefix wildcard, or {@code *}).
     * @return {@code true} if {@code namespace} matches {@code pattern}.
     * @since 3.9.0
     */
    static boolean namespaceMatchesPattern(final String namespace, final String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.endsWith(".*")) {
            return namespace.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return namespace.equals(pattern);
    }

}
