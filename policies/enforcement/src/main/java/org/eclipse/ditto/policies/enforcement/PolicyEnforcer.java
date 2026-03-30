/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;

import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;

import org.slf4j.Logger;

/**
 * Policy together with its enforcer.
 */
@Immutable
public final class PolicyEnforcer {

    private static final Logger LOG = DittoLoggerFactory.getThreadSafeLogger(PolicyEnforcer.class);

    @Nullable private final Policy policy;
    private final Enforcer enforcer;

    private PolicyEnforcer(@Nullable final Policy policy, final Enforcer enforcer) {
        this.policy = policy;
        this.enforcer = enforcer;
    }

    /**
     * Create a policy enforcer from policy.
     *
     * @param policy the policy
     * @return the pair
     */
    public static CompletionStage<PolicyEnforcer> withResolvedImports(final Policy policy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver) {
        return policy.withResolvedImports(policyResolver)
                .thenApply(resolvedPolicy -> {
                    final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(resolvedPolicy);
                    return new PolicyEnforcer(resolvedPolicy, enforcer);
                });
    }

    /**
     * Creates a policy enforcer from a policy, resolving both its explicit imports and any namespace root policies
     * configured for the policy's namespace. Namespace root policies are resolved with their own imports and their
     * implicit entries are merged last with local entries taking precedence on label conflicts.
     * Matching namespace roots are applied in config precedence order: exact match first, then more
     * specific prefix wildcards, then broader prefix wildcards, and finally {@code *}.
     * <p>
     * This is the preferred factory method to use in the cache loader. Namespace root policy resolution bypasses
     * the normal READ permission pre-enforcer check, since namespace policies are operator-configured and injected
     * transparently — the user never explicitly declares them in the policy's {@code imports} field.
     * </p>
     *
     * @param policy the policy to build an enforcer for.
     * @param policyResolver resolves imported policies by ID.
     * @param namespacePoliciesConfig the static namespace policies configuration.
     * @return a completion stage with the fully resolved PolicyEnforcer.
     * @since 3.9.0
     */
    public static CompletionStage<PolicyEnforcer> withResolvedImportsAndNamespacePolicies(
            final Policy policy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver,
            final NamespacePoliciesConfig namespacePoliciesConfig) {

        return policy.withResolvedImports(policyResolver)
                .thenCompose(resolvedPolicy -> mergeNamespacePolicies(resolvedPolicy, policyResolver,
                        namespacePoliciesConfig))
                .thenApply(finalPolicy -> {
                    final var enforcer = PolicyEnforcers.defaultEvaluator(finalPolicy);
                    return new PolicyEnforcer(finalPolicy, enforcer);
                });
    }

    /**
     * Merges importable entries from configured namespace root policies into {@code resolvedPolicy}.
     * Local/imported entries always win: if a label already exists in {@code resolvedPolicy}, the corresponding
     * namespace root entry is skipped. Missing or deleted namespace root policies are logged as errors and silently
     * skipped — the policy continues to function without them.
     * <p>
     * Root policies are resolved in parallel for performance, then merged in precedence order
     * (exact match first, then more specific prefix wildcards, then broader, then catch-all).
     * </p>
     */
    private static CompletionStage<Policy> mergeNamespacePolicies(
            final Policy resolvedPolicy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver,
            final NamespacePoliciesConfig namespacePoliciesConfig) {

        final Optional<PolicyId> entityId = resolvedPolicy.getEntityId();
        final String namespace = resolvedPolicy.getNamespace().orElse("");
        final List<PolicyId> rootPolicies = namespacePoliciesConfig.getRootPoliciesForNamespace(namespace).stream()
                .filter(rootPolicyId -> !entityId.map(rootPolicyId::equals).orElse(false))
                .toList();

        if (rootPolicies.isEmpty()) {
            return CompletableFuture.completedFuture(resolvedPolicy);
        }

        final Map<PolicyId, CompletableFuture<Optional<Policy>>> resolutionFutures = new LinkedHashMap<>();
        for (final PolicyId rootPolicyId : rootPolicies) {
            resolutionFutures.put(rootPolicyId,
                    policyResolver.apply(rootPolicyId)
                            .thenCompose(rootPolicyOpt -> {
                                if (rootPolicyOpt.isEmpty()) {
                                    LOG.error("Namespace root policy <{}> for namespace <{}> does not exist" +
                                            " or was deleted - skipping its entries.", rootPolicyId, namespace);
                                    return CompletableFuture.completedFuture(Optional.<Policy>empty());
                                }
                                return rootPolicyOpt.get().withResolvedImports(policyResolver)
                                        .thenApply(Optional::of);
                            })
                            .toCompletableFuture());
        }

        final CompletableFuture<?>[] allFutures = resolutionFutures.values().toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(allFutures)
                .thenApply(ignored -> {
                    Policy result = resolvedPolicy;
                    for (final PolicyId rootPolicyId : rootPolicies) {
                        final Optional<Policy> rootPolicyOpt = resolutionFutures.get(rootPolicyId).join();
                        if (rootPolicyOpt.isPresent()) {
                            result = mergeImplicitEntries(rootPolicyOpt.get(), result);
                        }
                    }
                    return result;
                });
    }

