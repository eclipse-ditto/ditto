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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.enforcement.config.DefaultNamespacePoliciesConfig;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.Resource;
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

        final PolicyEnforcer initialChild = underTest.getBlocking(childPolicyId)
                .flatMap(Entry::get)
                .orElseThrow();
        assertThat(initialChild.getPolicy().orElseThrow()
                .contains(PoliciesModelFactory.newImportedLabel(importedPolicyId, initialImportedLabel))).isTrue();
        assertThat(initialChild.getPolicy().orElseThrow()
                .contains(PoliciesModelFactory.newImportedLabel(importedPolicyId, updatedImportedLabel))).isFalse();

        policies.put(importedPolicyId, policyWithLabel(importedPolicyId, 2L, updatedImportedLabel));

        final boolean invalidated = underTest.invalidate(importedPolicyId);
        assertThat(invalidated).isTrue();

        final PolicyEnforcer reloadedChild = underTest.getBlocking(childPolicyId)
                .flatMap(Entry::get)
                .orElseThrow();
        assertThat(reloadedChild.getPolicy().orElseThrow()
                .contains(PoliciesModelFactory.newImportedLabel(importedPolicyId, initialImportedLabel))).isFalse();
        assertThat(reloadedChild.getPolicy().orElseThrow()
                .contains(PoliciesModelFactory.newImportedLabel(importedPolicyId, updatedImportedLabel))).isTrue();
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
