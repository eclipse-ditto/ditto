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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

@Immutable
final class DefaultCreditDecisionConfig implements CreditDecisionConfig {

    private static final String CONFIG_PATH = "credit-decision";

    private final Duration interval;
    private final Duration timerThreshold;
    private final int creditPerBatch;

    DefaultCreditDecisionConfig(final Duration interval, final Duration timerThreshold, final int creditPerBatch) {
        this.interval = interval;
        this.timerThreshold = timerThreshold;
        this.creditPerBatch = creditPerBatch;
    }

    private DefaultCreditDecisionConfig(final ScopedConfig conf) {
        this.interval = conf.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.INTERVAL);
        this.timerThreshold = conf.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.TIMER_THRESHOLD);
        this.creditPerBatch = conf.getNonNegativeIntOrThrow(ConfigValue.CREDIT_PER_BATCH);
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
                    Objects.equals(timerThreshold, that.timerThreshold) &&
                    creditPerBatch == that.creditPerBatch;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(interval, timerThreshold, creditPerBatch);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[interval=" + interval +
                ",timerThreshold=" + timerThreshold +
                ",creditPerBatch=" + creditPerBatch +
                "]";
    }

}
