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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.config.KafkaConfig;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.kafka.ProducerSettings;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.kafka.ProducerSettingsFactory}.
 */
public final class ProducerSettingsFactoryTest {

    private static final String[] BOOTSTRAP_SERVERS = {
            "foo:123",
            "bar:456",
            "baz:789"
    };
    private static final String USERNAME = "user";
    @SuppressWarnings("squid:S2068")
    private static final String PASSWORD = "pw";
    private static final String TARGET_ADDRESS = "events";
    private static final EntityId CONNECTION_ID = TestConstants.createRandomConnectionId();

    private static KafkaConfig kafkaConfig;
    private static Connection connection;

    private ProducerSettingsFactory underTest;

    @BeforeClass
    public static void initTestFixture() {
        final String uri = "tcp://" + USERNAME + ":" + PASSWORD + "@" + BOOTSTRAP_SERVERS[BOOTSTRAP_SERVERS.length - 1];
        final Map<String, String> specificConfig = new HashMap<>();
        final String additionalBootstrapServers = Arrays.stream(BOOTSTRAP_SERVERS)
                .limit(BOOTSTRAP_SERVERS.length - 1L)
                .collect(Collectors.joining(","));
        specificConfig.put("bootstrapServers", additionalBootstrapServers);

        connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.KAFKA,
                        ConnectivityStatus.OPEN, uri)
                .targets(singletonList(
                        ConnectivityModelFactory.newTarget(TARGET_ADDRESS, AUTHORIZATION_CONTEXT, null, 1,
                                Topic.LIVE_EVENTS)))
                .specificConfig(specificConfig)
                .build();

        kafkaConfig = TestConstants.CONNECTION_CONFIG.getKafkaConfig();
    }

    @Before
    public void setUp() {
        underTest = ProducerSettingsFactory.getInstance(connection, kafkaConfig);
    }

    @Test
    public void addsBootstrapServers() {
        final ProducerSettings<String, String> settings = underTest.getProducerSettings();

        final scala.collection.immutable.Map<String, String> properties = settings.properties();
        final List<String> servers = properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)
                .map(s -> Arrays.asList(s.split(",")))
                .getOrElse(null);

        assertThat(servers).containsExactlyInAnyOrder(BOOTSTRAP_SERVERS);
    }

}
