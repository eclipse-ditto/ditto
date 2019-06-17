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

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

final class DefaultPersistenceCleanupConfig implements PersistenceCleanupConfig {

    private static final String CONFIG_PATH = "persistence-cleanup";

    private final Duration quietPeriod;
    private final CreditDecisionConfig creditDecisionConfig;
    private final PersistenceIdsConfig persistenceIdsConfig;

    private DefaultPersistenceCleanupConfig(final Config config) {
        this.quietPeriod = config.getDuration(ConfigValue.QUIET_PERIOD.getConfigPath());
        this.creditDecisionConfig = DefaultCreditDecisionConfig.of(config);
        this.persistenceIdsConfig = DefaultPersistenceIdsConfig.of(config);
    }

    static PersistenceCleanupConfig of(final Config serviceSpecificConfig) {
        return new DefaultPersistenceCleanupConfig(
                ConfigWithFallback.newInstance(serviceSpecificConfig, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public Duration getQuietPeriod() {
        return quietPeriod;
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
    public boolean equals(final Object o) {
        if (o instanceof DefaultPersistenceCleanupConfig) {
            final DefaultPersistenceCleanupConfig that = (DefaultPersistenceCleanupConfig) o;
            return Objects.equals(quietPeriod, that.quietPeriod) &&
                    Objects.equals(creditDecisionConfig, that.creditDecisionConfig) &&
                    Objects.equals(persistenceIdsConfig, that.persistenceIdsConfig);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(quietPeriod, creditDecisionConfig, persistenceIdsConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[ quietPeriod=" + quietPeriod +
                ", creditDecisionConfig" + creditDecisionConfig +
                ", persistenceIdsConfig" + persistenceIdsConfig +
                "]";
    }
}
