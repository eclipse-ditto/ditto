/*
* Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceWithMongoDbConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;

import com.typesafe.config.Config;

/**
 * This class is the implementation of {@link ConciergeConfig} for Ditto's Concierge service.
 */
@Immutable
public final class DittoConciergeConfig implements ConciergeConfig {

    private static final String CONFIG_PATH = "concierge";

    private final DittoServiceWithMongoDbConfig serviceSpecificConfig;
    private final EnforcementConfig enforcementConfig;
    private final CachesConfig cachesConfig;
    private final ThingsAggregatorConfig thingsAggregatorConfig;

    private DittoConciergeConfig(final DittoServiceWithMongoDbConfig serviceSpecificConfig,
            final EnforcementConfig enforcementConfig,
            final CachesConfig cachesConfig,
            final ThingsAggregatorConfig thingsAggregatorConfig) {

        this.serviceSpecificConfig = serviceSpecificConfig;
        this.enforcementConfig = enforcementConfig;
        this.cachesConfig = cachesConfig;
        this.thingsAggregatorConfig = thingsAggregatorConfig;
    }

    /**
     * Returns an instance of {@code DittoConciergeEnforcementConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the Concierge service config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws NullPointerException if {@code config} is {@code null}.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} did not contain a nested Config
     * at path {@value #CONFIG_PATH}.
     */
    public static DittoConciergeConfig of(final Config config) {
        final DittoServiceWithMongoDbConfig dittoServiceConfig = DittoServiceWithMongoDbConfig.of(config, CONFIG_PATH);

        return new DittoConciergeConfig(dittoServiceConfig,
                DittoConciergeEnforcementConfig.of(dittoServiceConfig),
                DittoConciergeCachesConfig.of(dittoServiceConfig),
                DittoConciergeThingsAggregatorConfig.of(dittoServiceConfig));
    }

    @Override
    public EnforcementConfig getEnforcementConfig() {
        return enforcementConfig;
    }

    @Override
    public CachesConfig getCachesConfig() {
        return cachesConfig;
    }

    @Override
    public ThingsAggregatorConfig getThingsAggregatorConfig() {
        return thingsAggregatorConfig;
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return serviceSpecificConfig.getClusterConfig();
    }

    @Override
    public HealthCheckConfig getHealthCheckConfig() {
        return serviceSpecificConfig.getHealthCheckConfig();
    }

    @Override
    public LimitsConfig getLimitsConfig() {
        return serviceSpecificConfig.getLimitsConfig();
    }

    @Override
    public HttpConfig getHttpConfig() {
        return serviceSpecificConfig.getHttpConfig();
    }

    @Override
    public MetricsConfig getMetricsConfig() {
        return serviceSpecificConfig.getMetricsConfig();
    }

    @Override
    public MongoDbConfig getMongoDbConfig() {
        return serviceSpecificConfig.getMongoDbConfig();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoConciergeConfig that = (DittoConciergeConfig) o;
        return serviceSpecificConfig.equals(that.serviceSpecificConfig) &&
                enforcementConfig.equals(that.enforcementConfig) &&
                cachesConfig.equals(that.cachesConfig) &&
                thingsAggregatorConfig.equals(that.thingsAggregatorConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, enforcementConfig, cachesConfig, thingsAggregatorConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", enforcementConfig=" + enforcementConfig +
                ", cachesConfig=" + cachesConfig +
                ", thingsAggregatorConfig=" + thingsAggregatorConfig +
                "]";
    }

    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

    /**
     * This class implements {@link EnforcementConfig} for Ditto's Concierge service.
     */
    @Immutable
    public static final class DittoConciergeEnforcementConfig implements EnforcementConfig {

        private enum ConciergeEnforcementConfigValue implements KnownConfigValue {

            ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(10));

            private final String path;
            private final Object defaultValue;

            private ConciergeEnforcementConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

        private static final String CONFIG_PATH = "enforcement";

        private final Config config;

        private DittoConciergeEnforcementConfig(final Config theConfig) {
            config = theConfig;
        }

        /**
         * Returns an instance of {@code DittoConciergeEnforcementConfig} based on the settings of the specified Config.
         *
         * @param config is supposed to provide the settings of the enforcement config at {@value #CONFIG_PATH}.
         * @return the instance.
         * @throws NullPointerException if {@code config} is {@code null}.
         * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} did not contain a nested
         * {@code Config} for {@value #CONFIG_PATH}.
         */
        public static DittoConciergeEnforcementConfig of(final Config config) {
            return new DittoConciergeEnforcementConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, ConciergeEnforcementConfigValue.values()));
        }

