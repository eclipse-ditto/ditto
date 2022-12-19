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
import com.typesafe.config.ConfigValueType;

/**
 * This class is the default implementation of {@link ScopedConfig}.
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
     * Returns a new instance of {@code DefaultScopedConfig} based on the given config in scoped {@code "ditto"}.
     *
     * @param originalConfig the original Config which is supposed to provide a nested Config at {@code "ditto"}.
     * @return the instance.
     * @throws DittoConfigError if any argument is {@code null} or if the value of {@code originalConfig} at
     * {@code "ditto"} is either missing or not of type {@link com.typesafe.config.ConfigValueType#OBJECT}.
     */
    public static DefaultScopedConfig dittoScoped(final Config originalConfig) {
        return newInstance(originalConfig, ScopedConfig.DITTO_SCOPE);
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
        if (originalConfig instanceof ScopedConfig) {
            return newInstance((ScopedConfig) originalConfig, configPath);
        }

        validateArgument(originalConfig, "original Config");
        validateConfigPath(configPath);

        return new DefaultScopedConfig(tryToGetAsConfig(originalConfig, configPath), configPath);
    }

    private static void validateArgument(final Object argument, final String argumentDescription) {
        try {
            checkNotNull(argument, argumentDescription);
        } catch (final NullPointerException e) {
            throw new DittoConfigError(e);
        }
    }

    private static void validateConfigPath(final String configPath) {
        validateArgument(configPath, "config path");
    }

    private static Config tryToGetAsConfig(final Config originalConfig, final String configPath) {
        try {
            return originalConfig.getConfig(configPath);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get nested Config at <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, configPath), e);
        }
    }

    /**
     * Returns a new instance of {@code DefaultScopedConfig} based on the given arguments.
     * This method differs from {@link #newInstance(com.typesafe.config.Config, String)} in a way that it creates a
     * qualified* config path with the help of
     * {@link ScopedConfig#getConfigPath()} of the given ScopedConfig.
     *
     * @param originalConfig the original Config which is supposed to provide a nested Config at {@code configPath}.
     * @param configPath the path which points to the nested Config to be returned as result of this method.
     * @return the instance.
     * @throws DittoConfigError if any argument is {@code null} or if the value of {@code originalConfig} at
     * {@code configPath} is either missing or not of type {@link com.typesafe.config.ConfigValueType#OBJECT}.
     */
    public static DefaultScopedConfig newInstance(final ScopedConfig originalConfig, final String configPath) {
        validateArgument(originalConfig, "original ScopedConfig");
        validateConfigPath(configPath);

        final String qualifiedConfigPath = appendToConfigPath(originalConfig.getConfigPath(), configPath);
        return new DefaultScopedConfig(tryToGetAsConfig(originalConfig, configPath), qualifiedConfigPath);
    }

    private static String appendToConfigPath(final String configPath, final String toBeAppended) {
        return configPath + "." + toBeAppended;
    }

    /**
     * Returns a new empty instance of {@code DefaultScopedConfig} based on the given config path.
     *
     * @param configPath the path which points to the nested empty Config.
     * @return the instance.
     * @throws DittoConfigError if {@code configPath} is {@code null}.
     */
    public static DefaultScopedConfig empty(final String configPath) {
        validateConfigPath(configPath);
        return new DefaultScopedConfig(ConfigFactory.empty(), configPath);
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
        return new DefaultScopedConfig(config.withFallback(other), configPath);
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
        try {
            return config.getIsNull(path);
        } catch (final ConfigException.Missing e) {
            final var msgPattern = "Failed to check whether the value at path <{0}> is null!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public boolean getBoolean(final String path) {
        try {
            return config.getBoolean(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get boolean value for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public Number getNumber(final String path) {
        try {
            return config.getNumber(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get Number for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public int getInt(final String path) {
        try {
            return tryToGetIntValue(path);
        } catch(final ConfigException.Missing | ConfigException.WrongType | NumberFormatException e) {
            final var msgPattern = "Failed to get int value for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    private int tryToGetIntValue(final String path) {
        try {
            return config.getInt(path);
        } catch (final ConfigException.WrongType e) {
            final var configValue = config.getValue(path);
            if (ConfigValueType.STRING == configValue.valueType()) {
                return Integer.parseInt(String.valueOf(configValue.unwrapped()));
            }
            throw e;
        }
    }

    @Override
    public long getLong(final String path) {
        try {
            return tryToGetLongValue(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType | NumberFormatException e) {
            final var msgPattern = "Failed to get long value for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    private long tryToGetLongValue(final String path) {
        try {
            return config.getLong(path);
        } catch (final ConfigException.WrongType e) {
            final var configValue = config.getValue(path);
            if (ConfigValueType.STRING == configValue.valueType()) {
                return Long.parseLong(String.valueOf(configValue.unwrapped()));
            }
            throw e;
        }
    }

    @Override
    public double getDouble(final String path) {
        try {
            return tryToGetDoubleValue(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType | NumberFormatException e) {
            final var msgPattern = "Failed to get double value for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    private double tryToGetDoubleValue(final String path) {
        try {
            return config.getDouble(path);
        } catch (final ConfigException.WrongType e) {
            final var configValue = config.getValue(path);
            if (ConfigValueType.STRING == configValue.valueType()) {
                return Double.parseDouble(String.valueOf(configValue.unwrapped()));
            }
            throw e;
        }
    }

    @Override
    public String getString(final String path) {
        try {
            return config.getString(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get String value for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public <T extends Enum<T>> T getEnum(final Class<T> enumClass, final String path) {
        try {
            return config.getEnum(enumClass, path);
        } catch (final ConfigException.Missing | ConfigException.WrongType | ConfigException.BadValue e) {
            final var msgPattern = "Failed to get Enum for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public ConfigObject getObject(final String path) {
        try {
            return config.getObject(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get ConfigObject for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public Config getConfig(final String path) {
        try {
            return config.getConfig(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get Config for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public Object getAnyRef(final String path) {
        try {
            return config.getAnyRef(path);
        } catch (final ConfigException.Missing e) {
            final var msgPattern = "Failed to get the unwrapped Java object for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public ConfigValue getValue(final String path) {
        try {
            return config.getValue(path);
        } catch (final ConfigException.Missing e) {
            final var msgPattern = "Failed to get ConfigValue for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public Long getBytes(final String path) {
        try {
            return config.getBytes(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get long value for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public ConfigMemorySize getMemorySize(final String path) {
        try {
            return config.getMemorySize(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get memory size for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    @Deprecated(forRemoval = false) // can't be deleted as we inherit this @Deprecated flag
    public Long getMilliseconds(final String path) {
        return config.getMilliseconds(path);
    }

    @Override
    @Deprecated(forRemoval = false) // can't be deleted as we inherit this @Deprecated flag
    public Long getNanoseconds(final String path) {
        return config.getNanoseconds(path);
    }

    @Override
    public long getDuration(final String path, final TimeUnit unit) {
        try {
            return config.getDuration(path, unit);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get duration as long value for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public Duration getDuration(final String path) {
        try {
            return config.getDuration(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get Duration value for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public Period getPeriod(final String path) {
        try {
            return config.getPeriod(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType | ConfigException.BadValue e) {
            final var msgPattern = "Failed to get Period for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public TemporalAmount getTemporal(final String path) {
        try {
            return config.getTemporal(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType | ConfigException.BadValue e) {
            final var msgPattern = "Failed to get TemporalAmount for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public ConfigList getList(final String path) {
        try {
            return config.getList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get ConfigList for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<Boolean> getBooleanList(final String path) {
        try {
            return config.getBooleanList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of boolean values for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<Number> getNumberList(final String path) {
        try {
            return config.getNumberList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of Numbers for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<Integer> getIntList(final String path) {
        try {
            return config.getIntList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of int values for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<Long> getLongList(final String path) {
        try {
            return config.getLongList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of long values for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<Double> getDoubleList(final String path) {
        try {
            return config.getDoubleList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of double values for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<String> getStringList(final String path) {
        try {
            return config.getStringList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of String values for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public <T extends Enum<T>> List<T> getEnumList(final Class<T> enumClass, final String path) {
        try {
            return config.getEnumList(enumClass, path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of Enums for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<? extends ConfigObject> getObjectList(final String path) {
        try {
            return config.getObjectList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of ConfigObjects for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<? extends Config> getConfigList(final String path) {
        try {
            return config.getConfigList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of Configs for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<? extends Object> getAnyRefList(final String path) {
        try {
            return config.getAnyRefList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List with elements of any kind for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<Long> getBytesList(final String path) {
        try {
            return config.getBytesList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of byte sizes for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<ConfigMemorySize> getMemorySizeList(final String path) {
        try {
            return config.getMemorySizeList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of memory sizes for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    @Deprecated(forRemoval = false) // can't be deleted as we inherit this @Deprecated flag
    public List<Long> getMillisecondsList(final String path) {
        return config.getMillisecondsList(path);
    }

    @Override
    @Deprecated(forRemoval = false) // can't be deleted as we inherit this @Deprecated flag
    public List<Long> getNanosecondsList(final String path) {
        return config.getNanosecondsList(path);
    }

    @Override
    public List<Long> getDurationList(final String path, final TimeUnit unit) {
        try {
            return config.getDurationList(path, unit);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of duration long values for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
    }

    @Override
    public List<Duration> getDurationList(final String path) {
        try {
            return config.getDurationList(path);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            final var msgPattern = "Failed to get List of Durations for path <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, appendToConfigPath(path)), e);
        }
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
        return configPath;
    }

    private String appendToConfigPath(final String toBeAppended) {
        return getConfigPath() + "." + toBeAppended;
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
