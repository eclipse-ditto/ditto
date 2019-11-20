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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.connectivity.messaging.backoff.BackOffConfig;
import org.eclipse.ditto.services.connectivity.messaging.backoff.DefaultBackOffConfig;
import org.eclipse.ditto.services.base.config.ThrottlingConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This class is the default implementation of {@link Amqp10Config}.
 */
@Immutable
public final class DefaultAmqp10Config implements Amqp10Config {

    private static final String CONFIG_PATH = "amqp10";
    private static final String CONSUMER_PATH = "consumer";

    private final int producerCacheSize;
    private final BackOffConfig backOffConfig;
    private final ThrottlingConfig consumerThrottlingConfig;

    private DefaultAmqp10Config(final ScopedConfig config) {
        producerCacheSize = config.getInt(Amqp10ConfigValue.PRODUCER_CACHE_SIZE.getConfigPath());
        backOffConfig = DefaultBackOffConfig.of(config);
        consumerThrottlingConfig = ThrottlingConfig.of(config.hasPath(CONSUMER_PATH)
                ? config.getConfig(CONSUMER_PATH)
                : ConfigFactory.empty());
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
    public ThrottlingConfig getConsumerThrottlingConfig() {
        return consumerThrottlingConfig;
    }

    @Override
    public int getProducerCacheSize() {
        return producerCacheSize;
    }

    @Override
    public BackOffConfig getBackOffConfig() {
        return backOffConfig;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAmqp10Config that = (DefaultAmqp10Config) o;
        return producerCacheSize == that.producerCacheSize &&
                Objects.equals(backOffConfig, that.backOffConfig) &&
                Objects.equals(consumerThrottlingConfig, that.consumerThrottlingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(producerCacheSize, backOffConfig, consumerThrottlingConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "producerCacheSize=" + producerCacheSize +
                ", backOffConfig=" + backOffConfig +
                ", consumerThrottlingConfig=" + consumerThrottlingConfig +
                "]";
    }

}
