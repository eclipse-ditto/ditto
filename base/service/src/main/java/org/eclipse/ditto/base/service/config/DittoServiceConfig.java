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
package org.eclipse.ditto.base.service.config;

import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.http.DefaultHttpConfig;
import org.eclipse.ditto.base.service.config.http.HttpConfig;
import org.eclipse.ditto.base.service.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.metrics.config.DefaultMetricsConfig;
import org.eclipse.ditto.internal.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.internal.utils.tracing.config.DefaultTracingConfig;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigMergeable;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;

/**
 * This class provides a common implementation of {@link ServiceSpecificConfig}
 * and is supposed to be used as delegation target for the methods of {@code ServiceSpecificConfig} within the
 * dedicated service-specific implementations of the interface.
 * Furthermore it is a {@link org.eclipse.ditto.internal.utils.config.ScopedConfig} to act as a source for additional
 * config settings.
 */
@Immutable
public final class DittoServiceConfig implements ScopedConfig, ServiceSpecificConfig {

    private final DefaultScopedConfig serviceScopedConfig;

    private final DefaultLimitsConfig limitsConfig;
    private final DefaultClusterConfig clusterConfig;
    private final DefaultHttpConfig httpConfig;
    private final DefaultMetricsConfig metricsConfig;
    private final DefaultTracingConfig tracingConfig;

    private DittoServiceConfig(final ScopedConfig dittoScopedConfig, final String configPath) {
        serviceScopedConfig = DefaultScopedConfig.newInstance(dittoScopedConfig, configPath);
        limitsConfig = DefaultLimitsConfig.of(dittoScopedConfig);
        clusterConfig = DefaultClusterConfig.of(dittoScopedConfig);
        httpConfig = DefaultHttpConfig.of(dittoScopedConfig);
        metricsConfig = DefaultMetricsConfig.of(dittoScopedConfig);
        tracingConfig = DefaultTracingConfig.of(dittoScopedConfig);
    }

