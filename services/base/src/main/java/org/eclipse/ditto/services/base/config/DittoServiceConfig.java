/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.base.config;

import java.io.Serializable;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.http.DefaultHttpConfig;
import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.base.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.metrics.config.DefaultMetricsConfig;
import org.eclipse.ditto.services.utils.metrics.config.MetricsConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigMergeable;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;

/**
 * This class provides a common implementation of {@link org.eclipse.ditto.services.base.config.ServiceSpecificConfig}
 * and is supposed to be used as delegation target for the methods of {@code ServiceSpecificConfig} within the
 * dedicated service-specific implementations of the interface.
 * Furthermore it is a {@link org.eclipse.ditto.services.utils.config.ScopedConfig} to act as a source for additional
 * config settings.
 */
@Immutable
public final class DittoServiceConfig implements ScopedConfig, ServiceSpecificConfig, Serializable {

    private static final long serialVersionUID = -3055318635902386342L;

    private final ScopedConfig config;
    private final DefaultLimitsConfig limitsConfig;
    private final DefaultClusterConfig clusterConfig;
    private final DefaultHttpConfig httpConfig;
    private final DefaultMetricsConfig metricsConfig;

    private DittoServiceConfig(final ScopedConfig theConfig, final DefaultLimitsConfig theLimitsConfig) {
        config = theConfig;
        limitsConfig = theLimitsConfig;
        clusterConfig = DefaultClusterConfig.of(config);
        httpConfig = DefaultHttpConfig.of(config);
        metricsConfig = DefaultMetricsConfig.of(config);
    }

    /**
     * Returns an instance of {@code DittoServiceConfig} based on the settings of the specified Config.
     *
     * @param scopedConfig is supposed to provide the settings of the service at the appropriate config path.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config}
     * <ul>
     * <li>is {@code null} or</li>
     * <li>did not contain a nested Config at path {@code configPath}.</li>
     * </ul>
     */
    public static DittoServiceConfig of(final ScopedConfig scopedConfig) {
        if (null == scopedConfig) {
            throw new DittoConfigError("The argument 'scopedConfig' must not be null!");
        }
        return new DittoServiceConfig(scopedConfig, DefaultLimitsConfig.of(scopedConfig));
    }

