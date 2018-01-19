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
package org.eclipse.ditto.services.policies.starter;

import org.eclipse.ditto.services.base.BaseConfigKey;
import org.eclipse.ditto.services.base.BaseConfigKeys;
import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.StatsdMongoDbMetricsStarter;
import org.eclipse.ditto.services.policies.util.ConfigKeys;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Entry point of the Policies Service.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings</li>
 * <li>Sets up ActorSystem</li>
 * </ul>
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class PoliciesService extends DittoService {

    /**
     * Name for the Akka Actor System of the Policies Service.
     */
    static final String SERVICE_NAME = "policies";

    private static final Logger LOGGER = LoggerFactory.getLogger(PoliciesService.class);

    private static final BaseConfigKeys CONFIG_KEYS = BaseConfigKeys.getBuilder()
            .put(BaseConfigKey.Cluster.MAJORITY_CHECK_ENABLED, ConfigKeys.Cluster.MAJORITY_CHECK_ENABLED)
            .put(BaseConfigKey.Cluster.MAJORITY_CHECK_DELAY, ConfigKeys.Cluster.MAJORITY_CHECK_DELAY)
            .put(BaseConfigKey.StatsD.HOSTNAME, ConfigKeys.StatsD.HOSTNAME)
            .put(BaseConfigKey.StatsD.PORT, ConfigKeys.StatsD.PORT)
            .build();

    private PoliciesService() {
        super(LOGGER, SERVICE_NAME, PoliciesRootActor.ACTOR_NAME, CONFIG_KEYS);
    }

    /**
     * Starts the Policies service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final PoliciesService policiesService = new PoliciesService();
        policiesService.start();
    }

    @Override
    protected void startStatsdMetricsReporter(final ActorSystem actorSystem, final Config config) {
        StatsdMongoDbMetricsStarter.newInstance(config, CONFIG_KEYS, actorSystem, SERVICE_NAME, LOGGER).run();
    }

    @Override
    protected Props getMainRootActorProps(final Config config, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return PoliciesRootActor.props(config, pubSubMediator, materializer);
    }

}
