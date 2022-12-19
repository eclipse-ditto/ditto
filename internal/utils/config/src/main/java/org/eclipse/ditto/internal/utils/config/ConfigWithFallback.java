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
package org.eclipse.ditto.internal.utils.config;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigMergeable;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;

/**
 * This implementation of {@link ScopedConfig} is guaranteed to contain only the settings at a particular path config
 * path and fallback values for not originally configured settings.
 */
@Immutable
public final class ConfigWithFallback implements ScopedConfig, ConfigMergeable {

    private final Config baseConfig;
    private final String configPath;

    private ConfigWithFallback(final Config theBaseConfig, final String theConfigPath) {
        baseConfig = theBaseConfig;
        configPath = theConfigPath;
    }

    /**
     * Returns a new instance of {@code ConfigWithFallback} based on the given arguments.
     *
     * @param originalConfig the original Config which is supposed to provide a nested Config at {@code configPath} and
     * which will be extended by the fall back config based on {@code fallBackValues}.
     * @param configPath the path which points to the nested Config to be returned as result of this method.
     * @param fallBackValues base for the fall back which is applied to the original Config within
     * {@code originalConfig} at {@code configPath}.
     * @return the instance.
     * @throws DittoConfigError if any argument is {@code null} or if the value of {@code originalConfig} at
     * {@code configPath} is not of type {@link com.typesafe.config.ConfigValueType#OBJECT}.
     */
    public static ConfigWithFallback newInstance(final Config originalConfig, final String configPath,
            final KnownConfigValue[] fallBackValues) {

        validateArgument(originalConfig, "original Config");
        validateArgument(configPath, "config path");
        validateArgument(fallBackValues, "fall-back values");

        Config baseConfig;
        if (originalConfig.hasPath(configPath)) {
            baseConfig = DefaultScopedConfig.newInstance(originalConfig, configPath);
        } else {
            baseConfig = DefaultScopedConfig.empty(configPath);
        }
        if (0 < fallBackValues.length) {
            baseConfig = baseConfig.withFallback(arrayToConfig(fallBackValues));
        }

        String configPathToUse = configPath;
        if (originalConfig instanceof ScopedConfig) {
            final WithConfigPath scopedConfig = (WithConfigPath) originalConfig;
            configPathToUse = scopedConfig.getConfigPath() + "." + configPath;
        }

        return new ConfigWithFallback(baseConfig, configPathToUse);
    }

    /**
     * Returns a new instance of {@code ConfigWithFallback} based on the given arguments.
     *
     * @param originalConfig the original Config which is supposed to provide a nested Config at {@code configPath} and
     * which will be extended by the fall back config based on {@code fallBackValues}.
     * @param fallBackValues base for the fall back which is applied to the original Config within
     * {@code originalConfig} at {@code configPath}.
     * @return the instance.
     * @throws DittoConfigError if any argument is {@code null} or if the value of {@code originalConfig} at
     * {@code configPath} is not of type {@link com.typesafe.config.ConfigValueType#OBJECT}.
     */
    public static ConfigWithFallback newInstance(final Config originalConfig, final KnownConfigValue[] fallBackValues) {

        validateArgument(originalConfig, "original Config");
        validateArgument(fallBackValues, "fall-back values");

        var baseConfig = originalConfig;
        if (0 < fallBackValues.length) {
            baseConfig = baseConfig.withFallback(arrayToConfig(fallBackValues));
        }

        return new ConfigWithFallback(baseConfig, "");
    }

    private static void validateArgument(final Object argument, final String argumentDescription) {
        try {
            checkNotNull(argument, argumentDescription);
        } catch (final NullPointerException e) {
            throw new DittoConfigError(e);
        }
    }

