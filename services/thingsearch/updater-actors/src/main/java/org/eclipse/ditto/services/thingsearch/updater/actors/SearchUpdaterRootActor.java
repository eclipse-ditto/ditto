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

import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.thingsearch.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.MongoEventToPersistenceStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.MongoThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.akka.streaming.StreamConsumerSettings;
import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonCommandListener;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonConnectionPoolListener;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;

import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import com.typesafe.config.Config;

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

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final SupervisorStrategy supervisorStrategy = RootSupervisorStrategyFactory.createStrategy(log);

    private final ActorRef thingsUpdaterActor;
    private final MongoClientWrapper mongoDbClientWrapper;

    private SearchUpdaterRootActor(final ServiceConfigReader configReader,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final StreamMetadataPersistence thingsSyncPersistence,
            final StreamMetadataPersistence policiesSyncPersistence) {

        final int numberOfShards = configReader.cluster().numberOfShards();

        final Config config = configReader.getRawConfig();
        final ActorSystem actorSystem = getContext().getSystem();

        final CommandListener kamonCommandListener = config.getBoolean(ConfigKeys.MONITORING_COMMANDS_ENABLED) ?
                new KamonCommandListener(KAMON_METRICS_PREFIX) : null;
        final ConnectionPoolListener kamonConnectionPoolListener =
                config.getBoolean(ConfigKeys.MONITORING_CONNECTION_POOL_ENABLED) ?
                        new KamonConnectionPoolListener(KAMON_METRICS_PREFIX) : null;

        mongoDbClientWrapper =
                MongoClientWrapper.newInstance(config, kamonCommandListener, kamonConnectionPoolListener);

        final ThingsSearchUpdaterPersistence searchUpdaterPersistence =
                inizializeThingsSearchUpdaterPersistence(mongoDbClientWrapper, materializer, config);

        final int maxFailures = config.getInt(ConfigKeys.MONGO_CIRCUIT_BREAKER_FAILURES);
        final Duration callTimeout = config.getDuration(ConfigKeys.MONGO_CIRCUIT_BREAKER_TIMEOUT_CALL);
        final Duration resetTimeout = config.getDuration(ConfigKeys.MONGO_CIRCUIT_BREAKER_TIMEOUT_RESET);
        final CircuitBreaker circuitBreaker =
                new CircuitBreaker(getContext().dispatcher(), actorSystem.scheduler(), maxFailures,
                        callTimeout, resetTimeout);
        circuitBreaker.onOpen(() -> log.warning(
                "The circuit breaker for this search updater instance is open which means that all ThingUpdaters" +
                        " won't process any messages until the circuit breaker is closed again"));
        circuitBreaker.onClose(() -> log.info(
                "The circuit breaker for this search updater instance is closed again. Therefore all ThingUpdaters" +
                        " process events again"));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());

        final boolean eventProcessingActive = config.getBoolean(ConfigKeys.EVENT_PROCESSING_ACTIVE);
        if (!eventProcessingActive) {
            log.warning("Event processing is disabled.");
        }

        final Duration thingUpdaterActivityCheckInterval =
                config.getDuration(ConfigKeys.THINGS_ACTIVITY_CHECK_INTERVAL);
        final ShardRegionFactory shardRegionFactory = ShardRegionFactory.getInstance(actorSystem);
        final int maxBulkSize = config.hasPath(ConfigKeys.MAX_BULK_SIZE)
                ? config.getInt(ConfigKeys.MAX_BULK_SIZE)
                : ThingUpdater.UNLIMITED_MAX_BULK_SIZE;

        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);
        thingsUpdaterActor = startChildActor(ThingsUpdater.ACTOR_NAME,
                ThingsUpdater.props(numberOfShards, shardRegionFactory, searchUpdaterPersistence, circuitBreaker,
                        eventProcessingActive, thingUpdaterActivityCheckInterval, maxBulkSize, blockedNamespaces));

        // start namespace ops actor as cluster singleton
        startClusterSingletonActor(ThingsSearchNamespaceOpsActor.ACTOR_NAME,
                ThingsSearchNamespaceOpsActor.props(pubSubMediator, searchUpdaterPersistence));

        final boolean thingsSynchronizationActive = config.getBoolean(ConfigKeys.THINGS_SYNCER_ACTIVE);
        if (thingsSynchronizationActive) {
            final StreamConsumerSettings streamConsumerSettings = createThingsStreamConsumerSettings(config);

            startClusterSingletonActor(ThingsStreamSupervisorCreator.ACTOR_NAME,
                    ThingsStreamSupervisorCreator.props(thingsUpdaterActor, pubSubMediator, thingsSyncPersistence,
                            materializer, streamConsumerSettings));
        } else {
            log.warning("Things synchronization is not active");
        }

        final boolean policiesSynchronizationActive = config.getBoolean(ConfigKeys.POLICIES_SYNCER_ACTIVE);
        if (policiesSynchronizationActive) {
            final StreamConsumerSettings streamConsumerSettings = createPoliciesStreamConsumerSettings(config);

            startClusterSingletonActor(PoliciesStreamSupervisorCreator.ACTOR_NAME,
                    PoliciesStreamSupervisorCreator.props(thingsUpdaterActor, pubSubMediator, policiesSyncPersistence,
                            materializer, streamConsumerSettings, searchUpdaterPersistence));
        } else {
            log.warning("Policies synchronization is not active");
        }

        final boolean deletionEnabled = config.getBoolean(ConfigKeys.DELETION_ENABLED);
        if (deletionEnabled) {
            startClusterSingletonActor(ThingsSearchIndexDeletionActor.ACTOR_NAME,
                    ThingsSearchIndexDeletionActor.props(mongoDbClientWrapper));
        } else {
            log.warning("Deletion of marked as deleted Things from search index is not enabled");
        }
    }

    private ThingsSearchUpdaterPersistence inizializeThingsSearchUpdaterPersistence(
            final MongoClientWrapper mongoClientWrapper, final ActorMaterializer materializer, final Config rawConfig) {
        final ThingsSearchUpdaterPersistence searchUpdaterPersistence =
                new MongoThingsSearchUpdaterPersistence(mongoClientWrapper, log,
                        MongoEventToPersistenceStrategyFactory.getInstance(), materializer);

        final boolean indexInitializationEnabled = rawConfig.getBoolean(ConfigKeys.INDEX_INITIALIZATION_ENABLED);
        if (indexInitializationEnabled) {
            searchUpdaterPersistence.initializeIndices();
        } else {
            log.info("Skipping IndexInitializer because it is disabled.");
        }

        return searchUpdaterPersistence;
    }

    private static StreamConsumerSettings createThingsStreamConsumerSettings(final Config config) {
        return StreamConsumerSettings.fromRelativeConfig(config.getConfig(ConfigKeys.SYNC_THINGS));
    }

    private static StreamConsumerSettings createPoliciesStreamConsumerSettings(final Config config) {
        return StreamConsumerSettings.fromRelativeConfig(config.getConfig(ConfigKeys.SYNC_POLICIES));
    }

    /**
     * Creates Akka configuration object Props for this SearchUpdaterRootActor.
     *
     * @param configReader the configuration reader of this service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer actor materializer to create stream actors.
     * @param thingsSyncPersistence persistence for background synchronization of things.
     * @param policiesSyncPersistence persistence for background synchronization of policies.
     * @return a Props object to create this actor.
     */
    public static Props props(final ServiceConfigReader configReader,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final StreamMetadataPersistence thingsSyncPersistence,
            final StreamMetadataPersistence policiesSyncPersistence) {

        return Props.create(SearchUpdaterRootActor.class, new Creator<SearchUpdaterRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public SearchUpdaterRootActor create() {
                return new SearchUpdaterRootActor(configReader, pubSubMediator, materializer, thingsSyncPersistence,
                        policiesSyncPersistence);
            }
        });
    }

    @Override
    public void postStop() throws Exception {
        mongoDbClientWrapper.close();
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
        ClusterUtil.startSingleton(getContext(), ConfigKeys.SEARCH_ROLE, actorName, props);
    }

}
