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
package org.eclipse.ditto.wot.validation.config;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.wot.validation.ValidationContext;

/**
 * Provides configuration settings for WoT (Web of Things) integration regarding the validation of Things and Features
 * based on their WoT ThingModels.
 *
 * @since 3.6.0
 */
@Immutable
public interface TmValidationConfig {

    /**
     * @return whether the ThingModel validation of Things/Features should be enabled or not.
     */
    boolean isEnabled();

    /**
     * @return whether to instead of to reject/fail API calls (when enabled=true), log a WARNING log instead
     */
    boolean logWarningInsteadOfFailingApiCalls();

    /**
     * @return the config for validating things.
     */
    ThingValidationConfig getThingValidationConfig();

    /**
     * @return the config for validating features.
     */
    FeatureValidationConfig getFeatureValidationConfig();

    /**
     * Creates a new specific instance of this {@link org.eclipse.ditto.wot.validation.config.TmValidationConfig} scoped with the provided validation
     * {@code context} of the API call.
     *
     * @param context the validation context of the API call.
     * @return an API call specific instance of the validation config.
     */
    TmValidationConfig withValidationContext(@Nullable ValidationContext context);

    /**
     * Returns the list of dynamic validation configurations for specific contexts.
     * @since 3.8.0
     */
    List<DynamicValidationConfig> getDynamicConfigs();

    /**
     * @return whether the generated JsonSchemas based on the WoT model properties should be cached.
     * @since 3.8.0
     */
    boolean isJsonSchemaCacheEnabled();

    /**
     * @return the cache configuration for when {@link #isJsonSchemaCacheEnabled()} is enabled.
     * @since 3.8.0
     */
    CacheConfig getJsonSchemaCacheConfig();

    /**
     * @return whether to enforce that all prefixes used in WoT ThingModels are defined in the {@code @context}.
     * @since 3.9.0
     */
    boolean isEnforceContextPrefixes();


    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code TmValidationConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Whether the TM based validation should be enabled or not.
         */
        ENABLED("enabled", true),

        /**
         * Whether to instead of to reject/fail API calls (when {@code enabled=true}), log a WARNING log instead.
         */
        LOG_WARNING_INSTEAD_OF_FAILING_API_CALLS("log-warning-instead-of-failing-api-calls", false),

        /**
         * Whether the JsonSchema cache should be enabled.
         * @since 3.8.0
         */
        JSON_SCHEMA_CACHE_ENABLED("json-schema-cache-enabled", true),

        /**
         * Whether to enforce that all prefixes used in WoT ThingModels are defined in the @context.
         * @since 3.9.0
         */
        ENFORCE_CONTEXT_PREFIXES("enforce-context-prefixes", true);


        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}
