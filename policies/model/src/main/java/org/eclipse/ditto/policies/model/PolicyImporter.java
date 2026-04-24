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
package org.eclipse.ditto.policies.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Policy model functionality used in order to perform the importing/merging of imported {@link PolicyEntry}s into the
 * importing Policy.
 *
 * @since 3.1.0
 */
public final class PolicyImporter {

    /**
     * Maximum depth for transitive import resolution. Prevents infinite recursion caused by mutual
     * transitive cycles (e.g., A→B with transitiveImports=["C"], B→C with transitiveImports=["A"]).
     * <p>
     * When this limit is reached, resolution stops and returns the loaded policy's own entries without
     * recursing further. This is intentionally silent at the model layer (no SLF4J dependency in model
     * modules). Callers that need diagnostics should check the resolved entry count or implement their
     * own depth tracking.
     * <p>
     * Cross-policy cycles (A→B→C→A) are not rejected at write time because cycle detection across
     * multiple independently-managed policies would require loading the full transitive graph on every
     * PUT. Instead, cycles are broken gracefully at resolution time via the {@code visited} set and
     * this depth limit.
     */
    public static final int MAX_TRANSITIVE_RESOLUTION_DEPTH = 10;

    private PolicyImporter() {
        throw new AssertionError();
    }

    /**
     * Merges potentially {@code imported} {@link PolicyEntry}s from the passed {@code policy} into the policy entries
     * of this passed policy returning a new Set with the combined/merged policy entries.
     *
     * @param policy the Policy to use all contained {@link PolicyEntry}s from, importing configured
     * {@link PolicyImports} by using the provided {@code policyLoader} used to resolve/load the imported policies.
     * @param policyLoader a function to load imported policies, e.g. provided by a cache.
     * @return a combined set of existing {@link PolicyEntry}s from the passed {@code policy} merged with policy entries
     * from imported policies.
     */
    public static CompletionStage<Set<PolicyEntry>> mergeImportedPolicyEntries(final Policy policy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyLoader) {
        final List<PolicyImport> imports = policy.getPolicyImports().stream().collect(Collectors.toList());
        return mergeImportedPolicyEntries(policy.getEntriesSet(), imports, policyLoader, 0, true,
                Collections.emptySet());
    }

