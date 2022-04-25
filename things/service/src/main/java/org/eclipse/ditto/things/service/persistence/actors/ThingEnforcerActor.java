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
package org.eclipse.ditto.things.service.persistence.actors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractEnforcerActor;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.policies.enforcement.CreationRestrictionEnforcer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.things.model.ThingId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * TODO TJ add javadoc
 */
public final class ThingEnforcerActor
        extends AbstractEnforcerActor<ThingId, PolicyCommand<?>, PolicyQueryCommandResponse<?>> {

    @Nullable private AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCacheLoader;

    @SuppressWarnings("unused")
    private ThingEnforcerActor(final ThingId thingId,
            final CreationRestrictionEnforcer creationRestrictionEnforcer,
            final ActorRef pubSubMediator) {

        super(thingId, null, pubSubMediator); // TODO pass in correct Enforcement
    }

    /**
     * TODO TJ doc
     */
    public static Props props(final ThingId thingId, final CreationRestrictionEnforcer creationRestrictionEnforcer,
            final ActorRef pubSubMediator) {
        return Props.create(ThingEnforcerActor.class, thingId, creationRestrictionEnforcer, pubSubMediator);
    }

    @Override
    protected CompletionStage<PolicyId> providePolicyIdForEnforcement() {
        if (null != policyIdForEnforcement) {
            return CompletableFuture.completedStage(policyIdForEnforcement);
        } else {
            return CompletableFuture.completedStage(null);
            // TODO lookup policyId from ThingPersistenceActor
        }
    }

    @Override
    protected CompletionStage<PolicyEnforcer> providePolicyEnforcer(final PolicyId policyId) {
        final ActorSystem actorSystem = getContext().getSystem();
        if (null == policyEnforcerCacheLoader) {
            final ClusterConfig clusterConfig =
                    DefaultClusterConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
            final var shardRegionProxyActorFactory = ShardRegionProxyActorFactory.newInstance(
                    actorSystem, clusterConfig);
            final var policiesShardRegionProxy = shardRegionProxyActorFactory.getShardRegionProxyActor(
                    PoliciesMessagingConstants.CLUSTER_ROLE,
                    PoliciesMessagingConstants.SHARD_REGION);

            // TODO TJ configure + load correctly
            final AskWithRetryConfig askWithRetryConfig = DefaultAskWithRetryConfig.of(ConfigFactory.empty(), "foo");

            // TODO TJ maybe pass in the loader as constructor arg instead?
            policyEnforcerCacheLoader = new PolicyEnforcerCacheLoader(askWithRetryConfig, actorSystem.getScheduler(),
                    policiesShardRegionProxy);
        }

        // TODO TJ use explicit executor instead of taking up resources on the main dispatcher!
        try {
            return policyEnforcerCacheLoader.asyncLoad(EnforcementCacheKey.of(policyId), actorSystem.dispatcher())
                    .thenApply(entry -> {
                        if (entry.exists()) {
                            return entry.getValueOrThrow();
                        } else {
                            return null; // TODO TJ?
                        }
                    });
        } catch (final Exception e) {
            throw new RuntimeException(e); // TODO TJ
        }
    }

}
