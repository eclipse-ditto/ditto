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

import org.eclipse.ditto.connectivity.service.config.javascript.DefaultJavaScriptConfig;
import org.eclipse.ditto.connectivity.service.config.javascript.JavaScriptConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the mapping config.
 */
@Immutable
public final class DefaultMappingConfig implements MappingConfig {

    private static final String CONFIG_PATH = "mapping";

    private final int bufferSize;
    private final int parallelism;
    private final int maxPoolSize;
    private final boolean publishFailedEnrichments;
    private final JavaScriptConfig javaScriptConfig;
    private final MapperLimitsConfig mapperLimitsConfig;

    private DefaultMappingConfig(final ScopedConfig config) {
        bufferSize = config.getNonNegativeIntOrThrow(MappingConfigValue.BUFFER_SIZE);
        parallelism = config.getPositiveIntOrThrow(MappingConfigValue.PARALLELISM);
        maxPoolSize = config.getPositiveIntOrThrow(MappingConfigValue.MAX_POOL_SIZE);
        publishFailedEnrichments = config.getBoolean(MappingConfigValue.PUBLISH_FAILED_ENRICHMENTS.getConfigPath());
        mapperLimitsConfig = DefaultMapperLimitsConfig.of(config);
        javaScriptConfig = DefaultJavaScriptConfig.of(config);
    }

    /**
     * Returns an instance of {@code DefaultMappingConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMappingConfig of(final Config config) {
        final var mappingScopedConfig =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, MappingConfigValue.values());

        return new DefaultMappingConfig(mappingScopedConfig);
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public int getParallelism() {
        return parallelism;
    }

    @Override
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    @Override
    public boolean getPublishFailedEnrichments() {
        return publishFailedEnrichments;
    }

    @Override
    public JavaScriptConfig getJavaScriptConfig() {
        return javaScriptConfig;
    }

    @Override
    public MapperLimitsConfig getMapperLimitsConfig() {
        return mapperLimitsConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMappingConfig that = (DefaultMappingConfig) o;
        return bufferSize == that.bufferSize &&
                parallelism == that.parallelism &&
                maxPoolSize == that.maxPoolSize &&
                publishFailedEnrichments == that.publishFailedEnrichments &&
                Objects.equals(javaScriptConfig, that.javaScriptConfig) &&
                Objects.equals(mapperLimitsConfig, that.mapperLimitsConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bufferSize, parallelism, maxPoolSize, publishFailedEnrichments, javaScriptConfig, mapperLimitsConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "bufferSize=" + bufferSize +
                ", parallelism=" + parallelism +
                ", maxPoolSize=" + maxPoolSize +
                ", publishFailedEnrichments=" + publishFailedEnrichments +
                ", javaScriptConfig=" + javaScriptConfig +
                ", mapperLimitsConfig=" + mapperLimitsConfig +
                "]";
    }

}
