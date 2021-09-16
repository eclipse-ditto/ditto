/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link Amqp10PublisherConfig}.
 */
@Immutable
final class DefaultAmqp10PublisherConfig implements Amqp10PublisherConfig {

    private static final String CONFIG_PATH = "publisher";

    private final int maxQueueSize;
    private final int parallelism;

    private DefaultAmqp10PublisherConfig(final ScopedConfig config) {
        maxQueueSize = config.getNonNegativeIntOrThrow(ConfigValue.MAX_QUEUE_SIZE);
        parallelism = config.getNonNegativeIntOrThrow(ConfigValue.PARALLELISM);
    }

    /**
     * Returns an instance of {@code DefaultAmqp10PublisherConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the AMQP 1.0 config setting.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAmqp10PublisherConfig of(final Config config) {
        return new DefaultAmqp10PublisherConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAmqp10PublisherConfig that = (DefaultAmqp10PublisherConfig) o;
        return maxQueueSize == that.maxQueueSize &&
                parallelism == that.parallelism;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxQueueSize, parallelism);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxQueueSize=" + maxQueueSize +
                ", parallelism=" + parallelism +
                "]";
    }

    @Override
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    @Override
    public int getParallelism() {
        return parallelism;
    }
}
