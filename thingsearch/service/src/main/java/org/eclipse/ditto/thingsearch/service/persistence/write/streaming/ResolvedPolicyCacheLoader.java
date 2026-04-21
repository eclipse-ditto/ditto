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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.enforcement.PolicyCacheLoader;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.slf4j.Logger;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

final class ResolvedPolicyCacheLoader
        implements AsyncCacheLoader<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>> {

    private static final Logger LOG = DittoLoggerFactory.getThreadSafeLogger(ResolvedPolicyCacheLoader.class);

    private final PolicyCacheLoader policyCacheLoader;
    private final CompletableFuture<Cache<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>>> cacheFuture;
    private final NamespacePoliciesConfig namespacePoliciesConfig;

    ResolvedPolicyCacheLoader(final PolicyCacheLoader policyCacheLoader,
            final CompletableFuture<Cache<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>>> cacheFuture,
            final NamespacePoliciesConfig namespacePoliciesConfig) {
        this.policyCacheLoader = policyCacheLoader;
        this.cacheFuture = cacheFuture;
        this.namespacePoliciesConfig = namespacePoliciesConfig;
    }

    @Override
    public CompletableFuture<? extends Entry<Pair<Policy, Set<PolicyTag>>>> asyncLoad(
            final PolicyIdResolvingImports policyIdResolvingImports,
            final Executor executor) {

        return policyCacheLoader.asyncLoad(policyIdResolvingImports.policyId(), executor)
                .thenComposeAsync(policyEntry -> {
                    if (policyEntry.exists()) {
                        final Policy policy = policyEntry.getValueOrThrow();
                        final long revision = policy.getRevision().map(PolicyRevision::toLong)
                                .orElseThrow(
                                        () -> new IllegalStateException("Bad SudoRetrievePolicyResponse: no revision"));
                        final Set<PolicyTag> referencedPolicies = ConcurrentHashMap.newKeySet();

                        if (policyIdResolvingImports.resolveImports()) {
                            return cacheFuture.thenComposeAsync(cache ->
                                            resolvePolicyImports(cache, policy, referencedPolicies)
                                                    .thenCompose(resolvedPolicy ->
                                                            mergeNamespaceRootPolicies(cache, resolvedPolicy,
                                                                    referencedPolicies, executor)),
                                            executor)
                                    .thenApplyAsync(resolvedPolicy ->
                                            Entry.of(revision, new Pair<>(resolvedPolicy, referencedPolicies)),
                                            executor);
                        } else {
                            return CompletableFuture.completedFuture(
                                    Entry.of(revision, new Pair<>(policy, referencedPolicies))
                            );
                        }
                    } else {
                        return CompletableFuture.completedFuture(Entry.nonexistent());
                    }
                }, executor);
    }

    /**
     * Merges implicit entries from configured namespace root policies into {@code resolvedPolicy}.
     * Root policies are loaded via the cache in parallel; their revisions are added to
     * {@code referencedPolicies} so that search index entries are invalidated when a root policy changes.
     * Local/imported entries always take precedence: if a label already exists in {@code resolvedPolicy},
     * the corresponding namespace root entry is skipped.
     */
    private CompletionStage<Policy> mergeNamespaceRootPolicies(
            final Cache<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>> cache,
            final Policy resolvedPolicy,
            final Set<PolicyTag> referencedPolicies,
            final Executor executor) {

        if (namespacePoliciesConfig.isEmpty()) {
            return CompletableFuture.completedFuture(resolvedPolicy);
        }

        final Optional<PolicyId> entityId = resolvedPolicy.getEntityId();
        final String namespace = resolvedPolicy.getNamespace().orElse("");
        final List<PolicyId> rootPolicies = namespacePoliciesConfig.getRootPoliciesForNamespace(namespace).stream()
                // Skip only the self-reference, while still allowing other matching roots such as a global catch-all.
                .filter(rootId -> !entityId.map(rootId::equals).orElse(false))
                .toList();

        if (rootPolicies.isEmpty()) {
            return CompletableFuture.completedFuture(resolvedPolicy);
        }

        final Map<PolicyId, CompletableFuture<Optional<Pair<Policy, Set<PolicyTag>>>>> futures =
                new LinkedHashMap<>();
        for (final PolicyId rootId : rootPolicies) {
            futures.put(rootId, resolveNamespaceRootPolicy(cache, rootId, namespace, executor));
        }

        return CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new))
                .thenApplyAsync(ignored -> {
                    Policy result = resolvedPolicy;
                    for (final PolicyId rootId : rootPolicies) {
                        final Optional<Pair<Policy, Set<PolicyTag>>> rootPolicyOpt =
                                futures.get(rootId).join();
                        if (rootPolicyOpt.isPresent()) {
                            referencedPolicies.addAll(rootPolicyOpt.get().second());
                            result = mergeImplicitEntries(rootPolicyOpt.get().first(), result);
                        }
                    }
                    return result;
                }, executor);
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

    private static CompletionStage<Policy> resolvePolicyImports(
            final Cache<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>> cache,
            final Policy policy,
            final Set<PolicyTag> referencedPolicies) {

        // Track transitive policy IDs as referenced policies for search index invalidation.
        // These lookups run in parallel with the main import resolution below.
        // See also PolicyImports.getExpectedReferencedPolicyIds() for the authoritative set of
        // referenced IDs (used in BackgroundSyncStream for consistency checks).
        final List<CompletableFuture<Void>> transitiveTagFutures = policy.getPolicyImports().stream()
                .flatMap(imp -> imp.getTransitiveImports().stream())
                .distinct()
                .map(transitivePolicyId ->
                        cache.get(new PolicyIdResolvingImports(transitivePolicyId, false))
                                .thenApply(entry -> entry.flatMap(Entry::get))
                                .thenAccept(optionalRefPolicy -> optionalRefPolicy.ifPresent(refPair ->
                                        addPolicyTag(refPair.first(), referencedPolicies)))
                                .toCompletableFuture()
                )
                .collect(Collectors.toList());

        final CompletableFuture<Void> allTransitiveTags =
                CompletableFuture.allOf(transitiveTagFutures.toArray(CompletableFuture[]::new));

        final CompletionStage<Policy> resolvedPolicyCs = policy.withResolvedImports(importedPolicyId ->
                cache.get(new PolicyIdResolvingImports(importedPolicyId, false)) // don't transitively resolve imports, only 1 "level"
                        .thenApply(entry -> entry.flatMap(Entry::get))
                        .thenApply(optionalReferencedPolicy -> {
                            if (optionalReferencedPolicy.isPresent()) {
                                final Policy referencedPolicy = optionalReferencedPolicy.get().first();
                                final Optional<PolicyRevision> refRevision = referencedPolicy.getRevision();
                                final Optional<PolicyId> entityId = referencedPolicy.getEntityId();
                                if (refRevision.isPresent() && entityId.isPresent()) {
                                    referencedPolicies.add(PolicyTag.of(entityId.get(), refRevision.get().toLong()));
                                }
                            }
                            return optionalReferencedPolicy.map(Pair::first);
                        })
        );

        // Wait for both transitive tag tracking and import resolution to complete
        return resolvedPolicyCs.thenCombine(allTransitiveTags, (resolvedPolicy, ignored) -> resolvedPolicy);
    }

    private CompletableFuture<Optional<Pair<Policy, Set<PolicyTag>>>> resolveNamespaceRootPolicy(
            final Cache<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>> cache,
            final PolicyId rootPolicyId,
            final String namespace,
            final Executor executor) {

        return cache.get(new PolicyIdResolvingImports(rootPolicyId, true))
                .thenApplyAsync(entry -> entry.flatMap(Entry::get), executor)
                .thenApplyAsync(optionalResolvedRootPolicy -> {
                    if (optionalResolvedRootPolicy.isEmpty()) {
                        LOG.error("Namespace root policy <{}> for namespace <{}> does not exist or was deleted - " +
                                "skipping its entries.", rootPolicyId, namespace);
                        return Optional.empty();
                    }

                    final Pair<Policy, Set<PolicyTag>> resolvedRootPolicy = optionalResolvedRootPolicy.get();
                    final Set<PolicyTag> rootReferencedPolicies = new LinkedHashSet<>(resolvedRootPolicy.second());
                    addPolicyTag(resolvedRootPolicy.first(), rootReferencedPolicies);
                    return Optional.of(new Pair<>(resolvedRootPolicy.first(), rootReferencedPolicies));
                }, executor);
    }

    private static void addPolicyTag(final Policy policy, final Set<PolicyTag> referencedPolicies) {
        policy.getRevision()
                .flatMap(revision -> policy.getEntityId()
                        .map(entityId -> PolicyTag.of(entityId, revision.toLong())))
                .ifPresent(referencedPolicies::add);
    }

}
