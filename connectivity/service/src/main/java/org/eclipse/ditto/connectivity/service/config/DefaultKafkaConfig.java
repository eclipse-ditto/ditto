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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link KafkaConfig}.
 */
@Immutable
final class DefaultKafkaConfig implements KafkaConfig {

    private static final String KAFKA_PATH = "kafka";

    private final KafkaConsumerConfig consumerConfig;
    private final KafkaProducerConfig producerConfig;
    private final KafkaCommitterConfig committerConfig;

    private DefaultKafkaConfig(final ScopedConfig kafkaScopedConfig) {
        consumerConfig = KafkaConsumerConfig.of(kafkaScopedConfig);
        producerConfig = KafkaProducerConfig.of(kafkaScopedConfig);
        committerConfig = KafkaCommitterConfig.of(kafkaScopedConfig);
    }

    /**
     * Returns an instance of {@code DefaultKafkaConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the Kafka config setting at {@value #KAFKA_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultKafkaConfig of(final Config config) {
        return new DefaultKafkaConfig(DefaultScopedConfig.newInstance(config, KAFKA_PATH));
    }

    @Override
    public KafkaConsumerConfig getConsumerConfig() {
        return consumerConfig;
    }

    @Override
    public KafkaProducerConfig getProducerConfig() {
        return producerConfig;
    }

    @Override
    public KafkaCommitterConfig getCommitterConfig() {
        return committerConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultKafkaConfig that = (DefaultKafkaConfig) o;
        return Objects.equals(consumerConfig, that.consumerConfig) &&
                Objects.equals(producerConfig, that.producerConfig) &&
                Objects.equals(committerConfig, that.committerConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerConfig, producerConfig, committerConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "consumerConfig=" + consumerConfig +
                ", producerConfig=" + producerConfig +
                ", committerConfig=" + committerConfig +
                "]";
    }
}
