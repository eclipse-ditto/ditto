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

import com.typesafe.config.Config;

/**
 * Default implementation of {@code ThingMessageConfig}.
 */
public final class DefaultThingMessageConfig implements ThingMessageConfig {

    private static final String CONFIG_PATH = "message";

    private final List<PreDefinedExtraFieldsConfig> preDefinedExtraFieldsConfigs;

    private DefaultThingMessageConfig(final ScopedConfig config) {
        preDefinedExtraFieldsConfigs =
                config.getObjectList(ThingMessageConfigValue.PRE_DEFINED_EXTRA_FIELDS.getConfigPath())
                        .stream()
                        .map(configObj -> DefaultPreDefinedExtraFieldsConfig.of(configObj.toConfig()))
                        .map(PreDefinedExtraFieldsConfig.class::cast)
                        .toList();
    }

    /**
     * Returns an instance of the default event journal config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the event journal config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultThingMessageConfig of(final Config config) {
        return new DefaultThingMessageConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ThingMessageConfigValue.values())
        );
    }

    @Override
    public List<PreDefinedExtraFieldsConfig> getPredefinedExtraFieldsConfigs() {
        return preDefinedExtraFieldsConfigs;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof final DefaultThingMessageConfig that)) {
            return false;
        }
        return Objects.equals(preDefinedExtraFieldsConfigs, that.preDefinedExtraFieldsConfigs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preDefinedExtraFieldsConfigs);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "preDefinedExtraFieldsConfigs=" + preDefinedExtraFieldsConfigs +
                "]";
    }
}
