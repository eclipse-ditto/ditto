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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the JavaScript mapping config.
 */
@Immutable
public final class DefaultJavaScriptConfig implements JavaScriptConfig {

    private static final String CONFIG_PATH = JavaScriptMessageMapperRhino.ALIAS;

    private final int maxScriptSizeBytes;
    private final Duration maxScriptExecutionTime;
    private final int maxScriptStackDepth;

    private DefaultJavaScriptConfig(final ScopedConfig config) {
        maxScriptSizeBytes = config.getInt(JavaScriptConfigValue.MAX_SCRIPT_SIZE_BYTES.getConfigPath());
        maxScriptExecutionTime = config.getDuration(JavaScriptConfigValue.MAX_SCRIPT_EXECUTION_TIME.getConfigPath());
        maxScriptStackDepth = config.getInt(JavaScriptConfigValue.MAX_SCRIPT_STACK_DEPTH.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultJavaScriptConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
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
