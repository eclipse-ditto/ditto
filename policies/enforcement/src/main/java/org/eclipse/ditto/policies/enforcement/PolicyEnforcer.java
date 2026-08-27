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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.EntryReference;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImporter;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.policies.model.enforcers.SubjectClassification;

import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;

import org.slf4j.Logger;

/**
 * Policy together with its enforcer.
 */
@Immutable
public final class PolicyEnforcer {

    private static final Logger LOG = DittoLoggerFactory.getThreadSafeLogger(PolicyEnforcer.class);

    private static final ResourceKey ROOT_THING_RESOURCE =
            PoliciesResourceType.thingResource(JsonPointer.empty());
    private static final Permissions READ_PERMISSIONS = Permissions.newInstance(Permission.READ);

    @Nullable private final Policy policy;
    private final Enforcer enforcer;
    // Memoizes forNamespace(ns) results so the enforcer tree is not rebuilt per signal. Observably
    // immutable (like readClassificationCache below): this instance is replaced wholesale by
    // CachingPolicyEnforcerProvider on every policy update, so the memo is invalidated naturally.
    private final Cache<String, PolicyEnforcer> namespaceEnforcerCache;
    // Memoizes classifySubjects(resource, READ) per resource so the policy tree is walked at most once per
    // distinct resource instead of per emitted ThingEvent / per read-grant collection. classifySubjects is a
    // pure function of (resolved policy grants, resource path) — independent of Thing field values — so this is
    // safe: the instance is replaced wholesale by CachingPolicyEnforcerProvider on every grant change, so the
    // memo is invalidated naturally. Generalizes the former single-slot root-read-classification memo.
    private final Cache<ResourceKey, SubjectClassification> readClassificationCache;
    // Memoizes the rendered "ditto-read-subjects" header value per resource, layered on top of
    // readClassificationCache: the value is a pure function of the resolved grants and the resource path, so
    // rendering it (merge -> sort -> JsonArray -> escaped string + CBOR) once per policy revision instead of once
    // per emitted ThingEvent is safe. Same natural invalidation as the caches above. Only used for the
    // include-partial-read-subjects variant, which is the only one depending on the resource key.
    private final Cache<ResourceKey, JsonArray> readGrantedSubjectsHeaderValueCache;
    // Single-slot memo for the root-only variant, whose value does not depend on the resource key. Volatile
    // rather than a Cache because it is a single reference read on the hot path.
    @Nullable private volatile JsonArray rootOnlyReadGrantedSubjectsHeaderValue;
    // The read-classification bound (<= 0 means unbounded), kept so forNamespace children can inherit it:
    // the things-service hot path calls classifyReadSubjects on the namespace-filtered child, so its cache
    // must be bounded too (unlike the child's namespace cache, which stays empty).
    private final long readClassificationCacheMaxSize;
    // The operator-configured authorization-verdict memo bound (reference.conf
    // 'authorization-memo-cache-max-size'), kept for the same reason as readClassificationCacheMaxSize above:
    // forNamespace children must inherit it. The things-service hot path enforces against the
    // namespace-filtered child, so a configured bound - 0 in particular, the documented "disable the memo"
    // escape hatch - has to reach the child's enforcer, not just this instance's. null means no configuration
    // was supplied (transient of/embed instances), in which case PolicyEnforcers' own default applies.
    @Nullable private final Integer authorizationMemoMaxSize;

    /**
     * Creates an instance with an <em>unbounded</em> namespace-filtered-enforcer cache. Used for transient
     * or fast-path-only instances (see {@link #of}, {@link #embed}, filtered children) on which
     * {@link #forNamespace(String)} is either never called or called only a handful of times before the
     * instance is garbage-collected, so the cache never accumulates. Only the long-lived, provider-cached
     * instances need a bound.
     */
    private PolicyEnforcer(@Nullable final Policy policy, final Enforcer enforcer) {
        this(policy, enforcer, Caffeine.newBuilder().build(), 0L, null);
    }

