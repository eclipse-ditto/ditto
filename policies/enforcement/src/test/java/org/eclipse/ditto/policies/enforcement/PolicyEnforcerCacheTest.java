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

import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.enforcement.config.DefaultNamespacePoliciesConfig;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
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
