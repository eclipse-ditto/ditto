/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.api.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the WoT (Web of Things) {@link TmBasedCreationConfig}.
 */
@Immutable
final class DefaultTmBasedCreationConfig implements TmBasedCreationConfig {

    private static final String CONFIG_PATH = "tm-based-creation";

    private final TmScopedCreationConfig thingCreationConfig;
    private final TmScopedCreationConfig featureCreationConfig;

    private DefaultTmBasedCreationConfig(final ScopedConfig scopedConfig) {
        thingCreationConfig = DefaultTmScopedCreationConfig.of(scopedConfig, "thing");
        featureCreationConfig = DefaultTmScopedCreationConfig.of(scopedConfig, "feature");
    }

    /**
     * Returns an instance of the thing config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the thing config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTmBasedCreationConfig of(final Config config) {
        return new DefaultTmBasedCreationConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                ConfigValue.values()));
    }

    @Override
    public TmScopedCreationConfig getThingCreationConfig() {
        return thingCreationConfig;
    }

    @Override
    public TmScopedCreationConfig getFeatureCreationConfig() {
        return featureCreationConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultTmBasedCreationConfig that = (DefaultTmBasedCreationConfig) o;
        return Objects.equals(thingCreationConfig, that.thingCreationConfig) &&
                Objects.equals(featureCreationConfig, that.featureCreationConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingCreationConfig, featureCreationConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingCreationConfig=" + thingCreationConfig +
                ", featureCreationConfig=" + featureCreationConfig +
                "]";
    }
}
