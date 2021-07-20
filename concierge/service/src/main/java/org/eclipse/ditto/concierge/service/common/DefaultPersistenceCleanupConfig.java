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

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

final class DefaultPersistenceCleanupConfig implements PersistenceCleanupConfig {

    private static final String CONFIG_PATH = "persistence-cleanup";

    private final boolean enabled;
    private final Duration quietPeriod;
    private final Duration cleanupTimeout;
    private final int parallelism;
    private final int keptCreditDecisions;
    private final int keptActions;
    private final int keptEvents;
    private final CreditDecisionConfig creditDecisionConfig;
    private final PersistenceIdsConfig persistenceIdsConfig;
    private final ScopedConfig config;

    private DefaultPersistenceCleanupConfig(final ScopedConfig config) {
        this.enabled = config.getBoolean(ConfigValue.ENABLED.getConfigPath());
        this.quietPeriod = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.QUIET_PERIOD);
        this.cleanupTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.CLEANUP_TIMEOUT);
        this.parallelism = config.getPositiveIntOrThrow(ConfigValue.PARALLELISM);
        this.keptCreditDecisions = config.getPositiveIntOrThrow(ConfigValue.KEEP_CREDIT_DECISIONS);
        this.keptActions = config.getPositiveIntOrThrow(ConfigValue.KEEP_ACTIONS);
        this.keptEvents = config.getPositiveIntOrThrow(ConfigValue.KEEP_EVENTS);
        this.creditDecisionConfig = DefaultCreditDecisionConfig.of(config);
        this.persistenceIdsConfig = DefaultPersistenceIdsConfig.of(config);
        this.config = config;
    }

    static PersistenceCleanupConfig of(final Config serviceSpecificConfig) {
        return new DefaultPersistenceCleanupConfig(
                ConfigWithFallback.newInstance(serviceSpecificConfig, CONFIG_PATH, ConfigValue.values()));
    }

    static PersistenceCleanupConfig updated(final Config extractedConfig) {
        return new DefaultPersistenceCleanupConfig(
                ConfigWithFallback.newInstance(extractedConfig, new KnownConfigValue[0]));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getQuietPeriod() {
        return quietPeriod;
    }

    @Override
    public Duration getCleanupTimeout() {
        return cleanupTimeout;
    }

    @Override
    public int getParallelism() {
        return parallelism;
    }

    @Override
    public CreditDecisionConfig getCreditDecisionConfig() {
        return creditDecisionConfig;
    }

    @Override
    public PersistenceIdsConfig getPersistenceIdsConfig() {
        return persistenceIdsConfig;
    }

    @Override
    public int getKeptCreditDecisions() {
        return keptCreditDecisions;
    }

    @Override
    public int getKeptActions() {
        return keptActions;
    }

    @Override
    public int getKeptEvents() {
        return keptEvents;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof DefaultPersistenceCleanupConfig) {
            final DefaultPersistenceCleanupConfig that = (DefaultPersistenceCleanupConfig) o;
            return enabled == that.enabled &&
                    Objects.equals(quietPeriod, that.quietPeriod) &&
                    Objects.equals(cleanupTimeout, that.cleanupTimeout) &&
                    parallelism == that.parallelism &&
                    keptCreditDecisions == that.keptCreditDecisions &&
                    keptActions == that.keptActions &&
                    keptEvents == that.keptEvents &&
                    Objects.equals(creditDecisionConfig, that.creditDecisionConfig) &&
                    Objects.equals(persistenceIdsConfig, that.persistenceIdsConfig) &&
                    Objects.equals(config, that.config);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, quietPeriod, cleanupTimeout, parallelism, keptCreditDecisions, keptActions,
                keptEvents, creditDecisionConfig, persistenceIdsConfig, config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[ enabled=" + enabled +
                ", quietPeriod=" + quietPeriod +
                ", cleanupTimeout=" + cleanupTimeout +
                ", parallelism=" + parallelism +
                ", keptCreditDecisions" + keptCreditDecisions +
                ", keptActions" + keptActions +
                ", keptEvents" + keptEvents +
                ", creditDecisionConfig" + creditDecisionConfig +
                ", persistenceIdsConfig" + persistenceIdsConfig +
                "]";
    }

}
