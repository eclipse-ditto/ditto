/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.common.config;

import java.util.List;
import java.util.Objects;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultEventConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@code ThingEventConfig}.
 */
public final class DefaultThingEventConfig implements ThingEventConfig {

    private static final String CONFIG_PATH = "event";

    private final DefaultEventConfig defaultEventConfigDelegated;
    private final List<PreDefinedExtraFieldsConfig> preDefinedExtraFieldsConfigs;
    private final boolean partialAccessEventsEnabled;

    private DefaultThingEventConfig(final DefaultEventConfig delegate, final ScopedConfig config) {
        this.defaultEventConfigDelegated = delegate;
        preDefinedExtraFieldsConfigs =
                config.getObjectList(ThingEventConfigValue.PRE_DEFINED_EXTRA_FIELDS.getConfigPath())
                        .stream()
                        .map(configObj -> DefaultPreDefinedExtraFieldsConfig.of(configObj.toConfig()))
                        .map(PreDefinedExtraFieldsConfig.class::cast)
                        .toList();
        partialAccessEventsEnabled =
                config.getBoolean(ThingEventConfigValue.PARTIAL_ACCESS_EVENTS_ENABLED.getConfigPath());
    }

    /**
     * Returns an instance of the default event journal config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the event journal config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultThingEventConfig of(final Config config) {
        return new DefaultThingEventConfig(DefaultEventConfig.of(config),
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ThingEventConfigValue.values()));
    }

    @Override
    public List<String> getHistoricalHeadersToPersist() {
        return defaultEventConfigDelegated.getHistoricalHeadersToPersist();
    }

    @Override
    public List<PreDefinedExtraFieldsConfig> getPredefinedExtraFieldsConfigs() {
        return preDefinedExtraFieldsConfigs;
    }

    @Override
    public boolean isPartialAccessEventsEnabled() {
        return partialAccessEventsEnabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof final DefaultThingEventConfig that)) {
            return false;
        }
        return Objects.equals(defaultEventConfigDelegated, that.defaultEventConfigDelegated) &&
                Objects.equals(preDefinedExtraFieldsConfigs, that.preDefinedExtraFieldsConfigs) &&
                partialAccessEventsEnabled == that.partialAccessEventsEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultEventConfigDelegated, preDefinedExtraFieldsConfigs, partialAccessEventsEnabled);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "defaultEventConfigDelegated=" + defaultEventConfigDelegated +
                ", preDefinedExtraFieldsConfigs=" + preDefinedExtraFieldsConfigs +
                ", partialAccessEventsEnabled=" + partialAccessEventsEnabled +
                "]";
    }
}
