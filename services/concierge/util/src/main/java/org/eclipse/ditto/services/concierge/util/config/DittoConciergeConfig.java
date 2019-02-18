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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DefaultClusterConfig;
import org.eclipse.ditto.services.base.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.base.config.DefaultHttpConfig;
import org.eclipse.ditto.services.base.config.DefaultLimitsConfig;
import org.eclipse.ditto.services.base.config.DefaultMetricsConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

/**
 * This class is the implementation of {@link ConciergeConfig} for Ditto's Concierge service.
 */
public final class DittoConciergeConfig implements ConciergeConfig {

    private static final String CONFIG_PATH = "concierge";

    private final EnforcementConfig enforcementConfig;
    private final CachesConfig cachesConfig;
    private final ThingsAggregatorConfig thingsAggregatorConfig;
    private final ClusterConfig clusterConfig;
    private final HealthCheckConfig healthCheckConfig;
    private final LimitsConfig limitsConfig;
    private final HttpConfig httpConfig;
    private final MetricsConfig metricsConfig;
    private final MongoDbConfig mongoDbConfig;

    private DittoConciergeConfig(final Config config, final LimitsConfig theLimitsConfig) {
        enforcementConfig = DittoConciergeEnforcementConfig.of(config);
        cachesConfig = DittoConciergeCachesConfig.of(config);
        thingsAggregatorConfig = DittoConciergeThingsAggregatorConfig.of(config);
        clusterConfig = DefaultClusterConfig.of(config);
        healthCheckConfig = DefaultHealthCheckConfig.of(config);
        limitsConfig = theLimitsConfig;
        httpConfig = DefaultHttpConfig.of(config);
        metricsConfig = DefaultMetricsConfig.of(config);
        mongoDbConfig = DefaultMongoDbConfig.of(config);
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
        final Config conciergeConfig = tryToGetServiceSpecificConfig(checkNotNull(config, "original Config"));
        final LimitsConfig limitsConfig = DefaultLimitsConfig.of(config);

        return new DittoConciergeConfig(conciergeConfig, limitsConfig);
    }

    private static Config tryToGetServiceSpecificConfig(final Config originalConfig) {
        try {
            return getServiceSpecificConfig(originalConfig);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final String msgPattern = "Failed to get nested Config for key <{0}> from <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, CONFIG_PATH, originalConfig), e);
        }
    }

    private static Config getServiceSpecificConfig(final Config originalConfig) {
        return originalConfig.getConfig(CONFIG_PATH);
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
        return clusterConfig;
    }

    @Override
    public HealthCheckConfig getHealthCheckConfig() {
        return healthCheckConfig;
    }

    @Override
    public LimitsConfig getLimitsConfig() {
        return limitsConfig;
    }

    @Override
    public HttpConfig getHttpConfig() {
        return httpConfig;
    }

    @Override
    public MetricsConfig getMetricsConfig() {
        return metricsConfig;
    }

    @Override
    public MongoDbConfig getMongoDbConfig() {
        return mongoDbConfig;
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoConciergeConfig that = (DittoConciergeConfig) o;
        return enforcementConfig.equals(that.enforcementConfig) &&
                cachesConfig.equals(that.cachesConfig) &&
                thingsAggregatorConfig.equals(that.thingsAggregatorConfig) &&
                clusterConfig.equals(that.clusterConfig) &&
                healthCheckConfig.equals(that.healthCheckConfig) &&
                limitsConfig.equals(that.limitsConfig) &&
                httpConfig.equals(that.httpConfig) &&
                metricsConfig.equals(that.metricsConfig) &&
                mongoDbConfig.equals(that.mongoDbConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforcementConfig, cachesConfig, thingsAggregatorConfig, clusterConfig, healthCheckConfig,
                limitsConfig, httpConfig, metricsConfig, mongoDbConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforcementConfig=" + enforcementConfig +
                ", cachesConfig=" + cachesConfig +
                ", thingsAggregatorConfig=" + thingsAggregatorConfig +
                ", clusterConfig=" + clusterConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", limitsConfig=" + limitsConfig +
                ", httpConfig=" + httpConfig +
                ", metricsConfig=" + metricsConfig +
                ", mongoDbConfig=" + mongoDbConfig +
                "]";
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
            public String getPath() {
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
         * @throws com.typesafe.config.ConfigException.WrongType if {@code config} did not contain a nested
         * {@code Config} for {@value #CONFIG_PATH}.
         */
        public static DittoConciergeEnforcementConfig of(final Config config) {
            return new DittoConciergeEnforcementConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, ConciergeEnforcementConfigValue.values()));
        }

        @Override
        public Duration getAskTimeout() {
            return config.getDuration(ConciergeEnforcementConfigValue.ASK_TIMEOUT.getPath());
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
            public String getPath() {
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
         * @throws com.typesafe.config.ConfigException.WrongType if {@code config} did not contain a nested
         * {@code Config} for {@value #CONFIG_PATH}.
         */
        public static DittoConciergeCachesConfig of(final Config config) {
            return new DittoConciergeCachesConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, ConciergeCachesConfigValue.values()));
        }

        @Override
        public Duration getAskTimeout() {
            return config.getDuration(ConciergeCachesConfigValue.ASK_TIMEOUT.getPath());
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
            public String getPath() {
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
         * @throws com.typesafe.config.ConfigException.WrongType if {@code config} did not contain a nested
         * {@code Config} for {@value #CONFIG_PATH}.
         */
        public static DittoConciergeThingsAggregatorConfig of(final Config config) {
            return new DittoConciergeThingsAggregatorConfig(
                    ConfigWithFallback.newInstance(config, CONFIG_PATH, ThingsAggregatorConfigValue.values()));
        }

        @Override
        public Duration getSingleRetrieveThingTimeout() {
            return config.getDuration(ThingsAggregatorConfigValue.SINGLE_RETRIEVE_THING_TIMEOUT.getPath());
        }

        @Override
        public int getMaxParallelism() {
            return config.getInt(ThingsAggregatorConfigValue.MAX_PARALLELISM.getPath());
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
