/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.policies.starter;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.config.DittoServiceConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.utils.metrics.dropwizard.DropwizardMetricsPrometheusReporter;
import org.eclipse.ditto.services.utils.metrics.dropwizard.MetricRegistryFactory;
import org.slf4j.Logger;

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

    /**
     * Constructs a new {@code AbstractPoliciesService}.
     *
     * @param logger the logger to use.
     */
    protected AbstractPoliciesService(final Logger logger) {
        super(logger, SERVICE_NAME, PoliciesRootActor.ACTOR_NAME, DittoServiceConfigReader.from(SERVICE_NAME));
    }

    @Override
    protected void addDropwizardMetricRegistries(final ActorSystem actorSystem,
            final ServiceConfigReader configReader) {
        DropwizardMetricsPrometheusReporter.addMetricRegistry(
                MetricRegistryFactory.mongoDb(actorSystem, configReader.getRawConfig()));
    }
}
