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
package org.eclipse.ditto.services.utils.config;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigMergeable;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValue;

/**
 * This class is the default implementation of {@link org.eclipse.ditto.services.utils.config.ScopedConfig}.
 */
@Immutable
public final class DefaultScopedConfig implements ScopedConfig {

    private final Config config;
    private final String configPath;

    private DefaultScopedConfig(final Config theConfig, final String theConfigPath) {
        config = theConfig;
        configPath = theConfigPath;
    }

    /**
     * Returns a new instance of {@code DefaultScopedConfig} based on the given arguments.
     *
     * @param originalConfig the original Config which is supposed to provide a nested Config at {@code configPath}.
     * @param configPath the path which points to the nested Config to be returned as result of this method.
     * @return the instance.
     * @throws DittoConfigError if any argument is {@code null} or if the value of {@code originalConfig} at
     * {@code configPath} is either missing or not of type {@link com.typesafe.config.ConfigValueType#OBJECT}.
     */
    public static DefaultScopedConfig newInstance(final Config originalConfig, final String configPath) {
        validateArgument(originalConfig, "original Config");
        validateArgument(configPath, "config path");

        return new DefaultScopedConfig(tryToGetAsConfig(originalConfig, configPath), configPath);
    }

    private static void validateArgument(final Object argument, final String argumentDescription) {
        try {
            checkNotNull(argument, argumentDescription);
        } catch (final NullPointerException e) {
            throw new DittoConfigError(e);
        }
    }

    private static Config tryToGetAsConfig(final Config originalConfig, final String configPath) {
        try {
            return originalConfig.getConfig(configPath);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final String msgPattern = "Failed to get nested Config at <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, configPath), e);
        }
    }

    /**
     * Returns a new empty instance of {@code DefaultScopedConfig} based on the given config path.
     *
     * @param configPath the path which points to the nested empty Config.
     * @return the instance.
     * @throws DittoConfigError if {@code configPath} is {@code null}.
     */
    public static DefaultScopedConfig empty(final String configPath) {
        validateArgument(configPath, "config path");
        return new DefaultScopedConfig(ConfigFactory.empty(), configPath);
    }

    @Override
    public ConfigObject root() {return config.root();}

    @Override
    public ConfigOrigin origin() {return config.origin();}

    @Override
    public Config withFallback(final ConfigMergeable other) {return config.withFallback(other);}

    @Override
    public Config resolve() {return config.resolve();}

    @Override
    public Config resolve(final ConfigResolveOptions options) {return config.resolve(options);}

    @Override
    public boolean isResolved() {return config.isResolved();}

    @Override
    public Config resolveWith(final Config source) {return config.resolveWith(source);}

    @Override
    public Config resolveWith(final Config source, final ConfigResolveOptions options) {
        return config.resolveWith(source, options);
    }

    @Override
    public void checkValid(final Config reference, final String... restrictToPaths) {
        config.checkValid(reference, restrictToPaths);
    }

    @Override
    public boolean hasPath(final String path) {return config.hasPath(path);}

    @Override
    public boolean hasPathOrNull(final String path) {return config.hasPathOrNull(path);}

    @Override
    public boolean isEmpty() {return config.isEmpty();}

    @Override
    public Set<Map.Entry<String, ConfigValue>> entrySet() {return config.entrySet();}

    @Override
    public boolean getIsNull(final String path) {return config.getIsNull(path);}

    @Override
    public boolean getBoolean(final String path) {return config.getBoolean(path);}

    @Override
    public Number getNumber(final String path) {return config.getNumber(path);}

    @Override
    public int getInt(final String path) {return config.getInt(path);}

    @Override
    public long getLong(final String path) {return config.getLong(path);}

    @Override
    public double getDouble(final String path) {return config.getDouble(path);}

    @Override
    public String getString(final String path) {return config.getString(path);}

    @Override
    public <T extends Enum<T>> T getEnum(final Class<T> enumClass, final String path) {
        return config.getEnum(enumClass, path);
    }

    @Override
    public ConfigObject getObject(final String path) {return config.getObject(path);}

    @Override
    public Config getConfig(final String path) {return config.getConfig(path);}

    @Override
    public Object getAnyRef(final String path) {return config.getAnyRef(path);}

    @Override
    public ConfigValue getValue(final String path) {return config.getValue(path);}

    @Override
    public Long getBytes(final String path) {return config.getBytes(path);}

    @Override
    public ConfigMemorySize getMemorySize(final String path) {return config.getMemorySize(path);}

    @Override
    @Deprecated
    public Long getMilliseconds(final String path) {return config.getMilliseconds(path);}

    @Override
    @Deprecated
    public Long getNanoseconds(final String path) {return config.getNanoseconds(path);}

    @Override
    public long getDuration(final String path, final TimeUnit unit) {return config.getDuration(path, unit);}

    @Override
    public Duration getDuration(final String path) {return config.getDuration(path);}

    @Override
    public Period getPeriod(final String path) {return config.getPeriod(path);}

    @Override
    public TemporalAmount getTemporal(final String path) {return config.getTemporal(path);}

    @Override
    public ConfigList getList(final String path) {return config.getList(path);}

    @Override
    public List<Boolean> getBooleanList(final String path) {return config.getBooleanList(path);}

    @Override
    public List<Number> getNumberList(final String path) {return config.getNumberList(path);}

    @Override
    public List<Integer> getIntList(final String path) {return config.getIntList(path);}

    @Override
    public List<Long> getLongList(final String path) {return config.getLongList(path);}

    @Override
    public List<Double> getDoubleList(final String path) {return config.getDoubleList(path);}

    @Override
    public List<String> getStringList(final String path) {return config.getStringList(path);}

    @Override
    public <T extends Enum<T>> List<T> getEnumList(final Class<T> enumClass,
            final String path) {return config.getEnumList(enumClass, path);}

    @Override
    public List<? extends ConfigObject> getObjectList(final String path) {return config.getObjectList(path);}

    @Override
    public List<? extends Config> getConfigList(final String path) {return config.getConfigList(path);}

    @Override
    public List<? extends Object> getAnyRefList(final String path) {return config.getAnyRefList(path);}

    @Override
    public List<Long> getBytesList(final String path) {return config.getBytesList(path);}

    @Override
    public List<ConfigMemorySize> getMemorySizeList(final String path) {return config.getMemorySizeList(path);}

    @Override
    @Deprecated
    public List<Long> getMillisecondsList(final String path) {return config.getMillisecondsList(path);}

    @Override
    @Deprecated
    public List<Long> getNanosecondsList(final String path) {return config.getNanosecondsList(path);}

    @Override
    public List<Long> getDurationList(final String path, final TimeUnit unit) {
        return config.getDurationList(path, unit);
    }

    @Override
    public List<Duration> getDurationList(final String path) {return config.getDurationList(path);}

    @Override
    public Config withOnlyPath(final String path) {return config.withOnlyPath(path);}

    @Override
    public Config withoutPath(final String path) {return config.withoutPath(path);}

    @Override
    public Config atPath(final String path) {return config.atPath(path);}

    @Override
    public Config atKey(final String key) {return config.atKey(key);}

    @Override
    public Config withValue(final String path, final ConfigValue value) {return config.withValue(path, value);}

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
        final DefaultScopedConfig that = (DefaultScopedConfig) o;
        return Objects.equals(config, that.config) && Objects.equals(configPath, that.configPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, configPath);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "config=" + config +
                ", configPath=" + configPath +
                "]";
    }

}
