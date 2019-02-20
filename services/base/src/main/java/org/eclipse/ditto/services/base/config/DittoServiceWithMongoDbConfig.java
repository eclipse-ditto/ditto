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
package org.eclipse.ditto.services.base.config;

import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigMergeable;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;

/**
 * This class is the same as {@link org.eclipse.ditto.services.base.config.DittoServiceConfig} except that it
 * additionally implements {@link org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig}.
 */
@Immutable
public final class DittoServiceWithMongoDbConfig implements ScopedConfig, ServiceSpecificConfig, WithMongoDbConfig {

    private final DittoServiceConfig dittoServiceConfig;
    private final MongoDbConfig mongoDbConfig;

    private DittoServiceWithMongoDbConfig(final DittoServiceConfig theDittoServiceConfig,
            final MongoDbConfig theMongoDbConfig) {

        dittoServiceConfig = theDittoServiceConfig;
        mongoDbConfig = theMongoDbConfig;
    }

    /**
     * Returns an instance of {@code DittoServiceWithMongoDbConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the Concierge service config at the specified config path.
     * @param configPath the path of the nested Config within {@code config} that provides the service specific config
     * settings.
     * Mostly this value is the service name.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if
     * <ul>
     *     <li>{@code config} is {@code null},</li>
     *     <li>{@code configPath} is {@code null} or if</li>
     *     <li>{@code config} did not contain a nested Config at path {@code configPath}.</li>
     * </ul>
     */
    public static DittoServiceWithMongoDbConfig of(final Config config, final String configPath) {
        final DittoServiceConfig dittoServiceConfig = DittoServiceConfig.of(config, configPath);

        return new DittoServiceWithMongoDbConfig(dittoServiceConfig, DefaultMongoDbConfig.of(dittoServiceConfig));
    }

    @Override
    public ClusterConfig getClusterConfig() {return dittoServiceConfig.getClusterConfig();}

    @Override
    public HealthCheckConfig getHealthCheckConfig() {return dittoServiceConfig.getHealthCheckConfig();}

    @Override
    public LimitsConfig getLimitsConfig() {return dittoServiceConfig.getLimitsConfig();}

    @Override
    public HttpConfig getHttpConfig() {return dittoServiceConfig.getHttpConfig();}

    @Override
    public MetricsConfig getMetricsConfig() {return dittoServiceConfig.getMetricsConfig();}

    @Override
    public ConfigObject root() {return dittoServiceConfig.root();}

    @Override
    public ConfigOrigin origin() {return dittoServiceConfig.origin();}

    @Override
    public Config withFallback(final ConfigMergeable other) {return dittoServiceConfig.withFallback(other);}

    @Override
    public Config resolve() {return dittoServiceConfig.resolve();}

    @Override
    public Config resolve(final ConfigResolveOptions options) {return dittoServiceConfig.resolve(options);}

    @Override
    public boolean isResolved() {return dittoServiceConfig.isResolved();}

    @Override
    public Config resolveWith(final Config source) {return dittoServiceConfig.resolveWith(source);}

    @Override
    public Config resolveWith(final Config source,
            final ConfigResolveOptions options) {return dittoServiceConfig.resolveWith(source, options);}

    @Override
    public void checkValid(final Config reference, final String... restrictToPaths) {
        dittoServiceConfig.checkValid(reference, restrictToPaths);
    }

    @Override
    public boolean hasPath(final String path) {return dittoServiceConfig.hasPath(path);}

    @Override
    public boolean hasPathOrNull(final String path) {return dittoServiceConfig.hasPathOrNull(path);}

    @Override
    public boolean isEmpty() {return dittoServiceConfig.isEmpty();}

    @Override
    public Set<Map.Entry<String, ConfigValue>> entrySet() {return dittoServiceConfig.entrySet();}

    @Override
    public boolean getIsNull(final String path) {return dittoServiceConfig.getIsNull(path);}

    @Override
    public boolean getBoolean(final String path) {return dittoServiceConfig.getBoolean(path);}

    @Override
    public Number getNumber(final String path) {return dittoServiceConfig.getNumber(path);}

    @Override
    public int getInt(final String path) {return dittoServiceConfig.getInt(path);}

    @Override
    public long getLong(final String path) {return dittoServiceConfig.getLong(path);}

    @Override
    public double getDouble(final String path) {return dittoServiceConfig.getDouble(path);}

    @Override
    public String getString(final String path) {return dittoServiceConfig.getString(path);}

    @Override
    public <T extends Enum<T>> T getEnum(final Class<T> enumClass,
            final String path) {return dittoServiceConfig.getEnum(enumClass, path);}

