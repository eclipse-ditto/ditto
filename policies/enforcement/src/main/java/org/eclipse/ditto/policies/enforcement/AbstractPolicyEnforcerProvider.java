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

import java.util.concurrent.CompletableFuture;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.dispatch.MessageDispatcher;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.enforcement.config.DefaultNamespacePoliciesConfig;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.PolicyId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

/**
 * Abstract base of {@link PolicyEnforcer} implementations.
 */
abstract class AbstractPolicyEnforcerProvider implements PolicyEnforcerProvider {

    protected AbstractPolicyEnforcerProvider() {
        // no-op
    }

    protected static AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCacheLoader(
            final ActorSystem actorSystem) {
        return policyEnforcerCacheLoader(actorSystem,
                DefaultNamespacePoliciesConfig.of(actorSystem.settings().config()));
    }

    protected static AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCacheLoader(
            final ActorSystem actorSystem, final NamespacePoliciesConfig namespacePoliciesConfig) {
        final PolicyCacheLoader policyCacheLoader = PolicyCacheLoader.getSingletonInstance(actorSystem);
        return new PolicyEnforcerCacheLoader(policyCacheLoader, actorSystem, namespacePoliciesConfig);
    }

    protected static AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCacheLoader(
            final ActorSystem actorSystem,
            final NamespacePoliciesConfig namespacePoliciesConfig,
            final CompletableFuture<Cache<PolicyId, Entry<PolicyEnforcer>>> cacheFuture) {
        final PolicyCacheLoader policyCacheLoader = PolicyCacheLoader.getSingletonInstance(actorSystem);
        return new PolicyEnforcerCacheLoader(policyCacheLoader, actorSystem, namespacePoliciesConfig, cacheFuture);
    }

    protected static MessageDispatcher enforcementCacheDispatcher(final ActorSystem actorSystem) {
        return actorSystem.dispatchers().lookup(PolicyEnforcerCacheLoader.ENFORCEMENT_CACHE_DISPATCHER);
    }

}