    private static Config arrayToConfig(final KnownConfigValue[] knownConfigValues) {
        final Map<String, Object> fallbackValues = new HashMap<>(knownConfigValues.length);
        for (final KnownConfigValue knownConfigValue : knownConfigValues) {
            final Object fallbackValue = knownConfigValue.getDefaultValue();
            if (fallbackValue instanceof JsonObject jsonObject) {
                final Map<String, JsonValue> fallbackMap = jsonObject.stream()
                        .collect(Collectors.toMap(f -> f.getKey().toString(), JsonField::getValue));
                fallbackValues.put(knownConfigValue.getConfigPath(), fallbackMap);
            } else {
                fallbackValues.put(knownConfigValue.getConfigPath(), fallbackValue);
            }
        }
        return ConfigFactory.parseMap(fallbackValues);
    }

    private static Map<String, JsonValue> getJsonObjectAsMap(final JsonObject jsonObject) {
        return jsonObject.stream().collect(Collectors.toMap(JsonField::getKeyName, JsonField::getValue));
    }

    @Override
    public ConfigObject root() {
        return baseConfig.root();
    }

    @Override
    public ConfigOrigin origin() {
        return baseConfig.origin();
    }

    @Override
    public Config withFallback(final ConfigMergeable other) {
        return baseConfig.withFallback(other);
    }

    @Override
    public Config resolve() {
        return baseConfig.resolve();
    }

    @Override
    public Config resolve(final ConfigResolveOptions options) {
        return baseConfig.resolve(options);
    }

    @Override
    public boolean isResolved() {
        return baseConfig.isResolved();
    }

    @Override
    public Config resolveWith(final Config source) {
        return baseConfig.resolveWith(source);
    }

    @Override
    public Config resolveWith(final Config source, final ConfigResolveOptions options) {
        return baseConfig.resolveWith(source, options);
    }

    @Override
    public void checkValid(final Config reference, final String... restrictToPaths) {
        baseConfig.checkValid(reference, restrictToPaths);
    }

    @Override
    public boolean hasPath(final String path) {
        return baseConfig.hasPath(path);
    }

    @Override
    public boolean hasPathOrNull(final String path) {
        return baseConfig.hasPathOrNull(path);
    }

    @Override
    public boolean isEmpty() {
        return baseConfig.isEmpty();
    }

    @Override
    public Set<Map.Entry<String, ConfigValue>> entrySet() {
        return baseConfig.entrySet();
    }

    @Override
    public boolean getIsNull(final String path) {
        return baseConfig.getIsNull(path);
    }

    @Override
    public boolean getBoolean(final String path) {
        return baseConfig.getBoolean(path);
    }

    @Override
    public Number getNumber(final String path) {
        return baseConfig.getNumber(path);
    }

    @Override
    public int getInt(final String path) {
        return baseConfig.getInt(path);
    }

    @Override
    public long getLong(final String path) {
        return baseConfig.getLong(path);
    }

    @Override
    public double getDouble(final String path) {
        return baseConfig.getDouble(path);
    }

    @Override
    public String getString(final String path) {
        return baseConfig.getString(path);
    }

    /**
     * Returns the string value at the specified path expression.
     * If the value is absent or {@code null} this method returns {@code null} instead of throwing
     * {@link com.typesafe.config.ConfigException.Missing}.
     *
     * @param withConfigPath provides the path expression.
     * @return the string value at the requested path or {@code null}.
     * @throws DittoConfigError if value is not convertible to a string.
     */
    @Nullable
    public String getStringOrNull(final WithConfigPath withConfigPath) {
        if (baseConfig.hasPath(withConfigPath.getConfigPath())) {
            if (baseConfig.getIsNull(withConfigPath.getConfigPath())) {
                return null;
            }
            return baseConfig.getString(withConfigPath.getConfigPath());
        }

        return null;
    }

    @Override
    public <T extends Enum<T>> T getEnum(final Class<T> enumClass, final String path) {
        return baseConfig.getEnum(enumClass, path);
    }

    @Override
    public ConfigObject getObject(final String path) {
        return baseConfig.getObject(path);
    }

    @Override
    public Config getConfig(final String path) {
        return baseConfig.getConfig(path);
    }

    @Override
    public Object getAnyRef(final String path) {
        return baseConfig.getAnyRef(path);
    }

    @Override
    public ConfigValue getValue(final String path) {
        return baseConfig.getValue(path);
    }

    @Override
    public Long getBytes(final String path) {
        return baseConfig.getBytes(path);
    }

