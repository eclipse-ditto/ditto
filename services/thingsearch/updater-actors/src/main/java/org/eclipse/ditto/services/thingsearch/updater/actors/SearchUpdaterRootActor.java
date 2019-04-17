/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.time.Duration;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.thingsearch.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.MongoEventToPersistenceStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.MongoThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.updater.config.DeletionConfig;
import org.eclipse.ditto.services.thingsearch.updater.config.UpdaterConfig;
import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.config.IndexInitializationConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonCommandListener;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonConnectionPoolListener;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;

import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.CircuitBreaker;
import akka.stream.ActorMaterializer;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
public final class SearchUpdaterRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "searchUpdaterRoot";

    private static final String KAMON_METRICS_PREFIX = "updater";

    private static final String SEARCH_ROLE = "things-search";

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final SupervisorStrategy supervisorStrategy = RootSupervisorStrategyFactory.createStrategy(log);

    private final ActorRef thingsUpdaterActor;
    private final DittoMongoClient mongoClient;

    private SearchUpdaterRootActor(final ServiceSpecificConfig.ClusterConfig clusterConfig,
            final MongoDbConfig mongoDbConfig,
            final IndexInitializationConfig indexInitializationConfig,
            final UpdaterConfig updaterConfig,
            final DeletionConfig deletionConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final TimestampPersistence thingsSyncPersistence,
            final TimestampPersistence policiesSyncPersistence) {

        final int numberOfShards = clusterConfig.getNumberOfShards();

        final ActorSystem actorSystem = getContext().getSystem();

        mongoClient = MongoClientWrapper.getBuilder(mongoDbConfig)
                .addCommandListener(getCommandListenerOrNull(mongoDbConfig.getMonitoringConfig()))
                .addConnectionPoolListener(getConnectionPoolListenerOrNull(mongoDbConfig.getMonitoringConfig()))
                .build();

        final ThingsSearchUpdaterPersistence searchUpdaterPersistence =
                initializeThingsSearchUpdaterPersistence(mongoClient, materializer, indexInitializationConfig);

        final MongoDbConfig.CircuitBreakerConfig circuitBreakerConfig = mongoDbConfig.getCircuitBreakerConfig();
        final MongoDbConfig.CircuitBreakerConfig.TimeoutConfig timeoutConfig = circuitBreakerConfig.getTimeoutConfig();
        final CircuitBreaker circuitBreaker = new CircuitBreaker(getContext().dispatcher(), actorSystem.scheduler(),
                circuitBreakerConfig.getMaxFailures(), timeoutConfig.getCall(), timeoutConfig.getReset());
        circuitBreaker.onOpen(() -> log.warning(
                "The circuit breaker for this search updater instance is open which means that all ThingUpdaters" +
                        " won't process any messages until the circuit breaker is closed again"));
        circuitBreaker.onClose(() -> log.info(
                "The circuit breaker for this search updater instance is closed again. Therefore all ThingUpdaters" +
                        " process events again"));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());

        final boolean eventProcessingActive = updaterConfig.isEventProcessingActive();
        if (!eventProcessingActive) {
            log.warning("Event processing is disabled!");
        }

        final Duration thingUpdaterActivityCheckInterval = updaterConfig.getActivityCheckInterval();
        final ShardRegionFactory shardRegionFactory = ShardRegionFactory.getInstance(actorSystem);
        final int maxBulkSize = updaterConfig.getMaxBulkSize();

        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);
        thingsUpdaterActor = startChildActor(
                ThingsUpdater.props(numberOfShards, shardRegionFactory, searchUpdaterPersistence, circuitBreaker,
                        eventProcessingActive, thingUpdaterActivityCheckInterval, maxBulkSize, blockedNamespaces));

        // start namespace ops actor as cluster singleton
        startClusterSingletonActor(ThingsSearchNamespaceOpsActor.ACTOR_NAME,
                ThingsSearchNamespaceOpsActor.props(pubSubMediator, searchUpdaterPersistence));

        startThingsStreamSupervisor(updaterConfig.getThingsSyncConfig(), pubSubMediator, materializer,
                thingsSyncPersistence);

        startPoliciesStreamsSupervisor(updaterConfig.getPoliciesSyncConfig(), pubSubMediator, materializer,
                policiesSyncPersistence, searchUpdaterPersistence);

        if (deletionConfig.isEnabled()) {
            startClusterSingletonActor(ThingsSearchIndexDeletionActor.ACTOR_NAME, ThingsSearchIndexDeletionActor.props(
                    mongoClient.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME),
                    deletionConfig.getDeletionAge(), deletionConfig.getRunInterval(),
                    deletionConfig.getFirstIntervalHour()));
        } else {
            log.warning("Deletion of marked as deleted things from search index is not enabled!");
        }
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

    private ThingsSearchUpdaterPersistence initializeThingsSearchUpdaterPersistence(final DittoMongoClient mongoClient,
            final ActorMaterializer materializer, final IndexInitializationConfig indexInitializationConfig) {

        final ThingsSearchUpdaterPersistence searchUpdaterPersistence =
                new MongoThingsSearchUpdaterPersistence(mongoClient, log,
                        MongoEventToPersistenceStrategyFactory.getInstance(), materializer);

        if (indexInitializationConfig.isIndexInitializationConfigEnabled()) {
            searchUpdaterPersistence.initializeIndices();
        } else {
            log.info("Skipping IndexInitializer because it is disabled.");
        }

        return searchUpdaterPersistence;
    }

    /**
     * Creates Akka configuration object Props for this SearchUpdaterRootActor.
     *
     * @param clusterConfig the cluster configuration settings of this service.
     * @param mongoDbConfig the MongoDB configuration settings of this service.
     * @param updaterConfig the updater configuration settings of this service.
     * @param deletionConfig the configuration settings for the physical deletion of thing entities that are marked as
     * {@code "__deleted"}..
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer actor materializer to create stream actors.
     * @param thingsSyncPersistence persistence for background synchronization of things.
     * @param policiesSyncPersistence persistence for background synchronization of policies.
     * @return a Props object to create this actor.
     */
    public static Props props(final ServiceSpecificConfig.ClusterConfig clusterConfig,
            final MongoDbConfig mongoDbConfig,
            final IndexInitializationConfig indexInitializationConfig,
            final UpdaterConfig updaterConfig,
            final DeletionConfig deletionConfig,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final TimestampPersistence thingsSyncPersistence,
            final TimestampPersistence policiesSyncPersistence) {

        return Props.create(SearchUpdaterRootActor.class, new Creator<SearchUpdaterRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public SearchUpdaterRootActor create() {
                return new SearchUpdaterRootActor(clusterConfig, mongoDbConfig, indexInitializationConfig,
                        updaterConfig, deletionConfig, pubSubMediator, materializer, thingsSyncPersistence,
                        policiesSyncPersistence);
            }
        });
    }

    @Override
    public void postStop() throws Exception {
        mongoClient.close();
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

    private ActorRef startChildActor(final Props props) {
        log.info("Starting child actor <{}>.", ThingsUpdater.ACTOR_NAME);
        return getContext().actorOf(props, ThingsUpdater.ACTOR_NAME);
    }

    private void startClusterSingletonActor(final String actorName, final Props props) {
        ClusterUtil.startSingleton(getContext(), SEARCH_ROLE, actorName, props);
    }

}
