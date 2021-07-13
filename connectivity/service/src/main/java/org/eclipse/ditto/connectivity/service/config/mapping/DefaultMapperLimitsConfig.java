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

package org.eclipse.ditto.connectivity.service.config.mapping;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the mapper limits config.
 */
@Immutable
public class DefaultMapperLimitsConfig implements MapperLimitsConfig {

    private static final String CONFIG_PATH = "mapper-limits";

    private final int maxSourceMappers;
    private final int maxMappedInboundMessages;
    private final int maxTargetMappers;
    private final int maxMappedOutboundMessages;

    private DefaultMapperLimitsConfig(final ScopedConfig config) {

        maxSourceMappers = config.getNonNegativeIntOrThrow(MapperLimitsConfigValue.MAX_SOURCE_MAPPERS);
        maxMappedInboundMessages =
                config.getNonNegativeIntOrThrow(MapperLimitsConfigValue.MAX_MAPPED_INBOUND_MESSAGE);
        maxTargetMappers = config.getNonNegativeIntOrThrow(MapperLimitsConfigValue.MAX_TARGET_MAPPERS);
        maxMappedOutboundMessages =
                config.getNonNegativeIntOrThrow(MapperLimitsConfigValue.MAX_MAPPED_OUTBOUND_MESSAGE);
    }

    /**
     * Returns an instance of {@code DefaultMapperLimitsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the mapper-limits mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMapperLimitsConfig of(final Config config) {
        return new DefaultMapperLimitsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH,
                        MapperLimitsConfigValue.values()));
    }

    @Override
    public int getMaxSourceMappers() {
        return maxSourceMappers;
    }

    @Override
    public int getMaxMappedInboundMessages() {
        return maxMappedInboundMessages;
    }

    @Override
    public int getMaxTargetMappers() {
        return maxTargetMappers;
    }

    @Override
    public int getMaxMappedOutboundMessages() {
        return maxMappedOutboundMessages;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMapperLimitsConfig that = (DefaultMapperLimitsConfig) o;
        return maxSourceMappers == that.maxSourceMappers &&
                maxMappedInboundMessages == that.maxMappedInboundMessages &&
                maxTargetMappers == that.maxTargetMappers &&
                maxMappedOutboundMessages == that.maxMappedOutboundMessages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxSourceMappers, maxMappedInboundMessages, maxTargetMappers, maxMappedOutboundMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxSourceMappers=" + maxSourceMappers +
                ", maxMappedInboundMessages=" + maxMappedInboundMessages +
                ", maxTargetMappers=" + maxTargetMappers +
                ", maxMappedOutboundMessages=" + maxMappedOutboundMessages +
                "]";
    }

}
