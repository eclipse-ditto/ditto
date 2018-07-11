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
package org.eclipse.ditto.services.utils.metrics.dropwizard;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.io.IOException;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * Helps reporting dropwizard metrics to prometheus.
 */
public class DropwizardMetricsPrometheusReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DropwizardMetricsPrometheusReporter.class);
    private static final CollectorRegistry registry = CollectorRegistry.defaultRegistry;

    private DropwizardMetricsPrometheusReporter() {}

    /**
     * Adds a named {@link NamedMetricRegistry} to the list of reported metric registries.
     *
     * @param metricRegistry the named registry of the metrics.
     * @throws NullPointerException if metricRegistry is {@code null}.
     */
    public static void addMetricRegistry(final NamedMetricRegistry metricRegistry) {
        checkNotNull(metricRegistry, "metrics registry");
        registry.register(new DropwizardExports(metricRegistry.getRegistry()));

        LOGGER.info("Started to export dropwizard metrics <{}>.", metricRegistry.getName());
    }

    /**
     * Gets the recorded data in Prometheus format.
     *
     * @return The recorded data in Prometheus format.
     */
    public static String getData() {
        try (final StringWriter stringWriter = new StringWriter()) {
            TextFormat.write004(stringWriter, registry.metricFamilySamples());
            return stringWriter.toString();
        } catch (IOException e) {
            LOGGER.error("Could export metrics from dropwizard to prometheus", e);
            return "";
        }
    }
}
