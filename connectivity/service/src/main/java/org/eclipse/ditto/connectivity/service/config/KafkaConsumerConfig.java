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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;

import com.typesafe.config.Config;

/**
 * Provides configuration settings of the Kafka consumer.
 */
@Immutable
public interface KafkaConsumerConfig {

    /**
     * Returns the throttling config.
     *
     * @return the config.
     */
    ThrottlingConfig getThrottlingConfig();

    /**
     * Returns the Config for consumers needed by the Kafka client.
     *
     * @return consumer configuration needed by the Kafka client.
     */
    Config getAlpakkaConfig();

    /**
     * Returns an instance of {@code KafkaConsumerConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static KafkaConsumerConfig of(final Config config) {
        return DefaultKafkaConsumerConfig.of(config);
    }

}