    /**
     * Returns an instance of {@code DittoServiceConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the service config at the specified config path.
     * @param configPath the path of the nested Config within {@code config} that provides the service specific config
     * settings.
     * Mostly this value is the service name.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if
     * <ul>
     * <li>{@code config} is {@code null},</li>
     * <li>{@code configPath} is {@code null} or if</li>
     * <li>{@code config} did not contain a nested Config at path {@code configPath}.</li>
     * </ul>
     */
    public static DittoServiceConfig of(final Config config, final String configPath) {
        return of(DefaultScopedConfig.newInstance(config, configPath));
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return clusterConfig;
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
    public ConfigObject root() {
        return config.root();
    }

    @Override
    public ConfigOrigin origin() {
        return config.origin();
    }

    @Override
    public Config withFallback(final ConfigMergeable other) {
        return config.withFallback(other);
    }

    @Override
    public Config resolve() {
        return config.resolve();
    }

    @Override
    public Config resolve(final ConfigResolveOptions options) {
        return config.resolve(options);
    }

    @Override
    public boolean isResolved() {
        return config.isResolved();
    }

    @Override
    public Config resolveWith(final Config source) {
        return config.resolveWith(source);
    }

    @Override
    public Config resolveWith(final Config source, final ConfigResolveOptions options) {
        return config.resolveWith(source, options);
    }

    @Override
    public void checkValid(final Config reference, final String... restrictToPaths) {
        config.checkValid(reference, restrictToPaths);
    }

    @Override
    public boolean hasPath(final String path) {
        return config.hasPath(path);
    }

    @Override
    public boolean hasPathOrNull(final String path) {
        return config.hasPathOrNull(path);
    }

    @Override
    public boolean isEmpty() {
        return config.isEmpty();
    }

    @Override
    public Set<Map.Entry<String, ConfigValue>> entrySet() {
        return config.entrySet();
    }

    @Override
    public boolean getIsNull(final String path) {
        return config.getIsNull(path);
    }

    @Override
    public boolean getBoolean(final String path) {
        return config.getBoolean(path);
    }

    @Override
    public Number getNumber(final String path) {
        return config.getNumber(path);
    }

    @Override
    public int getInt(final String path) {
        return config.getInt(path);
    }

    @Override
    public long getLong(final String path) {
        return config.getLong(path);
    }

    @Override
    public double getDouble(final String path) {
        return config.getDouble(path);
    }

    @Override
    public String getString(final String path) {
        return config.getString(path);
    }

    @Override
    public <T extends Enum<T>> T getEnum(final Class<T> enumClass, final String path) {
        return config.getEnum(enumClass, path);
    }

    @Override
    public ConfigObject getObject(final String path) {
        return config.getObject(path);
    }

    @Override
    public Config getConfig(final String path) {
        return config.getConfig(path);
    }

    @Override
    public Object getAnyRef(final String path) {
        return config.getAnyRef(path);
    }

    @Override
    public ConfigValue getValue(final String path) {
        return config.getValue(path);
    }

    @Override
    public Long getBytes(final String path) {
        return config.getBytes(path);
    }

    @Override
    public ConfigMemorySize getMemorySize(final String path) {
        return config.getMemorySize(path);
    }

    @Override
    public Long getMilliseconds(final String path) {
        return config.getMilliseconds(path);
    }

    @Override
    public Long getNanoseconds(final String path) {
        return config.getNanoseconds(path);
    }

    @Override
    public long getDuration(final String path, final TimeUnit unit) {
        return config.getDuration(path, unit);
    }

    @Override
    public Duration getDuration(final String path) {
        return config.getDuration(path);
    }

    @Override
    public Period getPeriod(final String path) {
        return config.getPeriod(path);
    }

    @Override
    public TemporalAmount getTemporal(final String path) {
        return config.getTemporal(path);
    }

    @Override
    public ConfigList getList(final String path) {
        return config.getList(path);
    }

    @Override
    public List<Boolean> getBooleanList(final String path) {
        return config.getBooleanList(path);
    }

    @Override
    public List<Number> getNumberList(final String path) {
        return config.getNumberList(path);
    }

    @Override
    public List<Integer> getIntList(final String path) {
        return config.getIntList(path);
    }

    @Override
    public List<Long> getLongList(final String path) {
        return config.getLongList(path);
    }

    @Override
    public List<Double> getDoubleList(final String path) {
        return config.getDoubleList(path);
    }

    @Override
    public List<String> getStringList(final String path) {
        return config.getStringList(path);
    }

    @Override
    public <T extends Enum<T>> List<T> getEnumList(final Class<T> enumClass, final String path) {
        return config.getEnumList(enumClass, path);
    }

    @Override
    public List<? extends ConfigObject> getObjectList(final String path) {
        return config.getObjectList(path);
    }

    @Override
    public List<? extends Config> getConfigList(final String path) {
        return config.getConfigList(path);
    }

    @Override
    public List<? extends Object> getAnyRefList(final String path) {
        return config.getAnyRefList(path);
    }

    @Override
    public List<Long> getBytesList(final String path) {
        return config.getBytesList(path);
    }

    @Override
    public List<ConfigMemorySize> getMemorySizeList(final String path) {
        return config.getMemorySizeList(path);
    }

    @Override
    public List<Long> getMillisecondsList(final String path) {
        return config.getMillisecondsList(path);
    }

    @Override
    public List<Long> getNanosecondsList(final String path) {
        return config.getNanosecondsList(path);
    }

    @Override
    public List<Long> getDurationList(final String path, final TimeUnit unit) {
        return config.getDurationList(path, unit);
    }

    @Override
    public List<Duration> getDurationList(final String path) {
        return config.getDurationList(path);
    }

    @Override
    public Config withOnlyPath(final String path) {
        return config.withOnlyPath(path);
    }

    @Override
    public Config withoutPath(final String path) {
        return config.withoutPath(path);
    }

    @Override
    public Config atPath(final String path) {
        return config.atPath(path);
    }

    @Override
    public Config atKey(final String key) {
        return config.atKey(key);
    }

    @Override
    public Config withValue(final String path, final ConfigValue value) {
        return config.withValue(path, value);
    }

    @Override
    public String getConfigPath() {
        return config.getConfigPath();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoServiceConfig that = (DittoServiceConfig) o;
        return limitsConfig.equals(that.limitsConfig) &&
                clusterConfig.equals(that.clusterConfig) &&
                httpConfig.equals(that.httpConfig) &&
                metricsConfig.equals(that.metricsConfig) &&
                config.equals(that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limitsConfig, clusterConfig, httpConfig, metricsConfig, config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "limitsConfig=" + limitsConfig +
                ", clusterConfig=" + clusterConfig +
                ", httpConfig=" + httpConfig +
                ", metricsConfig=" + metricsConfig +
                ", config=" + config +
                "]";
    }

}
