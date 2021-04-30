/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the cloud events endpoint config.
 */
@Immutable
public final class DefaultCloudEventsConfig implements CloudEventsConfig {

    private static final String CONFIG_PATH = "cloud-events";

    private final boolean emptySchemaAllowed;

    private final Set<String> dataTypes;

    private DefaultCloudEventsConfig(final ScopedConfig scopedConfig) {
        emptySchemaAllowed = scopedConfig.getBoolean(CloudEventsConfigValue.EMPTY_SCHEMA_ALLOWED.getConfigPath());
        dataTypes = Set.copyOf(scopedConfig.getStringList(CloudEventsConfigValue.DATA_TYPES.getConfigPath()));
    }

    /**
     * Returns an instance of {@code DefaultCloudEventsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the public health config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCloudEventsConfig of(final Config config) {
        return new DefaultCloudEventsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, CloudEventsConfigValue.values()));
    }

    @Override
    public boolean isEmptySchemaAllowed() {
        return emptySchemaAllowed;
    }

    @Override
    public Set<String> getDataTypes() {
        return dataTypes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultCloudEventsConfig that = (DefaultCloudEventsConfig) o;
        return emptySchemaAllowed == that.emptySchemaAllowed && dataTypes.equals(that.dataTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(emptySchemaAllowed, dataTypes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "emptySchemaAllowed=" + emptySchemaAllowed +
                ", dataTypes=" + dataTypes +
                "]";
    }

}
