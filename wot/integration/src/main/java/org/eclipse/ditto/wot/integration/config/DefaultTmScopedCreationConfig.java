/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the WoT (Web of Things) {@link TmScopedCreationConfig}.
 */
@Immutable
final class DefaultTmScopedCreationConfig implements TmScopedCreationConfig {

    private final boolean skeletonCreationEnabled;
    private final boolean generateDefaultsForOptionalProperties;
    private final boolean throwExceptionOnWotErrors;

    private DefaultTmScopedCreationConfig(final ScopedConfig scopedConfig) {
        skeletonCreationEnabled =
                scopedConfig.getBoolean(ConfigValue.SKELETON_CREATION_ENABLED.getConfigPath());
        generateDefaultsForOptionalProperties =
                scopedConfig.getBoolean(ConfigValue.GENERATE_DEFAULTS_FOR_OPTIONAL_PROPERTIES.getConfigPath());
        throwExceptionOnWotErrors =
                scopedConfig.getBoolean(ConfigValue.THROW_EXCEPTION_ON_WOT_ERRORS.getConfigPath());
    }

    /**
     * Returns an instance of the thing config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the thing config at {@code configPath}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTmScopedCreationConfig of(final Config config, final String configPath) {
        return new DefaultTmScopedCreationConfig(ConfigWithFallback.newInstance(config, configPath,
                ConfigValue.values()));
    }

    @Override
    public boolean isSkeletonCreationEnabled() {
        return skeletonCreationEnabled;
    }

    @Override
    public boolean shouldGenerateDefaultsForOptionalProperties() {
        return generateDefaultsForOptionalProperties;
    }

    @Override
    public boolean shouldThrowExceptionOnWotErrors() {
        return throwExceptionOnWotErrors;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultTmScopedCreationConfig that = (DefaultTmScopedCreationConfig) o;
        return skeletonCreationEnabled == that.skeletonCreationEnabled &&
                generateDefaultsForOptionalProperties == that.generateDefaultsForOptionalProperties &&
                throwExceptionOnWotErrors == that.throwExceptionOnWotErrors;
    }

    @Override
    public int hashCode() {
        return Objects.hash(skeletonCreationEnabled, generateDefaultsForOptionalProperties, throwExceptionOnWotErrors);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "skeletonCreationEnabled=" + skeletonCreationEnabled +
                "generateDefaultsForOptionalProperties=" + generateDefaultsForOptionalProperties +
                "throwExceptionOnWotErrors=" + throwExceptionOnWotErrors +
                "]";
    }
}
