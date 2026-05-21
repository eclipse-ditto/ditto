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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.enforcement.config.DefaultNamespacePoliciesConfig;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.AllowedAddition;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.json.JsonPointer;
import scala.concurrent.ExecutionContextExecutor;

public final class PolicyEnforcerCacheTest {

    private ActorSystem actorSystem;


    @Before
    public void setup() {
        actorSystem = ActorSystem.create();
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
        actorSystem = null;
    }

    @Test
    public void getPolicyEnforcerFromCacheLoader() throws Exception {

        final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> cacheLoader = mock(AsyncCacheLoader.class);
        final ExecutionContextExecutor executor = actorSystem.dispatcher();
        final var underTest = new PolicyEnforcerCache(
                cacheLoader,
                executor,
                DefaultCacheConfig.of(actorSystem.settings().config(), "ditto.policies-enforcer-cache"),
                DefaultNamespacePoliciesConfig.of(actorSystem.settings().config())
        );

        new TestKit(actorSystem) {{
            final PolicyId policyId = PolicyId.generateRandom();
            verifyLoadedFromCacheLoader(Policy.newBuilder(policyId).build(), underTest, cacheLoader);
        }};

    }

    @Test
    public void policyTagInvalidatesCacheOfPolicyAndPoliciesWhichImportedThePolicy() throws Exception {
        final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> cacheLoader = mock(AsyncCacheLoader.class);
        final ExecutionContextExecutor executor = actorSystem.dispatcher();
        final var underTest = new PolicyEnforcerCache(
                cacheLoader,
                executor,
                DefaultCacheConfig.of(actorSystem.settings().config(), "ditto.policies-enforcer-cache"),
                DefaultNamespacePoliciesConfig.of(actorSystem.settings().config())
        );

        final var otherPolicyId = PolicyId.generateRandom();
        final var importingPolicyId = PolicyId.generateRandom();
        final var otherImportingPolicyId = PolicyId.generateRandom();
        final var changedImportedPolicyId = PolicyId.generateRandom();

        new TestKit(actorSystem) {{
            //When
            final Policy changedImportedPolicy = Policy.newBuilder(changedImportedPolicyId)
                    .build();
            final Policy importingPolicy = Policy.newBuilder(importingPolicyId)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(changedImportedPolicyId))
                    .build();
            final Policy otherImportingPolicy = Policy.newBuilder(otherImportingPolicyId)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(changedImportedPolicyId))
                    .build();
            final Policy otherPolicy = Policy.newBuilder(otherPolicyId)
                    .build();

            verifyLoadedFromCacheLoader(changedImportedPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(importingPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(otherImportingPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(otherPolicy, underTest, cacheLoader);
            reset(cacheLoader);

            verifyLoadedFromCache(changedImportedPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(importingPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(otherImportingPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(otherPolicy, underTest, cacheLoader);
            reset(cacheLoader);

            underTest.invalidate(changedImportedPolicyId);

            verifyLoadedFromCacheLoader(changedImportedPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(importingPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(otherImportingPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(otherPolicy, underTest, cacheLoader);
        }};

    }

    @Test
    public void transitiveImportChangeCascadeInvalidatesToImportingPolicy() throws Exception {
        final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> cacheLoader = mock(AsyncCacheLoader.class);
        final ExecutionContextExecutor executor = actorSystem.dispatcher();
        final var underTest = new PolicyEnforcerCache(
                cacheLoader,
                executor,
                DefaultCacheConfig.of(actorSystem.settings().config(), "ditto.policies-enforcer-cache"),
                DefaultNamespacePoliciesConfig.of(actorSystem.settings().config())
        );

        // Template policy (C) → imported by intermediate (B) → transitively imported by leaf (A)
        final var templatePolicyId = PolicyId.generateRandom();
        final var intermediatePolicyId = PolicyId.generateRandom();
        final var leafPolicyId = PolicyId.generateRandom();
        final var unrelatedPolicyId = PolicyId.generateRandom();

        new TestKit(actorSystem) {{
            // Intermediate policy imports from template (direct import)
            final Policy intermediatePolicy = Policy.newBuilder(intermediatePolicyId)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(templatePolicyId))
                    .build();

            // Leaf policy imports from intermediate with transitiveImports listing the template
            final List<Label> noLabels = Collections.emptyList();
            final Policy leafPolicy = Policy.newBuilder(leafPolicyId)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(intermediatePolicyId,
                            PoliciesModelFactory.newEffectedImportedLabels(
                                    noLabels,
                                    List.<PolicyId>of(templatePolicyId))))
                    .build();

            final Policy templatePolicy = Policy.newBuilder(templatePolicyId).build();
            final Policy unrelatedPolicy = Policy.newBuilder(unrelatedPolicyId).build();

            // Load all policies into cache
            verifyLoadedFromCacheLoader(templatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(intermediatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(leafPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(unrelatedPolicy, underTest, cacheLoader);
            reset(cacheLoader);

            // Verify all served from cache
            verifyLoadedFromCache(templatePolicy, underTest, cacheLoader);
            verifyLoadedFromCache(intermediatePolicy, underTest, cacheLoader);
            verifyLoadedFromCache(leafPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(unrelatedPolicy, underTest, cacheLoader);
            reset(cacheLoader);

            // When: template policy changes
            underTest.invalidate(templatePolicyId);

            // Then: template, intermediate (direct importer), AND leaf (transitive importer) are invalidated
            verifyLoadedFromCacheLoader(templatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(intermediatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(leafPolicy, underTest, cacheLoader);
            // Unrelated policy is still cached
            verifyLoadedFromCache(unrelatedPolicy, underTest, cacheLoader);
        }};
    }

    @Test
    public void removingTransitiveImportsStopsInvalidationCascade() throws Exception {
        final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> cacheLoader = mock(AsyncCacheLoader.class);
        final ExecutionContextExecutor executor = actorSystem.dispatcher();
        final var underTest = new PolicyEnforcerCache(
                cacheLoader,
                executor,
                DefaultCacheConfig.of(actorSystem.settings().config(), "ditto.policies-enforcer-cache"),
                DefaultNamespacePoliciesConfig.of(actorSystem.settings().config())
        );

        final var templatePolicyId = PolicyId.generateRandom();
        final var intermediatePolicyId = PolicyId.generateRandom();
        final var leafPolicyId = PolicyId.generateRandom();

        new TestKit(actorSystem) {{
            final Policy templatePolicy = Policy.newBuilder(templatePolicyId).build();
            final Policy intermediatePolicy = Policy.newBuilder(intermediatePolicyId)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(templatePolicyId))
                    .build();

            final List<Label> noLabels = Collections.emptyList();
            final Policy leafPolicyWithTransitive = Policy.newBuilder(leafPolicyId)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(intermediatePolicyId,
                            PoliciesModelFactory.newEffectedImportedLabels(
                                    noLabels,
                                    List.<PolicyId>of(templatePolicyId))))
                    .build();

            // Phase 1: Load all policies. Leaf has transitiveImports → template cascades to leaf.
            verifyLoadedFromCacheLoader(templatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(intermediatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(leafPolicyWithTransitive, underTest, cacheLoader);
            reset(cacheLoader);

            underTest.invalidate(templatePolicyId);
            // Template change invalidates: template, intermediate (direct), leaf (transitive)
            verifyLoadedFromCacheLoader(templatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(intermediatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(leafPolicyWithTransitive, underTest, cacheLoader);
            reset(cacheLoader);

            // Phase 2: Invalidate template again — this clears the import mapping for templatePolicyId.
            // Then reload all three, but this time the leaf has NO transitiveImports.
            // The fresh mapping for templatePolicyId will only contain {intermediate}, not {leaf}.
            final Policy leafPolicyWithoutTransitive = Policy.newBuilder(leafPolicyId)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(intermediatePolicyId))
                    .build();

            underTest.invalidate(templatePolicyId);
            verifyLoadedFromCacheLoader(templatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(intermediatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(leafPolicyWithoutTransitive, underTest, cacheLoader);
            reset(cacheLoader);

            // Phase 3: Template changes again → leaf should NO LONGER be invalidated
            // because leafPolicyWithoutTransitive did not register templatePolicyId as a dependency.
            underTest.invalidate(templatePolicyId);
            verifyLoadedFromCacheLoader(templatePolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(intermediatePolicy, underTest, cacheLoader);
            // Leaf is still cached — template change no longer cascades to it
            verifyLoadedFromCache(leafPolicyWithoutTransitive, underTest, cacheLoader);
        }};
    }

    /**
     * Integration-style cache test for the customer-shaped 3-policy graph (template / main-with-references /
     * leaf-with-transitiveImports). Unlike the other tests in this file, this one wires a real
     * {@link com.github.benmanes.caffeine.cache.AsyncCacheLoader} that actually runs
     * {@link PolicyEnforcer#withResolvedImportsAndNamespacePolicies} against an in-memory map of raw policies,
     * so the test exercises both:
     * <ol>
     *   <li>that the model layer's reference resolution materializes main's {@code importable=implicit}
     *       entries with subjects merged in from a locally-referenced {@code importable=explicit} entry
     *       (the symptom that motivated this test — entries were observed missing in the resolved leaf view), and</li>
     *   <li>that {@code invalidate(mainId)} cascades to the leaf and the subsequent cache load actually re-resolves
     *       leaf against the updated main, so a change to main's MANAGER subject is observable in leaf's
     *       resolved view without restart.</li>
     * </ol>
     */
    @Test
    public void transitiveImportWithLocalReferenceIsResolvedAndCacheReloadsOnMainChange() throws Exception {
        // ===== Build the 3-policy customer-shaped graph =====
        final PolicyId templateId = PolicyId.of("test.cache", "template");
        final PolicyId mainId = PolicyId.of("test.cache", "main");
        final PolicyId leafId = PolicyId.of("test.cache", "leaf");

        final Label roomAccessLabel = Label.of("ROOM_ACCESS");
        final Label managerLabel = Label.of("MANAGER");
        final ResourceKey roomThing = ResourceKey.newInstance("thing", JsonPointer.of("features/room"));

        // template: IMPLICIT ROOM_ACCESS with READ on thing:/features/room, no subjects,
        // allowedAdditions=[SUBJECTS] so consumers may attach subjects but not extra resources.
        final PolicyEntry templateRoom = PoliciesModelFactory.newPolicyEntry(roomAccessLabel,
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(Resource.newInstance(roomThing,
                        EffectedPermissions.newInstance(Permissions.newInstance("READ"), Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS), null);
        final Policy template = PoliciesModelFactory.newPolicyBuilder(templateId)
                .setRevision(1L)
                .set(templateRoom)
                .build();

        // main: imports template; carries the manager subject in an EXPLICIT entry (not pulled into the leaf
        // because leaf imports main with entries:[]); plus a per-resource IMPLICIT ROOM_ACCESS entry that only
        // declares references — one local to MANAGER (for subjects), one import-ref to template's ROOM_ACCESS
        // (for resources). This is the entry the customer observed missing in the resolved leaf view.
        final SubjectId aliceId = SubjectId.newInstance(SubjectIssuer.GOOGLE, "alice");
        final PolicyEntry mainManager = PoliciesModelFactory.newPolicyEntry(managerLabel,
                Subjects.newInstance(Subject.newInstance(aliceId)),
                PoliciesModelFactory.emptyResources(),
                ImportableType.EXPLICIT);
        final PolicyEntry mainRoom = PoliciesModelFactory.newPolicyEntry(roomAccessLabel,
                PoliciesModelFactory.emptySubjects(),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null,
                Arrays.asList(
                        PoliciesModelFactory.newLocalEntryReference(managerLabel),
                        PoliciesModelFactory.newEntryReference(templateId, roomAccessLabel)));
        final Policy main = PoliciesModelFactory.newPolicyBuilder(mainId)
                .setRevision(1L)
                .set(mainManager)
                .set(mainRoom)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(templateId, (EffectedImports) null))
                .build();

        // leaf: imports main with empty entries list + transitiveImports=[template], no own entries.
        final EffectedImports leafImportOfMain = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), Collections.singletonList(templateId));
        final Policy leaf = PoliciesModelFactory.newPolicyBuilder(leafId)
                .setRevision(1L)
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(mainId, leafImportOfMain))
                .build();

        // ===== Wire the cache with a map-backed loader that runs real reference resolution =====
        final ConcurrentMap<PolicyId, Policy> policies = new ConcurrentHashMap<>();
        policies.put(templateId, template);
        policies.put(mainId, main);
        policies.put(leafId, leaf);

        final NamespacePoliciesConfig nsConfig =
                DefaultNamespacePoliciesConfig.of(actorSystem.settings().config());
        final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> loader =
                new MapBackedPolicyEnforcerCacheLoader(policies, nsConfig);
        final ExecutionContextExecutor executor = actorSystem.dispatcher();
        final PolicyEnforcerCache cache = new PolicyEnforcerCache(
                loader, executor,
                DefaultCacheConfig.of(actorSystem.settings().config(), "ditto.policies-enforcer-cache"),
                nsConfig);

        new TestKit(actorSystem) {{
            // ===== Phase 1: First read of leaf — verify entries with merged references are present =====
            final Optional<Entry<PolicyEnforcer>> leafEntry1 = cache.get(leafId).toCompletableFuture().join();
            final Policy leafResolved1 = leafEntry1
                    .flatMap(Entry::get)
                    .flatMap(PolicyEnforcer::getPolicy)
                    .orElseThrow(() -> new AssertionError("leaf failed to load"));

            final Label mainPrefixedRoom = PoliciesModelFactory.newImportedLabel(mainId, roomAccessLabel);
            final PolicyEntry roomEntry1 = leafResolved1.getEntryFor(mainPrefixedRoom)
                    .orElseThrow(() -> new AssertionError(
                            "imported-<mainId>-ROOM_ACCESS missing from leaf's resolved view — " +
                                    "the IMPLICIT entry with a local reference to an EXPLICIT MANAGER " +
                                    "entry must survive the import + reference resolution"));

            assertThat(subjectIdsOf(roomEntry1))
                    .as("local reference to EXPLICIT MANAGER must merge alice into ROOM_ACCESS")
                    .contains(aliceId.toString());
            assertThat(roomEntry1.getResources().getResource(roomThing))
                    .as("import reference to template:ROOM_ACCESS must merge template's resource")
                    .isPresent();

            // ===== Phase 2: Update main — replace MANAGER's alice subject with bob =====
            final SubjectId bobId = SubjectId.newInstance(SubjectIssuer.GOOGLE, "bob");
            final PolicyEntry mainManagerUpdated = PoliciesModelFactory.newPolicyEntry(managerLabel,
                    Subjects.newInstance(Subject.newInstance(bobId)),
                    PoliciesModelFactory.emptyResources(),
                    ImportableType.EXPLICIT);
            final Policy mainUpdated = PoliciesModelFactory.newPolicyBuilder(mainId)
                    .setRevision(2L)
                    .set(mainManagerUpdated)
                    .set(mainRoom)
                    .setPolicyImport(PoliciesModelFactory.newPolicyImport(templateId, (EffectedImports) null))
                    .build();
            policies.put(mainId, mainUpdated);

            // ===== Phase 3: Invalidate main — must cascade to leaf via the main → leaf edge =====
            cache.invalidate(mainId);

            // ===== Phase 4: Re-read leaf — must reflect updated MANAGER subject =====
            final Optional<Entry<PolicyEnforcer>> leafEntry2 = cache.get(leafId).toCompletableFuture().join();
            final Policy leafResolved2 = leafEntry2
                    .flatMap(Entry::get)
                    .flatMap(PolicyEnforcer::getPolicy)
                    .orElseThrow(() -> new AssertionError("leaf failed to reload"));

            final PolicyEntry roomEntry2 = leafResolved2.getEntryFor(mainPrefixedRoom)
                    .orElseThrow(() -> new AssertionError(
                            "imported-<mainId>-ROOM_ACCESS missing from leaf after main invalidation"));
            assertThat(subjectIdsOf(roomEntry2))
                    .as("after invalidate(mainId), reloaded leaf must reflect MANAGER's new subject")
                    .contains(bobId.toString())
                    .doesNotContain(aliceId.toString());
        }};
    }

    private static Set<String> subjectIdsOf(final PolicyEntry entry) {
        return StreamSupport.stream(entry.getSubjects().spliterator(), false)
                .map(s -> s.getId().toString())
                .collect(Collectors.toSet());
    }

    /**
     * Map-backed {@link AsyncCacheLoader} that mirrors the production {@link PolicyEnforcerCacheLoader}:
     * fetches the raw policy from an in-memory map, then resolves imports + references via the same map.
     * The resolver is rebuilt on every load and reads the current map state, so updates published into the
     * map are picked up by the next cache miss.
     */
    private static final class MapBackedPolicyEnforcerCacheLoader
            implements AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> {

        private final ConcurrentMap<PolicyId, Policy> policies;
        private final NamespacePoliciesConfig namespacePoliciesConfig;

        MapBackedPolicyEnforcerCacheLoader(final ConcurrentMap<PolicyId, Policy> policies,
                final NamespacePoliciesConfig namespacePoliciesConfig) {
            this.policies = policies;
            this.namespacePoliciesConfig = namespacePoliciesConfig;
        }

        @Override
        public CompletableFuture<Entry<PolicyEnforcer>> asyncLoad(final PolicyId policyId,
                final Executor executor) {
            final Policy policy = policies.get(policyId);
            if (policy == null) {
                return CompletableFuture.completedFuture(Entry.nonexistent());
            }
            final Function<PolicyId, CompletionStage<Optional<Policy>>> resolver =
                    id -> CompletableFuture.completedFuture(Optional.ofNullable(policies.get(id)));
            final long revision = policy.getRevision().map(PolicyRevision::toLong).orElse(1L);
            return PolicyEnforcer
                    .withResolvedImportsAndNamespacePolicies(policy, resolver, namespacePoliciesConfig)
                    .thenApply(enforcer -> Entry.of(revision, enforcer))
                    .toCompletableFuture();
        }
    }

    @Test
    public void policyTagInvalidatesCachedPoliciesInNamespacesOfChangedRootPolicy() throws Exception {
        final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> cacheLoader = mock(AsyncCacheLoader.class);
        final ExecutionContextExecutor executor = actorSystem.dispatcher();
        final PolicyId rootPolicyId = PolicyId.of("org.example", "tenant-root-local");

        final var underTest = new PolicyEnforcerCache(
                cacheLoader,
                executor,
                DefaultCacheConfig.of(actorSystem.settings().config(), "ditto.policies-enforcer-cache"),
                namespacePoliciesConfigFor(rootPolicyId)
        );

        final Policy devicesPolicy = Policy.newBuilder(PolicyId.of("org.example.devices", "policy-a")).build();
        final Policy sensorsPolicy = Policy.newBuilder(PolicyId.of("org.example.sensors", "policy-b")).build();
        final Policy unrelatedPolicy = Policy.newBuilder(PolicyId.of("org.example.other", "policy-c")).build();

        new TestKit(actorSystem) {{
            verifyLoadedFromCacheLoader(devicesPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(sensorsPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(unrelatedPolicy, underTest, cacheLoader);
            reset(cacheLoader);

            verifyLoadedFromCache(devicesPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(sensorsPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(unrelatedPolicy, underTest, cacheLoader);
            reset(cacheLoader);

            final boolean invalidated = underTest.invalidate(rootPolicyId);
            assertThat(invalidated).isTrue();

            verifyLoadedFromCacheLoader(devicesPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(sensorsPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(unrelatedPolicy, underTest, cacheLoader);
        }};
    }

    @Test
    public void wildcardNamespacePatternInvalidatesCachedPoliciesInMatchingNamespaces() throws Exception {
        final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> cacheLoader = mock(AsyncCacheLoader.class);
        final ExecutionContextExecutor executor = actorSystem.dispatcher();
        final PolicyId rootPolicyId = PolicyId.of("org.example", "tenant-root-wildcard");

        // configure root policy via wildcard pattern "org.example.*"
        final var underTest = new PolicyEnforcerCache(
                cacheLoader,
                executor,
                DefaultCacheConfig.of(actorSystem.settings().config(), "ditto.policies-enforcer-cache"),
                namespacePoliciesConfigForWildcard(rootPolicyId, "org.example.*")
        );

        final Policy devicesPolicy = Policy.newBuilder(PolicyId.of("org.example.devices", "policy-a")).build();
        final Policy sensorsPolicy = Policy.newBuilder(PolicyId.of("org.example.sensors", "policy-b")).build();
        // "org.examples" must NOT match "org.example.*" (no dot separator)
        final Policy unrelatedPolicy = Policy.newBuilder(PolicyId.of("org.examples", "policy-c")).build();

        new TestKit(actorSystem) {{
            verifyLoadedFromCacheLoader(devicesPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(sensorsPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(unrelatedPolicy, underTest, cacheLoader);
            reset(cacheLoader);

            verifyLoadedFromCache(devicesPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(sensorsPolicy, underTest, cacheLoader);
            verifyLoadedFromCache(unrelatedPolicy, underTest, cacheLoader);
            reset(cacheLoader);

            final boolean invalidated = underTest.invalidate(rootPolicyId);
            assertThat(invalidated).isTrue();

            // matching namespace policies must be reloaded
            verifyLoadedFromCacheLoader(devicesPolicy, underTest, cacheLoader);
            verifyLoadedFromCacheLoader(sensorsPolicy, underTest, cacheLoader);
            // non-matching policy must still be served from cache
            verifyLoadedFromCache(unrelatedPolicy, underTest, cacheLoader);
        }};
    }

    @Test
    public void importedPolicyChangeInvalidatesRootAndMatchingChildPolicies() {
        final ExecutionContextExecutor executor = actorSystem.dispatcher();
        final PolicyId childPolicyId = PolicyId.of("org.example.devices", "child-policy");
        final PolicyId rootPolicyId = PolicyId.of("org.example", "tenant-root");
        final PolicyId importedPolicyId = PolicyId.of("org.example", "imported-policy");
        final Label initialImportedLabel = Label.of("IMPORTED_READER");
        final Label updatedImportedLabel = Label.of("IMPORTED_ADMIN");

        final NamespacePoliciesConfig config = DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                "ditto.namespace-policies {\n" +
                "  \"org.example.devices\" = [\"org.example:tenant-root\"]\n" +
                "}"
        ));

        final Map<PolicyId, Policy> policies = new ConcurrentHashMap<>();
        policies.put(importedPolicyId, policyWithLabel(importedPolicyId, 1L, initialImportedLabel));
        policies.put(rootPolicyId, Policy.newBuilder(rootPolicyId)
                .setRevision(1L)
                .setPolicyImport(PolicyImport.newInstance(importedPolicyId, null))
                .build());
        policies.put(childPolicyId, Policy.newBuilder(childPolicyId)
                .setRevision(1L)
                .build());

        final PolicyCacheLoader policyCacheLoader = mock(PolicyCacheLoader.class);
        when(policyCacheLoader.asyncLoad(any(), any())).thenAnswer(invocation -> {
            final PolicyId policyId = invocation.getArgument(0);
            final Policy policy = policies.get(policyId);
            return CompletableFuture.completedFuture(policy == null
                    ? Entry.nonexistent()
                    : Entry.of(policy.getRevision().orElseThrow().toLong(), policy));
        });

        final CompletableFuture<org.eclipse.ditto.internal.utils.cache.Cache<PolicyId, Entry<PolicyEnforcer>>> cacheFuture =
                new CompletableFuture<>();
        final PolicyEnforcerCache underTest = new PolicyEnforcerCache(
                new PolicyEnforcerCacheLoader(policyCacheLoader, actorSystem, config, cacheFuture),
                executor,
                DefaultCacheConfig.of(actorSystem.settings().config(), "ditto.policies-enforcer-cache"),
                config
        );
        cacheFuture.complete(underTest);

        // The transitive import inside the root resolves to imported-<importedId>-<originalLabel>; when the root
        // is merged into the child via namespace-policies, the label is wrapped again with nsimported-<rootId>-.
        final Label initialMergedLabel = PoliciesModelFactory.newNsImportedLabel(rootPolicyId,
                PoliciesModelFactory.newImportedLabel(importedPolicyId, initialImportedLabel));
        final Label updatedMergedLabel = PoliciesModelFactory.newNsImportedLabel(rootPolicyId,
                PoliciesModelFactory.newImportedLabel(importedPolicyId, updatedImportedLabel));

        final PolicyEnforcer initialChild = underTest.getBlocking(childPolicyId)
                .flatMap(Entry::get)
                .orElseThrow();
        assertThat(initialChild.getPolicy().orElseThrow().contains(initialMergedLabel)).isTrue();
        assertThat(initialChild.getPolicy().orElseThrow().contains(updatedMergedLabel)).isFalse();

        policies.put(importedPolicyId, policyWithLabel(importedPolicyId, 2L, updatedImportedLabel));

        final boolean invalidated = underTest.invalidate(importedPolicyId);
        assertThat(invalidated).isTrue();

        final PolicyEnforcer reloadedChild = underTest.getBlocking(childPolicyId)
                .flatMap(Entry::get)
                .orElseThrow();
        assertThat(reloadedChild.getPolicy().orElseThrow().contains(initialMergedLabel)).isFalse();
        assertThat(reloadedChild.getPolicy().orElseThrow().contains(updatedMergedLabel)).isTrue();
    }

    private NamespacePoliciesConfig namespacePoliciesConfigForWildcard(final PolicyId rootPolicyId,
            final String pattern) {
        final NamespacePoliciesConfig config = mock(NamespacePoliciesConfig.class);
        when(config.getAllNamespaceRootPolicyIds()).thenReturn(Collections.singleton(rootPolicyId));
        when(config.getNamespacesForRootPolicy(rootPolicyId)).thenReturn(Collections.singleton(pattern));
        return config;
    }

    private NamespacePoliciesConfig namespacePoliciesConfigFor(final PolicyId rootPolicyId) {
        final NamespacePoliciesConfig config = mock(NamespacePoliciesConfig.class);
        final Map<String, List<PolicyId>> namespacePolicies = new HashMap<>();
        namespacePolicies.put("org.example.devices", Collections.singletonList(rootPolicyId));
        namespacePolicies.put("org.example.sensors", Collections.singletonList(rootPolicyId));

        final Set<String> coveredNamespaces = new HashSet<>();
        coveredNamespaces.add("org.example.devices");
        coveredNamespaces.add("org.example.sensors");

        when(config.getNamespacePolicies()).thenReturn(namespacePolicies);
        when(config.getAllNamespaceRootPolicyIds()).thenReturn(Collections.singleton(rootPolicyId));
        when(config.getNamespacesForRootPolicy(rootPolicyId)).thenReturn(coveredNamespaces);
        return config;
    }

    private static Policy policyWithLabel(final PolicyId policyId, final long revision, final Label label) {
        final Subjects subjects = Subjects.newInstance(
                Subject.newInstance(
                        SubjectId.newInstance(SubjectIssuer.newInstance("pre"), label.toString()),
                        SubjectType.newInstance("pre-authenticated")
                )
        );
        final Resources resources = Resources.newInstance(
                Resource.newInstance("thing", JsonPointer.of("/"),
                        EffectedPermissions.newInstance(Permissions.newInstance("READ"), Permissions.none()))
        );
        return Policy.newBuilder(policyId)
                .setRevision(revision)
                .set(PoliciesModelFactory.newPolicyEntry(
                        label,
                        subjects,
                        resources,
                        ImportableType.IMPLICIT
                ))
                .build();
    }

    private void verifyLoadedFromCacheLoader(final Policy policy,
            final PolicyEnforcerCache cache,
            final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> cacheLoader) throws Exception {
        final PolicyEnforcer enforcer = PolicyEnforcer.of(policy);
        final PolicyId policyId = policy.getEntityId().orElseThrow();

        final CompletableFuture enforcerResponseFromCache =
                CompletableFuture.completedFuture(Entry.of(1L, enforcer));
        when(cacheLoader.asyncLoad(eq(policyId), any())).thenReturn(enforcerResponseFromCache);

        final var policyEnforcer = cache.get(policyId).toCompletableFuture();
        assertThat(policyEnforcer.join().flatMap(Entry::get)).contains(enforcer);
        verify(cacheLoader).asyncLoad(eq(policyId), any());
    }

    private void verifyLoadedFromCache(final Policy policy,
            final PolicyEnforcerCache cache,
            final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> cacheLoader) throws Exception {
        final PolicyId policyId = policy.getEntityId().orElseThrow();

        final var policyEnforcer = cache.get(policyId).toCompletableFuture();
        assertThat(policyEnforcer.join().flatMap(Entry::get).flatMap(PolicyEnforcer::getPolicy)).contains(policy);
        verifyNoMoreInteractions(cacheLoader);
        verify(cacheLoader, never()).asyncLoad(eq(policyId), any());
    }

}