    private static Policy mergeImplicitEntries(final Policy rootPolicy, final Policy currentPolicy) {
        Policy result = currentPolicy;
        for (final PolicyEntry entry : rootPolicy) {
            if (ImportableType.IMPLICIT.equals(entry.getImportableType()) && !result.contains(entry.getLabel())) {
                result = result.setEntry(entry);
            }
        }
        return result;
    }

    /**
     * Create a policy together with its enforcer.
     *
     * @param policy the policy
     * @return the pair
     */
    public static PolicyEnforcer of(final Policy policy) {
        return new PolicyEnforcer(policy, PolicyEnforcers.defaultEvaluator(policy));
    }

    /**
     * Create a cache entry containing an Enforcer extracted from the passed {@code policyEnforcerEntry}.
     *
     * @param policyEnforcerEntry a {@link PolicyEnforcer} containing both policy and enforcer.
     * @return the cache entry containing an Enforcer.
     */
    public static Entry<Enforcer> project(final Entry<PolicyEnforcer> policyEnforcerEntry) {
        if (policyEnforcerEntry.exists()) {
            return Entry.of(policyEnforcerEntry.getRevision(), policyEnforcerEntry.getValueOrThrow().getEnforcer());
        } else {
            return Entry.nonexistent();
        }
    }

    /**
     * Create a cache entry containing a PolicyEnforcer without policy.
     *
     * @param enforcerEntry the enforcer cache entry.
     * @return the cache entry containing a PolicyEnforcer without policy
     */
    public static Entry<PolicyEnforcer> embed(final Entry<Enforcer> enforcerEntry) {
        if (enforcerEntry.exists()) {
            return Entry.of(enforcerEntry.getRevision(), new PolicyEnforcer(null, enforcerEntry.getValueOrThrow()));
        } else {
            return Entry.nonexistent();
        }
    }

    /**
     * Retrieve the policy.
     *
     * @return the policy.
     */
    public Optional<Policy> getPolicy() {
        return Optional.ofNullable(policy);
    }

    /**
     * Retrieve the enforcer.
     *
     * @return the enforcer.
     */
    public Enforcer getEnforcer() {
        return enforcer;
    }

    /**
     * Returns a new {@code PolicyEnforcer} whose enforcer only considers policy entries applicable to the given
     * thing namespace. If no entries have namespace restrictions, returns {@code this} unchanged (optimization).
     * <p>
     * <strong>Note:</strong> The returned enforcer's {@link #getPolicy()} still returns the full unfiltered policy
     * (including entries that do not match the given namespace). Only {@link #getEnforcer()} is filtered. Callers
     * that need to inspect policy entries directly should use the enforcer for permission checks, not the policy.
     *
     * @param thingNamespace the namespace of the thing being enforced.
     * @return a {@code PolicyEnforcer} filtered for the given namespace.
     * @since 3.9.0
     */
    public PolicyEnforcer forNamespace(final String thingNamespace) {
        checkNotNull(thingNamespace, "thingNamespace");
        if (policy == null) {
            return this;
        }
        boolean anyFiltered = false;
        final List<PolicyEntry> filteredEntries = new ArrayList<>();
        for (final PolicyEntry entry : policy) {
            if (entry.appliesToNamespace(thingNamespace)) {
                filteredEntries.add(entry);
            } else {
                anyFiltered = true;
            }
        }
        if (!anyFiltered) {
            return this;
        }
        final Enforcer filteredEnforcer = PolicyEnforcers.defaultEvaluator(filteredEntries);
        return new PolicyEnforcer(policy, filteredEnforcer);
    }

}