    /**
     * Creates an instance with <em>bounded</em> namespace-filtered-enforcer and read-classification caches, for
     * the long-lived instances on which the things-service enforcer calls {@link #forNamespace(String)} and
     * {@link #classifyReadSubjects(ResourceKey)} per signal — the CPU hotspots these caches address. The bounds
     * (operator-configurable) cap distinct namespaces / resource paths per policy.
     */
    private PolicyEnforcer(@Nullable final Policy policy, final Enforcer enforcer,
            final long namespaceEnforcerCacheMaxSize, final long readClassificationCacheMaxSize,
            @Nullable final Integer authorizationMemoMaxSize) {
        this(policy, enforcer,
                Caffeine.newBuilder().maximumSize(namespaceEnforcerCacheMaxSize).build(),
                readClassificationCacheMaxSize, authorizationMemoMaxSize);
    }

    private PolicyEnforcer(@Nullable final Policy policy, final Enforcer enforcer,
            final Cache<String, PolicyEnforcer> namespaceEnforcerCache,
            final long readClassificationCacheMaxSize,
            @Nullable final Integer authorizationMemoMaxSize) {
        this.policy = policy;
        this.enforcer = enforcer;
        this.namespaceEnforcerCache = namespaceEnforcerCache;
        this.readClassificationCacheMaxSize = readClassificationCacheMaxSize;
        this.authorizationMemoMaxSize = authorizationMemoMaxSize;
        // <= 0 means unbounded (of/embed/transient instances); provider-cached and forNamespace children
        // inherit the operator-configured bound.
        this.readClassificationCache = readClassificationCacheMaxSize > 0
                ? Caffeine.newBuilder().maximumSize(readClassificationCacheMaxSize).build()
                : Caffeine.newBuilder().build();
        // Entries correspond one-to-one to readClassificationCache entries, so the same bound keeps the retained
        // memory of both memos proportional; no separate configuration knob is needed.
        this.readGrantedSubjectsHeaderValueCache = readClassificationCacheMaxSize > 0
                ? Caffeine.newBuilder().maximumSize(readClassificationCacheMaxSize).build()
                : Caffeine.newBuilder().build();
    }

    /**
     * Create a policy enforcer from policy.
     *
     * @param policy the policy
     * @return the pair
     */
    public static CompletionStage<PolicyEnforcer> withResolvedImports(final Policy policy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver) {
        return policy.withResolvedImports(policyResolver, missingReferenceLogger(policy))
                .thenApply(resolvedPolicy -> {
                    final Enforcer enforcer = PolicyEnforcers.defaultEvaluator(resolvedPolicy);
                    return new PolicyEnforcer(resolvedPolicy, enforcer);
                });
    }

    // Dedup window for missing-reference warnings: 5 minutes since the same key was last logged.
    private static final long DEDUP_WINDOW_NANOS = 5L * 60L * 1_000_000_000L;
    // Cap to bound memory if a misbehaving caller produces many distinct keys; eviction is best-effort
    // (oldest insertion is replaced when full). 10 000 ≈ a few MB of strings.
    private static final int DEDUP_MAX_SIZE = 10_000;
    private static final ConcurrentHashMap<String, Long> RECENTLY_LOGGED = new ConcurrentHashMap<>();

    /**
     * Returns a callback that logs a warning each time {@link Policy#withResolvedImports} encounters an
     * entry reference that cannot be resolved (e.g. the referenced entry was deleted, or an import is
     * not declared). The model layer silently skips missing references — this surfaces them so operators
     * can investigate silent permission degradation.
     * <p>
     * The same (policyId, referencing-entry, target) tuple is suppressed for a 5-minute window so a
     * dangling reference doesn't flood logs on every enforcer-cache miss.
     */
    private static BiConsumer<PolicyEntry, EntryReference> missingReferenceLogger(final Policy policy) {
        final String policyIdStr = policy.getEntityId().map(Object::toString).orElse("?");
        return (referencingEntry, ref) -> {
            final String target = ref.getImportedPolicyId()
                    .map(id -> id + ":" + ref.getEntryLabel())
                    .orElseGet(() -> ref.getEntryLabel().toString());
            final String dedupKey = policyIdStr + " " + referencingEntry.getLabel() + " " + target;
            if (shouldLog(dedupKey)) {
                LOG.warn("Policy <{}>: entry <{}> has an unresolved reference to <{}> — silently skipped." +
                                " The referenced entry may have been deleted or never existed.",
                        policyIdStr, referencingEntry.getLabel(), target);
            }
        };
    }

