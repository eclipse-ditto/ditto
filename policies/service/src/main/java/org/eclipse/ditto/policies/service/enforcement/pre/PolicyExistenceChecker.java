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
package org.eclipse.ditto.policies.service.enforcement.pre;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * checks for the existence of policies.
 */
final class PolicyExistenceChecker {

    public static final String ENFORCEMENT_CACHE_DISPATCHER = "enforcement-cache-dispatcher";

    private final AsyncCacheLoader<PolicyId, Entry<PolicyId>> policyIdLoader;
    private final ActorSystem actorSystem;

    /**
     * Constructs a new instance of PolicyExistenceChecker extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     */
    PolicyExistenceChecker(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        final var enforcementConfig = DefaultEnforcementConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        policyIdLoader = getPolicyIdLoader(actorSystem, enforcementConfig);
    }

    private AsyncCacheLoader<PolicyId, Entry<PolicyId>> getPolicyIdLoader(
            final ActorSystem actorSystem,
            final EnforcementConfig enforcementConfig) {

        final var clusterConfig = DefaultClusterConfig.of(actorSystem.settings().config().getConfig("ditto.cluster"));
        final ShardRegionProxyActorFactory shardRegionProxyActorFactory =
                ShardRegionProxyActorFactory.newInstance(actorSystem, clusterConfig);

        final ActorRef policiesShardRegion = shardRegionProxyActorFactory.getShardRegionProxyActor(
                PoliciesMessagingConstants.CLUSTER_ROLE, PoliciesMessagingConstants.SHARD_REGION);

        return new PreEnforcementPolicyIdCacheLoader(enforcementConfig.getAskWithRetryConfig(),
                actorSystem.getScheduler(),
                policiesShardRegion);
    }

    public CompletionStage<Boolean> checkExistence(final ModifyPolicy signal) {

        try {
            return policyIdLoader.asyncLoad(signal.getEntityId(),
                            actorSystem.dispatchers().lookup(ENFORCEMENT_CACHE_DISPATCHER))
                    .thenApply(Entry::exists);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not load policyId via policyIdLoader", e);
        }
    }

}
