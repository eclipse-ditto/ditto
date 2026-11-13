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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.config;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link PostgresConfig.DdlCredentialsConfig}.
 */
@Immutable
public final class DefaultDdlCredentialsConfig implements PostgresConfig.DdlCredentialsConfig {

    static final String CONFIG_PATH = "ddl-credentials";

    private final String username;
    private final String password;

    private DefaultDdlCredentialsConfig(final ScopedConfig config) {
        username = config.getString(DdlCredentialsConfigValue.USERNAME.getConfigPath());
        password = config.getString(DdlCredentialsConfigValue.PASSWORD.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultDdlCredentialsConfig} based on the settings of the given Config.
     *
     * @param config is supposed to provide the settings at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultDdlCredentialsConfig of(final Config config) {
        return new DefaultDdlCredentialsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, DdlCredentialsConfigValue.values()));
    }

    @Override
    public boolean isConfigured() {
        return !username.isEmpty() && !password.isEmpty();
    }

    @Override
    public Optional<String> getUsername() {
        return username.isEmpty() ? Optional.empty() : Optional.of(username);
    }

    @Override
    public Optional<String> getPassword() {
        return password.isEmpty() ? Optional.empty() : Optional.of(password);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultDdlCredentialsConfig that = (DefaultDdlCredentialsConfig) o;
        return Objects.equals(username, that.username) && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }

    @Override
    public String toString() {
        // password intentionally not logged
        return getClass().getSimpleName() + " [" +
                "configured=" + isConfigured() +
                "]";
    }

}
