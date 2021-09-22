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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This class is the default implementation of {@link Amqp10Config}.
 */
@Immutable
public final class DefaultAmqp10Config implements Amqp10Config, WithStringMapDecoding {

    private static final String CONFIG_PATH = "amqp10";
    private static final String BACKOFF_PATH = "backoff";

    private final Amqp10ConsumerConfig consumerConfig;
    private final Amqp10PublisherConfig publisherConfig;
    private final int producerCacheSize;
    private final BackOffConfig backOffConfig;
    private final Duration globalConnectTimeout;
    private final Duration globalSendTimeout;
    private final Duration globalRequestTimeout;
    private final int globalPrefetchPolicyAllCount;
    private final Map<String, String> hmacAlgorithms;
    private final Duration initialConsumerResourceStatusAskTimeout;

    private DefaultAmqp10Config(final ScopedConfig config) {
        consumerConfig = DefaultAmqp10ConsumerConfig.of(config);
        publisherConfig = DefaultAmqp10PublisherConfig.of(config);
        producerCacheSize = config.getPositiveIntOrThrow(Amqp10ConfigValue.PRODUCER_CACHE_SIZE);
        backOffConfig = DefaultBackOffConfig.of(config.hasPath(BACKOFF_PATH)
                ? config
                : ConfigFactory.parseString(BACKOFF_PATH + "={}"));
        globalConnectTimeout = config.getNonNegativeDurationOrThrow(Amqp10ConfigValue.GLOBAL_CONNECT_TIMEOUT);
        globalSendTimeout = config.getNonNegativeDurationOrThrow(Amqp10ConfigValue.GLOBAL_SEND_TIMEOUT);
        globalRequestTimeout = config.getNonNegativeDurationOrThrow(Amqp10ConfigValue.GLOBAL_REQUEST_TIMEOUT);
        globalPrefetchPolicyAllCount =
                config.getNonNegativeIntOrThrow(Amqp10ConfigValue.GLOBAL_PREFETCH_POLICY_ALL_COUNT);
        hmacAlgorithms = asStringMap(config, HttpPushConfig.ConfigValue.HMAC_ALGORITHMS.getConfigPath());
        initialConsumerResourceStatusAskTimeout =
                config.getNonNegativeDurationOrThrow(Amqp10ConfigValue.INITIAL_CONSUMER_RESOURCE_STATUS_ASK_TIMEOUT);
    }

    /**
     * Returns an instance of {@code DefaultAmqp10Config} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAmqp10Config of(final Config config) {
        return new DefaultAmqp10Config(ConfigWithFallback.newInstance(config, CONFIG_PATH, Amqp10ConfigValue.values()));
    }

    @Override
    public Amqp10ConsumerConfig getConsumerConfig() {
        return consumerConfig;
    }

    @Override
    public Amqp10PublisherConfig getPublisherConfig() {
        return publisherConfig;
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
    public Duration getGlobalConnectTimeout() {
        return globalConnectTimeout;
    }

    @Override
    public Duration getGlobalSendTimeout() {
        return globalSendTimeout;
    }

    @Override
    public Duration getGlobalRequestTimeout() {
        return globalRequestTimeout;
    }

    @Override
    public int getGlobalPrefetchPolicyAllCount() {
        return globalPrefetchPolicyAllCount;
    }

    @Override
    public Map<String, String> getHmacAlgorithms() {
        return hmacAlgorithms;
    }

    @Override
    public Duration getInitialConsumerStatusAskTimeout() {
        return initialConsumerResourceStatusAskTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAmqp10Config that = (DefaultAmqp10Config) o;
        return Objects.equals(consumerConfig, that.consumerConfig) &&
                Objects.equals(publisherConfig, that.publisherConfig) &&
                producerCacheSize == that.producerCacheSize &&
                globalPrefetchPolicyAllCount == that.globalPrefetchPolicyAllCount &&
                Objects.equals(backOffConfig, that.backOffConfig) &&
                Objects.equals(globalConnectTimeout, that.globalConnectTimeout) &&
                Objects.equals(globalSendTimeout, that.globalSendTimeout) &&
                Objects.equals(globalRequestTimeout, that.globalRequestTimeout) &&
                Objects.equals(hmacAlgorithms, that.hmacAlgorithms) &&
                Objects.equals(initialConsumerResourceStatusAskTimeout, that.initialConsumerResourceStatusAskTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerConfig, publisherConfig, producerCacheSize, backOffConfig, globalConnectTimeout,
                globalSendTimeout, globalRequestTimeout, globalPrefetchPolicyAllCount, hmacAlgorithms,
                initialConsumerResourceStatusAskTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "consumerConfig=" + consumerConfig +
                ", publisherConfig=" + publisherConfig +
                ", producerCacheSize=" + producerCacheSize +
                ", backOffConfig=" + backOffConfig +
                ", globalConnectTimeout=" + globalConnectTimeout +
                ", globalSendTimeout=" + globalSendTimeout +
                ", globalRequestTimeout=" + globalRequestTimeout +
                ", globalPrefetchPolicyAllCount=" + globalPrefetchPolicyAllCount +
                ", hmacAlgorithms=" + hmacAlgorithms +
                ", initialConsumerResourceStatusAskTimeout=" + initialConsumerResourceStatusAskTimeout +
                "]";
    }

}
