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
package org.eclipse.ditto.concierge.service.common;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

@Immutable
final class DefaultCreditDecisionConfig implements CreditDecisionConfig {

    private static final String CONFIG_PATH = "credit-decision";

    private final Duration interval;
    private final Duration metricReportTimeout;
    private final Duration timerThreshold;
    private final int creditPerBatch;
    private final int creditForRequests;
    private final int maxPendingRequests;

    private DefaultCreditDecisionConfig(final Config conf) {
        this.interval = conf.getDuration(ConfigValue.INTERVAL.getConfigPath());
        this.metricReportTimeout = conf.getDuration(ConfigValue.METRIC_REPORT_TIMEOUT.getConfigPath());
        this.timerThreshold = conf.getDuration(ConfigValue.TIMER_THRESHOLD.getConfigPath());
        this.creditPerBatch = conf.getInt(ConfigValue.CREDIT_PER_BATCH.getConfigPath());
        creditForRequests = conf.getInt(ConfigValue.CREDIT_FOR_REQUESTS.getConfigPath());
        maxPendingRequests = conf.getInt(ConfigValue.MAX_PENDING_REQUESTS.getConfigPath());
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
    public int getCreditForRequests() {
        return creditForRequests;
    }

    @Override
    public int getMaxPendingRequests() {
        return maxPendingRequests;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof DefaultCreditDecisionConfig) {
            final DefaultCreditDecisionConfig that = (DefaultCreditDecisionConfig) o;
            return Objects.equals(interval, that.interval) &&
                    Objects.equals(metricReportTimeout, that.metricReportTimeout) &&
                    Objects.equals(timerThreshold, that.timerThreshold) &&
                    creditPerBatch == that.creditPerBatch &&
                    creditForRequests == that.creditForRequests &&
                    maxPendingRequests == that.maxPendingRequests;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(interval, metricReportTimeout, timerThreshold, creditPerBatch, creditForRequests,
                maxPendingRequests);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[ interval=" + interval +
                ", metricReportTimeout" + metricReportTimeout +
                ", timerThreshold" + timerThreshold +
                ", creditPerBatch" + creditPerBatch +
                ", creditForRequests" + creditForRequests +
                ", maxPendingRequests" + maxPendingRequests +
                "]";
    }
}
