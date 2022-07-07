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
package org.eclipse.ditto.things.service.starter;

import static org.eclipse.ditto.things.api.ThingsMessagingConstants.CLUSTER_ROLE;

import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.service.RootChildActorStarter;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.internal.utils.aggregator.DefaultThingsAggregatorConfig;
import org.eclipse.ditto.internal.utils.aggregator.ThingsAggregatorActor;
import org.eclipse.ditto.internal.utils.aggregator.ThingsAggregatorConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.ClusterUtil;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.internal.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespacesUpdater;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.PersistenceCleanupActor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsubthings.LiveSignalPub;
import org.eclipse.ditto.internal.utils.pubsubthings.ThingEventPubSubFactory;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.enforcement.DefaultPolicyEnforcerProvider;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.common.config.ThingsConfig;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceActorPropsFactory;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceOperationsActor;
import org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor;
import org.eclipse.ditto.things.service.persistence.actors.ThingsPersistenceStreamingActorCreator;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
public final class ThingsRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "thingsRoot";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final RetrieveStatisticsDetailsResponseSupplier retrieveStatisticsDetailsResponseSupplier;

    @SuppressWarnings("unused")
    private ThingsRootActor(final ThingsConfig thingsConfig,
            final ActorRef pubSubMediator,
            final ThingPersistenceActorPropsFactory propsFactory) {

        final var actorSystem = getContext().system();

        final var clusterConfig = thingsConfig.getClusterConfig();
        final var shardRegionExtractor =
                ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem);
        final var distributedAcks = DistributedAcks.lookup(actorSystem);
        final var pubSubFactory =
                ThingEventPubSubFactory.of(getContext(), shardRegionExtractor, distributedAcks);
        final DistributedPub<ThingEvent<?>> distributedPubThingEventsForTwin = pubSubFactory.startDistributedPub();
        final LiveSignalPub liveSignalPub = LiveSignalPub.of(getContext(), distributedAcks);

        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);
        // start cluster singleton that writes to the distributed cache of blocked namespaces
        final Props blockedNamespacesUpdaterProps = BlockedNamespacesUpdater.props(blockedNamespaces, pubSubMediator);
        ClusterUtil.startSingleton(actorSystem, getContext(), CLUSTER_ROLE,
                BlockedNamespacesUpdater.ACTOR_NAME, blockedNamespacesUpdaterProps);
        final PolicyEnforcerProvider policyEnforcerProvider = DefaultPolicyEnforcerProvider.getInstance(actorSystem);
        final Props thingSupervisorActorProps = getThingSupervisorActorProps(pubSubMediator,
                distributedPubThingEventsForTwin,
                liveSignalPub,
                propsFactory,
                blockedNamespaces,
                policyEnforcerProvider
        );
        final ActorRef thingsShardRegion = ClusterSharding.get(actorSystem)
                .start(ThingsMessagingConstants.SHARD_REGION,
                        thingSupervisorActorProps,
                        ClusterShardingSettings.create(actorSystem).withRole(CLUSTER_ROLE),
                        shardRegionExtractor
                );

        startChildActor(ThingPersistenceOperationsActor.ACTOR_NAME,
                ThingPersistenceOperationsActor.props(pubSubMediator, thingsConfig.getMongoDbConfig(),
                        actorSystem.settings().config(), thingsConfig.getPersistenceOperationsConfig()));

        final ThingsAggregatorConfig thingsAggregatorConfig = DefaultThingsAggregatorConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        final Props props = ThingsAggregatorActor.props(thingsShardRegion, thingsAggregatorConfig);
        final ActorRef thingsAggregatorActor = getContext().actorOf(props, ThingsAggregatorActor.ACTOR_NAME);

        retrieveStatisticsDetailsResponseSupplier = RetrieveStatisticsDetailsResponseSupplier.of(thingsShardRegion,
                ThingsMessagingConstants.SHARD_REGION, log);

        final var healthCheckConfig = thingsConfig.getHealthCheckConfig();
        final var hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());
        if (healthCheckConfig.getPersistenceConfig().isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }

        final var healthCheckingActorOptions = hcBuilder.build();
        final var metricsReporterConfig =
                healthCheckConfig.getPersistenceConfig().getMetricsReporterConfig();
        final ActorRef healthCheckingActor = startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, MongoHealthChecker.props()));

        final ActorRef snapshotStreamingActor =
                ThingsPersistenceStreamingActorCreator.startSnapshotStreamingActor(this::startChildActor);

        final var cleanupConfig = thingsConfig.getThingConfig().getCleanupConfig();
        final var mongoReadJournal = newMongoReadJournal(thingsConfig.getMongoDbConfig(), actorSystem);
        final Props cleanupActorProps = PersistenceCleanupActor.props(cleanupConfig, mongoReadJournal, CLUSTER_ROLE);
        startChildActor(PersistenceCleanupActor.NAME, cleanupActorProps);

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());
        pubSubMediator.tell(DistPubSubAccess.put(snapshotStreamingActor), getSelf());

        bindHttpStatusRoute(thingsConfig.getHttpConfig(), healthCheckingActor);

        final var rawServiceConfig = ScopedConfig.getOrEmpty(actorSystem.settings().config(), "ditto.things");
        RootChildActorStarter.get(actorSystem, rawServiceConfig).execute(getContext());

    }

    /**
     * Creates Akka configuration object Props for this ThingsRootActor.
     *
     * @param thingsConfig the configuration settings of the Things service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param propsFactory factory of Props of thing-persistence-actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ThingsConfig thingsConfig,
            final ActorRef pubSubMediator,
            final ThingPersistenceActorPropsFactory propsFactory) {

        return Props.create(ThingsRootActor.class, thingsConfig, pubSubMediator, propsFactory);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, this::handleRetrieveStatisticsDetails)
                .build().orElse(super.createReceive());
    }

    private void handleRetrieveStatisticsDetails(final RetrieveStatisticsDetails command) {
        log.info("Sending the namespace stats of the things shard as requested ...");
        Patterns.pipe(retrieveStatisticsDetailsResponseSupplier
                .apply(command.getDittoHeaders()), getContext().dispatcher()).to(getSender());
    }

    private static Props getThingSupervisorActorProps(final ActorRef pubSubMediator,
            final DistributedPub<ThingEvent<?>> distributedPubThingEventsForTwin,
            final LiveSignalPub liveSignalPub,
            final ThingPersistenceActorPropsFactory propsFactory,
            final BlockedNamespaces blockedNamespaces,
            final PolicyEnforcerProvider policyEnforcerProvider) {

        return ThingSupervisorActor.props(pubSubMediator, distributedPubThingEventsForTwin,
                liveSignalPub, propsFactory, blockedNamespaces, policyEnforcerProvider);
    }

    private static MongoReadJournal newMongoReadJournal(final MongoDbConfig mongoDbConfig,
            final ActorSystem actorSystem) {

        final var config = actorSystem.settings().config();
        final var mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
        return MongoReadJournal.newInstance(config, mongoClient, actorSystem);
    }

}
