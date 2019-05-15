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
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the DevOps config.
 */
@Immutable
public final class DefaultDevOpsConfig implements DevOpsConfig, Serializable {

    private static final String CONFIG_PATH = "devops";

    private static final long serialVersionUID = 2795685309875009036L;

    private final boolean secureStatus;
    private final String password;

    private DefaultDevOpsConfig(final ConfigWithFallback configWithFallback) {
        secureStatus = configWithFallback.getBoolean(DevOpsConfigValue.SECURE_STATUS.getConfigPath());
        password = configWithFallback.getString(DevOpsConfigValue.PASSWORD.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultDevOpsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the DevOps config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultDevOpsConfig of(final Config config) {
        return new DefaultDevOpsConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, DevOpsConfigValue.values()));
    }

    @Override
    public boolean isSecureStatus() {
        return secureStatus;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultDevOpsConfig that = (DefaultDevOpsConfig) o;
        return secureStatus == that.secureStatus &&
                password.equals(that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(secureStatus, password);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "secureStatus=" + secureStatus +
                ", password=*****" +
                "]";
    }

}
