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
package org.eclipse.ditto.policies.service.enforcement;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;

/**
 * Loads the policies contributing to the resolved policy view ({@code policy-view=resolved}) of a single request.
 * <p>
 * Every policy loaded via {@link #apply(PolicyId)} is restricted to what the caller may READ in that policy itself.
 * The requested policy, its imports, transitive imports, namespace root policies with their imports and
 * {@code references} are all resolved via this loader, so the merged policy can only contain content the caller may
 * read in the policy it originates from.
 * </p>
 * <p>
 * Each policy and the enforcer used to restrict it are loaded only once per request, so the merged policy and the
 * source-side READ checks are based on the same state of every policy.
 * </p>
 * <p>
 * Instances are not shared between requests.
 * </p>
 */
final class CallerReadablePolicyLoader implements Function<PolicyId, CompletionStage<Optional<Policy>>> {

    private static final JsonKey SUBJECTS = PolicyEntry.JsonFields.SUBJECTS.getPointer().getRoot().orElseThrow();
    private static final JsonKey RESOURCES = PolicyEntry.JsonFields.RESOURCES.getPointer().getRoot().orElseThrow();
    private static final JsonKey NAMESPACES = PolicyEntry.JsonFields.NAMESPACES.getPointer().getRoot().orElseThrow();

    private final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver;
    private final NamespacePoliciesConfig namespacePoliciesConfig;
    private final AuthorizationContext authorizationContext;

    private final Map<PolicyId, CompletableFuture<Optional<Policy>>> rawPolicies = new ConcurrentHashMap<>();
    private final Map<PolicyId, CompletableFuture<Optional<PolicyEnforcer>>> sourceEnforcers =
            new ConcurrentHashMap<>();
    private final Map<PolicyId, CompletableFuture<Optional<Policy>>> readablePolicies = new ConcurrentHashMap<>();

    CallerReadablePolicyLoader(final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver,
            final NamespacePoliciesConfig namespacePoliciesConfig,
            final AuthorizationContext authorizationContext) {
        this.policyResolver = policyResolver;
        this.namespacePoliciesConfig = namespacePoliciesConfig;
        this.authorizationContext = authorizationContext;
    }

    /**
     * Loads the policy with the given ID restricted to what the caller may READ in it.
     *
     * @param policyId the ID of the policy to load.
     * @return the restricted policy or an empty Optional if the policy does not exist.
     */
    @Override
    public CompletionStage<Optional<Policy>> apply(final PolicyId policyId) {
        return memoize(readablePolicies, policyId, () -> loadRawPolicy(policyId)
                .thenCombine(getSourceEnforcer(policyId), (policyOpt, enforcerOpt) -> policyOpt.flatMap(
                        policy -> enforcerOpt.map(enforcer -> restrictToReadable(policy, enforcer.getEnforcer())))));
    }

    /**
     * Returns the enforcer of the policy with the given ID including its imports and namespace root policies, i.e.
     * the enforcer which decides what the caller may READ in that policy.
     *
     * @param policyId the ID of the policy.
     * @return the enforcer or an empty Optional if the policy does not exist.
     */
    CompletableFuture<Optional<PolicyEnforcer>> getSourceEnforcer(final PolicyId policyId) {
        // Source-side READ views must see the same effective grants the caller has on the source policy at
        // enforcement time, which includes operator-configured namespace-root entries. Without the ns-policy
        // merge, a caller whose READ on the source comes only via the namespace-root (e.g. a global devops
        // admin) has no READ on any entry of the source and silently loses all imported entries in the
        // resolved view.
        return memoize(sourceEnforcers, policyId, () -> loadRawPolicy(policyId)
                .thenCompose(policyOpt -> policyOpt
                        .map(policy -> PolicyEnforcer.withResolvedImportsAndNamespacePolicies(policy,
                                        this::loadRawPolicy, namespacePoliciesConfig)
                                .thenApply(Optional::of))
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()))));
    }

    /**
     * Loads the policy with the given ID unrestricted, i.e. as it is persisted.
     *
     * @param policyId the ID of the policy to load.
     * @return the policy or an empty Optional if the policy does not exist.
     */
    CompletableFuture<Optional<Policy>> loadRawPolicy(final PolicyId policyId) {
        return memoize(rawPolicies, policyId, () -> policyResolver.apply(policyId).toCompletableFuture());
    }

    /**
     * Restricts the entries of the passed policy to what the caller may READ of them. A subject or resource is only
     * kept if it is completely readable, as the policy model cannot express partially readable ones. Entries without
     * anything readable are removed.
     */
    private Policy restrictToReadable(final Policy policy, final Enforcer enforcer) {
        Policy result = policy;
        for (final PolicyEntry entry : policy) {
            final JsonObject entryJson = entry.toJson();
            final JsonObject readableJson = enforcer.buildJsonView(
                    PoliciesResourceType.policyResource("/entries/" + entry.getLabel()), entryJson,
                    authorizationContext, Permission.READ);
            if (readableJson.isEmpty()) {
                result = result.removeEntry(entry.getLabel());
            } else {
                result = result.setEntry(restrictToReadable(entry, entryJson, readableJson));
            }
        }
        return result;
    }

    private static PolicyEntry restrictToReadable(final PolicyEntry entry, final JsonObject entryJson,
            final JsonObject readableJson) {

        final List<Subject> readableSubjects = entry.getSubjects().stream()
                .filter(subject -> isReadable(entryJson, readableJson,
                        JsonPointer.of(SUBJECTS).addLeaf(JsonKey.of(subject.getId()))))
                .toList();
        final List<Resource> readableResources = entry.getResources().stream()
                .filter(resource -> isReadable(entryJson, readableJson,
                        JsonPointer.of(RESOURCES).addLeaf(JsonKey.of(resource.getFullQualifiedPath()))))
                .toList();
        final List<String> readableNamespaces = entry.getNamespaces()
                .filter(namespaces -> isReadable(entryJson, readableJson, JsonPointer.of(NAMESPACES)))
                .orElse(null);

        // importable, allowedAdditions and references are kept: they define how the entry is merged, and are
        // not copied into other entries
        return PoliciesModelFactory.newPolicyEntry(entry.getLabel(),
                Subjects.newInstance(readableSubjects),
                Resources.newInstance(readableResources),
                readableNamespaces,
                entry.getImportableType(),
                entry.getAllowedAdditions().orElse(null),
                entry.getReferences().isEmpty() ? null : entry.getReferences());
    }

    private static boolean isReadable(final JsonObject entryJson, final JsonObject readableJson,
            final JsonPointer pointer) {
        final Optional<JsonValue> value = entryJson.getValue(pointer);
        return value.isPresent() && value.equals(readableJson.getValue(pointer));
    }

    private static <T> CompletableFuture<T> memoize(final Map<PolicyId, CompletableFuture<T>> memo,
            final PolicyId policyId, final Supplier<CompletableFuture<T>> loader) {
        // no computeIfAbsent: the loader may complete synchronously and recursively load further policies
        final CompletableFuture<T> existing = memo.get(policyId);
        if (null != existing) {
            return existing;
        }
        final CompletableFuture<T> loaded = loader.get();
        final CompletableFuture<T> previous = memo.putIfAbsent(policyId, loaded);
        return null != previous ? previous : loaded;
    }

}
