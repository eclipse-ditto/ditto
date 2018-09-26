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
package org.eclipse.ditto.services.utils.metrics.dropwizard;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import com.codahale.metrics.MetricRegistry;

/**
 * Wraps a "codehale Metrics framework" {@link MetricRegistry} and adds a {@code metricName}.
 */
public final class NamedMetricRegistry {

    private final String metricName;
    private final MetricRegistry metricRegistry;

    /**
     * Constructs a new Named Metric Registry. Is package private to restrict instantiation to only be possible in
     * {@link MetricRegistryFactory}.
     */
    NamedMetricRegistry(final String metricName, final MetricRegistry metricRegistry) {

        checkNotNull(metricRegistry, "metric registry");
        argumentNotEmpty(metricName, "metric name");

        this.metricName = metricName;
        this.metricRegistry = metricRegistry;
    }

    /**
     * Gets the name of this metric registry.
     *
     * @return The name of this metric registry.
     */
    public String getName() {
        return metricName;
    }

    /**
     * Gets the dropwizard metric registry.
     *
     * @return The dropwizard metric registry.
     */
    public MetricRegistry getRegistry() {
        return metricRegistry;
    }
}
