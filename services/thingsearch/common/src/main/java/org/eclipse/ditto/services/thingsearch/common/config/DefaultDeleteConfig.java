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
package org.eclipse.ditto.services.thingsearch.common.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link DeleteConfig}.
 */
@Immutable
public final class DefaultDeleteConfig implements DeleteConfig, Serializable {

    private static final String CONFIG_PATH = "delete";

    private static final long serialVersionUID = -4078280758858400852L;

    private final boolean deleteEvent;
    private final boolean deleteNamespace;

    private DefaultDeleteConfig(final ConfigWithFallback configWithFallback) {
        deleteEvent = configWithFallback.getBoolean(DeleteConfigValue.EVENT.getConfigPath());
        deleteNamespace = configWithFallback.getBoolean(DeleteConfigValue.NAMESPACE.getConfigPath());
    }

    /**
     * Returns an instance of DefaultDeleteConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the delete config at {@code configPath}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultDeleteConfig of(final Config config) {
        return new DefaultDeleteConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, DeleteConfigValue.values()));
    }

    @Override
    public boolean isDeleteEvent() {
        return deleteEvent;
    }

    @Override
    public boolean isDeleteNamespace() {
        return deleteNamespace;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultDeleteConfig that = (DefaultDeleteConfig) o;
        return deleteEvent == that.deleteEvent &&
                deleteNamespace == that.deleteNamespace;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deleteEvent, deleteNamespace);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "deleteEvent=" + deleteEvent +
                ", deleteNamespace=" + deleteNamespace +
                "]";
    }

}
