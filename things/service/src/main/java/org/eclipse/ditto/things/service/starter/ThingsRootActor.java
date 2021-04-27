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

import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.things.service.common.config.ThingsConfig;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceActorPropsFactory;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceOperationsActor;
import org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor;
import org.eclipse.ditto.things.service.persistence.actors.ThingsPersistenceStreamingActorCreator;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.MetricsReporterConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoMetricsReporter;
import org.eclipse.ditto.services.utils.persistence.mongo.config.TagsConfig;
import org.eclipse.ditto.services.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.services.utils.pubsub.ThingEventPubSubFactory;
import org.eclipse.ditto.model.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

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

        final ActorSystem actorSystem = getContext().system();

        final ClusterConfig clusterConfig = thingsConfig.getClusterConfig();
        final ShardRegionExtractor shardRegionExtractor =
                ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem);
        final DistributedAcks distributedAcks = DistributedAcks.lookup(actorSystem);
        final ThingEventPubSubFactory pubSubFactory =
                ThingEventPubSubFactory.of(getContext(), shardRegionExtractor, distributedAcks);
        final DistributedPub<ThingEvent<?>> distributedPub = pubSubFactory.startDistributedPub();

        final ActorRef thingsShardRegion = ClusterSharding.get(actorSystem)
                .start(ThingsMessagingConstants.SHARD_REGION,
                        getThingSupervisorActorProps(pubSubMediator, distributedPub, propsFactory),
                        ClusterShardingSettings.create(actorSystem).withRole(CLUSTER_ROLE),
                        shardRegionExtractor);

        startChildActor(ThingPersistenceOperationsActor.ACTOR_NAME,
                ThingPersistenceOperationsActor.props(pubSubMediator, thingsConfig.getMongoDbConfig(),
                        actorSystem.settings().config(), thingsConfig.getPersistenceOperationsConfig()));

        retrieveStatisticsDetailsResponseSupplier = RetrieveStatisticsDetailsResponseSupplier.of(thingsShardRegion,
                ThingsMessagingConstants.SHARD_REGION, log);

        final HealthCheckConfig healthCheckConfig = thingsConfig.getHealthCheckConfig();
        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());
        if (healthCheckConfig.getPersistenceConfig().isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        final MetricsReporterConfig metricsReporterConfig =
                healthCheckConfig.getPersistenceConfig().getMetricsReporterConfig();
        final ActorRef healthCheckingActor = startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions,
                        MongoHealthChecker.props(),
                        MongoMetricsReporter.props(
                                metricsReporterConfig.getResolution(),
                                metricsReporterConfig.getHistory(),
                                pubSubMediator
                        )
                ));

        final TagsConfig tagsConfig = thingsConfig.getTagsConfig();
        final ActorRef eventStreamingActor =
                ThingsPersistenceStreamingActorCreator.startEventStreamingActor(tagsConfig.getStreamingCacheSize(),
                        this::startChildActor);
        final ActorRef snapshotStreamingActor =
                ThingsPersistenceStreamingActorCreator.startSnapshotStreamingActor(this::startChildActor);

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());
        pubSubMediator.tell(DistPubSubAccess.put(eventStreamingActor), getSelf());
        pubSubMediator.tell(DistPubSubAccess.put(snapshotStreamingActor), getSelf());

        bindHttpStatusRoute(thingsConfig.getHttpConfig(), healthCheckingActor);
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

        // Beware: ThingPersistenceActorPropsFactory is not serializable.
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

    private static Props getThingSupervisorActorProps(
            final ActorRef pubSubMediator,
            final DistributedPub<ThingEvent<?>> distributedPub,
            final ThingPersistenceActorPropsFactory propsFactory) {

        return ThingSupervisorActor.props(pubSubMediator, distributedPub, propsFactory);
    }

}
