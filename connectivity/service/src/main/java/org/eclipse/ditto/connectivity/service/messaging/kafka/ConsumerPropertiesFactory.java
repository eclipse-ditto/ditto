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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.eclipse.ditto.connectivity.model.Connection;

final class ConsumerPropertiesFactory {

    private final Connection connection;

    private ConsumerPropertiesFactory(final Connection connection) {
        this.connection = checkNotNull(connection, "connection");
    }

    static ConsumerPropertiesFactory getInstance(final Connection connection) {
        return new ConsumerPropertiesFactory(connection);
    }

    Properties getConsumerProperties() {
        // TODO: kafka source - Adjust configuration according to kafka documentation
        Properties properties = new Properties();
        properties.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, connection.getUri());
        properties.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, connection.getId().toString());
        properties.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        properties.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        return properties;
    }

}
