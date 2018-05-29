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
package org.eclipse.ditto.services.utils.metrics;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Responsible for starting new {@link KamonMetricsReporter} for given {@link NamedMetricRegistry}
 * </p>
 * <p>
 * The implementation is heavily synchronized, but this is not a problem because it is only used at startup and
 * operations are fast.
 * </p>
 */
public final class KamonMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonMetrics.class);

    private static final short POLL_PERIOD = 5; // seconds between polls

    private static final Map<String, NamedMetricRegistry> metricRegistries = new HashMap<>();

    private static boolean started = false;

    private static String serviceName;

    private KamonMetrics() {}

    /**
     * Adds named Starts a new instance of {@link KamonMetricsReporter} for the given metric registry.
     *
     * @param metricRegistry the named registry of the metrics.
     * @throws NullPointerException if metricRegistry is {@code null}.
     */
    public static synchronized void addMetricRegistry(final NamedMetricRegistry metricRegistry) {

        checkNotNull(metricRegistry, "metrics registry");

        final String metricName = metricRegistry.getMetricName();

        if (metricRegistries.containsKey(metricName)) {

            LOGGER.warn("Metrics already exist for registry <{}>, data may be overwritten", metricName);
        }

        metricRegistries.put(metricName, metricRegistry);

        if (started) {
            // Start new reporter immediately, because reporting is already running.
            startNewReporter(metricRegistry, serviceName);
        }
    }

    /**
     * Starts metrics reporting for the given service name
     *
     * @param serviceName The name of the service for which metrics should be reported
     */
    public static synchronized void start(final String serviceName) {

        checkNotNull(serviceName, "service name");
        KamonMetrics.serviceName = serviceName;
        metricRegistries.values().forEach(metricRegistry -> startNewReporter(metricRegistry, KamonMetrics.serviceName));
        started = true;
    }

    private static synchronized void startNewReporter(final NamedMetricRegistry metricRegistry,
            final String serviceName) {

        try (KamonMetricsReporter kamonMetricsReporter = new KamonMetricsReporter(metricRegistry.getMetricRegistry(),
                metricRegistry.getMetricName(), serviceName)) {

            kamonMetricsReporter.start(POLL_PERIOD, SECONDS);
        }
    }
}