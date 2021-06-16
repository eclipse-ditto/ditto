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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;

import com.typesafe.config.Config;

/**
 * Provides configuration settings of the Kafka protocol.
 */
@Immutable
public interface KafkaConfig {

    /**
     * Returns the Config for consumers needed by the Kafka client.
     *
     * @return consumer configuration needed by the Kafka client.
     */
    Config getConsumerConfig();

    /**
     * Returns the consumer throttling config.
     *
     * @return the config.
     */
    ThrottlingConfig getConsumerThrottlingConfig();

    /**
     * Returns the Config for producers needed by the Kafka client.
     *
     * @return producer configuration needed by the Kafka client.
     */
    Config getProducerConfig();

    /**
     * @return number of maximum buffered messages before dropping them.
     */
    int getProducerQueueSize();

    /**
     * @return number of maximum parallel message publications.
     */
    int getProducerParallelism();

    /**
     * @return minimum duration before restarting the producer stream after a failure.
     */
    Duration getProducerMinBackoff();

    /**
     * @return maximum duration before restarting the producer stream after a failure.
     */
    Duration getProducerMaxBackoff();

    /**
     * @return random value to vary the restart interval.
     */
    double getProducerRandomFactor();

}