    private static boolean shouldLog(final String dedupKey) {
        final long now = System.nanoTime();
        final Long previous = RECENTLY_LOGGED.compute(dedupKey, (k, v) -> {
            if (v != null && now - v < DEDUP_WINDOW_NANOS) {
                return v;
            }
            return now;
        });
        if (RECENTLY_LOGGED.size() > DEDUP_MAX_SIZE) {
            // Best-effort cap: drop a few of the oldest entries to keep memory bounded.
            RECENTLY_LOGGED.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit((long) (DEDUP_MAX_SIZE / 10))
                    .map(Map.Entry::getKey)
                    .forEach(RECENTLY_LOGGED::remove);
        }
        return Objects.equals(previous, now);
    }

    /**
     * Creates a policy enforcer from a policy, resolving both its explicit imports and any namespace root policies
     * configured for the policy's namespace. Namespace root policies are resolved with their own imports and their
     * importable entries are merged last under rewritten labels of the form
     * {@code nsimported-<sourcePolicyId>-<originalLabel>}, mirroring how declared imports use
     * {@code imported-<sourceId>-<originalLabel>}. Local labels starting with {@code nsimported-} are rejected by
     * the validator, so local/imported labels and namespace-root-contributed labels cannot collide by construction.
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

        return resolveImportsAndNamespacePolicies(policy, policyResolver, namespacePoliciesConfig)
                .thenApply(finalPolicy ->
                        new PolicyEnforcer(finalPolicy, PolicyEnforcers.defaultEvaluator(finalPolicy)));
    }

    /**
     * Same as {@link #withResolvedImportsAndNamespacePolicies(Policy, Function, NamespacePoliciesConfig)} but
     * with a bounded maximum size for the per-instance namespace-filtered-enforcer cache (see
     * {@link #forNamespace(String)}). Used by the enforcer cache loader, which is the source of the long-lived
     * instances on which {@code forNamespace} is called per signal — the only place the bound matters.
     *
     * @param policy the policy to build an enforcer for.
     * @param policyResolver resolves imported policies by ID.
     * @param namespacePoliciesConfig the static namespace policies configuration.
     * @param namespaceEnforcerCacheMaxSize the maximum size of the per-instance namespace-filtered-enforcer cache.
     * @param readClassificationCacheMaxSize the maximum size of the per-instance read-classification cache
     * (see {@link #classifyReadSubjects(ResourceKey)}).
     * @return a completion stage with the fully resolved PolicyEnforcer.
     * @since 3.9.0
     */
    public static CompletionStage<PolicyEnforcer> withResolvedImportsAndNamespacePolicies(
            final Policy policy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver,
            final NamespacePoliciesConfig namespacePoliciesConfig,
            final long namespaceEnforcerCacheMaxSize,
            final long readClassificationCacheMaxSize) {

        return resolveImportsAndNamespacePolicies(policy, policyResolver, namespacePoliciesConfig)
                .thenApply(finalPolicy ->
                        new PolicyEnforcer(finalPolicy, PolicyEnforcers.defaultEvaluator(finalPolicy),
                                namespaceEnforcerCacheMaxSize, readClassificationCacheMaxSize, null));
    }

