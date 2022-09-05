/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.service.actors.StartChildActor;
import org.eclipse.ditto.internal.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.internal.utils.cluster.ClusterUtil;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.write.impl.MongoThingsSearchUpdaterPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchUpdateMapper;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchUpdaterStream;
import org.eclipse.ditto.thingsearch.service.starter.actors.MongoClientExtension;
import org.eclipse.ditto.thingsearch.service.starter.actors.SearchRootActor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 * Child of {@code SearchRootActor}.
 */
public final class SearchUpdaterRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "searchUpdaterRoot";

    /**
     * The main cluster role of the cluster member where this actor and its children start.
     */
    public static final String CLUSTER_ROLE = ThingsSearchConstants.CLUSTER_ROLE;

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final SupervisorStrategy supervisorStrategy = RootSupervisorStrategyFactory.createStrategy(log);

    private final ActorRef thingsUpdaterActor;
    private final ActorRef backgroundSyncActorProxy;
    private final DittoMongoClient dittoMongoClient;

    @SuppressWarnings("unused")
    private SearchUpdaterRootActor(final SearchConfig searchConfig,
            final ActorRef pubSubMediator,
            final MongoThingsSearchPersistence thingsSearchPersistence,
            final TimestampPersistence backgroundSyncPersistence) {

        final var clusterConfig = searchConfig.getClusterConfig();
        final int numberOfShards = clusterConfig.getNumberOfShards();

        final var actorSystem = getContext().getSystem();

        dittoMongoClient = MongoClientExtension.get(actorSystem).getUpdaterClient();

        final var shardRegionFactory = ShardRegionFactory.getInstance(actorSystem);
        final var blockedNamespaces = BlockedNamespaces.of(actorSystem);

        final var updaterConfig = searchConfig.getUpdaterConfig();
        if (!updaterConfig.isEventProcessingActive()) {
            log.warning("Event processing is disabled!");
        }

        final ActorRef thingsShard = shardRegionFactory.getThingsShardRegion(numberOfShards);
        final ActorRef policiesShard = shardRegionFactory.getPoliciesShardRegion(numberOfShards);
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        final var searchUpdateMapper = SearchUpdateMapper.get(actorSystem, dittoExtensionsConfig);
        final SearchUpdaterStream searchUpdaterStream =
                SearchUpdaterStream.of(updaterConfig, actorSystem, thingsShard, policiesShard,
                        dittoMongoClient.getDefaultDatabase(), blockedNamespaces,
                        searchUpdateMapper);

        final var thingUpdaterProps =
                ThingUpdater.props(searchUpdaterStream.flow(), thingsSearchPersistence::recoverLastWriteModel,
                        searchConfig, pubSubMediator);
        final ActorRef updaterShard =
                shardRegionFactory.getSearchUpdaterShardRegion(numberOfShards, thingUpdaterProps, CLUSTER_ROLE);

        final var searchUpdaterPersistence =
                MongoThingsSearchUpdaterPersistence.of(dittoMongoClient.getDefaultDatabase(),
                        updaterConfig.getUpdaterPersistenceConfig());

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());

        final var thingsUpdaterProps =
                ThingsUpdater.props(updaterShard, updaterConfig, blockedNamespaces, pubSubMediator);

        thingsUpdaterActor = startChildActor(ThingsUpdater.ACTOR_NAME, thingsUpdaterProps);

        // start policy modification forwarder
        startChildActor(PolicyModificationForwarder.ACTOR_NAME, PolicyModificationForwarder.props(
                pubSubMediator, thingsUpdaterActor, blockedNamespaces, searchUpdaterPersistence));

        // start background sync actor as cluster singleton
        final var backgroundSyncActorProps = BackgroundSyncActor.props(
                updaterConfig.getBackgroundSyncConfig(),
                pubSubMediator,
                thingsSearchPersistence,
                backgroundSyncPersistence,
                shardRegionFactory.getPoliciesShardRegion(numberOfShards),
                thingsUpdaterActor
        );
        backgroundSyncActorProxy =
                ClusterUtil.startSingletonProxy(getContext(), CLUSTER_ROLE,
                        startClusterSingletonActor(BackgroundSyncActor.ACTOR_NAME, backgroundSyncActorProps)
                );

        startChildActor(ThingsSearchPersistenceOperationsActor.ACTOR_NAME,
                ThingsSearchPersistenceOperationsActor.props(pubSubMediator, searchUpdaterPersistence,
                        searchConfig.getPersistenceOperationsConfig()));
    }

    /**
     * Creates Akka configuration object Props for this SearchUpdaterRootActor.
     *
     * @param searchConfig the configuration settings of the Things-Search service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param thingsSearchPersistence persistence to access the search index in read-only mode.
     * @param backgroundSyncPersistence persistence for background synchronization.
     * @return a Props object to create this actor.
     */
    public static Props props(final SearchConfig searchConfig,
            final ActorRef pubSubMediator,
            final MongoThingsSearchPersistence thingsSearchPersistence,
            final TimestampPersistence backgroundSyncPersistence) {
        return Props.create(SearchUpdaterRootActor.class, searchConfig, pubSubMediator, thingsSearchPersistence,
                backgroundSyncPersistence);
    }

    /**
     * Select the ThingsUpdater in the actor system.
     *
     * @param system the actor system.
     * @return actor selection for the ThingsUpdater in the system.
     */
    public static ActorSelection getThingsUpdater(final ActorRefFactory system) {
        return system.actorSelection(
                String.format("user/%s/%s/%s", SearchRootActor.ACTOR_NAME, ACTOR_NAME, ThingsUpdater.ACTOR_NAME));
    }

    @Override
    public void postStop() throws Exception {
        dittoMongoClient.close();
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, cmd -> thingsUpdaterActor.forward(cmd, getContext()))
                .match(RetrieveHealth.class, cmd -> backgroundSyncActorProxy.forward(cmd, getContext()))
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .match(StartChildActor.class, this::startChildActor)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    private void startChildActor(final StartChildActor message) {
        startChildActor(message.getActorName(), message.getProps());
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor <{}>.", actorName);
        return getContext().actorOf(props, actorName);
    }

    private ActorRef startClusterSingletonActor(final String actorName, final Props props) {
        return ClusterUtil.startSingleton(getContext(), CLUSTER_ROLE, actorName, props);
    }

}
