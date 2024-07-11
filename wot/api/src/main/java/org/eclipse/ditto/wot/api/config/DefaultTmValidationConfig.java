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

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.wot.validation.ValidationContext;
import org.eclipse.ditto.wot.validation.config.FeatureValidationConfig;
import org.eclipse.ditto.wot.validation.config.ThingValidationConfig;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This class is the default implementation of the WoT (Web of Things) {@link org.eclipse.ditto.wot.validation.config.TmValidationConfig}.
 */
@Immutable
final class DefaultTmValidationConfig implements TmValidationConfig {

    private static final String CONFIG_PATH = "tm-model-validation";

    private static final String CONFIG_KEY_DYNAMIC_CONFIGURATION = "dynamic-configuration";

    private final ScopedConfig scopedConfig;
    private final List<InternalDynamicTmValidationConfiguration> dynamicTmValidationConfigurations;

    private final boolean enabled;
    private final ThingValidationConfig thingValidationConfig;
    private final FeatureValidationConfig featureValidationConfig;


    private DefaultTmValidationConfig(final ScopedConfig scopedConfig,
            final List<InternalDynamicTmValidationConfiguration> dynamicTmValidationConfigurations,
            @Nullable final ValidationContext context
    ) {
        this.scopedConfig = scopedConfig;
        this.dynamicTmValidationConfigurations = dynamicTmValidationConfigurations;

        final Config effectiveConfig = dynamicTmValidationConfigurations.stream()
                .flatMap(dynamicConfig -> dynamicConfig.calculateDynamicTmValidationConfigOverrides(context).stream())
                .reduce(ConfigFactory.empty(), (a, b) -> b.withFallback(a))
                .withFallback(scopedConfig.resolve());
        enabled = effectiveConfig.getBoolean(ConfigValue.ENABLED.getConfigPath());

        thingValidationConfig = DefaultThingValidationConfig.of(effectiveConfig);
        featureValidationConfig = DefaultFeatureValidationConfig.of(effectiveConfig);
    }

    /**
     * Returns an instance of the thing config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the thing config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTmValidationConfig of(final Config config) {
        final List<InternalDynamicTmValidationConfiguration> dynamicTmValidationConfigurations =
                DefaultScopedConfig.newInstance(config, CONFIG_PATH)
                        .getConfigList(CONFIG_KEY_DYNAMIC_CONFIGURATION)
                        .stream()
                        .map(InternalDynamicTmValidationConfiguration::new)
                        .toList();
        return new DefaultTmValidationConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                ConfigValue.values()), dynamicTmValidationConfigurations, null);
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
    public TmValidationConfig withValidationContext(@Nullable final ValidationContext context) {
        if (dynamicTmValidationConfigurations.isEmpty()) {
            return this;
        } else {
            return new DefaultTmValidationConfig(scopedConfig, dynamicTmValidationConfigurations, context);
        }
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
        return Objects.equals(dynamicTmValidationConfigurations, that.dynamicTmValidationConfigurations) &&
                enabled == that.enabled &&
                Objects.equals(thingValidationConfig, that.thingValidationConfig) &&
                Objects.equals(featureValidationConfig, that.featureValidationConfig) &&
                Objects.equals(scopedConfig, that.scopedConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dynamicTmValidationConfigurations, enabled, thingValidationConfig, featureValidationConfig,
                scopedConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dynamicTmValidationConfiguration=" + dynamicTmValidationConfigurations +
                ", enabled=" + enabled +
                ", thingValidationConfig=" + thingValidationConfig +
                ", featureValidationConfig=" + featureValidationConfig +
                ", scopedConfig=" + scopedConfig +
                "]";
    }
}
