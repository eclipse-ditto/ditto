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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link Amqp10Config}.
 */
@Immutable
public final class DefaultAmqp10Config implements Amqp10Config {

    private static final String CONFIG_PATH = "amqp10";

    private final Duration consumerThrottlingInterval;
    private final int consumerThrottlingLimit;
    private final int producerCacheSize;

    private DefaultAmqp10Config(final ScopedConfig config) {
        consumerThrottlingInterval = config.getDuration(Amqp10ConfigValue.CONSUMER_THROTTLING_INTERVAL.getConfigPath());
        consumerThrottlingLimit = config.getInt(Amqp10ConfigValue.CONSUMER_THROTTLING_LIMIT.getConfigPath());
        producerCacheSize = config.getInt(Amqp10ConfigValue.PRODUCER_CACHE_SIZE.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultAmqp10Config} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAmqp10Config of(final Config config) {
        return new DefaultAmqp10Config(ConfigWithFallback.newInstance(config, CONFIG_PATH, Amqp10ConfigValue.values()));
    }

    @Override
    public Duration getConsumerThrottlingInterval() {
        return consumerThrottlingInterval;
    }

    @Override
    public int getConsumerThrottlingLimit() {
        return consumerThrottlingLimit;
    }

    @Override
    public int getProducerCacheSize() {
        return producerCacheSize;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultAmqp10Config)) {
            return false;
        }
        final DefaultAmqp10Config that = (DefaultAmqp10Config) o;
        return consumerThrottlingLimit == that.consumerThrottlingLimit &&
                producerCacheSize == that.producerCacheSize &&
                Objects.equals(consumerThrottlingInterval, that.consumerThrottlingInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerThrottlingInterval, consumerThrottlingLimit, producerCacheSize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "consumerThrottlingInterval=" + consumerThrottlingInterval +
                ", consumerThrottlingLimit=" + consumerThrottlingLimit +
                ", producerCacheSize=" + producerCacheSize +
                "]";
    }
}
