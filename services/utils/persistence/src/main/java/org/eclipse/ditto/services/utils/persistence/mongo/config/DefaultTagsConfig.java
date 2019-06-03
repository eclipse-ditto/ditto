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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class implements the policies tags config.
 */
@Immutable
public final class DefaultTagsConfig implements TagsConfig {

    private static final String CONFIG_PATH = "tags";

    private final int streamingCacheSize;

    private DefaultTagsConfig(final Config config) {
        streamingCacheSize = config.getInt(TagsConfigValue.STREAMING_CACHE_SIZE.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultTagsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the policies tags config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTagsConfig of(final Config config) {
        return new DefaultTagsConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, TagsConfigValue.values()));
    }

    @Override
    public int getStreamingCacheSize() {
        return streamingCacheSize;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultTagsConfig that = (DefaultTagsConfig) o;
        return streamingCacheSize == that.streamingCacheSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamingCacheSize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "streamingCacheSize=" + streamingCacheSize +
                "]";
    }

}
