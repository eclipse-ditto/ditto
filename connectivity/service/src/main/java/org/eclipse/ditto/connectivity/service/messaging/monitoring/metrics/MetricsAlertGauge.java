/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.KamonGauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kamon based implementation of {@link org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge}, which can
 * be used for triggering Connection Metric alerts.
 */
@Immutable
public final class MetricsAlertGauge implements Gauge {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsAlertGauge.class);

    private final Gauge delegee;
    private final MetricsAlert metricsAlert;
    private final MeasurementWindow measurementWindow;

    private MetricsAlertGauge(final Gauge delegee,
            final MetricsAlert metricsAlert,
            final MeasurementWindow measurementWindow) {

        this.delegee = delegee;
        this.metricsAlert = metricsAlert;
        this.measurementWindow = measurementWindow;
    }

    /**
     * Creates a new instance of {@code MetricsAlertGauge}.
     * @param name the name of the gauge.
     * @param metricsAlert the metrics alert that should be evaluated and conditionally triggered.
     * @param measurementWindow the measurement window to which the alert applies.
     * @return the new instance.
     */
    public static Gauge newGauge(final String name, final MetricsAlert metricsAlert,
            final MeasurementWindow measurementWindow) {

        checkNotNull(name, "name");
        return new MetricsAlertGauge(KamonGauge.newGauge(checkNotNull(name, "name")),
                checkNotNull(metricsAlert, "metricsAlert"),
                checkNotNull(measurementWindow, "measurementWindow"));
    }

    @Override
    public Gauge increment() {
        delegee.increment();
        final long value = get();
        if (metricsAlert.evaluateCondition(measurementWindow, 99999, value)) {
            LOGGER.debug("Triggering metric alert: <{}>", metricsAlert);
            metricsAlert.triggerAction(Instant.now().toEpochMilli(), value);
        }
        return this;
    }

    @Override
    public Gauge decrement() {
        delegee.decrement();
        return this;
    }

    @Override
    public void set(final Long value) {
        delegee.set(value);
    }

    @Override
    public void set(final Double value) {
        delegee.set(value);
    }

    @Override
    public Long get() {
       return delegee.get();
    }

    @Override
    public MetricsAlertGauge tag(final Tag tag) {
        return new MetricsAlertGauge(delegee.tag(tag), metricsAlert, measurementWindow);
    }

    @Override
    public MetricsAlertGauge tags(final TagSet tags) {
        final var taggedDelegee = delegee.tags(tags);
        return new MetricsAlertGauge(taggedDelegee, metricsAlert, measurementWindow);
    }

    @Override
    public TagSet getTagSet() {
        return delegee.getTagSet();
    }

    /**
     * Sets the value of the gauge to 0.
     *
     * @return True if value could be set successfully.
     */
    @Override
    public boolean reset() {
        delegee.reset();
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "delegee=" + delegee +
                ", metricsAlert=" + metricsAlert +
                ", measurementWindow=" + measurementWindow +
                "]";
    }

}
