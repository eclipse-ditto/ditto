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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigValue;

/**
 * Default implementation of {@link CustomSearchIndexConfig}.
 */
@Immutable
final class DefaultCustomSearchIndexConfig implements CustomSearchIndexConfig {

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
