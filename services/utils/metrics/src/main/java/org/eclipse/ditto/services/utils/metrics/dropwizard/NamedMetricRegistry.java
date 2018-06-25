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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import com.codahale.metrics.MetricRegistry;

/**
 * Wraps a "codehale Metrics framework" {@link MetricRegistry} and adds a {@code metricName}.
 */
public final class NamedMetricRegistry {

    private final String metricName;
    private final MetricRegistry metricRegistry;

    NamedMetricRegistry(final String metricName, final MetricRegistry metricRegistry) {

        checkNotNull(metricRegistry, "metric registry");
        argumentNotEmpty(metricName, "metric name");

        this.metricName = metricName;
        this.metricRegistry = metricRegistry;
    }

    public String getMetricName() {
        return metricName;
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }
}
