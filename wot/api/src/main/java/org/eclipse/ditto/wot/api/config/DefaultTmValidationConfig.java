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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.ConfigOverrides;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.wot.validation.ValidationContext;
import org.eclipse.ditto.wot.validation.config.FeatureValidationConfig;
import org.eclipse.ditto.wot.validation.config.ThingValidationConfig;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

/**
 * This class is the default implementation of the WoT (Web of Things) {@link org.eclipse.ditto.wot.validation.config.TmValidationConfig}.
 */
@Immutable
public final class DefaultTmValidationConfig implements TmValidationConfig {

    private static final String CONFIG_PATH = "tm-model-validation";

    private static final String CONFIG_KEY_DYNAMIC_CONFIGURATION = "dynamic-configuration";
    private static final String JSON_SCHEMA_CACHE = "json-schema-cache";

    private final ScopedConfig scopedConfig;
    private final List<InternalDynamicTmValidationConfiguration> dynamicTmValidationConfigurations;

    private final boolean enabled;
    private final boolean logWarningInsteadOfFailingApiCalls;
    private final ThingValidationConfig thingValidationConfig;
    private final FeatureValidationConfig featureValidationConfig;
    private final boolean jsonSchemaCacheEnabled;
    private final CacheConfig jsonSchemaCacheConfig;
    private final boolean enforceContextPrefixes;


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
        logWarningInsteadOfFailingApiCalls = effectiveConfig.getBoolean(ConfigValue.LOG_WARNING_INSTEAD_OF_FAILING_API_CALLS.getConfigPath());

        thingValidationConfig = DefaultThingValidationConfig.of(effectiveConfig);
        featureValidationConfig = DefaultFeatureValidationConfig.of(effectiveConfig);
        jsonSchemaCacheEnabled = effectiveConfig.getBoolean(ConfigValue.JSON_SCHEMA_CACHE_ENABLED.getConfigPath());
        jsonSchemaCacheConfig = DefaultCacheConfig.of(effectiveConfig, JSON_SCHEMA_CACHE);
        enforceContextPrefixes = effectiveConfig.getBoolean(ConfigValue.ENFORCE_CONTEXT_PREFIXES.getConfigPath());
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
        return new DefaultTmValidationConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()),
                dynamicTmValidationConfigurations,
                null);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean logWarningInsteadOfFailingApiCalls() {
        return logWarningInsteadOfFailingApiCalls;
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
    public boolean isJsonSchemaCacheEnabled() {
        return jsonSchemaCacheEnabled;
    }

    @Override
    public CacheConfig getJsonSchemaCacheConfig() {
        return jsonSchemaCacheConfig;
    }

    @Override
    public boolean isEnforceContextPrefixes() {
        return enforceContextPrefixes;
    }

    /**
     * Returns the list of dynamic validation configurations for specific contexts.
     *
     * @since 3.8.0
     */
    @Override
    public List<DynamicValidationConfig> getDynamicConfigs() {
        if (dynamicTmValidationConfigurations.isEmpty()) {
            return Collections.emptyList();
        }

        return dynamicTmValidationConfigurations.stream()
                .map(internal -> {
                    final var contextConfig = internal.getDynamicValidationContextConfiguration();

                    final var dittoHeadersPatterns = contextConfig.dittoHeadersPatterns().stream()
                            .map(map -> map.entrySet().stream()
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            e -> e.getValue().pattern()
                                    )))
                            .toList();

                    final var thingDefinitionPatterns = contextConfig.thingDefinitionPatterns().stream()
                            .map(Pattern::pattern)
                            .toList();

                    final var featureDefinitionPatterns = contextConfig.featureDefinitionPatterns().stream()
                            .map(Pattern::pattern)
                            .toList();

                    final var validationContext = org.eclipse.ditto.things.model.devops.ValidationContext.of(
                            dittoHeadersPatterns,
                            thingDefinitionPatterns,
                            featureDefinitionPatterns
                    );

                    final var configOverridesJson = JsonObject.of(internal.configOverrides().root().render(
                            ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
                    ));

                    final Boolean enabled =  configOverridesJson.getValue(ConfigValue.ENABLED.getConfigPath())
                            .map(JsonValue::asBoolean).orElse(null);
                    final Boolean logWarning = configOverridesJson.getValue(ConfigValue.LOG_WARNING_INSTEAD_OF_FAILING_API_CALLS.getConfigPath())
                            .map(JsonValue::asBoolean).orElse(null);

                    final org.eclipse.ditto.things.model.devops.ThingValidationConfig thingConfig = configOverridesJson.getValue("thing")
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(org.eclipse.ditto.things.model.devops.ThingValidationConfig::fromJson)
                            .orElse(null);

                    final org.eclipse.ditto.things.model.devops.FeatureValidationConfig featureConfig = configOverridesJson.getValue("feature")
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(org.eclipse.ditto.things.model.devops.FeatureValidationConfig::fromJson)
                            .orElse(null);

                    final var configOverrides = ConfigOverrides.of(
                            enabled,
                            logWarning,
                            thingConfig,
                            featureConfig
                    );

                    return DynamicValidationConfig.of(
                            internal.getScopeId(),
                            validationContext,
                            configOverrides
                    );
                })
                .toList();
    }

    /**
     * Creates a new instance of this configuration with the specified validation context.
     * If dynamic configurations are present, they will be evaluated against the provided context.
     *
     * @param context the validation context to use for dynamic configuration selection
     * @return a new TmValidationConfig instance with context-specific settings applied
     */
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
                logWarningInsteadOfFailingApiCalls == that.logWarningInsteadOfFailingApiCalls &&
                Objects.equals(thingValidationConfig, that.thingValidationConfig) &&
                Objects.equals(featureValidationConfig, that.featureValidationConfig) &&
                Objects.equals(scopedConfig, that.scopedConfig) &&
                jsonSchemaCacheEnabled == that.jsonSchemaCacheEnabled &&
                Objects.equals(jsonSchemaCacheConfig, that.jsonSchemaCacheConfig) &&
                enforceContextPrefixes == that.enforceContextPrefixes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dynamicTmValidationConfigurations, enabled, logWarningInsteadOfFailingApiCalls,
                thingValidationConfig, featureValidationConfig, scopedConfig, jsonSchemaCacheEnabled,
                jsonSchemaCacheConfig, enforceContextPrefixes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dynamicTmValidationConfiguration=" + dynamicTmValidationConfigurations +
                ", enabled=" + enabled +
                ", logWarningInsteadOfFailingApiCalls=" + logWarningInsteadOfFailingApiCalls +
                ", thingValidationConfig=" + thingValidationConfig +
                ", featureValidationConfig=" + featureValidationConfig +
                ", scopedConfig=" + scopedConfig +
                ", jsonSchemaCacheEnabled=" + jsonSchemaCacheEnabled +
                ", jsonSchemaCacheConfig=" + jsonSchemaCacheConfig +
                ", enforceContextPrefixes=" + enforceContextPrefixes +
                "]";
    }

}
