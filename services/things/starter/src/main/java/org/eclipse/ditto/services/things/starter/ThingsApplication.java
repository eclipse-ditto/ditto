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
package org.eclipse.ditto.services.things.starter;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.eclipse.ditto.services.things.persistence.actors.ThingsActorsCreator;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;
import org.eclipse.ditto.services.utils.cluster.ClusterMemberAwareActor;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor;
import org.eclipse.ditto.services.utils.devops.LogbackLoggingFacade;
import org.eclipse.ditto.services.utils.health.status.StatusSupplierActor;
import org.slf4j.Logger;

import com.codahale.metrics.MetricRegistry;
import com.github.jjagged.metrics.reporting.StatsDReporter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.cluster.Cluster;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension$;
import kamon.Kamon;
import scala.concurrent.duration.FiniteDuration;

/**
 * Class for starting Things service with configurable actors.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings</li>
 * <li>Sets up ActorSystem</li>
 * <li>Wires up Akka HTTP Routes</li>
 * </ul>
 */
public final class ThingsApplication {

    private final Logger logger;
    private final String clusterName;
    private final String serviceName;
    private final BiFunction<Config, ActorSystem, ThingsActorsCreator> actorsCreatorCreator;

    /**
     * Creates a Things application.
     *
     * @param logger The logger.
     * @param clusterName Name of the Akka cluster.
     * @param serviceName Name of the Things service.
     * @param actorsCreatorCreator Creator of actors in Things service.
     */
    public ThingsApplication(final Logger logger, final String clusterName, final String serviceName,
            final BiFunction<Config, ActorSystem, ThingsActorsCreator> actorsCreatorCreator) {
        this.logger = logger;
        this.clusterName = clusterName;
        this.serviceName = serviceName;
        this.actorsCreatorCreator = actorsCreatorCreator;
    }

    /**
     * Starts the Things application.
     *
     * @param thingsConfig configuration for Things application.
     */
    public void start(final Config thingsConfig) {
        start(thingsConfig, ConfigFactory.load("kamon"));
    }

    /**
     * Starts the Things application.
     *
     * @param thingsConfig configuration for Things application.
     * @param kamonConfig configuration for Kamon.
     */
    public void start(final Config thingsConfig, final Config kamonConfig) {
        runtimeParameters();
        Kamon.start(kamonConfig);

        final ActorSystem system = ActorSystem.create(clusterName, thingsConfig);
        system.actorOf(StatusSupplierActor.props(ThingsRootActor.ACTOR_NAME), StatusSupplierActor.ACTOR_NAME);
        system.actorOf(
                DevOpsCommandsActor.props(LogbackLoggingFacade.newInstance(), ThingsService.SERVICE_NAME, ConfigUtil.instanceIndex()),
                DevOpsCommandsActor.ACTOR_NAME);

        ClusterUtil.joinCluster(system, thingsConfig);
        // important: register Kamon::shutdown after joining the cluster as there is also a "registerOnTermination"
        // and they are executed in reverse order
        system.registerOnTermination(Kamon::shutdown);

        final boolean majorityCheckEnabled = thingsConfig.getBoolean(ConfigKeys.Cluster.MAJORITY_CHECK_ENABLED);
        final Duration majorityCheckDelay = thingsConfig.getDuration(ConfigKeys.Cluster.MAJORITY_CHECK_DELAY);
        logger.info("Starting actor '{}'", ClusterMemberAwareActor.ACTOR_NAME);
        system.actorOf(ClusterMemberAwareActor.props(serviceName, majorityCheckEnabled, majorityCheckDelay),
                ClusterMemberAwareActor.ACTOR_NAME);

        final Cancellable shutdownIfJoinFails =
                system.scheduler().scheduleOnce(FiniteDuration.apply(30, TimeUnit.SECONDS), () ->
                {
                    logger.error("Member was not able to join the cluster, going to shutdown system now");
                    system.terminate();
                }, system.dispatcher());

        logger.info("Waiting for member to be UP before proceeding with further initialization");
        Cluster.get(system).registerOnMemberUp(() ->
        {
            logger.info("Member successfully joined the cluster, instantiating remaining Actors (Rabbit, HTTP)");

            shutdownIfJoinFails.cancel();

            if (thingsConfig.hasPath(ConfigKeys.StatsD.HOSTNAME)) {
                // enable logging of mongo-persistence-plugin statistics to statsD
                // CAUTION: the cast is not redundant for maven.
                final MetricRegistry registry =
                        ((MongoPersistenceExtension) MongoPersistenceExtension$.MODULE$.apply(system))
                                .configured(thingsConfig)
                                .registry();

                final StatsDReporter metricsReporter = StatsDReporter.forRegistry(registry)
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(TimeUnit.MILLISECONDS)
                        .prefixedWith(serviceName + "." + ConfigUtil.calculateInstanceUniqueSuffix())
                        .build(thingsConfig.getString(ConfigKeys.StatsD.HOSTNAME), thingsConfig.getInt(ConfigKeys.StatsD.PORT));
                metricsReporter.start(5, TimeUnit.SECONDS);
            } else {
                logger.warn("MongoDB monitoring will be deactivated as '{}' is not configured",
                        ConfigKeys.StatsD.HOSTNAME);
            }

            final ThingsActorsCreator actorsCreator = actorsCreatorCreator.apply(thingsConfig, system);
            system.actorOf(actorsCreator.createRootActor(), ThingsRootActor.ACTOR_NAME);
        });
    }

    private void runtimeParameters() {
        final RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        logger.info("Running with following runtime parameters: {}", bean.getInputArguments());
        logger.info("Available processors: {}", Runtime.getRuntime().availableProcessors());
    }

}
