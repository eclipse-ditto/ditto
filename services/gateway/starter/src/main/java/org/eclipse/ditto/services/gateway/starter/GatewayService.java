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
package org.eclipse.ditto.services.gateway.starter;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.utils.cluster.ClusterMemberAwareActor;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.health.status.StatusSupplierActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.github.jjagged.metrics.reporting.StatsDReporter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension$;
import akka.stream.ActorMaterializer;
import kamon.Kamon;
import scala.concurrent.duration.FiniteDuration;

/**
 * A gateway server for Eclipse Ditto.
 */
public class GatewayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayService.class);

    private static final String CLUSTER_NAME = "ditto-cluster";
    static final String SERVICE_NAME = "gateway";

    /**
     * Starts the gateway server.
     *
     * @param args CommandLine arguments
     */
    public static void main(final String... args) {
        runtimeParameters();
        Kamon.start(ConfigFactory.load("kamon"));

        final Config config = ConfigUtil.determineConfig(SERVICE_NAME);
        final ActorSystem system = ActorSystem.create(CLUSTER_NAME, config);
        system.actorOf(StatusSupplierActor.props(GatewayRootActor.ACTOR_NAME), StatusSupplierActor.ACTOR_NAME);

        ClusterUtil.joinCluster(system, config);
        // important: register Kamon::shutdown after joining the cluster as there is also a "registerOnTermination"
        // and they are executed in reverse order
        system.registerOnTermination(Kamon::shutdown);

        final boolean majorityCheckEnabled = config.getBoolean(ConfigKeys.CLUSTER_MAJORITY_CHECK_ENABLED);
        final Duration majorityCheckDelay = config.getDuration(ConfigKeys.CLUSTER_MAJORITY_CHECK_DELAY);
        LOGGER.info("Starting actor '{}'", ClusterMemberAwareActor.ACTOR_NAME);
        system.actorOf(ClusterMemberAwareActor.props(SERVICE_NAME, majorityCheckEnabled, majorityCheckDelay),
                ClusterMemberAwareActor.ACTOR_NAME);

        final Cancellable shutdownIfJoinFails =
                system.scheduler().scheduleOnce(FiniteDuration.apply(30, TimeUnit.SECONDS), () ->
                {
                    LOGGER.error("Member was not able to join the cluster, going to shutdown system now");
                    system.terminate();
                }, system.dispatcher());

        LOGGER.info("Waiting for member to be UP before proceeding with further initialization");
        Cluster.get(system).registerOnMemberUp(() ->
        {
            LOGGER.info("Member successfully joined the cluster, instantiating remaining Actors (Rabbit, HTTP)");

            shutdownIfJoinFails.cancel();

            if (config.hasPath(ConfigKeys.STATSD_HOSTNAME)) {
                // enable logging of mongo-persistence-plugin statistics to statsD
                final MetricRegistry registry =
                        ((MongoPersistenceExtension) MongoPersistenceExtension$.MODULE$.apply(system))
                                .configured(config)
                                .registry();

                final String hostnameOverride = ConfigUtil.calculateInstanceUniqueSuffix();

                final StatsDReporter metricsReporter = StatsDReporter.forRegistry(registry)
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(TimeUnit.MILLISECONDS)
                        .prefixedWith(SERVICE_NAME + "." + hostnameOverride)
                        .build(config.getString(ConfigKeys.STATSD_HOSTNAME), config.getInt(ConfigKeys.STATSD_PORT));
                metricsReporter.start(5, TimeUnit.SECONDS);
            } else {
                LOGGER.warn("MongoDB monitoring will be deactivated as '{}' is not configured",
                        ConfigKeys.STATSD_HOSTNAME);
            }

            final ActorRef pubSubMediator = DistributedPubSub.get(system).mediator();
            final ActorMaterializer materializer = ActorMaterializer.create(system);

            system.actorOf(GatewayRootActor.props(config, pubSubMediator, materializer), GatewayRootActor.ACTOR_NAME);
        });
    }

    private static void runtimeParameters() {
        final RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        LOGGER.info("Running with following runtime parameters: {}", bean.getInputArguments());
        LOGGER.info("Available processors: {}", Runtime.getRuntime().availableProcessors());
    }

    private GatewayService() {
        // no-op
    }

}
