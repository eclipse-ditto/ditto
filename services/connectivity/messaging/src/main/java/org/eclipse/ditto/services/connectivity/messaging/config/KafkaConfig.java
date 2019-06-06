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

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;

/**
 * Provides configuration settings of the Kafka protocol.
 */
@Immutable
public interface KafkaConfig {

    /**
     * Returns the Config for producers needed by akka-stream-kafka.
     *
     * @see <a href="https://doc.akka.io/docs/akka-stream-kafka/current/producer.html#settings">akka-stream-kafka Producer settings</a>
     * @return internal producer configuration needed by akka-stream-kafka client.
     */
    Config getInternalProducerConfig();

}
