/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.config.KafkaConfig;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ProducerPropertiesFactory}.
 */
public final class ProducerPropertiesFactoryTest {

    private static final String[] BOOTSTRAP_SERVERS = {
            "foo:123",
            "bar:456",
            "baz:789"
    };
    private static final String USERNAME = "user";
    @SuppressWarnings("squid:S2068")
    private static final String PASSWORD = "pw";
    private static final String TARGET_ADDRESS = "events";
    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();

    private static KafkaConfig kafkaConfig;
    private static Connection connection;

    private ProducerPropertiesFactory underTest;

    @BeforeClass
    public static void initTestFixture() {
        final String uri = "tcp://" + USERNAME + ":" + PASSWORD + "@" + BOOTSTRAP_SERVERS[BOOTSTRAP_SERVERS.length - 1];
        final Map<String, String> specificConfig = new HashMap<>();
        final String additionalBootstrapServers = Arrays.stream(BOOTSTRAP_SERVERS)
                .limit(BOOTSTRAP_SERVERS.length - 1L)
                .collect(Collectors.joining(","));
        specificConfig.put("bootstrapServers", additionalBootstrapServers);

        connection = ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.KAFKA,
                ConnectivityStatus.OPEN, uri)
                .targets(singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address(TARGET_ADDRESS)
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .topics(Topic.LIVE_EVENTS)
                        .build()))
                .specificConfig(specificConfig)
                .build();

        kafkaConfig = TestConstants.CONNECTION_CONFIG.getKafkaConfig();
    }

    @Before
    public void setUp() {
        underTest = ProducerPropertiesFactory.getInstance(connection, kafkaConfig);
    }

    @Test
    public void addsBootstrapServersAndFlattensProperties() {
        final Map<String, Object> properties = underTest.getProducerProperties();

        final List<String> servers =
                Arrays.asList(properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG).toString().split(","));

        assertThat(servers).containsExactlyInAnyOrder(BOOTSTRAP_SERVERS);

        // check flattening of client properties in kafka.producer.internal.kafka-clients
        assertThat(properties).contains(
                new AbstractMap.SimpleEntry<>("connections.max.idle.ms", 543210),
                new AbstractMap.SimpleEntry<>("reconnect.backoff.ms", 500),
                new AbstractMap.SimpleEntry<>("reconnect.backoff.max.ms", 10000)
        );
    }

}
