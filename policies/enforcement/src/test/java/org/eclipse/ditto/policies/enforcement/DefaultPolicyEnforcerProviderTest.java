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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.dispatch.MessageDispatcher;

@RunWith(MockitoJUnitRunner.class)
public final class DefaultPolicyEnforcerProviderTest {

    @Mock
    public AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> cacheLoader;

    @Mock
    public MessageDispatcher messageDispatcher;

    private DefaultPolicyEnforcerProvider underTest;

    @Before
    public void setup() {
        underTest = new DefaultPolicyEnforcerProvider(cacheLoader, messageDispatcher);
    }

    @Test
    public void getPolicyEnforcer() throws Exception {
        final PolicyId policyId = PolicyId.generateRandom();
        final PolicyEnforcer policyEnforcer = Mockito.mock(PolicyEnforcer.class);
        final CompletableFuture entryCompletableFuture =
                CompletableFuture.completedFuture(Entry.of(0, policyEnforcer));
        when(cacheLoader.asyncLoad(policyId, messageDispatcher))
                .thenReturn(entryCompletableFuture);
        final Optional<PolicyEnforcer> optionalPolicyEnforcer =
                underTest.getPolicyEnforcer(policyId).toCompletableFuture().join();
        assertThat(optionalPolicyEnforcer).contains(policyEnforcer);
    }

    @Test
    public void getPolicyEnforcerWithNullId() throws Exception {
        final Optional<PolicyEnforcer> optionalPolicyEnforcer =
                underTest.getPolicyEnforcer(null).toCompletableFuture().join();
        assertThat(optionalPolicyEnforcer).isEmpty();
        verifyNoInteractions(cacheLoader, messageDispatcher);
    }

    @Test
    public void getPolicyEnforcerWhenEnforcerIsNotPresent() throws Exception {
        final PolicyId policyId = PolicyId.generateRandom();
        final CompletableFuture entryCompletableFuture =
                CompletableFuture.completedFuture(Entry.nonexistent());
        when(cacheLoader.asyncLoad(policyId, messageDispatcher))
                .thenReturn(entryCompletableFuture);
        final Optional<PolicyEnforcer> optionalPolicyEnforcer =
                underTest.getPolicyEnforcer(policyId).toCompletableFuture().join();
        assertThat(optionalPolicyEnforcer).isEmpty();
    }

    @Test
    public void getPolicyEnforcerWhenCacheLoaderThrowsException() throws Exception {
        final PolicyId policyId = PolicyId.generateRandom();
        when(cacheLoader.asyncLoad(policyId, messageDispatcher))
                .thenThrow(new IllegalStateException("This is expected"));
        final Optional<PolicyEnforcer> optionalPolicyEnforcer =
                underTest.getPolicyEnforcer(policyId).toCompletableFuture().join();
        assertThat(optionalPolicyEnforcer).isEmpty();
    }

    @Test
    public void getPolicyEnforcerWhenCacheLoaderReturnsFailedFuture() throws Exception {
        final PolicyId policyId = PolicyId.generateRandom();
        when(cacheLoader.asyncLoad(policyId, messageDispatcher))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("This is expected")));
        final Optional<PolicyEnforcer> optionalPolicyEnforcer =
                underTest.getPolicyEnforcer(policyId).toCompletableFuture().join();
        assertThat(optionalPolicyEnforcer).isEmpty();
    }

}