    /**
     * Returns an instance of {@code DittoServiceConfig} based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the service config at the {@code "ditto"} config
     * path.
     * @param configPath the path of the nested Config within {@code config} that provides the service specific config
     * settings.
     * Mostly this value is the service name.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if
     * <ul>
     * <li>{@code config} is {@code null},</li>
     * <li>{@code configPath} is {@code null} or if</li>
     * <li>{@code config} did not contain a nested Config at path {@code configPath}.</li>
     * </ul>
     */
    public static DittoServiceConfig of(final ScopedConfig dittoScopedConfig, final String configPath) {
        if (null == dittoScopedConfig) {
            throw new DittoConfigError("The argument 'scopedConfig' must not be null!");
        }
        return new DittoServiceConfig(dittoScopedConfig, configPath);
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
    public TracingConfig getTracingConfig() {
        return tracingConfig;
    }

    @Override
    public ConfigObject root() {
        return serviceScopedConfig.root();
    }

    @Override
    public ConfigOrigin origin() {
        return serviceScopedConfig.origin();
    }

    @Override
    public Config withFallback(final ConfigMergeable other) {
        return serviceScopedConfig.withFallback(other);
    }

    @Override
    public Config resolve() {
        return serviceScopedConfig.resolve();
    }

    @Override
    public Config resolve(final ConfigResolveOptions options) {
        return serviceScopedConfig.resolve(options);
    }

    @Override
    public boolean isResolved() {
        return serviceScopedConfig.isResolved();
    }

    @Override
    public Config resolveWith(final Config source) {
        return serviceScopedConfig.resolveWith(source);
    }

    @Override
    public Config resolveWith(final Config source, final ConfigResolveOptions options) {
        return serviceScopedConfig.resolveWith(source, options);
    }

    @Override
    public void checkValid(final Config reference, final String... restrictToPaths) {
        serviceScopedConfig.checkValid(reference, restrictToPaths);
    }

    @Override
    public boolean hasPath(final String path) {
        return serviceScopedConfig.hasPath(path);
    }

    @Override
    public boolean hasPathOrNull(final String path) {
        return serviceScopedConfig.hasPathOrNull(path);
    }

    @Override
    public boolean isEmpty() {
        return serviceScopedConfig.isEmpty();
    }

    @Override
    public Set<Map.Entry<String, ConfigValue>> entrySet() {
        return serviceScopedConfig.entrySet();
    }

    @Override
    public boolean getIsNull(final String path) {
        return serviceScopedConfig.getIsNull(path);
    }

    @Override
    public boolean getBoolean(final String path) {
        return serviceScopedConfig.getBoolean(path);
    }

    @Override
    public Number getNumber(final String path) {
        return serviceScopedConfig.getNumber(path);
    }

    @Override
    public int getInt(final String path) {
        return serviceScopedConfig.getInt(path);
    }

    @Override
    public long getLong(final String path) {
        return serviceScopedConfig.getLong(path);
    }

    @Override
    public double getDouble(final String path) {
        return serviceScopedConfig.getDouble(path);
    }

    @Override
    public String getString(final String path) {
        return serviceScopedConfig.getString(path);
    }

    @Override
    public <T extends Enum<T>> T getEnum(final Class<T> enumClass, final String path) {
        return serviceScopedConfig.getEnum(enumClass, path);
    }

    @Override
    public ConfigObject getObject(final String path) {
        return serviceScopedConfig.getObject(path);
    }

    @Override
    public Config getConfig(final String path) {
        return serviceScopedConfig.getConfig(path);
    }

    @Override
    public Object getAnyRef(final String path) {
        return serviceScopedConfig.getAnyRef(path);
    }

    @Override
    public ConfigValue getValue(final String path) {
        return serviceScopedConfig.getValue(path);
    }

    @Override
    public Long getBytes(final String path) {
        return serviceScopedConfig.getBytes(path);
    }

    @Override
    public ConfigMemorySize getMemorySize(final String path) {
        return serviceScopedConfig.getMemorySize(path);
    }

    @Override
    public Long getMilliseconds(final String path) {
        return serviceScopedConfig.getMilliseconds(path);
    }

    @Override
    public Long getNanoseconds(final String path) {
        return serviceScopedConfig.getNanoseconds(path);
    }

    @Override
    public long getDuration(final String path, final TimeUnit unit) {
        return serviceScopedConfig.getDuration(path, unit);
    }

    @Override
    public Duration getDuration(final String path) {
        return serviceScopedConfig.getDuration(path);
    }

    @Override
    public Period getPeriod(final String path) {
        return serviceScopedConfig.getPeriod(path);
    }

    @Override
    public TemporalAmount getTemporal(final String path) {
        return serviceScopedConfig.getTemporal(path);
    }

    @Override
    public ConfigList getList(final String path) {
        return serviceScopedConfig.getList(path);
    }

    @Override
    public List<Boolean> getBooleanList(final String path) {
        return serviceScopedConfig.getBooleanList(path);
    }

    @Override
    public List<Number> getNumberList(final String path) {
        return serviceScopedConfig.getNumberList(path);
    }

    @Override
    public List<Integer> getIntList(final String path) {
        return serviceScopedConfig.getIntList(path);
    }

    @Override
    public List<Long> getLongList(final String path) {
        return serviceScopedConfig.getLongList(path);
    }

    @Override
    public List<Double> getDoubleList(final String path) {
        return serviceScopedConfig.getDoubleList(path);
    }

    @Override
    public List<String> getStringList(final String path) {
        return serviceScopedConfig.getStringList(path);
    }

    @Override
    public <T extends Enum<T>> List<T> getEnumList(final Class<T> enumClass, final String path) {
        return serviceScopedConfig.getEnumList(enumClass, path);
    }

    @Override
    public List<? extends ConfigObject> getObjectList(final String path) {
        return serviceScopedConfig.getObjectList(path);
    }

    @Override
    public List<? extends Config> getConfigList(final String path) {
        return serviceScopedConfig.getConfigList(path);
    }

    @Override
    public List<? extends Object> getAnyRefList(final String path) {
        return serviceScopedConfig.getAnyRefList(path);
    }

    @Override
    public List<Long> getBytesList(final String path) {
        return serviceScopedConfig.getBytesList(path);
    }

    @Override
    public List<ConfigMemorySize> getMemorySizeList(final String path) {
        return serviceScopedConfig.getMemorySizeList(path);
    }

    @Override
    public List<Long> getMillisecondsList(final String path) {
        return serviceScopedConfig.getMillisecondsList(path);
    }

    @Override
    public List<Long> getNanosecondsList(final String path) {
        return serviceScopedConfig.getNanosecondsList(path);
    }

    @Override
    public List<Long> getDurationList(final String path, final TimeUnit unit) {
        return serviceScopedConfig.getDurationList(path, unit);
    }

    @Override
    public List<Duration> getDurationList(final String path) {
        return serviceScopedConfig.getDurationList(path);
    }

    @Override
    public Config withOnlyPath(final String path) {
        return serviceScopedConfig.withOnlyPath(path);
    }

    @Override
    public Config withoutPath(final String path) {
        return serviceScopedConfig.withoutPath(path);
    }

    @Override
    public Config atPath(final String path) {
        return serviceScopedConfig.atPath(path);
    }

    @Override
    public Config atKey(final String key) {
        return serviceScopedConfig.atKey(key);
    }

    @Override
    public Config withValue(final String path, final ConfigValue value) {
        return serviceScopedConfig.withValue(path, value);
    }

    @Override
    public String getConfigPath() {
        return serviceScopedConfig.getConfigPath();
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
                tracingConfig.equals(that.tracingConfig) &&
                serviceScopedConfig.equals(that.serviceScopedConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limitsConfig, clusterConfig, httpConfig, metricsConfig, tracingConfig, serviceScopedConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "limitsConfig=" + limitsConfig +
                ", clusterConfig=" + clusterConfig +
                ", httpConfig=" + httpConfig +
                ", metricsConfig=" + metricsConfig +
                ", tracingConfig=" + tracingConfig +
                ", config=" + serviceScopedConfig +
                "]";
    }

}
