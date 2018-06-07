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

import java.util.Map;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.metrics.MongoDbMetricRegistryFactory;
import org.eclipse.ditto.services.base.metrics.StatsdMetricsReporter;
import org.eclipse.ditto.services.base.metrics.StatsdMetricsStarter;
import org.eclipse.ditto.services.base.config.DittoServiceConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * The Gateway service for Eclipse Ditto.
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class GatewayService extends DittoService {

    /**
     * Name for the Akka actor system of the Gateway service.
     */
    static final String SERVICE_NAME = "gateway";

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayService.class);

    private GatewayService() {
        super(LOGGER, SERVICE_NAME, GatewayRootActor.ACTOR_NAME, DittoServiceConfigReader.from(SERVICE_NAME));
    }

    /**
     * Starts the Gateway service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final GatewayService gatewayService = new GatewayService();
        gatewayService.start();
    }

    @Override
    protected void startDevOpsCommandsActor(final ActorSystem actorSystem, final Config config) {
        // The DevOpsCommandsActor is started by GatewayRootActor as it uses the ActorRef.
    }

    @Override
    protected void startStatsdMetricsReporter(final ActorSystem actorSystem, final ServiceConfigReader configReader) {
        final Map.Entry<String, MetricRegistry> mongoDbMetrics =
                MongoDbMetricRegistryFactory.createOrGet(actorSystem, configReader.getRawConfig());
        StatsdMetricsReporter.getInstance().add(mongoDbMetrics);

        StatsdMetricsStarter.newInstance(configReader, actorSystem, SERVICE_NAME).run();
    }

    @Override
    protected Props getMainRootActorProps(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return GatewayRootActor.props(configReader, pubSubMediator, materializer);
    }

}
