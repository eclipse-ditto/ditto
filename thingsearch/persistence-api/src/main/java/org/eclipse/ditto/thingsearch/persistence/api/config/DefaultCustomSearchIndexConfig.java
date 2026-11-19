/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.api.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

/**
 * Default implementation of {@link CustomSearchIndexConfig}.
 */
@Immutable
public final class DefaultCustomSearchIndexConfig implements CustomSearchIndexConfig {

    private final String name;
    private final List<CustomSearchIndexFieldConfig> fields;

    private DefaultCustomSearchIndexConfig(final String name, final ConfigWithFallback configWithFallback) {
        this.name = name;
        if (this.name.isEmpty()) {
            throw new IllegalArgumentException("Index name must not be empty");
        }

        final ConfigList fieldsConfig = configWithFallback.getList(
                CustomSearchIndexConfigValue.FIELDS.getConfigPath());
        this.fields = fieldsConfig.stream()
                .map(DefaultCustomSearchIndexConfig::toFieldConfig)
                .collect(Collectors.toUnmodifiableList());

        if (this.fields.isEmpty()) {
            throw new IllegalArgumentException("Index '" + name + "' must have at least one field");
        }
    }

    private static CustomSearchIndexFieldConfig toFieldConfig(final ConfigValue configValue) {
        final Config fieldConfig = configValue.atKey("temp").getConfig("temp");
        return DefaultCustomSearchIndexFieldConfig.of(fieldConfig);
    }

    /**
     * Returns an instance of {@code DefaultCustomSearchIndexConfig} based on the settings of the specified Config.
     *
     * @param name the name of the index (from the config map key).
     * @param config is supposed to provide the config for the index at its current level.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCustomSearchIndexConfig of(final String name, final Config config) {
        return new DefaultCustomSearchIndexConfig(name,
                ConfigWithFallback.newInstance(config, CustomSearchIndexConfigValue.values()));
    }

    /**
     * Returns the custom index configs of all entries of the specified {@code custom-indexes} object, keyed and named
     * by the entry key.
     *
     * @param customIndexes the object whose entries are the custom index configs (index name to index config).
     * @return the unmodifiable map of index name to index config, in the entry order of {@code customIndexes}.
     * @throws IllegalArgumentException if an index name is empty or an index has no field.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if an index config is invalid.
     */
    public static Map<String, CustomSearchIndexConfig> allOf(final ConfigObject customIndexes) {
        final Map<String, CustomSearchIndexConfig> result = new LinkedHashMap<>();
        customIndexes.forEach((name, value) ->
                result.put(name, of(name, ConfigFactory.empty().withFallback(value))));
        return Collections.unmodifiableMap(result);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<CustomSearchIndexFieldConfig> getFields() {
        return fields;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultCustomSearchIndexConfig that = (DefaultCustomSearchIndexConfig) o;
        return Objects.equals(name, that.name) && Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", fields=" + fields +
                "]";
    }
}
