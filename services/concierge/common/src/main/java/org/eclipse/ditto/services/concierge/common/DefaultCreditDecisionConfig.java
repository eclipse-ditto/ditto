/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.common;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

@Immutable
final class DefaultCreditDecisionConfig implements CreditDecisionConfig {

    private static final String CONFIG_PATH = "credit-decision";

    private final Duration interval;
    private final Duration metricReportTimeout;
    private final Duration timerThreshold;
    private final int creditPerBatch;

    private DefaultCreditDecisionConfig(final Config conf) {
        this.interval = conf.getDuration(ConfigValue.INTERVAL.getConfigPath());
        this.metricReportTimeout = conf.getDuration(ConfigValue.METRIC_REPORT_TIMEOUT.getConfigPath());
        this.timerThreshold = conf.getDuration(ConfigValue.TIMER_THRESHOLD.getConfigPath());
        this.creditPerBatch = conf.getInt(ConfigValue.CREDIT_PER_BATCH.getConfigPath());
    }

    static CreditDecisionConfig of(final Config config) {
        return new DefaultCreditDecisionConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public Duration getMetricReportTimeout() {
        return metricReportTimeout;
    }

    @Override
    public Duration getTimerThreshold() {
        return timerThreshold;
    }

    @Override
    public int getCreditPerBatch() {
        return creditPerBatch;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof DefaultCreditDecisionConfig) {
            final DefaultCreditDecisionConfig that = (DefaultCreditDecisionConfig) o;
            return Objects.equals(interval, that.interval) &&
                    Objects.equals(metricReportTimeout, that.metricReportTimeout) &&
                    Objects.equals(timerThreshold, that.timerThreshold) &&
                    creditPerBatch == that.creditPerBatch;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(interval, metricReportTimeout, timerThreshold, creditPerBatch);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[ interval=" + interval +
                ", metricReportTimeout" + metricReportTimeout +
                ", timerThreshold" + timerThreshold +
                ", creditPerBatch" + creditPerBatch +
                "]";
    }
}
