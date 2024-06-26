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
import org.eclipse.ditto.wot.validation.config.FeatureValidationConfig;
import org.eclipse.ditto.wot.validation.config.ThingValidationConfig;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the WoT (Web of Things) {@link org.eclipse.ditto.wot.validation.config.TmValidationConfig}.
 */
@Immutable
final class DefaultTmValidationConfig implements TmValidationConfig {

    private static final String CONFIG_PATH = "tm-model-validation";

    private final boolean enabled;
    private final ThingValidationConfig thingValidationConfig;
    private final FeatureValidationConfig featureValidationConfig;

    private DefaultTmValidationConfig(final ScopedConfig scopedConfig) {
        enabled = scopedConfig.getBoolean(ConfigValue.ENABLED.getConfigPath());
        thingValidationConfig = DefaultThingValidationConfig.of(scopedConfig);
        featureValidationConfig = DefaultFeatureValidationConfig.of(scopedConfig);
    }

    /**
     * Returns an instance of the thing config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the thing config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTmValidationConfig of(final Config config) {
        return new DefaultTmValidationConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                ConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public ThingValidationConfig getThingValidationConfig() {
        return thingValidationConfig;
    }

    @Override
    public FeatureValidationConfig getFeatureValidationConfig() {
        return featureValidationConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultTmValidationConfig that = (DefaultTmValidationConfig) o;
        return enabled == that.enabled &&
                Objects.equals(thingValidationConfig, that.thingValidationConfig) &&
                Objects.equals(featureValidationConfig, that.featureValidationConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, thingValidationConfig, featureValidationConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", thingValidationConfig=" + thingValidationConfig +
                ", featureValidationConfig=" + featureValidationConfig +
                "]";
    }
}
