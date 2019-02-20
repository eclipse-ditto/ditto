/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.util;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * TODO
 */
@Immutable
public final class DefaultJavaScriptConfig implements ConnectivityConfig.MappingConfig.JavaScriptConfig {

    private static final String CONFIG_PATH = "javascript";

    private final int maxScriptSizeBytes;
    private final Duration maxScriptExecutionTime;
    private final int maxScriptStackDepth;

    private DefaultJavaScriptConfig(final ScopedConfig config) {
        maxScriptSizeBytes = config.getInt(JavaScriptConfigValue.MAX_SCRIPT_SIZE_BYTES.getConfigPath());
        maxScriptExecutionTime = config.getDuration(JavaScriptConfigValue.MAX_SCRIPT_EXECUTION_TIME.getConfigPath());
        maxScriptStackDepth = config.getInt(JavaScriptConfigValue.MAX_SCRIPT_STACK_DEPTH.getConfigPath());
    }

    /**
     * TODO
     *
     * @param config
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultJavaScriptConfig of(final Config config) {
        return new DefaultJavaScriptConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, JavaScriptConfigValue.values()));
    }

    @Override
    public int getMaxScriptSizeBytes() {
        return maxScriptSizeBytes;
    }

    @Override
    public Duration getMaxScriptExecutionTime() {
        return maxScriptExecutionTime;
    }

    @Override
    public int getMaxScriptStackDepth() {
        return maxScriptStackDepth;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultJavaScriptConfig that = (DefaultJavaScriptConfig) o;
        return maxScriptSizeBytes == that.maxScriptSizeBytes &&
                maxScriptStackDepth == that.maxScriptStackDepth &&
                Objects.equals(maxScriptExecutionTime, that.maxScriptExecutionTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxScriptSizeBytes, maxScriptExecutionTime, maxScriptStackDepth);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxScriptSizeBytes=" + maxScriptSizeBytes +
                ", maxScriptExecutionTime=" + maxScriptExecutionTime +
                ", maxScriptStackDepth=" + maxScriptStackDepth +
                "]";
    }

}
