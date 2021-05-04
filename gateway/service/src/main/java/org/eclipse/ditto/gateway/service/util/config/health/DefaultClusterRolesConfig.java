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
package org.eclipse.ditto.gateway.service.util.config.health;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the cluster roles config of the Gateway health check.
 */
@Immutable
public final class DefaultClusterRolesConfig implements HealthCheckConfig.ClusterRolesConfig {

    private static final String CONFIG_PATH = "cluster-roles";

    private final boolean enabled;
    private final Set<String> expectedClusterRoles;

    private DefaultClusterRolesConfig(final ScopedConfig scopedConfig) {
        enabled = scopedConfig.getBoolean(ClusterRolesConfigValue.ENABLED.getConfigPath());
        expectedClusterRoles = Collections.unmodifiableSet(
                new HashSet<>(scopedConfig.getStringList(ClusterRolesConfigValue.EXPECTED.getConfigPath())));
    }

    /**
     * Returns an instance of {@code DefaultClusterRolesConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the cluster roles config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultClusterRolesConfig of(final Config config) {
        return new DefaultClusterRolesConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ClusterRolesConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Set<String> getExpectedClusterRoles() {
        return expectedClusterRoles;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultClusterRolesConfig that = (DefaultClusterRolesConfig) o;
        return enabled == that.enabled && Objects.equals(expectedClusterRoles, that.expectedClusterRoles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, expectedClusterRoles);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", expectedClusterRoles=" + expectedClusterRoles +
                "]";
    }

}
