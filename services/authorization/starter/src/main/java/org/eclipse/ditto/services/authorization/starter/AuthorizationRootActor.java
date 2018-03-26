/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.authorization.starter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.authorization.util.cache.EnforcerCacheLoader;
import org.eclipse.ditto.services.authorization.util.cache.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.authorization.util.config.AuthorizationConfigReader;
import org.eclipse.ditto.services.authorization.util.enforcement.EnforcerActor;
import org.eclipse.ditto.services.authorization.util.update.CacheUpdater;
import org.eclipse.ditto.services.base.config.ClusterConfigReader;
import org.eclipse.ditto.services.base.metrics.StatsdMetricsReporter;
import org.eclipse.ditto.services.models.authorization.AuthorizationMessagingConstants;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.japi.pf.ReceiveBuilder;

/**
 * The root actor of Authorization Service.
 */
public final class AuthorizationRootActor extends AbstractActor {

    // TODO: supervisory strategy, best without code duplication

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "authorizationRoot";

    private AuthorizationRootActor(final AuthorizationConfigReader configReader, final ActorRef pubSubMediator) {
        final ActorSystem actorSystem = getContext().getSystem();
        final ClusterConfigReader clusterConfigReader = configReader.cluster();

        final int numberOfShards = clusterConfigReader.numberOfShards();

        final ActorRef policiesShardRegionProxy = startProxy(actorSystem, numberOfShards,
                PoliciesMessagingConstants.SHARD_REGION, PoliciesMessagingConstants.CLUSTER_ROLE);

        final ActorRef thingsShardRegionProxy = startProxy(actorSystem, numberOfShards,
                ThingsMessagingConstants.SHARD_REGION, ThingsMessagingConstants.CLUSTER_ROLE);

        final EntityRegionMap entityRegionMap =  EntityRegionMap.newBuilder()
                .put(PolicyCommand.RESOURCE_TYPE, policiesShardRegionProxy)
                .put(ThingCommand.RESOURCE_TYPE, thingsShardRegionProxy)
                .build();

        final Duration askTimeout = configReader.caches().askTimeout();
        final Map<String, AsyncCacheLoader<EntityId, Entry<EntityId>>> enforcementIdCacheLoaders = new HashMap<>();
        final ThingEnforcementIdCacheLoader thingEnforcerIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegionProxy);
        enforcementIdCacheLoaders.put(ThingCommand.RESOURCE_TYPE, thingEnforcerIdCacheLoader);

        final EnforcerCacheLoader enforcerCacheLoader =
                new EnforcerCacheLoader(askTimeout, thingsShardRegionProxy, policiesShardRegionProxy);

        final AuthorizationCaches caches = new AuthorizationCaches(configReader.caches(), enforcerCacheLoader,
                enforcementIdCacheLoaders,
                namedMetricRegistry -> StatsdMetricsReporter.getInstance().add(namedMetricRegistry));

        final Props enforcerProps = EnforcerActor.props(pubSubMediator, entityRegionMap, caches);

        // start enforcer shard region; no need to keep the reference
        startShardRegion(actorSystem, clusterConfigReader, enforcerProps);

        // start cache updater
        getContext().actorOf(CacheUpdater.props(pubSubMediator, caches, configReader.instanceIndex()),
                CacheUpdater.ACTOR_NAME);
    }

    /**
     * Creates Akka configuration object Props for this AuthorizationRootActor
     *
     * @param configReader the authorization service config reader.
     * @param pubSubMediator the PubSub mediator Actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final AuthorizationConfigReader configReader, final ActorRef pubSubMediator) {
        return Props.create(AuthorizationRootActor.class,
                () -> new AuthorizationRootActor(configReader, pubSubMediator));
    }

    @Override
    public Receive createReceive() {
        // TODO: do something.
        return ReceiveBuilder.create()
                .build();
    }

    private static ActorRef startProxy(final ActorSystem actorSystem,
            final int numberOfShards,
            final String shardRegionName,
            final String clusterRole) {

        final ShardRegionExtractor shardRegionExtractor = ShardRegionExtractor.of(numberOfShards, actorSystem);

        return ClusterSharding.get(actorSystem)
                .startProxy(shardRegionName, Optional.of(clusterRole), shardRegionExtractor);
    }

    private static ActorRef startShardRegion(final ActorSystem actorSystem,
            final ClusterConfigReader clusterConfigReader,
            final Props props) {

        final String shardName = AuthorizationMessagingConstants.SHARD_REGION;

        final ClusterShardingSettings settings = ClusterShardingSettings.create(actorSystem)
                .withRole(AuthorizationMessagingConstants.CLUSTER_ROLE);

        final ShardRegionExtractor extractor =
                ShardRegionExtractor.of(clusterConfigReader.numberOfShards(), actorSystem);

        return ClusterSharding.get(actorSystem).start(shardName, props, settings, extractor);
    }
}
