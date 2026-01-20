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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the NamespaceActivityCheckConfig.
 * It is instantiated for each namespace activity check entry containing the namespace pattern
 * and the activity check intervals.
 *
 * @since 3.9.0
 */
@Immutable
public final class DefaultNamespaceActivityCheckConfig implements NamespaceActivityCheckConfig {

    private final String namespacePattern;
    private final Duration inactiveInterval;
    private final Duration deletedInterval;

    private DefaultNamespaceActivityCheckConfig(final ConfigWithFallback configWithFallback) {
        this.namespacePattern = configWithFallback.getString(
                NamespaceActivityCheckConfigValue.NAMESPACE_PATTERN.getConfigPath());
        this.inactiveInterval = configWithFallback.getDuration(
                NamespaceActivityCheckConfigValue.INACTIVE_INTERVAL.getConfigPath());
        this.deletedInterval = configWithFallback.getDuration(
                NamespaceActivityCheckConfigValue.DELETED_INTERVAL.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultNamespaceActivityCheckConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the config for the namespace activity check at its current level.
     * @return the instance.
     */
    public static DefaultNamespaceActivityCheckConfig of(final Config config) {
        return new DefaultNamespaceActivityCheckConfig(
                ConfigWithFallback.newInstance(config, NamespaceActivityCheckConfigValue.values()));
    }

    @Override
    public String getNamespacePattern() {
        return namespacePattern;
    }

    @Override
    public Duration getInactiveInterval() {
        return inactiveInterval;
    }

    @Override
    public Duration getDeletedInterval() {
        return deletedInterval;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultNamespaceActivityCheckConfig that = (DefaultNamespaceActivityCheckConfig) o;
        return Objects.equals(namespacePattern, that.namespacePattern) &&
                Objects.equals(inactiveInterval, that.inactiveInterval) &&
                Objects.equals(deletedInterval, that.deletedInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespacePattern, inactiveInterval, deletedInterval);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "namespacePattern=" + namespacePattern +
                ", inactiveInterval=" + inactiveInterval +
                ", deletedInterval=" + deletedInterval +
                "]";
    }
}
