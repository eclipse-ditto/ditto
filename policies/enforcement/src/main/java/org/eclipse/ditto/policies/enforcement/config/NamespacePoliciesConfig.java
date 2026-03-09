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
 * Configuration mapping namespaces to a set of "namespace root" policy IDs whose implicit entries are
 * automatically merged into every policy belonging to that namespace during enforcer resolution.
 * <p>
 * This allows operators to define tenant/namespace-wide access policies without requiring individual
 * policies to declare explicit imports. The injection happens transparently at enforcer-build time, so
 * the stored policy's {@code imports} field is never modified.
 * </p>
 *
 * @since 3.9.0
 */
@Immutable
public interface NamespacePoliciesConfig {

    /**
     * Returns the full mapping of namespace to list of namespace root policy IDs.
     *
     * @return immutable map of namespace string to list of PolicyIds.
     */
    Map<String, List<PolicyId>> getNamespacePolicies();

    /**
     * Returns the list of namespace root policy IDs configured for the given {@code namespace}.
     * Returns an empty list if no namespace root policies are configured for the namespace.
     *
     * @param namespace the namespace to look up.
     * @return list of PolicyIds acting as namespace roots for the given namespace, never null.
     */
    List<PolicyId> getRootPoliciesForNamespace(String namespace);

    /**
     * Returns all policy IDs that are configured as namespace root policies across all namespaces.
     * Used to build the cache invalidation reverse map.
     *
     * @return set of all namespace root PolicyIds.
     */
    Set<PolicyId> getAllNamespaceRootPolicyIds();

    /**
     * Returns the set of namespaces that the given {@code rootPolicyId} covers.
     * Used during cache invalidation: when a namespace root policy changes, all cached policies
     * in its covered namespaces must be invalidated.
     *
     * @param rootPolicyId the policy ID of a namespace root policy.
     * @return set of namespaces covered by the given root policy, empty if none.
     */
    Set<String> getNamespacesForRootPolicy(PolicyId rootPolicyId);

    /**
     * Returns whether no namespace policies are configured at all.
     *
     * @return {@code true} if the config is empty.
     */
    boolean isEmpty();

}
