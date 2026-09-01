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
package org.eclipse.ditto.wot.api.config;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the WoT (Web of Things) {@link WotHostValidationConfig}.
 *
 * @since 3.8.13
 */
@Immutable
public final class DefaultWotHostValidationConfig implements WotHostValidationConfig {

    private static final String CONFIG_PATH = "http.security";

    private final boolean enabled;
    private final Collection<String> allowedHostnames;
    private final Collection<String> blockedHostnames;
    private final Collection<String> blockedSubnets;
    private final String blockedHostRegex;
    private final int maxRedirects;

    private DefaultWotHostValidationConfig(final ScopedConfig scopedConfig) {
        enabled = scopedConfig.getBoolean(ConfigValue.ENABLED.getConfigPath());
        allowedHostnames = fromCommaSeparatedString(scopedConfig, ConfigValue.ALLOWED_HOSTNAMES);
        blockedHostnames = fromCommaSeparatedString(scopedConfig, ConfigValue.BLOCKED_HOSTNAMES);
        blockedSubnets = fromCommaSeparatedString(scopedConfig, ConfigValue.BLOCKED_SUBNETS);
        blockedHostRegex = scopedConfig.getString(ConfigValue.BLOCKED_HOST_REGEX.getConfigPath());
        maxRedirects = scopedConfig.getInt(ConfigValue.MAX_REDIRECTS.getConfigPath());
    }

    /**
     * Returns an instance based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultWotHostValidationConfig of(final Config config) {
        return new DefaultWotHostValidationConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    private static Collection<String> fromCommaSeparatedString(final ScopedConfig config,
            final ConfigValue configValue) {
        final String commaSeparated = config.getString(configValue.getConfigPath());
        return List.of(commaSeparated.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<String> getAllowedHostnames() {
        return allowedHostnames;
    }

    @Override
    public Collection<String> getBlockedHostnames() {
        return blockedHostnames;
    }

    @Override
    public Collection<String> getBlockedSubnets() {
        return blockedSubnets;
    }

    @Override
    public String getBlockedHostRegex() {
        return blockedHostRegex;
    }

    @Override
    public int getMaxRedirects() {
        return maxRedirects;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultWotHostValidationConfig that = (DefaultWotHostValidationConfig) o;
        return enabled == that.enabled &&
                maxRedirects == that.maxRedirects &&
                Objects.equals(allowedHostnames, that.allowedHostnames) &&
                Objects.equals(blockedHostnames, that.blockedHostnames) &&
                Objects.equals(blockedSubnets, that.blockedSubnets) &&
                Objects.equals(blockedHostRegex, that.blockedHostRegex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, allowedHostnames, blockedHostnames, blockedSubnets, blockedHostRegex,
                maxRedirects);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", allowedHostnames=" + allowedHostnames +
                ", blockedHostnames=" + blockedHostnames +
                ", blockedSubnets=" + blockedSubnets +
                ", blockedHostRegex=" + blockedHostRegex +
                ", maxRedirects=" + maxRedirects +
                "]";
    }
}
