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
import java.util.function.BiConsumer;
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
     * Maximum depth for transitive import resolution. Pure cycles are normally caught earlier by the
     * {@code visited} set in {@link #resolveTransitiveImports}; this limit is a hard ceiling that also
     * fires for any legitimately deep chain.
     * <p>
     * When this limit is reached, resolution fails fast with a {@link PolicyImportInvalidException}
     * (HTTP 400) so the caller learns the policy graph is invalid rather than silently receiving a
     * partially-resolved view. The exception is thrown without {@code DittoHeaders}; the calling layer
     * is expected to attach request headers via
     * {@code org.eclipse.ditto.base.model.exceptions.DittoRuntimeException#setDittoHeaders}.
     * <p>
     * Cross-policy cycles (A→B→C→A) are not rejected at write time because cycle detection across
     * multiple independently-managed policies would require loading the full transitive graph on every
     * PUT. They are detected at resolution time: the {@code visited} set short-circuits the cycle on
     * the second encounter, and any chain that nevertheless exceeds this depth is rejected here.
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
            final int depth,
            final boolean applyImportPrefix,
            final Set<PolicyId> visited
    ) {
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
                        // Resolve the loaded policy's own entry references before importing.
                        // This ensures entries that inherit resources/subjects via references
                        // have those values materialized before the references are stripped
                        // during label rewriting.
                        final Set<PolicyEntry> withResolvedRefs =
                                resolveReferences(loadedPolicy, resolvedEntries);
                        final ImportedLabels importedLabels = policyImport.getEffectedImports()
                                .map(EffectedImports::getImportedLabels)
                                .orElse(ImportedLabels.none());
                        return rewriteImportedLabels(importedPolicyId, withResolvedRefs,
                                importedLabels, applyImportPrefix);
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
     * filters and {@code references} resolution.
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
            final Set<PolicyId> visited
    ) {
        if (depth >= MAX_TRANSITIVE_RESOLUTION_DEPTH) {
            final PolicyId loadedPolicyId = loadedPolicy.getEntityId().orElse(null);
            throw PolicyImportInvalidException.newBuilder()
                    .message("The policy import graph exceeded the maximum transitive resolution depth of " +
                            MAX_TRANSITIVE_RESOLUTION_DEPTH + ".")
                    .description("Resolution stopped at policy '" + loadedPolicyId +
                            "'. This usually indicates a cycle across imported policies' " +
                            "transitiveImports declarations or an excessively deep import chain. " +
                            "Shorten the chain or break the cycle by removing one of the transitiveImports entries.")
                    .build();
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

        // Resolve filtered imports WITH label prefixing so transitive entries don't collide
        // with the loaded policy's own entries (e.g. both having a "driver" entry). The prefix
        // allows resolveReferences to find the transitive entries by their import-prefixed labels.
        // The outer resolution applies its own prefix on top.
        return mergeImportedPolicyEntries(loadedPolicy.getEntriesSet(), filteredImportsList,
                policyLoader, depth + 1, true, unmodifiableVisited);
    }

    private static Set<PolicyEntry> rewriteImportedLabels(final PolicyId importedPolicyId,
            final Set<PolicyEntry> importedEntries, final Collection<Label> importedLabels,
            final boolean applyImportPrefix) {

        return importedEntries.stream()
                .flatMap(importedEntry -> importEntry(importedLabels, importedEntry))
                .map(entry -> rewriteLabel(importedPolicyId, entry, applyImportPrefix))
                .collect(Collectors.toSet());
    }

    // Uses the 6-parameter factory intentionally: references are
    // local concepts of the source policy and are not carried over during import.
    private static PolicyEntry rewriteLabel(final PolicyId importedPolicyId,
            final PolicyEntry entry, final boolean applyImportPrefix) {

        final Label finalLabel = applyImportPrefix
                ? PoliciesModelFactory.newImportedLabel(importedPolicyId, entry.getLabel())
                : entry.getLabel();

        return PoliciesModelFactory.newPolicyEntry(
                finalLabel,
                entry.getSubjects(),
                entry.getResources(),
                entry.getNamespaces().orElse(null),
                entry.getImportableType(),
                entry.getAllowedAdditions().orElse(null)
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

    /**
     * Resolves {@link EntryReference}s on the importing policy's own entries. Each entry may declare a
     * {@code references} array containing import references (pointing to imported policy entries) and/or
     * local references (pointing to entries within the same policy).
     * <p>
     * For import references, the referenced entry is looked up in the resolved set (label-prefixed imported entries).
     * For local references, the referenced entry is looked up directly in the importing policy.
     * In both cases, resources, namespaces, and subjects are additively merged.
     *
     * @param importingPolicy the policy whose entries may contain references.
     * @param resolvedEntries the full set of resolved entries (own + imported, with prefixed labels).
     * @return a new set with references resolved (merged with referenced entry content).
     * @since 3.9.0
     */
    public static Set<PolicyEntry> resolveReferences(final Policy importingPolicy,
            final Set<PolicyEntry> resolvedEntries) {
        return resolveReferences(importingPolicy, resolvedEntries, (entry, ref) -> { /* no-op */ });
    }

    /**
     * Variant of {@link #resolveReferences(Policy, Set)} that invokes {@code onMissingReference} when a
     * reference cannot be resolved (the referenced entry is absent from the resolved set or from the
     * importing policy). The callback receives the entry holding the reference and the reference itself,
     * allowing callers in higher layers (e.g. enforcement) to log a diagnostic without the model module
     * taking on a logging dependency.
     *
     * @param importingPolicy the policy whose entries may contain references.
     * @param resolvedEntries the full set of resolved entries (own + imported, with prefixed labels).
     * @param onMissingReference invoked once per reference that fails to resolve; never null.
     * @return a new set with references resolved (merged with referenced entry content).
     * @since 3.9.0
     */
    public static Set<PolicyEntry> resolveReferences(final Policy importingPolicy,
            final Set<PolicyEntry> resolvedEntries,
            final BiConsumer<PolicyEntry, EntryReference> onMissingReference) {
        return resolveReferences(importingPolicy, resolvedEntries, onMissingReference,
                (entry, stripped) -> { /* no-op */ });
    }

    /**
     * Variant of {@link #resolveReferences(Policy, Set, BiConsumer)} that also reports when the
     * referencing entry's own additions (subjects, resources, namespaces) are silently stripped at
     * resolution time because at least one referenced entry's {@code allowedAdditions} excludes
     * those kinds. The {@code onOwnAdditionsStripped} callback receives the referencing entry and
     * the set of {@link AllowedAddition} kinds that survived (may be empty for deny-all). Callers
     * can combine this with the missing-reference callback to surface the two main causes of
     * silent permission degradation.
     *
     * @param importingPolicy the policy whose entries may contain references.
     * @param resolvedEntries the full set of resolved entries (own + imported, with prefixed labels).
     * @param onMissingReference invoked once per reference that fails to resolve; never null.
     * @param onOwnAdditionsStripped invoked once per referencing entry whose own additions are
     * partially or fully stripped at enforcement time; receives the entry and the set of surviving
     * addition kinds; never null.
     * @return a new set with references resolved (merged with referenced entry content).
     * @since 3.9.0
     */
    public static Set<PolicyEntry> resolveReferences(final Policy importingPolicy,
            final Set<PolicyEntry> resolvedEntries,
            final BiConsumer<PolicyEntry, EntryReference> onMissingReference,
            final BiConsumer<PolicyEntry, Set<AllowedAddition>> onOwnAdditionsStripped) {

        final Set<PolicyEntry> result = new LinkedHashSet<>(resolvedEntries);
        for (final PolicyEntry ownEntry : importingPolicy) {
            final List<EntryReference> refs = ownEntry.getReferences();
            if (!refs.isEmpty()) {
                final PolicyEntry merged = resolveAllReferences(importingPolicy, resolvedEntries, ownEntry,
                        onMissingReference, onOwnAdditionsStripped);
                result.remove(ownEntry);
                result.add(merged);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Resolves all references on a single entry. Subjects, resources, and namespaces from each
     * referenced entry are accumulated independently, then the own entry's additions are merged
     * in only if every referenced entry's {@code allowedAdditions} permits them.
     * <p>
     * Local and import references share the same merge semantics for {@code importable=never}
     * skipping and {@code allowedAdditions} filtering:
     * <ul>
     *   <li>References to entries marked {@code importable=never} are skipped (the author's
     *       "do-not-inherit-from-me" signal applies to both kinds).</li>
     *   <li>Each referenced entry's {@code allowedAdditions} contributes to the strictest
     *       intersection. A referenced entry that does NOT explicitly declare
     *       {@code allowedAdditions} imposes no restriction (absent ≠ deny-all). Only
     *       explicit declarations narrow the effective set.</li>
     * </ul>
     * <p>
     * References do <strong>not</strong> chain transitively at this resolution level: looking up a
     * referenced entry yields its raw content (the referenced entry's own subjects/resources/
     * namespaces), not a recursively-resolved view of that entry's own references. This avoids
     * unbounded recursion within a single policy and keeps resolution semantics predictable.
     * Cross-policy chains (where an entry in an imported policy references a third policy's entry)
     * only succeed when the importing policy declares the deeper imports via {@code transitiveImports}
     * on the import — without that, the deeper reference target is not in the resolved set and is
     * silently skipped. Within a single policy, local references resolve one level deep, period.
     */
    private static PolicyEntry resolveAllReferences(final Policy importingPolicy,
            final Set<PolicyEntry> resolvedEntries, final PolicyEntry ownEntry,
            final BiConsumer<PolicyEntry, EntryReference> onMissingReference,
            final BiConsumer<PolicyEntry, Set<AllowedAddition>> onOwnAdditionsStripped) {

        Resources accumulatedResources = PoliciesModelFactory.emptyResources();
        Subjects accumulatedSubjects = PoliciesModelFactory.emptySubjects();
        List<String> accumulatedNamespaces = Collections.emptyList();
        // Strictest allowedAdditions across all referenced entries that explicitly declared one.
        // null = no referenced entry has restricted additions yet (default = unrestricted).
        Set<AllowedAddition> effectiveAllowed = null;

        for (final EntryReference ref : ownEntry.getReferences()) {
            final Optional<PolicyEntry> referencedEntryOpt = lookupReference(importingPolicy, resolvedEntries, ref);
            if (!referencedEntryOpt.isPresent()) {
                onMissingReference.accept(ownEntry, ref);
                continue;
            }
            final PolicyEntry referencedEntry = referencedEntryOpt.get();
            if (referencedEntry.getImportableType() == ImportableType.NEVER) {
                continue;
            }

            accumulatedResources = mergeResources(accumulatedResources, referencedEntry.getResources());
            accumulatedNamespaces = mergeNamespaces(
                    accumulatedNamespaces.isEmpty() ? null : accumulatedNamespaces,
                    referencedEntry.getNamespaces().orElse(Collections.emptyList()));
            accumulatedSubjects = mergeSubjects(accumulatedSubjects, referencedEntry.getSubjects());

            // Narrow effective allowedAdditions only when the target explicitly declares it.
            // Absent contributes nothing (no restriction); explicit empty intersects to deny-all.
            final Optional<Set<AllowedAddition>> templateAllowedOpt =
                    referencedEntry.getAllowedAdditions();
            if (templateAllowedOpt.isPresent()) {
                final Set<AllowedAddition> templateAllowed = templateAllowedOpt.get();
                if (effectiveAllowed == null) {
                    effectiveAllowed = new LinkedHashSet<>(templateAllowed);
                } else {
                    effectiveAllowed.retainAll(templateAllowed);
                }
            }
        }

        final boolean hasRestrictions = effectiveAllowed != null;
        final boolean resourcesAllowed = !hasRestrictions ||
                effectiveAllowed.contains(AllowedAddition.RESOURCES);
        final boolean subjectsAllowed = !hasRestrictions ||
                effectiveAllowed.contains(AllowedAddition.SUBJECTS);
        final boolean namespacesAllowed = !hasRestrictions ||
                effectiveAllowed.contains(AllowedAddition.NAMESPACES);

        // Notify only when restrictions are in play AND the entry actually had own additions of
        // a now-disallowed kind. Empty own-fields produce no observable strip, so don't fire.
        if (hasRestrictions) {
            final boolean strippedResources = !resourcesAllowed && !ownEntry.getResources().isEmpty();
            final boolean strippedSubjects = !subjectsAllowed && !ownEntry.getSubjects().isEmpty();
            final boolean strippedNamespaces = !namespacesAllowed &&
                    ownEntry.getNamespaces().map(ns -> !ns.isEmpty()).orElse(false);
            if (strippedResources || strippedSubjects || strippedNamespaces) {
                onOwnAdditionsStripped.accept(ownEntry, Collections.unmodifiableSet(effectiveAllowed));
            }
        }

        final Resources finalResources = resourcesAllowed
                ? mergeResources(accumulatedResources, ownEntry.getResources())
                : accumulatedResources;
        final Subjects finalSubjects = subjectsAllowed
                ? mergeSubjects(accumulatedSubjects, ownEntry.getSubjects())
                : accumulatedSubjects;
        final List<String> finalNamespaces = namespacesAllowed
                ? mergeNamespaces(
                        accumulatedNamespaces.isEmpty() ? null : accumulatedNamespaces,
                        ownEntry.getNamespaces().orElse(Collections.emptyList()))
                : accumulatedNamespaces;

        return PoliciesModelFactory.newPolicyEntry(
                ownEntry.getLabel(),
                finalSubjects,
                finalResources,
                finalNamespaces.isEmpty() ? null : finalNamespaces,
                ownEntry.getImportableType(),
                effectiveAllowed,
                ownEntry.getReferences()
        );
    }

    private static Optional<PolicyEntry> lookupReference(final Policy importingPolicy,
            final Set<PolicyEntry> resolvedEntries, final EntryReference ref) {
        if (ref.isImportReference()) {
            final Label referencedLabel = PoliciesModelFactory.newImportedLabel(
                    ref.getImportedPolicyId().orElseThrow(() ->
                            new IllegalStateException("Import reference without imported policy ID")),
                    ref.getEntryLabel());
            return resolvedEntries.stream()
                    .filter(e -> e.getLabel().equals(referencedLabel))
                    .findFirst();
        } else {
            return importingPolicy.getEntryFor(ref.getEntryLabel());
        }
    }

    private static Subjects mergeSubjects(final Subjects template, final Subjects additional) {
        final List<Subject> merged = new ArrayList<>();
        template.forEach(merged::add);
        final Set<String> seenIds = merged.stream()
                .map(s -> s.getId().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (final Subject subject : additional) {
            if (seenIds.add(subject.getId().toString())) {
                merged.add(subject);
            }
        }
        return PoliciesModelFactory.newSubjects(merged);
    }
}
