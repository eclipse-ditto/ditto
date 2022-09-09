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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.config.KafkaConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerSettings;

/**
 * Unit test for {@link PropertiesFactory}.
 */
public final class PropertiesFactoryTest {

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

    private PropertiesFactory underTest;

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
        underTest = PropertiesFactory.newInstance(connection, kafkaConfig, UUID.randomUUID().toString());
    }

    @Test
    public void addsBootstrapServersAndFlattensPropertiesFromProducerSettings() {
        final ProducerSettings<String, ByteBuffer> producerSettings = underTest.getProducerSettings();
        final Map<String, Object> properties = producerSettings.getProperties();

        final List<String> servers =
                Arrays.asList(properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG).toString().split(","));

        assertThat(servers).containsExactlyInAnyOrder(BOOTSTRAP_SERVERS);

        // check flattening of client properties in kafka.producer
        assertThat(properties)
                .containsEntry("connections.max.idle.ms", "543210")
                .containsEntry("reconnect.backoff.ms", "500")
                .containsEntry("reconnect.backoff.max.ms", "10000");
    }

    @Test
    public void addsBootstrapServersAndFlattensPropertiesFromConsumerSettings() {

        final ConsumerSettings<String, ByteBuffer> consumerSettings = underTest.getConsumerSettings(false);
        final Map<String, Object> properties = consumerSettings.getProperties();

        final List<String> servers =
                Arrays.asList(properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG).toString().split(","));

        assertThat(servers).containsExactlyInAnyOrder(BOOTSTRAP_SERVERS);

        // check flattening of client properties in kafka.producer
        assertThat(properties)
                .containsEntry("enable.auto.commit", "true")
                .containsEntry("retries", "0")
                .containsEntry("request.timeout.ms", "10000");
    }

}
