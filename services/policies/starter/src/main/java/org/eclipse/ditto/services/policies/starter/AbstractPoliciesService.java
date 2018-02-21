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
import org.slf4j.Logger;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Abstract base implementation for starting Policies service with configurable actors.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings</li>
 * <li>Sets up ActorSystem</li>
 * </ul>
 */
public abstract class AbstractPoliciesService extends DittoService {

    /**
     * Name for the Akka Actor System of the Policies Service.
     */
    private static final String SERVICE_NAME = "policies";

    private static final BaseConfigKeys CONFIG_KEYS = BaseConfigKeys.getBuilder()
            .put(BaseConfigKey.Cluster.MAJORITY_CHECK_ENABLED, ConfigKeys.Cluster.MAJORITY_CHECK_ENABLED)
            .put(BaseConfigKey.Cluster.MAJORITY_CHECK_DELAY, ConfigKeys.Cluster.MAJORITY_CHECK_DELAY)
            .put(BaseConfigKey.StatsD.HOSTNAME, ConfigKeys.StatsD.HOSTNAME)
            .put(BaseConfigKey.StatsD.PORT, ConfigKeys.StatsD.PORT)
            .build();

    private final Logger logger;

    /**
     * Constructs a new {@code AbstractPoliciesService}.
     *
     * @param logger the logger to use.
     */
    protected AbstractPoliciesService(final Logger logger) {
        super(logger, SERVICE_NAME, PoliciesRootActor.ACTOR_NAME, CONFIG_KEYS);
        this.logger = logger;
    }

    @Override
    protected void startStatsdMetricsReporter(final ActorSystem actorSystem, final Config config) {
        StatsdMongoDbMetricsStarter.newInstance(config, CONFIG_KEYS, actorSystem, SERVICE_NAME, logger).run();
    }

}
