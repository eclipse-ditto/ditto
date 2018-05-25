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

import java.util.Map;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.metrics.MongoDbMetricRegistryFactory;
import org.eclipse.ditto.services.base.metrics.StatsdMetricsReporter;
import org.eclipse.ditto.services.base.metrics.StatsdMetricsStarter;
import org.eclipse.ditto.services.base.config.DittoServiceConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.slf4j.Logger;

import com.codahale.metrics.MetricRegistry;

import akka.actor.ActorSystem;

/**
 * Abstract base implementation for starting Policies service with configurable actors.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings</li>
 * <li>Sets up ActorSystem</li>
 * </ul>
 */
public abstract class AbstractPoliciesService extends DittoService<ServiceConfigReader> {

    /**
     * Name for the Akka Actor System of the Policies Service.
     */
    private static final String SERVICE_NAME = "policies";

    private final Logger logger;

    /**
     * Constructs a new {@code AbstractPoliciesService}.
     *
     * @param logger the logger to use.
     */
    protected AbstractPoliciesService(final Logger logger) {
        super(logger, SERVICE_NAME, PoliciesRootActor.ACTOR_NAME, DittoServiceConfigReader.from(SERVICE_NAME));
        this.logger = logger;
    }

    @Override
    protected void startStatsdMetricsReporter(final ActorSystem actorSystem, final ServiceConfigReader configReader) {
        final Map.Entry<String, MetricRegistry> mongoDbMetrics =
                MongoDbMetricRegistryFactory.createOrGet(actorSystem, configReader.getRawConfig());
        StatsdMetricsReporter.getInstance().add(mongoDbMetrics);

        StatsdMetricsStarter.newInstance(configReader, actorSystem, SERVICE_NAME).run();
    }

}
