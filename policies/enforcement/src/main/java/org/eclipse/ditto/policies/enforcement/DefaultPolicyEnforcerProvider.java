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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.model.PolicyId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;

/**
 * Loads the {@link org.eclipse.ditto.policies.model.Policy} from the policies shard region and wraps it into a
 * {@link PolicyEnforcer}.
 */
final class DefaultPolicyEnforcerProvider extends AbstractPolicyEnforcerProvider {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(DefaultPolicyEnforcerProvider.class);

    private final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCacheLoader;
    private final MessageDispatcher cacheDispatcher;

    DefaultPolicyEnforcerProvider(final ActorSystem actorSystem) {
        this(policyEnforcerCacheLoader(actorSystem), enforcementCacheDispatcher(actorSystem));
    }

    DefaultPolicyEnforcerProvider(
            final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCacheLoader,
            final MessageDispatcher cacheDispatcher) {

        this.policyEnforcerCacheLoader = policyEnforcerCacheLoader;
        this.cacheDispatcher = cacheDispatcher;
    }

    @Override
    public CompletionStage<Optional<PolicyEnforcer>> getPolicyEnforcer(@Nullable final PolicyId policyId) {
        if (null == policyId) {
            return CompletableFuture.completedStage(Optional.empty());
        } else {
            try {
                return policyEnforcerCacheLoader.asyncLoad(policyId, cacheDispatcher)
                        .thenApply(Entry::get)
                        .exceptionally(error -> Optional.empty());
            } catch (final Exception e) {
                LOGGER.warn(
                        "Got exception when trying to load the policy enforcer via cache loader. This is " +
                                "unexpected", e
                );
                return CompletableFuture.completedStage(Optional.empty());
            }
        }
    }
}
