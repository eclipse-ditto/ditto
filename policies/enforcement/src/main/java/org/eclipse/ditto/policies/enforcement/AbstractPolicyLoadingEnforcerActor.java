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
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.policies.model.PolicyId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.dispatch.MessageDispatcher;

/**
 * Abstract enforcer of commands performing authorization / enforcement of incoming signals based on policy
 * loaded via the policies shard region.
 *
 * @param <I> the type of the EntityId this enforcer actor enforces commands for.
 * @param <S> the type of the Signals this enforcer actor enforces.
 * @param <R> the type of the CommandResponses this enforcer actor filters.
 * @param <E> the type of the EnforcementReloaded this enforcer actor uses for doing command enforcements.
 */
public abstract class AbstractPolicyLoadingEnforcerActor<I extends EntityId, S extends Signal<?>, R extends CommandResponse<?>,
        E extends EnforcementReloaded<S, R>> extends AbstractEnforcerActor<I, S, R, E> {

    private final AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCacheLoader;
    private final MessageDispatcher enforcementCacheDispatcher;

    protected AbstractPolicyLoadingEnforcerActor(final I entityId,
            final E enforcement,
            final ActorRef pubSubMediator,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final AskWithRetryConfig askWithRetryConfig,
            final ActorRef policiesShardRegion) {
        super(entityId, enforcement, pubSubMediator, blockedNamespaces);
        this.policyEnforcerCacheLoader = new PolicyEnforcerCacheLoader(askWithRetryConfig,
                context().system().getScheduler(),
                policiesShardRegion
        );
        enforcementCacheDispatcher =
                context().system().dispatchers().lookup(PolicyEnforcerCacheLoader.ENFORCEMENT_CACHE_DISPATCHER);
    }

    @Override
    protected CompletionStage<PolicyEnforcer> providePolicyEnforcer(@Nullable final PolicyId policyId) {
        if (null == policyId) {
            return CompletableFuture.completedStage(null);
        } else {
            try {
                return policyEnforcerCacheLoader.asyncLoad(EnforcementCacheKey.of(policyId), enforcementCacheDispatcher)
                        .thenApply(entry -> {
                            if (entry.exists()) {
                                return entry.getValueOrThrow();
                            } else {
                                return null;
                            }
                        });
            } catch (final Exception e) {
                throw new IllegalStateException("Could not load policyEnforcer via policyEnforcerCacheLoader", e);
            }
        }
    }
}