    /**
     * Same as {@link #withResolvedImportsAndNamespacePolicies(Policy, Function, NamespacePoliciesConfig, long, long)}
     * but additionally bounds the bare enforcer's authorization-verdict memos (see
     * {@link org.eclipse.ditto.policies.model.enforcers.tree.TreeBasedPolicyEnforcer}). Used by the enforcer cache
     * loader with the operator-configured value.
     *
     * @param policy the policy to build an enforcer for.
     * @param policyResolver resolves imported policies by ID.
     * @param namespacePoliciesConfig the static namespace policies configuration.
     * @param namespaceEnforcerCacheMaxSize the maximum size of the per-instance namespace-filtered-enforcer cache.
     * @param readClassificationCacheMaxSize the maximum size of the per-instance read-classification cache.
     * @param authorizationMemoMaxSize the per-memo upper bound for the enforcer's authorization-verdict memos;
     * a value {@code <= 0} disables those memos (no maps allocated), and values above {@link Integer#MAX_VALUE}
     * saturate rather than wrap. The bound is retained on the returned instance so the namespace-filtered
     * children handed out by {@link #forNamespace(String)} inherit it.
     * @return a completion stage with the fully resolved PolicyEnforcer.
     * @since 3.9.7
     */
    public static CompletionStage<PolicyEnforcer> withResolvedImportsAndNamespacePolicies(
            final Policy policy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver,
            final NamespacePoliciesConfig namespacePoliciesConfig,
            final long namespaceEnforcerCacheMaxSize,
            final long readClassificationCacheMaxSize,
            final long authorizationMemoMaxSize) {

        final int memoMaxSize = clampAuthorizationMemoMaxSize(authorizationMemoMaxSize);
        return resolveImportsAndNamespacePolicies(policy, policyResolver, namespacePoliciesConfig)
                .thenApply(finalPolicy ->
                        new PolicyEnforcer(finalPolicy,
                                PolicyEnforcers.defaultEvaluator(finalPolicy, memoMaxSize),
                                namespaceEnforcerCacheMaxSize, readClassificationCacheMaxSize, memoMaxSize));
    }

    /**
     * Package-private test hook: the operator-configured authorization-verdict memo bound this instance was
     * built with, or {@code null} if none was configured (in which case {@code PolicyEnforcers}' own default
     * applies). Namespace-filtered children must inherit it.
     *
     * @return the configured bound, or {@code null}.
     */
    @Nullable
    Integer getAuthorizationMemoMaxSize() {
        return authorizationMemoMaxSize;
    }

    /**
     * Narrows a configured authorization-memo bound to the {@code int} the enforcer factory takes. A plain cast
     * would wrap for values above {@link Integer#MAX_VALUE} and could land on a negative number, silently
     * <em>disabling</em> the memo when the operator asked for a very large one; saturate instead. Values
     * {@code <= 0} keep their meaning: memoization off.
     *
     * @param authorizationMemoMaxSize the configured bound.
     * @return the bound clamped to {@code [0, Integer.MAX_VALUE]}.
     */
    private static int clampAuthorizationMemoMaxSize(final long authorizationMemoMaxSize) {
        if (authorizationMemoMaxSize <= 0L) {
            return 0;
        }
        return (int) Math.min(authorizationMemoMaxSize, Integer.MAX_VALUE);
    }

    private static CompletionStage<Policy> resolveImportsAndNamespacePolicies(
            final Policy policy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver,
            final NamespacePoliciesConfig namespacePoliciesConfig) {

        return policy.withResolvedImports(policyResolver, missingReferenceLogger(policy))
                .thenCompose(resolvedPolicy -> mergeNamespacePolicies(resolvedPolicy, policyResolver,
                        namespacePoliciesConfig));
    }

    /**
     * Merges importable entries from configured namespace root policies into {@code resolvedPolicy}. Each
     * contributed entry's label is rewritten to {@code nsimported-<sourcePolicyId>-<originalLabel>}; since local
     * labels starting with {@code nsimported-} are rejected by the validator, the merge cannot collide with
     * pre-existing entries in {@code resolvedPolicy}. Missing or deleted namespace root policies are logged as
     * errors and silently skipped — the policy continues to function without them.
     * <p>
     * Root policies are resolved in parallel for performance, then merged in precedence order
     * (exact match first, then more specific prefix wildcards, then broader, then catch-all). Two roots that
     * each contribute an entry under the same {@code originalLabel} therefore compose additively, since the
     * rewritten labels include the source policy id and remain distinct.
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
                                final Policy rootPolicy = rootPolicyOpt.get();
                                return rootPolicy.withResolvedImports(policyResolver,
                                                missingReferenceLogger(rootPolicy))
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
                            result = PolicyImporter.mergeImplicitNamespaceRootEntries(rootPolicyOpt.get(),
                                    rootPolicyId, result);
                        }
                    }
                    return result;
                });
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
     * Create a {@code PolicyEnforcer} wrapping the given policy and a pre-built enforcer.
     *
     * @param policy the policy.
     * @param enforcer the enforcer built from the policy.
     * @return the policy enforcer.
     * @since 3.9.4
     */
    public static PolicyEnforcer of(final Policy policy, final Enforcer enforcer) {
        return new PolicyEnforcer(policy, enforcer);
    }

