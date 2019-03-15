/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.util.ConnectionConfigReader;
import org.eclipse.ditto.services.connectivity.util.KafkaConfigReader;
import org.junit.Test;

import akka.kafka.ProducerSettings;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.kafka.ProducerSettingsFactory}.
 */
public class ProducerSettingsFactoryTest {

    private static final String[] BOOTSTRAP_SERVERS = {
            "foo:123",
            "bar:456",
            "baz:789"
    };
    private static final String ADDITIONAL_BOOTSTRAP_SERVERS = Arrays.stream(BOOTSTRAP_SERVERS)
            .limit(BOOTSTRAP_SERVERS.length - 1)
            .collect(Collectors.joining(","));
    private static final String USERNAME = "user";
    private static final String PASSWORD = "pw";
    private static final String URI = "tcp://" + USERNAME + ":" + PASSWORD + "@" + BOOTSTRAP_SERVERS[BOOTSTRAP_SERVERS.length - 1];
    private static final String TARGET_ADDRESS = "events";
    private static final Map<String, String> SPECIFIC_CONFIG = new HashMap<>();
    private static final KafkaConfigReader CONFIG_READER = ConnectionConfigReader.fromRawConfig(TestConstants.CONFIG).kafka();

    static {
        SPECIFIC_CONFIG.put("bootstrapServers", ADDITIONAL_BOOTSTRAP_SERVERS);
    }

    private final ProducerSettingsFactory underTest = ProducerSettingsFactory.getInstance();

    @Test
    public void addsBootstrapServers() {
        final ProducerSettings<String, String> settings = settings();

        final List<String> servers = settings.properties().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)
                .map(s -> Arrays.asList(s.split(",")))
                .getOrElse(null);
        assertThat(servers).containsExactlyInAnyOrder(BOOTSTRAP_SERVERS);
    }

    private ProducerSettings<String, String> settings() {
        return underTest.createProducerSettings(connection(), CONFIG_READER);
    }

    private Connection connection() {
        return ConnectivityModelFactory.newConnectionBuilder("kafka", ConnectionType.KAFKA,
                ConnectivityStatus.OPEN, URI)
                .targets(singletonList(
                        ConnectivityModelFactory.newTarget(TARGET_ADDRESS, AUTHORIZATION_CONTEXT, null, 1, Topic.LIVE_EVENTS)))
                .specificConfig(SPECIFIC_CONFIG)
                .build();
    }
}