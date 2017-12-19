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
package org.eclipse.ditto.services.amqpbridge;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.amqpbridge.actors.AmqpBridgeRootActor;
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

import org.eclipse.ditto.services.amqpbridge.util.ConfigKeys;

/**
 * Entry point of the AMQP Bridge. <ul> <li>Reads configuration, enhances it with cloud environment settings</li>
 * <li>Sets up ActorSystem</li> </ul>
 */
public final class AmqpBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpBridge.class);

    /**
     * Name for the Cluster of the AMQP Bridge.
     */
    private static final String CLUSTER_NAME = "ditto-cluster";

    /**
     * Name for the Akka Actor System of the AMQP Bridge.
     */
    private static final String SERVICE_NAME = "amqp-bridge";

    private AmqpBridge() {
        // no-op
    }

    /**
     * Starts the AMQP 1.0 Bridge.
     *
     * @param args CommandLine arguments
     */
    public static void main(final String... args) {
        runtimeParameters();
        Kamon.start(ConfigFactory.load("kamon"));

        final Config config = ConfigUtil.determineConfig(SERVICE_NAME);
        final ActorSystem system = ActorSystem.create(CLUSTER_NAME, config);
        system.actorOf(StatusSupplierActor.props(AmqpBridgeRootActor.ACTOR_NAME), StatusSupplierActor.ACTOR_NAME);
        system.actorOf(
                DevOpsCommandsActor.props(LogbackLoggingFacade.newInstance(), SERVICE_NAME, ConfigUtil.instanceIndex()),
                DevOpsCommandsActor.ACTOR_NAME);

        ClusterUtil.joinCluster(system, config);
        // important: register Kamon::shutdown after joining the cluster as there is also a "registerOnTermination"
        // and they are executed in reverse order
        system.registerOnTermination(Kamon::shutdown);

        final boolean majorityCheckEnabled = config.getBoolean(ConfigKeys.Cluster.MAJORITY_CHECK_ENABLED);
        final Duration majorityCheckDelay = config.getDuration(ConfigKeys.Cluster.MAJORITY_CHECK_DELAY);
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
        Cluster.get(system).registerOnMemberUp(() -> {
            LOGGER.info("Member successfully joined the cluster, instantiating remaining Actors");

            shutdownIfJoinFails.cancel();

            final ActorRef pubSubMediator = DistributedPubSub.get(system).mediator();
            final ActorMaterializer materializer = ActorMaterializer.create(system);

            system.actorOf(AmqpBridgeRootActor.props(config, pubSubMediator, materializer),
                    AmqpBridgeRootActor.ACTOR_NAME);
        });
    }

    private static void runtimeParameters() {
        final RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        LOGGER.info("Running with following runtime parameters: {}", bean.getInputArguments());
        LOGGER.info("Available processors: {}", Runtime.getRuntime().availableProcessors());
    }
}
