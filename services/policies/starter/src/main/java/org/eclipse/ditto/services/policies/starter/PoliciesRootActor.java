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
package org.eclipse.ditto.services.policies.starter;

import static org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants.CLUSTER_ROLE;

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.base.actors.DittoRootActor;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.policies.common.config.PoliciesConfig;
import org.eclipse.ditto.services.policies.persistence.actors.PoliciesPersistenceStreamingActorCreator;
import org.eclipse.ditto.services.policies.persistence.actors.PolicyPersistenceOperationsActor;
import org.eclipse.ditto.services.policies.persistence.actors.PolicySupervisorActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.MetricsReporterConfig;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoMetricsReporter;
import org.eclipse.ditto.services.utils.persistence.mongo.config.TagsConfig;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Parent Actor which takes care of supervision of all other Actors in our system.
 */
public final class PoliciesRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "policiesRoot";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final RetrieveStatisticsDetailsResponseSupplier retrieveStatisticsDetailsResponseSupplier;

    @SuppressWarnings("unused")
    private PoliciesRootActor(final PoliciesConfig policiesConfig,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator) {

        final ActorSystem actorSystem = getContext().system();
        final ClusterShardingSettings shardingSettings =
                ClusterShardingSettings.create(actorSystem).withRole(CLUSTER_ROLE);

        final Props policySupervisorProps = PolicySupervisorActor.props(pubSubMediator, snapshotAdapter);

        final TagsConfig tagsConfig = policiesConfig.getTagsConfig();
        final ActorRef persistenceStreamingActor = startChildActor(PoliciesPersistenceStreamingActorCreator.ACTOR_NAME,
                PoliciesPersistenceStreamingActorCreator.props(tagsConfig.getStreamingCacheSize()));

        pubSubMediator.tell(DistPubSubAccess.put(getSelf()), getSelf());
        pubSubMediator.tell(DistPubSubAccess.put(persistenceStreamingActor), getSelf());

        final ClusterConfig clusterConfig = policiesConfig.getClusterConfig();
        final ActorRef policiesShardRegion = ClusterSharding.get(actorSystem)
                .start(PoliciesMessagingConstants.SHARD_REGION, policySupervisorProps, shardingSettings,
                        ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem));

        startChildActor(PolicyPersistenceOperationsActor.ACTOR_NAME,
                PolicyPersistenceOperationsActor.props(pubSubMediator, policiesConfig.getMongoDbConfig(),
                        actorSystem.settings().config(), policiesConfig.getPersistenceOperationsConfig()));

        retrieveStatisticsDetailsResponseSupplier = RetrieveStatisticsDetailsResponseSupplier.of(policiesShardRegion,
                PoliciesMessagingConstants.SHARD_REGION, log);

        final HealthCheckConfig healthCheckConfig = policiesConfig.getHealthCheckConfig();
        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());
        if (healthCheckConfig.getPersistenceConfig().isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        final MetricsReporterConfig metricsReporterConfig =
                healthCheckConfig.getPersistenceConfig().getMetricsReporterConfig();
        final Props healthCheckingActorProps = DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions,
                MongoHealthChecker.props(),
                MongoMetricsReporter.props(
                        metricsReporterConfig.getResolution(),
                        metricsReporterConfig.getHistory(),
                        pubSubMediator
                )
        );
        final ActorRef healthCheckingActor =
                startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME, healthCheckingActorProps);

        bindHttpStatusRoute(policiesConfig.getHttpConfig(), healthCheckingActor);
    }

    /**
     * Creates Akka configuration object Props for this PoliciesRootActor.
     *
     * @param policiesConfig the configuration reader of this service.
     * @param snapshotAdapter serializer and deserializer of the Policies snapshot store.
     * @param pubSubMediator the PubSub mediator Actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final PoliciesConfig policiesConfig,
            final SnapshotAdapter<Policy> snapshotAdapter,
            final ActorRef pubSubMediator) {

        return Props.create(PoliciesRootActor.class, policiesConfig, snapshotAdapter, pubSubMediator);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, this::handleRetrieveStatisticsDetails)
                .build().orElse(super.createReceive());
    }

    private void handleRetrieveStatisticsDetails(final RetrieveStatisticsDetails command) {
        log.info("Sending the namespace stats of the policy shard as requested ...");
        Patterns.pipe(retrieveStatisticsDetailsResponseSupplier
                .apply(command.getDittoHeaders()), getContext().dispatcher()).to(getSender());
    }

}
