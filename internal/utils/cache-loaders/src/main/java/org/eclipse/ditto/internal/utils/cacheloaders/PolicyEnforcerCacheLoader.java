/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cacheloaders;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.Scheduler;

/**
 * Loads a policy-enforcer by asking the policies shard-region-proxy.
 */
@Immutable
public final class PolicyEnforcerCacheLoader implements AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>> {

    private final PolicyCacheLoader delegate;

    /**
     * Constructor.
     *
     * @param askWithRetryConfig the configuration for the "ask with retry" pattern applied for the cache loader.
     * @param scheduler the scheduler to use for the "ask with retry" for retries.
     * @param policiesShardRegionProxy the shard-region-proxy.
     */
    public PolicyEnforcerCacheLoader(final AskWithRetryConfig askWithRetryConfig,
            final Scheduler scheduler,
            final ActorRef policiesShardRegionProxy) {

        delegate = new PolicyCacheLoader(askWithRetryConfig, scheduler, policiesShardRegionProxy);
    }

    @Override
    public CompletableFuture<Entry<PolicyEnforcer>> asyncLoad(final EnforcementCacheKey key,
            final Executor executor) {
        return delegate.asyncLoad(key, executor).thenApply(PolicyEnforcerCacheLoader::evaluatePolicy);
    }

    private static Entry<PolicyEnforcer> evaluatePolicy(final Entry<Policy> entry) {
        if (entry.exists()) {
            final var revision = entry.getRevision();
            final var policy = entry.getValueOrThrow();
            final var enforcer = PolicyEnforcers.defaultEvaluator(policy);
            return Entry.of(revision, PolicyEnforcer.of(policy, enforcer));
        } else {
            return Entry.nonexistent();
        }
    }

}
