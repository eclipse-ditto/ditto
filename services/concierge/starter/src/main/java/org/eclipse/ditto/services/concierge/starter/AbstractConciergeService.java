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
package org.eclipse.ditto.services.concierge.starter;

import java.util.function.Function;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.concierge.starter.actors.ConciergeRootActor;
import org.eclipse.ditto.services.concierge.util.config.AbstractConciergeConfigReader;
import org.eclipse.ditto.services.utils.metrics.dropwizard.DropwizardMetricsPrometheusReporter;
import org.eclipse.ditto.services.utils.metrics.dropwizard.MetricRegistryFactory;
import org.eclipse.ditto.services.utils.metrics.dropwizard.NamedMetricRegistry;
import org.slf4j.Logger;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Abstract base implementation for starting a concierge service with configurable actors.
 */
public abstract class AbstractConciergeService<C extends AbstractConciergeConfigReader> extends DittoService<C> {

    /**
     * Name for the Akka actor system.
     */
    protected static final String SERVICE_NAME = "concierge";

    protected AbstractConciergeService(final Logger logger, final Function<Config, C> configReaderCreator) {
        super(logger, SERVICE_NAME, ConciergeRootActor.ACTOR_NAME, configReaderCreator);
    }

    @Override
    protected void addDropwizardMetricRegistries(final ActorSystem actorSystem, final C configReader) {
        final NamedMetricRegistry mongo = MetricRegistryFactory.mongoDb(actorSystem, configReader.getRawConfig());
        DropwizardMetricsPrometheusReporter.addMetricRegistry(mongo);
    }

}