    /**
     * Create a {@code PolicyEnforcer} with bounded per-instance namespace-filtered-enforcer and
     * read-classification caches.
     *
     * @param policy the policy.
     * @param namespaceEnforcerCacheMaxSize the maximum size of the namespace-filtered-enforcer cache.
     * @param readClassificationCacheMaxSize the maximum size of the read-classification cache.
     * @return the policy enforcer.
     * @since 3.9.4
     */
    public static PolicyEnforcer of(final Policy policy, final long namespaceEnforcerCacheMaxSize,
            final long readClassificationCacheMaxSize) {
        return new PolicyEnforcer(policy, PolicyEnforcers.defaultEvaluator(policy),
                namespaceEnforcerCacheMaxSize, readClassificationCacheMaxSize, null);
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
            return Entry.of(enforcerEntry.getRevision(),
                    new PolicyEnforcer(null, enforcerEntry.getValueOrThrow()));
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
     * Returns the {@link SubjectClassification} for {@code (thing:/, READ)} computed lazily once
     * and memoized for the lifetime of this instance. {@code PolicyEnforcer} is immutable and is
     * replaced wholesale by {@code CachingPolicyEnforcerProvider} on every policy update, so the
     * memo is invalidated naturally on policy changes. Used by the things-service event enrichment
     * hot path to avoid re-walking the full policy tree per emitted ThingEvent.
     *
     * @return the cached classification of subjects with READ permission on the root thing resource.
     * @since 3.9.1
     */
    public SubjectClassification getRootResourceReadClassification() {
        return classifyReadSubjects(ROOT_THING_RESOURCE);
    }

    /**
     * Returns the {@link SubjectClassification} for {@code (resourceKey, READ)}, computed at most once per
     * distinct {@code resourceKey} and memoized for the lifetime of this instance. {@code classifySubjects} is a
     * pure function of the resolved policy grants and the resource path — independent of any Thing field values —
     * so memoizing per resource is safe: this {@code PolicyEnforcer} is replaced wholesale by
     * {@code CachingPolicyEnforcerProvider} on every grant change (own policy, import, or namespace-root cascade),
     * invalidating the memo naturally. Used by the things-service per-event read-authorization
     * ({@code addEffectedReadSubjectsToThingSignal}) and read-grant collection ({@code ReadGrantCollector}) hot
     * paths to avoid re-walking the full policy tree per emitted ThingEvent.
     *
     * @param resourceKey the resource to classify READ subjects for.
     * @return the cached classification of subjects with READ permission on the given resource.
     * @since 3.9.4
     */
    public SubjectClassification classifyReadSubjects(final ResourceKey resourceKey) {
        checkNotNull(resourceKey, "resourceKey");
        return readClassificationCache.get(resourceKey,
                rk -> enforcer.classifySubjects(rk, READ_PERMISSIONS));
    }

    /**
     * Returns the rendered value of the {@code ditto-read-subjects} header for the given resource, computed at most
     * once per distinct {@code resourceKey} and memoized for the lifetime of this instance.
     * <p>
     * The subjects are the same ones {@link #classifyReadSubjects(ResourceKey)} yields, namely
     * {@code unrestricted(resourceKey) ∪ partialOnly(root) ∪ unrestricted(root)} when
     * {@code includePartialReadSubjects} is set and {@code unrestricted(root)} otherwise. Rendering them into a JSON
     * array is a pure function of those sets, so — like the classification itself — it is memoized here instead of
     * being repeated for every emitted ThingEvent. The memo is invalidated naturally, because
     * {@code CachingPolicyEnforcerProvider} replaces this instance wholesale on every grant change.
     * <p>
     * Subject IDs are sorted and de-duplicated, which makes the rendered value stable across pods and restarts.
     * Consumers parse the array back into a Set, so the ordering is not semantically relevant.
     *
     * @param resourceKey the resource to render the READ-granted subjects for.
     * @param includePartialReadSubjects whether subjects with only partial READ access on the root resource are
     * included.
     * @return the cached rendered header value; empty if no subject has READ access.
     * @since 3.9.7
     */
    public JsonArray getReadGrantedSubjectsHeaderValue(final ResourceKey resourceKey,
            final boolean includePartialReadSubjects) {

        checkNotNull(resourceKey, "resourceKey");
        if (includePartialReadSubjects) {
            return readGrantedSubjectsHeaderValueCache.get(resourceKey, this::renderReadGrantedSubjectsIncludingPartial);
        }
        JsonArray result = rootOnlyReadGrantedSubjectsHeaderValue;
        if (null == result) {
            result = renderSubjectIds(getRootResourceReadClassification().getUnrestricted());
            rootOnlyReadGrantedSubjectsHeaderValue = result;
        }
        return result;
    }

    private JsonArray renderReadGrantedSubjectsIncludingPartial(final ResourceKey resourceKey) {
        final SubjectClassification rootClassification = getRootResourceReadClassification();
        final TreeSet<String> subjectIds = new TreeSet<>();
        collectSubjectIds(classifyReadSubjects(resourceKey).getUnrestricted(), subjectIds);
        collectSubjectIds(rootClassification.getPartialOnly(), subjectIds);
        collectSubjectIds(rootClassification.getUnrestricted(), subjectIds);
        return toJsonArray(subjectIds);
    }

    private static JsonArray renderSubjectIds(final Set<AuthorizationSubject> subjects) {
        final TreeSet<String> subjectIds = new TreeSet<>();
        collectSubjectIds(subjects, subjectIds);
        return toJsonArray(subjectIds);
    }

    private static void collectSubjectIds(final Set<AuthorizationSubject> subjects, final TreeSet<String> target) {
        for (final AuthorizationSubject subject : subjects) {
            target.add(subject.getId());
        }
    }

    private static JsonArray toJsonArray(final TreeSet<String> subjectIds) {
        if (subjectIds.isEmpty()) {
            return JsonArray.empty();
        }
        return subjectIds.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
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
        // Memoize per namespace: building the filtered enforcer rebuilds the whole TreeBasedPolicyEnforcer,
        // which previously happened on every enforced signal (dominant CPU hotspot). computeForNamespace
        // always returns a non-null value (this instance or a filtered one), satisfying Caffeine's contract.
        return namespaceEnforcerCache.get(thingNamespace, this::computeForNamespace);
    }

    private PolicyEnforcer computeForNamespace(final String thingNamespace) {
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
        // The filtered child is the instance the things-service hot path enforces against, so it must inherit
        // both of the parent's operator-configured bounds: the read-classification one (classifyReadSubjects is
        // called on it per signal) and the authorization-verdict memo one (every permission check runs against
        // this enforcer). Without the latter, 'authorization-memo-cache-max-size' - including 0 to disable -
        // would not reach the very instance an operator is most likely tuning.
        final Enforcer filteredEnforcer = authorizationMemoMaxSize == null
                ? PolicyEnforcers.defaultEvaluator(filteredEntries)
                : PolicyEnforcers.defaultEvaluator(filteredEntries, authorizationMemoMaxSize);
        // The child's own namespace cache stays empty (forNamespace is never called on the child), so leave
        // that unbounded.
        return new PolicyEnforcer(policy, filteredEnforcer, Caffeine.newBuilder().build(),
                readClassificationCacheMaxSize, authorizationMemoMaxSize);
    }

}