    @Override
    public ConfigMemorySize getMemorySize(final String path) {
        return baseConfig.getMemorySize(path);
    }

    @Override
    @Deprecated(forRemoval = false) // can't be deleted as we inherit this @Deprecated flag
    public Long getMilliseconds(final String path) {
        return baseConfig.getMilliseconds(path);
    }

    @Override
    @Deprecated(forRemoval = false) // can't be deleted as we inherit this @Deprecated flag
    public Long getNanoseconds(final String path) {
        return baseConfig.getNanoseconds(path);
    }

    @Override
    public long getDuration(final String path, final TimeUnit unit) {
        return baseConfig.getDuration(path, unit);
    }

    @Override
    public Duration getDuration(final String path) {
        return baseConfig.getDuration(path);
    }

    @Override
    public Period getPeriod(final String path) {
        return baseConfig.getPeriod(path);
    }

    @Override
    public TemporalAmount getTemporal(final String path) {
        return baseConfig.getTemporal(path);
    }

    @Override
    public ConfigList getList(final String path) {
        return baseConfig.getList(path);
    }

    @Override
    public List<Boolean> getBooleanList(final String path) {
        return baseConfig.getBooleanList(path);
    }

    @Override
    public List<Number> getNumberList(final String path) {
        return baseConfig.getNumberList(path);
    }

    @Override
    public List<Integer> getIntList(final String path) {
        return baseConfig.getIntList(path);
    }

    @Override
    public List<Long> getLongList(final String path) {
        return baseConfig.getLongList(path);
    }

    @Override
    public List<Double> getDoubleList(final String path) {
        return baseConfig.getDoubleList(path);
    }

    @Override
    public List<String> getStringList(final String path) {
        return baseConfig.getStringList(path);
    }

    @Override
    public <T extends Enum<T>> List<T> getEnumList(final Class<T> enumClass,
            final String path) {
        return baseConfig.getEnumList(enumClass, path);
    }

    @Override
    public List<? extends ConfigObject> getObjectList(final String path) {
        return baseConfig.getObjectList(path);
    }

    @Override
    public List<? extends Config> getConfigList(final String path) {
        return baseConfig.getConfigList(path);
    }

    @Override
    public List<? extends Object> getAnyRefList(final String path) {
        return baseConfig.getAnyRefList(path);
    }

    @Override
    public List<Long> getBytesList(final String path) {
        return baseConfig.getBytesList(path);
    }

    @Override
    public List<ConfigMemorySize> getMemorySizeList(final String path) {
        return baseConfig.getMemorySizeList(path);
    }

    @Override
    @Deprecated(forRemoval = false) // can't be deleted as we inherit this @Deprecated flag
    public List<Long> getMillisecondsList(final String path) {
        return baseConfig.getMillisecondsList(path);
    }

    @Override
    @Deprecated(forRemoval = false) // can't be deleted as we inherit this @Deprecated flag
    public List<Long> getNanosecondsList(final String path) {
        return baseConfig.getNanosecondsList(path);
    }

    @Override
    public List<Long> getDurationList(final String path, final TimeUnit unit) {
        return baseConfig.getDurationList(path, unit);
    }

    @Override
    public List<Duration> getDurationList(final String path) {
        return baseConfig.getDurationList(path);
    }

    @Override
    public Config withOnlyPath(final String path) {
        return baseConfig.withOnlyPath(path);
    }

    @Override
    public Config withoutPath(final String path) {
        return baseConfig.withoutPath(path);
    }

    @Override
    public Config atPath(final String path) {
        return baseConfig.atPath(path);
    }

    @Override
    public Config atKey(final String key) {
        return baseConfig.atKey(key);
    }

    @Override
    public Config withValue(final String path, final ConfigValue value) {
        return baseConfig.withValue(path, value);
    }

    @Override
    public String getConfigPath() {
        return configPath;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConfigWithFallback that = (ConfigWithFallback) o;
        return baseConfig.equals(that.baseConfig) && configPath.equals(that.configPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "baseConfig=" + baseConfig +
                ", configPath=" + configPath +
                "]";
    }

}
