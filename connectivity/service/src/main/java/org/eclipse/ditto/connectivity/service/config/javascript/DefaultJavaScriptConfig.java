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
package org.eclipse.ditto.connectivity.service.config.javascript;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the JavaScript mapping config.
 */
@Immutable
public final class DefaultJavaScriptConfig implements JavaScriptConfig {

    private static final String CONFIG_PATH = "javascript";

    private final int maxScriptSizeBytes;
    private final Duration maxScriptExecutionTime;
    private final int maxScriptStackDepth;
    private final boolean allowUnsafeStandardObjects;
    @Nullable private final Path commonJsModulesPath;

    private DefaultJavaScriptConfig(final ScopedConfig config) {
        maxScriptSizeBytes = config.getPositiveIntOrThrow(JavaScriptConfigValue.MAX_SCRIPT_SIZE_BYTES);
        maxScriptExecutionTime =
                config.getNonNegativeAndNonZeroDurationOrThrow(JavaScriptConfigValue.MAX_SCRIPT_EXECUTION_TIME);
        maxScriptStackDepth = config.getPositiveIntOrThrow(JavaScriptConfigValue.MAX_SCRIPT_STACK_DEPTH);
        allowUnsafeStandardObjects = config.getBoolean(JavaScriptConfigValue.ALLOW_UNSAFE_STANDARD_OBJECTS
                .getConfigPath());
        final String commonJsModulesPathString = config.getString(
                JavaScriptConfigValue.COMMON_JS_MODULE_PATH.getConfigPath());
        if (commonJsModulesPathString.isEmpty()) {
            commonJsModulesPath = null;
        } else {
            commonJsModulesPath = Path.of(commonJsModulesPathString);
        }
    }

    /**
     * Returns an instance of {@code DefaultJavaScriptConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
    public boolean isAllowUnsafeStandardObjects() {
        return allowUnsafeStandardObjects;
    }

    @Override
    public Optional<Path> getCommonJsModulesPath() {
        return Optional.ofNullable(commonJsModulesPath);
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
                allowUnsafeStandardObjects == that.allowUnsafeStandardObjects &&
                Objects.equals(maxScriptExecutionTime, that.maxScriptExecutionTime) &&
                Objects.equals(commonJsModulesPath, that.commonJsModulesPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxScriptSizeBytes, maxScriptExecutionTime, maxScriptStackDepth, allowUnsafeStandardObjects,
                commonJsModulesPath);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxScriptSizeBytes=" + maxScriptSizeBytes +
                ", maxScriptExecutionTime=" + maxScriptExecutionTime +
                ", maxScriptStackDepth=" + maxScriptStackDepth +
                ", allowUnsafeStandardObjects=" + allowUnsafeStandardObjects +
                ", commonJsModulesPath=" + commonJsModulesPath +
                "]";
    }

}
