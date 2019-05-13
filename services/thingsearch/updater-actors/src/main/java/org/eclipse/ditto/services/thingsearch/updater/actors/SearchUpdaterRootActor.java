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

import java.time.Duration;

import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.thingsearch.common.util.RootSupervisorStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.MongoThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.ChangeQueueActor;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.SearchUpdaterStream;
import org.eclipse.ditto.services.utils.akka.streaming.StreamConsumerSettings;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.config.MongoConfig;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonCommandListener;
import org.eclipse.ditto.services.utils.persistence.mongo.monitoring.KamonConnectionPoolListener;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;

import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.reactivestreams.client.MongoDatabase;
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

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final SupervisorStrategy supervisorStrategy = RootSupervisorStrategyFactory.createStrategy(log);

    private final KillSwitch updaterStreamKillSwitch;
    private final ActorRef thingsUpdaterActor;
    private final DittoMongoClient dittoMongoClient;

    private SearchUpdaterRootActor(final ServiceConfigReader configReader,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final TimestampPersistence thingsSyncPersistence,
            final TimestampPersistence policiesSyncPersistence) {

        final int numberOfShards = configReader.cluster().numberOfShards();

        final Config config = configReader.getRawConfig();
        final ActorSystem actorSystem = getContext().getSystem();
        final ShardRegionFactory shardRegionFactory = ShardRegionFactory.getInstance(actorSystem);
        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);

        dittoMongoClient = startMongoClientWrapper(getContext());


        final ActorRef changeQueueActor = getContext().actorOf(ChangeQueueActor.props(), ChangeQueueActor.ACTOR_NAME);
        updaterStreamKillSwitch =
                startSearchUpdaterStream(actorSystem, shardRegionFactory, numberOfShards, changeQueueActor,
                        dittoMongoClient.getDefaultDatabase(), blockedNamespaces);

        final ThingsSearchUpdaterPersistence searchUpdaterPersistence =
                MongoThingsSearchUpdaterPersistence.of(dittoMongoClient.getDefaultDatabase());

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());

        final boolean eventProcessingActive = config.getBoolean(ConfigKeys.EVENT_PROCESSING_ACTIVE);
        if (!eventProcessingActive) {
            log.warning("Event processing is disabled.");
        }

        final Duration thingUpdaterMaxIdleTime =
                config.getDuration(ConfigKeys.THING_UPDATER_MAX_IDLE_TIME);

        final Props thingUpdaterProps =
                ThingUpdater.props(pubSubMediator, changeQueueActor, thingUpdaterMaxIdleTime);

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

        startChildActor(ThingsSearchPersistenceOperationsActor.ACTOR_NAME,
                ThingsSearchPersistenceOperationsActor.props(pubSubMediator, searchUpdaterPersistence));

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
            final TimestampPersistence thingsSyncPersistence,
            final TimestampPersistence policiesSyncPersistence) {

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
        ClusterUtil.startSingleton(getContext(), ConfigKeys.SEARCH_ROLE, actorName, props);
    }

    private KillSwitch startSearchUpdaterStream(
            final ActorSystem actorSystem,
            final ShardRegionFactory shardRegionFactory,
            final int numberOfShards,
            final ActorRef changeQueueActor,
            final MongoDatabase mongoDatabase,
            final BlockedNamespaces blockedNamespaces) {

        final ActorRef thingsShard = shardRegionFactory.getThingsShardRegion(numberOfShards);
        final ActorRef policiesShard = shardRegionFactory.getPoliciesShardRegion(numberOfShards);

        final SearchUpdaterStream searchUpdaterStream =
                SearchUpdaterStream.of(actorSystem, thingsShard, policiesShard, changeQueueActor, mongoDatabase,
                        blockedNamespaces);

        return searchUpdaterStream.start(getContext());
    }

    /**
     * Start a Mongo client wrapper in an actor. The actor should close the client when it stops.
     *
     * @param context the context of the actor calling this method.
     * @return a new MongoClientWrapper.
     */
    static DittoMongoClient startMongoClientWrapper(final ActorContext context) {

        final ActorSystem actorSystem = context.getSystem();

        final Config config = actorSystem.settings().config();

        final CommandListener kamonCommandListener = config.getBoolean(ConfigKeys.MONITORING_COMMANDS_ENABLED) ?
                new KamonCommandListener(KAMON_METRICS_PREFIX) : null;
        final ConnectionPoolListener kamonConnectionPoolListener =
                config.getBoolean(ConfigKeys.MONITORING_CONNECTION_POOL_ENABLED) ?
                        new KamonConnectionPoolListener(KAMON_METRICS_PREFIX) : null;

        return MongoClientWrapper.getBuilder(MongoConfig.of(config))
                .addCommandListener(kamonCommandListener)
                .addConnectionPoolListener(kamonConnectionPoolListener)
                .build();
    }
}
