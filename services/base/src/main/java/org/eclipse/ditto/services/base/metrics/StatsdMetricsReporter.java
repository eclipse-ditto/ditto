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
package org.eclipse.ditto.services.base.metrics;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.github.jjagged.metrics.reporting.StatsDReporter;

/**
 * <p>
 * Starts StatsD metrics reporters for configurable {@link MetricRegistry}s. If {@link MetricRegistry}s are
 * configured before StatsD has been configured (i.e. activated), they will be queued until the configuration has
 * been done.
 * </p>
 * <p>
 * This is a singleton, because this approach allows simple configuration of metrics in different parts of an
 * application. The implementation is heavily synchronized, but this is not a problem because it is only used at
 * startup and operations are fast.
 * </p>
 */
public class StatsdMetricsReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsdMetricsReporter.class);

    private static final StatsdMetricsReporter INSTANCE = new StatsdMetricsReporter();
    private static final short POLL_PERIOD = 5; // seconds between polls
    private static final int ACTIVATION_WARN_DELAY_SECONDS = 120;

    private volatile boolean deactivated = false;
    @Nullable
    private InetSocketAddress socketAddress;
    private final Map<String, MetricRegistry> metricsActivationQueue = new HashMap<>();
    private String metricsPrefix = "undefined";

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    private StatsdMetricsReporter() {
        scheduler.schedule(this::logWarningIfQueueNotEmptyAndNotDeactivated, ACTIVATION_WARN_DELAY_SECONDS, SECONDS);
    }

    /**
     * Returns the singleton instance.
     * @return the singleton instance.
     */
    public static StatsdMetricsReporter getInstance() {
        return INSTANCE;
    }

    /**
     * Activates reporting to StatsD.
     *
     * @param socketAddress the socket address.
     * @param serviceName the name of this service, to be used in the metrics prefix.
     */
    public synchronized void activate(final InetSocketAddress socketAddress, final String serviceName) {
        requireNonNull(socketAddress);
        requireNonNull(serviceName);

        if (deactivated) {
            throw new IllegalStateException("Cannot activate once deactivated.");
        }

        this.socketAddress = socketAddress;
        this.metricsPrefix = requireNonNull(serviceName) + "." + ConfigUtil.calculateInstanceUniqueSuffix();

        LOGGER.info("Activating StatsD reporting with socketAddress=<{}> and metricsPrefix=<{}>.",
                socketAddress, metricsPrefix);
        activateQueue();
    }

    /**
     * Deactivates reporting to StatsD, i.e. metrics will not be reported.
     */
    public synchronized void deactivate() {
        if (socketAddress != null) {
            throw new IllegalStateException("Cannot deactivate once activated.");
        }

        LOGGER.warn("Deactivating StatsD reporting.");
        deactivated = true;
    }

    /**
     * Configures a {@link MetricRegistry} for reporting.
     * @param namedRegistry a {@link Map.Entry} with key {@code name} (i.e. metrics prefix) and value
     * {@link MetricRegistry}.
     */
    public synchronized void add(final Map.Entry<String, MetricRegistry> namedRegistry) {
        requireNonNull(namedRegistry);

        add(namedRegistry.getKey(), namedRegistry.getValue());
    }

    /**
     * Configures a {@link MetricRegistry} for reporting.
     * @param name the {@code name} (i.e. metrics prefix) of the {@link MetricRegistry}.
     * @param registry the {@link MetricRegistry}.
     */
    public synchronized void add(final String name, final MetricRegistry registry) {
        requireNonNull(name);
        requireNonNull(registry);

        if (metricsActivationQueue.containsKey(name)) {
            LOGGER.warn("Metrics already exist for registry <{}>, data may be overwritten", name);
        }

        if (deactivated) {
            LOGGER.info("StatsD metrics are deactivated, cannot report metrics from registry <{}>.", name);
            return;
        }

        if (socketAddress == null) {
            LOGGER.info("StatsD metrics have not yet been activated, metrics for registry <{}> will be reported on " +
                    "activation.", name);
            metricsActivationQueue.put(name, registry);
            return;
        }

        // otherwise: address is configured -> active
        startReporting(name, registry);
    }

    private synchronized void logWarningIfQueueNotEmptyAndNotDeactivated() {
        if (!deactivated && !metricsActivationQueue.isEmpty()) {
            LOGGER.warn("StatsD reporting has not been activated after <{}> seconds, cannot report the following " +
                    "StatsD metrics: {}", ACTIVATION_WARN_DELAY_SECONDS, metricsActivationQueue.keySet());
        }
        scheduler.shutdown();
    }

    private void activateQueue() {
        LOGGER.info("Activating all {} queued StatsD metrics.", metricsActivationQueue.size());
        metricsActivationQueue.forEach(this::startReporting);
    }


    private void startReporting(final String name, final MetricRegistry metricRegistry) {
        LOGGER.info("Start reporting StatsD metrics for registry <{}>.", name);
        if (socketAddress == null) {
            throw new IllegalStateException();
        }

        final String hostname = socketAddress.getHostName();
        final int port = socketAddress.getPort();
        LOGGER.info("Reporting metrics from registry <{}> to StatsD server: <{}:{}>.", name, hostname, port);

        final StatsDReporter statsDReporter = createMetricsReporter(hostname, port, metricRegistry);
        statsDReporter.start(POLL_PERIOD, SECONDS);
    }

    private StatsDReporter createMetricsReporter(final String statsDHostname, final int statsDPort,
            final MetricRegistry metricRegistry) {

        return StatsDReporter.forRegistry(metricRegistry)
                .convertRatesTo(SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .prefixedWith(metricsPrefix)
                .build(statsDHostname, statsDPort);
    }

}
