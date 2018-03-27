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
package org.eclipse.ditto.services.authorization.starter.actors;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import org.eclipse.ditto.services.authorization.starter.proxy.AuthorizationProxyPropsFactory;
import org.eclipse.ditto.services.authorization.util.config.AuthorizationConfigReader;
import org.eclipse.ditto.services.base.config.ClusterConfigReader;
import org.eclipse.ditto.services.models.authorization.AuthorizationMessagingConstants;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * The root actor of Authorization Service.
 */
public final class AuthorizationRootActor extends AbstractActor {

    // TODO: supervisory strategy, best without code duplication

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef authorizationShardRegion;

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "authorizationRoot";

    private AuthorizationRootActor(final AuthorizationConfigReader configReader, final ActorRef pubSubMediator,
            final AuthorizationProxyPropsFactory authorizationProxyPropsFactory) {
        requireNonNull(configReader);
        requireNonNull(pubSubMediator);
        requireNonNull(authorizationProxyPropsFactory);

        final ActorSystem actorSystem = getContext().getSystem();
        final ClusterConfigReader clusterConfigReader = configReader.cluster();

        final int numberOfShards = clusterConfigReader.numberOfShards();

        final ActorRef policiesShardRegionProxy = startProxy(actorSystem, numberOfShards,
                PoliciesMessagingConstants.SHARD_REGION, PoliciesMessagingConstants.CLUSTER_ROLE);

        final ActorRef thingsShardRegionProxy = startProxy(actorSystem, numberOfShards,
                ThingsMessagingConstants.SHARD_REGION, ThingsMessagingConstants.CLUSTER_ROLE);

        final Props enforcerProps =
                authorizationProxyPropsFactory.props(getContext(), configReader, pubSubMediator,
                        policiesShardRegionProxy, thingsShardRegionProxy);

        // start enforcer shard region; no need to keep the reference
        authorizationShardRegion = startShardRegion(actorSystem, clusterConfigReader, enforcerProps);
    }

    /**
     * Creates Akka configuration object Props for this AuthorizationRootActor
     *
     * @param configReader the authorization service config reader.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param authorizationProxyPropsFactory the {@link AuthorizationProxyPropsFactory}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final AuthorizationConfigReader configReader, final ActorRef pubSubMediator,
            final AuthorizationProxyPropsFactory authorizationProxyPropsFactory) {
        return Props.create(AuthorizationRootActor.class,
                () -> new AuthorizationRootActor(configReader, pubSubMediator, authorizationProxyPropsFactory));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(ShardRegion.getShardRegionStateInstance(), getShardRegionState ->
                        authorizationShardRegion.forward(getShardRegionState, getContext()))
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure <{}>!", f))
                .matchAny(m -> {
                    log.warning("Unknown message <{}>.", m);
                    unhandled(m);
                }).build();
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
