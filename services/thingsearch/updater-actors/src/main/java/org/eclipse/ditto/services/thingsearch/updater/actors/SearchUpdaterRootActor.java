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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.thingsearch.common.config.DeleteConfig;
import org.eclipse.ditto.services.thingsearch.common.config.SearchConfig;
import org.eclipse.ditto.services.thingsearch.common.config.UpdaterConfig;
import org.eclipse.ditto.services.thingsearch.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.MongoThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.ChangeQueueActor;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.SearchUpdaterStream;
import org.eclipse.ditto.services.thingsearch.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.MongoThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.ChangeQueueActor;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.SearchUpdaterStream;
import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonCommandListener;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonConnectionPoolListener;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;

import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.KillSwitch;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 * Child of {@code SearchRootActor}.
 */
public final class SearchUpdaterRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "searchUpdaterRoot";

    static final String CLUSTER_ROLE = "things-search";

    private static final String KAMON_METRICS_PREFIX = "updater";

    private static final String SEARCH_ROLE = "things-search";

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final SupervisorStrategy supervisorStrategy = RootSupervisorStrategyFactory.createStrategy(log);

    private final KillSwitch updaterStreamKillSwitch;
    private final ActorRef thingsUpdaterActor;
    private final DittoMongoClient dittoMongoClient;

    @SuppressWarnings("unused")
    private SearchUpdaterRootActor(final SearchConfig searchConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final TimestampPersistence thingsSyncPersistence,
            final TimestampPersistence policiesSyncPersistence) {

        final ClusterConfig clusterConfig = searchConfig.getClusterConfig();
        final int numberOfShards = clusterConfig.getNumberOfShards();

        final ActorSystem actorSystem = getContext().getSystem();

        final MongoDbConfig mongoDbConfig = searchConfig.getMongoDbConfig();
        dittoMongoClient = MongoClientWrapper.getBuilder(mongoDbConfig)
                .addCommandListener(getCommandListenerOrNull(mongoDbConfig.getMonitoringConfig()))
                .addConnectionPoolListener(getConnectionPoolListenerOrNull(mongoDbConfig.getMonitoringConfig()))
                .build();

        final ShardRegionFactory shardRegionFactory = ShardRegionFactory.getInstance(actorSystem);
        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);
        final ActorRef changeQueueActor = getContext().actorOf(ChangeQueueActor.props(), ChangeQueueActor.ACTOR_NAME);
        updaterStreamKillSwitch =
                startSearchUpdaterStream(searchConfig, actorSystem, shardRegionFactory, numberOfShards,
                        changeQueueActor, dittoMongoClient.getDefaultDatabase(), blockedNamespaces);

        final ThingsSearchUpdaterPersistence searchUpdaterPersistence =
                MongoThingsSearchUpdaterPersistence.of(dittoMongoClient.getDefaultDatabase());

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());

        final UpdaterConfig updaterConfig = searchConfig.getUpdaterConfig();
        final boolean eventProcessingActive = updaterConfig.isEventProcessingActive();
        if (!eventProcessingActive) {
            log.warning("Event processing is disabled!");
        }

        final Props thingUpdaterProps = ThingUpdater.props(pubSubMediator, changeQueueActor);

        final ActorRef updaterShardRegion =
                shardRegionFactory.getSearchUpdaterShardRegion(numberOfShards, thingUpdaterProps, CLUSTER_ROLE);

        final Props thingsUpdaterProps =
                ThingsUpdater.props(pubSubMediator, updaterShardRegion, eventProcessingActive, blockedNamespaces);

        thingsUpdaterActor = getContext().actorOf(thingsUpdaterProps, ThingsUpdater.ACTOR_NAME);

        // start policy event forwarder as cluster singleton
        final Props policyEventForwarderProps =
                PolicyEventForwarder.props(pubSubMediator, thingsUpdaterActor, blockedNamespaces,
                        searchUpdaterPersistence);
        startClusterSingletonActor(PolicyEventForwarder.ACTOR_NAME, policyEventForwarderProps);

        // start manual updater as cluster singleton
        final Props manualUpdaterProps = ManualUpdater.props(dittoMongoClient.getDefaultDatabase(), thingsUpdaterActor);
        startClusterSingletonActor(ManualUpdater.ACTOR_NAME, manualUpdaterProps);

        // TODO Fix compilation error.
        startChildActor(ThingsSearchPersistenceOperationsActor.ACTOR_NAME,
                ThingsSearchPersistenceOperationsActor.props(pubSubMediator, searchUpdaterPersistence, config));

        startThingsStreamSupervisor(updaterConfig.getThingsSyncConfig(), pubSubMediator, materializer,
                thingsSyncPersistence);

        startPoliciesStreamsSupervisor(updaterConfig.getPoliciesSyncConfig(), pubSubMediator, materializer,
                policiesSyncPersistence, searchUpdaterPersistence);
    }

    private void startThingsStreamSupervisor(final SyncConfig thingsSyncConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final TimestampPersistence thingsSyncPersistence) {

        if (thingsSyncConfig.isEnabled()) {
            startClusterSingletonActor(ThingsStreamSupervisorCreator.ACTOR_NAME,
                    ThingsStreamSupervisorCreator.props(thingsUpdaterActor, pubSubMediator, thingsSyncPersistence,
                            materializer, thingsSyncConfig));
        } else {
            log.warning("Things synchronization is not active!");
        }
    }

    private void startPoliciesStreamsSupervisor(final SyncConfig policiesSyncConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final TimestampPersistence policiesSyncPersistence,
            final ThingsSearchUpdaterPersistence searchUpdaterPersistence) {

        if (policiesSyncConfig.isEnabled()) {
            startClusterSingletonActor(PoliciesStreamSupervisorCreator.ACTOR_NAME,
                    PoliciesStreamSupervisorCreator.props(thingsUpdaterActor, pubSubMediator, policiesSyncPersistence,
                            materializer, policiesSyncConfig, searchUpdaterPersistence));
        } else {
            log.warning("Policies synchronization is not active!");
        }
    }

    @Nullable
    private static CommandListener getCommandListenerOrNull(final MongoDbConfig.MonitoringConfig monitoringConfig) {
        return monitoringConfig.isCommandsEnabled() ? new KamonCommandListener(KAMON_METRICS_PREFIX) : null;
    }

    @Nullable
    private static ConnectionPoolListener getConnectionPoolListenerOrNull(
            final MongoDbConfig.MonitoringConfig monitoringConfig) {

        return monitoringConfig.isConnectionPoolEnabled()
                ? new KamonConnectionPoolListener(KAMON_METRICS_PREFIX)
                : null;
    }

    /**
     * Creates Akka configuration object Props for this SearchUpdaterRootActor.
     *
     * @param searchConfig the configuration settings of the Things-Search service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer actor materializer to create stream actors.
     * @param thingsSyncPersistence persistence for background synchronization of things.
     * @param policiesSyncPersistence persistence for background synchronization of policies.
     * @return a Props object to create this actor.
     */
    public static Props props(final SearchConfig searchConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final TimestampPersistence thingsSyncPersistence,
            final TimestampPersistence policiesSyncPersistence) {

        return Props.create(SearchUpdaterRootActor.class, searchConfig, pubSubMediator, materializer,
                thingsSyncPersistence, policiesSyncPersistence);
    }

    @Override
    public void postStop() throws Exception {
        updaterStreamKillSwitch.shutdown();
        dittoMongoClient.close();
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, cmd -> thingsUpdaterActor.forward(cmd, getContext()))
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
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

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor '{}'", actorName);
        return getContext().actorOf(props, actorName);
    }

    private void startClusterSingletonActor(final String actorName, final Props props) {
        ClusterUtil.startSingleton(getContext(), SEARCH_ROLE, actorName, props);
    }

    private KillSwitch startSearchUpdaterStream(final SearchConfig searchConfig,
            final ActorSystem actorSystem,
            final ShardRegionFactory shardRegionFactory,
            final int numberOfShards,
            final ActorRef changeQueueActor,
            final MongoDatabase mongoDatabase,
            final BlockedNamespaces blockedNamespaces) {

        final ActorRef thingsShard = shardRegionFactory.getThingsShardRegion(numberOfShards);
        final ActorRef policiesShard = shardRegionFactory.getPoliciesShardRegion(numberOfShards);

        final SearchUpdaterStream searchUpdaterStream =
                SearchUpdaterStream.of(searchConfig, actorSystem, thingsShard, policiesShard, changeQueueActor,
                        mongoDatabase, blockedNamespaces);

        return searchUpdaterStream.start(getContext());
    }

}