    @Override
    public ConfigObject getObject(final String path) {return dittoServiceConfig.getObject(path);}

    @Override
    public Config getConfig(final String path) {return dittoServiceConfig.getConfig(path);}

    @Override
    public Object getAnyRef(final String path) {return dittoServiceConfig.getAnyRef(path);}

    @Override
    public ConfigValue getValue(final String path) {return dittoServiceConfig.getValue(path);}

    @Override
    public Long getBytes(final String path) {return dittoServiceConfig.getBytes(path);}

    @Override
    public ConfigMemorySize getMemorySize(final String path) {return dittoServiceConfig.getMemorySize(path);}

    @Override
    public Long getMilliseconds(final String path) {return dittoServiceConfig.getMilliseconds(path);}

    @Override
    public Long getNanoseconds(final String path) {return dittoServiceConfig.getNanoseconds(path);}

    @Override
    public long getDuration(final String path, final TimeUnit unit) {return dittoServiceConfig.getDuration(path, unit);}

    @Override
    public Duration getDuration(final String path) {return dittoServiceConfig.getDuration(path);}

    @Override
    public Period getPeriod(final String path) {return dittoServiceConfig.getPeriod(path);}

    @Override
    public TemporalAmount getTemporal(final String path) {return dittoServiceConfig.getTemporal(path);}

    @Override
    public ConfigList getList(final String path) {return dittoServiceConfig.getList(path);}

    @Override
    public List<Boolean> getBooleanList(final String path) {return dittoServiceConfig.getBooleanList(path);}

    @Override
    public List<Number> getNumberList(final String path) {return dittoServiceConfig.getNumberList(path);}

    @Override
    public List<Integer> getIntList(final String path) {return dittoServiceConfig.getIntList(path);}

    @Override
    public List<Long> getLongList(final String path) {return dittoServiceConfig.getLongList(path);}

    @Override
    public List<Double> getDoubleList(final String path) {return dittoServiceConfig.getDoubleList(path);}

    @Override
    public List<String> getStringList(final String path) {return dittoServiceConfig.getStringList(path);}

    @Override
    public <T extends Enum<T>> List<T> getEnumList(final Class<T> enumClass,
            final String path) {return dittoServiceConfig.getEnumList(enumClass, path);}

    @Override
    public List<? extends ConfigObject> getObjectList(final String path) {
        return dittoServiceConfig.getObjectList(path);
    }

    @Override
    public List<? extends Config> getConfigList(final String path) {return dittoServiceConfig.getConfigList(path);}

    @Override
    public List<? extends Object> getAnyRefList(final String path) {return dittoServiceConfig.getAnyRefList(path);}

    @Override
    public List<Long> getBytesList(final String path) {return dittoServiceConfig.getBytesList(path);}

    @Override
    public List<ConfigMemorySize> getMemorySizeList(final String path) {
        return dittoServiceConfig.getMemorySizeList(path);
    }

    @Override
    public List<Long> getMillisecondsList(final String path) {return dittoServiceConfig.getMillisecondsList(path);}

    @Override
    public List<Long> getNanosecondsList(final String path) {return dittoServiceConfig.getNanosecondsList(path);}

    @Override
    public List<Long> getDurationList(final String path,
            final TimeUnit unit) {return dittoServiceConfig.getDurationList(path, unit);}

    @Override
    public List<Duration> getDurationList(final String path) {return dittoServiceConfig.getDurationList(path);}

    @Override
    public Config withOnlyPath(final String path) {return dittoServiceConfig.withOnlyPath(path);}

    @Override
    public Config withoutPath(final String path) {return dittoServiceConfig.withoutPath(path);}

    @Override
    public Config atPath(final String path) {return dittoServiceConfig.atPath(path);}

    @Override
    public Config atKey(final String key) {return dittoServiceConfig.atKey(key);}

    @Override
    public Config withValue(final String path, final ConfigValue value) {
        return dittoServiceConfig.withValue(path, value);
    }

    @Override
    public String getConfigPath() {
        return dittoServiceConfig.getConfigPath();
    }

    @Override
    public MongoDbConfig getMongoDbConfig() {
        return mongoDbConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoServiceWithMongoDbConfig that = (DittoServiceWithMongoDbConfig) o;
        return dittoServiceConfig.equals(that.dittoServiceConfig) &&
                mongoDbConfig.equals(that.mongoDbConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dittoServiceConfig, mongoDbConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dittoServiceConfig=" + dittoServiceConfig +
                ", mongoDbConfig=" + mongoDbConfig +
                "]";
    }

}