        @Override
        public Duration getAskTimeout() {
            return config.getDuration(ConciergeEnforcementConfigValue.ASK_TIMEOUT.getConfigPath());
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DittoConciergeEnforcementConfig that = (DittoConciergeEnforcementConfig) o;
            return config.equals(that.config);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "config=" + config +
                    "]";
        }

    }

    /**
     * This class implements {@link CachesConfig} for Ditto's Concierge service.
     */
    @Immutable
    public static final class DittoConciergeCachesConfig implements CachesConfig {

        private enum ConciergeCachesConfigValue implements KnownConfigValue {

            ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(10L));

            private final String path;
            private final Object defaultValue;

            private ConciergeCachesConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

        private static final String CONFIG_PATH = "caches";

        private final Config config;
        private final CacheConfig idCacheConfig;
        private final CacheConfig enforcerCacheConfig;

        private DittoConciergeCachesConfig(final Config theConfig) {
            config = theConfig;
            idCacheConfig = DittoConciergeCacheConfig.getInstance(config, "id");
            enforcerCacheConfig = DittoConciergeCacheConfig.getInstance(config, "enforcer");
        }

        /**
         * Returns an instance of {@code DittoConciergeCachesConfig} based on the settings of the specified Config.
         *
         * @param config is supposed to provide the settings of the caches config at {@value #CONFIG_PATH}.
         * @return the instance.
         * @throws NullPointerException if {@code config} is {@code null}.
         * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} did not contain a nested
         * {@code Config} for {@value #CONFIG_PATH}.
         */
        public static DittoConciergeCachesConfig of(final Config config) {
            return new DittoConciergeCachesConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, ConciergeCachesConfigValue.values()));
        }

        @Override
        public Duration getAskTimeout() {
            return config.getDuration(ConciergeCachesConfigValue.ASK_TIMEOUT.getConfigPath());
        }

        @Override
        public CacheConfig getIdCacheConfig() {
            return idCacheConfig;
        }

        @Override
        public CacheConfig getEnforcerCacheConfig() {
            return enforcerCacheConfig;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DittoConciergeCachesConfig that = (DittoConciergeCachesConfig) o;
            return config.equals(that.config) &&
                    idCacheConfig.equals(that.idCacheConfig) &&
                    enforcerCacheConfig.equals(that.enforcerCacheConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config, idCacheConfig, enforcerCacheConfig);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "config=" + config +
                    ", idCacheConfig=" + idCacheConfig +
                    ", enforcerCacheConfig=" + enforcerCacheConfig +
                    "]";
        }

    }

    /**
     * This class implements {@link ThingsAggregatorConfig} for Ditto's Concierge service.
     */
    @Immutable
    public static final class DittoConciergeThingsAggregatorConfig implements ThingsAggregatorConfig {

        private enum ThingsAggregatorConfigValue implements KnownConfigValue {

            SINGLE_RETRIEVE_THING_TIMEOUT("single-retrieve-thing-timeout", Duration.ofSeconds(30L)),

            MAX_PARALLELISM("max-parallelism", 20);

            private final String path;
            private final Object defaultValue;

            private ThingsAggregatorConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }
        }

        private static final String CONFIG_PATH = "things-aggregator";

        private final Config config;

        private DittoConciergeThingsAggregatorConfig(final Config theConfig) {
            config = theConfig;
        }

        /**
         * Returns an instance of {@code DittoConciergeThingsAggregatorConfig} based on the settings of the specified
         * Config.
         *
         * @param config is supposed to provide the settings of the things aggregator config at {@value #CONFIG_PATH}.
         * @return the instance.
         * @throws NullPointerException if {@code config} is {@code null}.
         * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} did not contain a nested
         * {@code Config} for {@value #CONFIG_PATH}.
         */
        public static DittoConciergeThingsAggregatorConfig of(final Config config) {
            return new DittoConciergeThingsAggregatorConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, ThingsAggregatorConfigValue.values()));
        }

        @Override
        public Duration getSingleRetrieveThingTimeout() {
            return config.getDuration(ThingsAggregatorConfigValue.SINGLE_RETRIEVE_THING_TIMEOUT.getConfigPath());
        }

        @Override
        public int getMaxParallelism() {
            return config.getInt(ThingsAggregatorConfigValue.MAX_PARALLELISM.getConfigPath());
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DittoConciergeThingsAggregatorConfig that = (DittoConciergeThingsAggregatorConfig) o;
            return config.equals(that.config);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "config=" + config +
                    "]";
        }

    }

}
