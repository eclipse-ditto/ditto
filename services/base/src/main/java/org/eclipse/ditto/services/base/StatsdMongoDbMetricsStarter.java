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
package org.eclipse.ditto.services.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.slf4j.Logger;

import com.codahale.metrics.MetricRegistry;
import com.github.jjagged.metrics.reporting.StatsDReporter;
import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension;
import akka.contrib.persistence.mongodb.MongoPersistenceExtension$;

/**
 * This Runnable starts a StatsD MongoDB metrics reporter if the appropriate configuration settings for StatsD are
 * provided.
 */
@NotThreadSafe
public final class StatsdMongoDbMetricsStarter implements Runnable {

    private final Runnable implementation;

    private StatsdMongoDbMetricsStarter(final Runnable theImplementation) {
        implementation = theImplementation;
    }

    /**
     * Returns a new instance of {@code StatsdMetricsStarter}.
     *
     * @param config the configuration settings of the service which sends StatsD metrics.
     * @param configKeys the configuration keys for accessing StatsD related settings.
     * @param actorSystem the Akka actor system to be used for creating the metric registry.
     * @param serviceName the name of the service which sends StatsD metrics.
     * @param logger the logger to be used.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws java.lang.IllegalStateException if {@code configKeys} did neither contain
     * {@link BaseConfigKey.StatsD#HOSTNAME} nor {@link BaseConfigKey.StatsD#PORT}.
     * @throws IllegalArgumentException if {@code serviceName} is empty.
     */
    public static StatsdMongoDbMetricsStarter newInstance(final Config config,
            final BaseConfigKeys configKeys,
            final ActorSystem actorSystem,
            final String serviceName,
            final Logger logger) {

        checkNotNull(config, "config");
        checkNotNull(configKeys, "config keys");
        configKeys.checkExistence(BaseConfigKey.StatsD.HOSTNAME, BaseConfigKey.StatsD.PORT);
        checkNotNull(actorSystem, "Akka actor system");
        argumentNotEmpty(serviceName, "service name");
        checkNotNull(logger, "logger");

        final Runnable implementation;
        final String statsdHostnameConfigKey = configKeys.getOrThrow(BaseConfigKey.StatsD.HOSTNAME);
        if (config.hasPath(statsdHostnameConfigKey)) {
            final MetricRegistry metricRegistry = createMetricRegistry(actorSystem, config);
            final String hostname = config.getString(statsdHostnameConfigKey);
            final int port = config.getInt(configKeys.getOrThrow(BaseConfigKey.StatsD.PORT));
            implementation = new StartMetricsReporterImplementation(metricRegistry, serviceName, hostname, port);
        } else {
            implementation = new LogOnlyImplementation(logger, statsdHostnameConfigKey);
        }

        return new StatsdMongoDbMetricsStarter(implementation);
    }

    /**
     * Enables logging of mongo-persistence-plugin statistics to StatsD.
     */
    @SuppressWarnings("RedundantCast")
    private static MetricRegistry createMetricRegistry(final ActorSystem actorSystem, final Config config) {
        // Would not compile without cast!
        // The cast is not redundant for Maven.
        return ((MongoPersistenceExtension) MongoPersistenceExtension$.MODULE$.apply(actorSystem))
                .configured(config)
                .registry();
    }

    @Override
    public void run() {
        implementation.run();
    }

    @NotThreadSafe
    private static final class StartMetricsReporterImplementation implements Runnable {

        private static final short POLL_PERIOD = 5; // seconds between polls

        private final MetricRegistry metricRegistry;
        private final String serviceName;
        private final String statsdHostname;
        private final int statsdPort;

        private StartMetricsReporterImplementation(final MetricRegistry metricRegistry,
                final String serviceName,
                final String statsdHostname,
                final int statsdPort) {

            this.metricRegistry = metricRegistry;
            this.serviceName = serviceName;
            this.statsdHostname = statsdHostname;
            this.statsdPort = statsdPort;
        }

        @Override
        public void run() {
            final StatsDReporter metricsReporter = createMetricsReporter(ConfigUtil.calculateInstanceUniqueSuffix());
            metricsReporter.start(POLL_PERIOD, TimeUnit.SECONDS);
        }

        private StatsDReporter createMetricsReporter(final String hostnameOverride) {
            return StatsDReporter.forRegistry(metricRegistry)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .prefixedWith(serviceName + "." + hostnameOverride)
                    .build(statsdHostname, statsdPort);
        }

    }

    @Immutable
    private static final class LogOnlyImplementation implements Runnable {

        private final Logger logger;
        private final String statsdHostnameConfigKey;

        private LogOnlyImplementation(final Logger theLogger, final String theStatsdHostnameConfigKey) {
            logger = theLogger;
            statsdHostnameConfigKey = theStatsdHostnameConfigKey;
        }

        @Override
        public void run() {
            logger.warn("MongoDB monitoring will be deactivated as <{}> is not configured.", statsdHostnameConfigKey);
        }

    }

}