    /**
     * Resolves the given {@code policyImports} using the {@code policyLoader}, merging the resulting entries
     * with the provided {@code baseEntries}. All imports are resolved in parallel; their entry sets are
     * collected in a single pass to avoid O(n²) intermediate copies.
     *
     * <p>
     * <b>Merge precedence:</b> The result uses a {@link LinkedHashSet}, so when two imports produce entries
     * with the same label, the first-encountered entry wins (based on import declaration order). This is
     * a deterministic, order-dependent merge — not a conflict error.
     *
     * @param baseEntries the policy's own entries (starting set for the merge).
     * @param policyImports the imports to resolve.
     * @param policyLoader a function to load imported policies, e.g. provided by a cache.
     * @param depth current transitive resolution depth.
     * @param applyImportPrefix whether to prefix imported entry labels with the imported policy ID.
     * @param visited policy IDs already being resolved in the current chain (cycle detection).
     * @return a combined set of {@code baseEntries} merged with entries from all resolved imports.
     */
    private static CompletionStage<Set<PolicyEntry>> mergeImportedPolicyEntries(
            final Set<PolicyEntry> baseEntries,
            final List<PolicyImport> policyImports,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyLoader,
            final int depth, final boolean applyImportPrefix,
            final Set<PolicyId> visited) {

        if (policyImports.isEmpty()) {
            return CompletableFuture.completedFuture(baseEntries);
        }

        final List<CompletableFuture<Set<PolicyEntry>>> importFutures = policyImports.stream()
                .map(policyImport -> resolveImport(policyImport, policyLoader, depth, applyImportPrefix,
                        visited).toCompletableFuture())
                .collect(Collectors.toList());

        return CompletableFuture.allOf(importFutures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    final Set<PolicyEntry> result = new LinkedHashSet<>(baseEntries);
                    for (final CompletableFuture<Set<PolicyEntry>> future : importFutures) {
                        result.addAll(future.join());
                    }
                    return Collections.unmodifiableSet(result);
                });
    }

    /**
     * Resolves a single policy import: loads the imported policy, optionally resolves its transitive
     * imports, then filters and rewrites the resulting entries.
     */
    private static CompletionStage<Set<PolicyEntry>> resolveImport(
            final PolicyImport policyImport,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyLoader,
            final int depth, final boolean applyImportPrefix,
            final Set<PolicyId> visited) {

        final PolicyId importedPolicyId = policyImport.getImportedPolicyId();
        return policyLoader.apply(importedPolicyId).thenCompose(loadedPolicyOpt ->
                loadedPolicyOpt.map(loadedPolicy -> {
                    final List<PolicyId> transitiveIds = policyImport.getTransitiveImports();
                    final CompletionStage<Set<PolicyEntry>> resolvedEntriesCs;
                    if (!transitiveIds.isEmpty()) {
                        resolvedEntriesCs = resolveTransitiveImports(
                                loadedPolicy, transitiveIds, policyLoader, depth, visited);
                    } else {
                        resolvedEntriesCs = CompletableFuture.completedFuture(loadedPolicy.getEntriesSet());
                    }
                    return resolvedEntriesCs.thenApply(resolvedEntries -> {
                        final ImportedLabels importedLabels = policyImport.getEffectedImports()
                                .map(EffectedImports::getImportedLabels)
                                .orElse(ImportedLabels.none());
                        final EntriesAdditions entriesAdditions = policyImport.getEntriesAdditions()
                                .orElse(null);
                        return rewriteImportedLabels(importedPolicyId, resolvedEntries,
                                importedLabels, entriesAdditions, applyImportPrefix);
                    });
                }).orElse(CompletableFuture.completedFuture(Collections.emptySet())));
    }

    /**
     * Resolves only the specified transitive imports on the loaded policy.
     * Filters the loaded policy's imports to only those that appear in {@code transitiveIds},
     * resolves those imports into entries, and returns the combined entry set (loaded policy's own
     * entries plus resolved transitive entries).
     * <p>
     * Entries resolved from transitive imports are added with their original labels (no import prefix),
     * so that the outer resolution can correctly apply the single import prefix and match {@code entries}
     * filters and {@code entriesAdditions} keys.
     * <p>
     * Cycle detection: transitive IDs that appear in {@code visited} are skipped to prevent infinite
     * recursion. Each level creates an immutable copy of the visited set with the current transitive IDs
     * added, so parallel import processing within {@code mergeImportedPolicyEntries} is safe.
     *
     * @param loadedPolicy the directly imported policy (persisted state).
     * @param transitiveIds the whitelisted policy IDs to resolve transitively.
     * @param policyLoader a function to load policies by ID.
     * @param depth the current transitive resolution depth (bounded by {@link #MAX_TRANSITIVE_RESOLUTION_DEPTH}).
     * @param visited policy IDs already being resolved in the current chain (cycle detection).
     * @return the loaded policy's entries merged with entries from the transitive imports.
     * @since 3.9.0
     */
    private static CompletionStage<Set<PolicyEntry>> resolveTransitiveImports(
            final Policy loadedPolicy,
            final List<PolicyId> transitiveIds,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyLoader,
            final int depth,
            final Set<PolicyId> visited) {

        if (depth >= MAX_TRANSITIVE_RESOLUTION_DEPTH) {
            return CompletableFuture.completedFuture(loadedPolicy.getEntriesSet());
        }

        // Filter out already-visited transitive IDs to break cycles
        final Set<PolicyId> transitiveIdSet = new LinkedHashSet<>(transitiveIds);
        transitiveIdSet.removeAll(visited);

        // Filter the loaded policy's imports to only those matching the transitive whitelist.
        // IDs that don't match any actual import are silently ignored (lenient / forward-reference semantics).
        final List<PolicyImport> filteredImportsList = loadedPolicy.getPolicyImports().stream()
                .filter(imp -> transitiveIdSet.contains(imp.getImportedPolicyId()))
                .collect(Collectors.toList());

        if (filteredImportsList.isEmpty()) {
            return CompletableFuture.completedFuture(loadedPolicy.getEntriesSet());
        }

        // Immutable snapshot: current visited set + transitive IDs being resolved at this level.
        // Each recursive call gets its own copy, so parallel import processing is safe.
        final Set<PolicyId> newVisited = new HashSet<>(visited);
        newVisited.addAll(transitiveIdSet);
        final Set<PolicyId> unmodifiableVisited = Collections.unmodifiableSet(newVisited);

        // Resolve filtered imports without label prefixing — the entries are merged into the
        // loaded policy's entries as if they were its own inline entries. The outer resolution
        // applies the prefix.
        return mergeImportedPolicyEntries(loadedPolicy.getEntriesSet(), filteredImportsList,
                policyLoader, depth + 1, false, unmodifiableVisited);
    }

    private static Set<PolicyEntry> rewriteImportedLabels(final PolicyId importedPolicyId,
            final Set<PolicyEntry> importedEntries, final Collection<Label> importedLabels,
            @Nullable final EntriesAdditions entriesAdditions, final boolean applyImportPrefix) {

        return importedEntries.stream()
                .flatMap(importedEntry -> importEntry(importedLabels, importedEntry))
                .map(entry -> applyAdditionsAndRewrite(importedPolicyId, entry, entriesAdditions,
                        applyImportPrefix))
                .collect(Collectors.toSet());
    }

    private static PolicyEntry applyAdditionsAndRewrite(final PolicyId importedPolicyId,
            final PolicyEntry entry, @Nullable final EntriesAdditions entriesAdditions,
            final boolean applyImportPrefix) {

        Subjects mergedSubjects = entry.getSubjects();
        Resources mergedResources = entry.getResources();
        List<String> mergedNamespaces = entry.getNamespaces().orElse(null);

        if (entriesAdditions != null) {
            final Optional<EntryAddition> addition = entriesAdditions.getAddition(entry.getLabel());
            if (addition.isPresent()) {
                final EntryAddition add = addition.get();
                final Set<AllowedImportAddition> allowed = entry.getAllowedImportAdditions()
                        .orElse(Collections.emptySet());
                if (add.getSubjects().isPresent() && allowed.contains(AllowedImportAddition.SUBJECTS)) {
                    mergedSubjects = mergeSubjects(mergedSubjects, add.getSubjects().get());
                }
                if (add.getResources().isPresent() && allowed.contains(AllowedImportAddition.RESOURCES)) {
                    mergedResources = mergeResources(mergedResources, add.getResources().get());
                }
                if (add.getNamespaces().isPresent() && allowed.contains(AllowedImportAddition.NAMESPACES)) {
                    mergedNamespaces = mergeNamespaces(mergedNamespaces, add.getNamespaces().get());
                }
            }
        }

        final Label finalLabel = applyImportPrefix
                ? PoliciesModelFactory.newImportedLabel(importedPolicyId, entry.getLabel())
                : entry.getLabel();

        return PoliciesModelFactory.newPolicyEntry(
                finalLabel,
                mergedSubjects,
                mergedResources,
                mergedNamespaces,
                entry.getImportableType(),
                entry.getAllowedImportAdditions().orElse(null)
        );
    }

    private static Stream<PolicyEntry> importEntry(final Collection<Label> importedLabels,
            final PolicyEntry importedEntry) {
        switch (importedEntry.getImportableType()) {
            case IMPLICIT:
                return Stream.of(importedEntry);
            case EXPLICIT:
                return importedLabels.contains(importedEntry.getLabel()) ? Stream.of(importedEntry) : Stream.empty();
            case NEVER:
            default:
                return Stream.empty();
        }
    }

    private static Subjects mergeSubjects(final Subjects templateSubjects, final Subjects additionalSubjects) {
        return templateSubjects.setSubjects(additionalSubjects);
    }

    // Note: inner merge is O(k) per resource due to immutable copy-on-write in Resources.setResource.
    // The overall merge across all entries is O(n·k) where n = entries and k = resources per entry.
    private static Resources mergeResources(final Resources templateResources, final Resources additionalResources) {
        Resources result = templateResources;
        for (final Resource additionalResource : additionalResources) {
            final Optional<Resource> existingOpt = templateResources.getResource(additionalResource.getResourceKey());
            if (existingOpt.isPresent()) {
                result = result.setResource(mergeResource(existingOpt.get(), additionalResource));
            } else {
                result = result.setResource(additionalResource);
            }
        }
        return result;
    }

    private static Resource mergeResource(final Resource templateResource, final Resource additionalResource) {
        final EffectedPermissions templatePerms = templateResource.getEffectedPermissions();
        final EffectedPermissions additionalPerms = additionalResource.getEffectedPermissions();

        final Set<String> mergedGrants = new LinkedHashSet<>(templatePerms.getGrantedPermissions());
        mergedGrants.addAll(additionalPerms.getGrantedPermissions());

        final Set<String> mergedRevokes = new LinkedHashSet<>(templatePerms.getRevokedPermissions());
        mergedRevokes.addAll(additionalPerms.getRevokedPermissions());

        return PoliciesModelFactory.newResource(
                templateResource.getResourceKey(),
                PoliciesModelFactory.newEffectedPermissions(mergedGrants, mergedRevokes)
        );
    }

    private static List<String> mergeNamespaces(@Nullable final List<String> templateNamespaces,
            final List<String> additionalNamespaces) {
        final Set<String> merged = templateNamespaces != null
                ? new LinkedHashSet<>(templateNamespaces)
                : new LinkedHashSet<>();
        merged.addAll(additionalNamespaces);
        return new ArrayList<>(merged);
    }
}
