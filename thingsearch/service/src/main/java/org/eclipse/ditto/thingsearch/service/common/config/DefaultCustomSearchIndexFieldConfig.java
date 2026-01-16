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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link CustomSearchIndexFieldConfig}.
 */
@Immutable
final class DefaultCustomSearchIndexFieldConfig implements CustomSearchIndexFieldConfig {

    private final String name;
    private final Direction direction;

    private DefaultCustomSearchIndexFieldConfig(final ConfigWithFallback configWithFallback) {
        this.name = configWithFallback.getString(CustomSearchIndexFieldConfigValue.NAME.getConfigPath());
        if (this.name.isEmpty()) {
            throw new IllegalArgumentException("Field name must not be empty");
        }

        final String directionString = configWithFallback.getString(
                CustomSearchIndexFieldConfigValue.DIRECTION.getConfigPath());
        this.direction = Direction.fromString(directionString);
    }

    /**
     * Returns an instance of {@code DefaultCustomSearchIndexFieldConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the config for the field at its current level.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCustomSearchIndexFieldConfig of(final Config config) {
        return new DefaultCustomSearchIndexFieldConfig(
                ConfigWithFallback.newInstance(config, CustomSearchIndexFieldConfigValue.values()));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Direction getDirection() {
        return direction;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultCustomSearchIndexFieldConfig that = (DefaultCustomSearchIndexFieldConfig) o;
        return Objects.equals(name, that.name) && direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, direction);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", direction=" + direction +
                "]";
    }
}
