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

import java.util.function.Function;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.config.DittoServiceConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.metrics.dropwizard.DropwizardMetricsPrometheusReporter;
import org.eclipse.ditto.services.utils.metrics.dropwizard.MetricRegistryFactory;
import org.eclipse.ditto.services.utils.persistence.mongo.suffixes.NamespaceSuffixCollectionNames;
import org.eclipse.ditto.services.utils.persistence.mongo.suffixes.SuffixBuilderConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.suffixes.SuffixBuilderConfigReader;
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
        configureMongoCollectionNameSuffixAppender();
    }

    @Override
    protected void addDropwizardMetricRegistries(final ActorSystem actorSystem, final ServiceConfigReader configReader) {
        DropwizardMetricsPrometheusReporter.addMetricRegistry(
                MetricRegistryFactory.mongoDb(actorSystem, configReader.getRawConfig()));
    }

    private void configureMongoCollectionNameSuffixAppender() {
        final Config config = ConfigUtil.determineConfig(SERVICE_NAME);
        final SuffixBuilderConfigReader suffixBuilderConfigReader = SuffixBuilderConfigReader.fromRawConfig(config);
        suffixBuilderConfigReader.getSuffixBuilderConfig().ifPresent(NamespaceSuffixCollectionNames::setConfig);
    }
}
