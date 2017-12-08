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
package org.eclipse.ditto.services.thingsearch.starter;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.thingsearch.starter.actors.SearchRootActor;
import org.eclipse.ditto.services.thingsearch.updater.actors.SearchUpdaterRootActor;
import org.eclipse.ditto.services.utils.cluster.ClusterMemberAwareActor;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor;
import org.eclipse.ditto.services.utils.devops.LogbackLoggingFacade;
import org.eclipse.ditto.services.utils.health.status.StatusSupplierActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.stream.ActorMaterializer;
import kamon.Kamon;
import scala.concurrent.duration.FiniteDuration;

/**
 * Entry point of the Search Service.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings</li>
 * <li>Sets up ActorSystem</li>
 * <li>Wires up Akka HTTP Routes</li>
 * </ul>
 */
public final class SearchService {

    /**
     * Name for the Akka Actor System of the Search Service.
     */
    private static final String CLUSTER_NAME = "ditto-cluster";

    /**
     * Name for the Search Service.
     */
    private static final String SERVICE_NAME = ConfigKeys.SERVICE_NAME;

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);

    /**
     * Starts the SearchService.
     *
     * @param args CommandLine arguments
     */
    public static void main(final String... args) {
        runtimeParameters();
        Kamon.start(ConfigFactory.load("kamon"));

        final Config config = ConfigUtil.determineConfig(SERVICE_NAME);
        final ActorSystem system = ActorSystem.create(CLUSTER_NAME, config);
        system.actorOf(StatusSupplierActor.props(SearchRootActor.ACTOR_NAME), StatusSupplierActor.ACTOR_NAME);
        system.actorOf(
                DevOpsCommandsActor.props(LogbackLoggingFacade.newInstance(), SERVICE_NAME, ConfigUtil.instanceIndex()),
                DevOpsCommandsActor.ACTOR_NAME);

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
                system.scheduler().scheduleOnce(FiniteDuration.apply(30, TimeUnit.SECONDS), () -> {
                    LOGGER.error("Member was not able to join the cluster, going to shutdown system now");
                    system.terminate();
                }, system.dispatcher());

        LOGGER.info("Waiting for member to be UP before proceeding with further initialization");
        Cluster.get(system).registerOnMemberUp(() -> {
            LOGGER.info("Member successfully joined the cluster, instantiating remaining Actors (Rabbit, HTTP)");

            shutdownIfJoinFails.cancel();

            final ActorRef pubSubMediator = DistributedPubSub.get(system).mediator();
            final ActorMaterializer materializer = ActorMaterializer.create(system);
            system.actorOf(SearchUpdaterRootActor.props(config, pubSubMediator), SearchUpdaterRootActor.ACTOR_NAME);
            system.actorOf(SearchRootActor.props(config, pubSubMediator, materializer), SearchRootActor.ACTOR_NAME);
        });
    }

    private static void runtimeParameters() {
        final RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        LOGGER.info("Running with following runtime parameters: {}", bean.getInputArguments());
        LOGGER.info("Available processors: {}", Runtime.getRuntime().availableProcessors());
    }

}
