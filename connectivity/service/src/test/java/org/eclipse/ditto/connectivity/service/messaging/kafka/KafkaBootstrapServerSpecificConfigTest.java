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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Test;

/**
 * Unit test for {@link KafkaBootstrapServerSpecificConfig}.
 */
public final class KafkaBootstrapServerSpecificConfigTest {

    private static final String FAIL_MESSAGE_TEMPLATE = "bootstrapServers: %s";

    private static final DittoHeaders HEADERS = DittoHeaders.empty();

    private static final String DEFAULT_SERVER = "s1.org.apache.kafka:9092";
    private static final String DEFAULT_SERVER_2 = "s2.org.apache.kafka:9092";
    private static final String DEFAULT_SERVER_3 = "s3.org.apache.kafka:9092";
    private static final String DEFAULT_URI = "tcp://user:pw@" + DEFAULT_SERVER;
    private static final String[] BOOTSTRAP_SERVERS_ARRAY = {DEFAULT_SERVER, DEFAULT_SERVER_2, DEFAULT_SERVER_3};
    private static final String BOOTSTRAP_SERVERS = String.join(",", BOOTSTRAP_SERVERS_ARRAY);
    private static final String BOOTSTRAP_SERVERS_CONFIG_KEY = "bootstrapServers";

    private static final String DEFAULT_SERVER_ENDING_WITH_DOT = "org.apache.kafka.:9092";

    private static final Set<Connection> CONNECTIONS_WITH_EMPTY_SERVERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    connectionWithBootstrapServers(null),
                    connectionWithBootstrapServers("")
            )));
    private static final Set<Connection> CONNECTIONS_WITH_INVALID_PATTERN = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    connectionWithBootstrapServers(DEFAULT_SERVER + ","),
                    connectionWithBootstrapServers(DEFAULT_SERVER + ";" + DEFAULT_SERVER),
                    connectionWithBootstrapServers(DEFAULT_URI),
                    connectionWithBootstrapServers("s1.org.apache.kafka")
            )));
    private static final Set<Connection> CONNECTIONS_WITH_CORRECT_SERVERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    connectionWithBootstrapServers(DEFAULT_SERVER),
                    connectionWithBootstrapServers(BOOTSTRAP_SERVERS)
            )));
    private static final Connection CONNECTION_WITH_SERVER_ENDING_WITH_DOT =
            connectionWithBootstrapServers(DEFAULT_SERVER_ENDING_WITH_DOT);

    private final KafkaBootstrapServerSpecificConfig bootstrapServerSpecificConfig =
            KafkaBootstrapServerSpecificConfig.getInstance();

    @Test
    public void shouldAlwaysBeApplicable() {
        CONNECTIONS_WITH_EMPTY_SERVERS.forEach(this::shouldBeApplicable);
        CONNECTIONS_WITH_INVALID_PATTERN.forEach(this::shouldBeApplicable);
        CONNECTIONS_WITH_CORRECT_SERVERS.forEach(this::shouldBeApplicable);
        shouldBeApplicable(CONNECTION_WITH_SERVER_ENDING_WITH_DOT);
    }

    private void shouldBeApplicable(final Connection connection) {
        assertThat(bootstrapServerSpecificConfig.isApplicable(connection))
                .withFailMessage(FAIL_MESSAGE_TEMPLATE,
                        connection.getSpecificConfig().get(BOOTSTRAP_SERVERS_CONFIG_KEY))
                .isTrue();
    }

    @Test
    public void shouldNotBeValidIfBootstrapServersAreEmpty() {
        CONNECTIONS_WITH_EMPTY_SERVERS.forEach(this::shouldNotBeValid);
    }

    @Test
    public void shouldNotBeValidIfBootstrapServersDontMatchValidPattern() {
        CONNECTIONS_WITH_INVALID_PATTERN.forEach(this::shouldNotBeValid);

    }

    @Test
    public void shouldBeValidForCorrectListOfBootstrapServers() {
        CONNECTIONS_WITH_CORRECT_SERVERS.forEach(this::shouldBeValid);
    }

    @Test
    public void shouldBeValidForBootstrapEntryEndingWithDot() {
        shouldBeValid(CONNECTION_WITH_SERVER_ENDING_WITH_DOT);
    }

    private void shouldBeValid(final Connection connection) {
        assertThat(bootstrapServerSpecificConfig.isValid(connection))
                .withFailMessage(FAIL_MESSAGE_TEMPLATE,
                        connection.getSpecificConfig().get(BOOTSTRAP_SERVERS_CONFIG_KEY))
                .isTrue();
    }

    private void shouldNotBeValid(final Connection connection) {
        assertThat(bootstrapServerSpecificConfig.isValid(connection))
                .withFailMessage(FAIL_MESSAGE_TEMPLATE,
                        connection.getSpecificConfig().get(BOOTSTRAP_SERVERS_CONFIG_KEY)).
                isFalse();
    }

    @Test
    public void shouldNotValidateIfBootstrapServersAreEmpty() {
        CONNECTIONS_WITH_EMPTY_SERVERS.forEach(this::shouldNotValidate);
    }

    @Test
    public void shouldNotValidateIfBootstrapServersDontMatchValidPattern() {
        CONNECTIONS_WITH_INVALID_PATTERN.forEach(this::shouldNotValidate);
    }

    @Test
    public void shouldValidateForCorrectListOfBootstrapServers() {
        CONNECTIONS_WITH_CORRECT_SERVERS.forEach(this::shouldValidate);
    }

    private void shouldValidate(final Connection connection) {
        bootstrapServerSpecificConfig.validateOrThrow(connection, HEADERS);
    }

    private void shouldNotValidate(final Connection connection) {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> bootstrapServerSpecificConfig.validateOrThrow(connection, HEADERS));
    }

    @Test
    public void shouldAddConnectionUriAsBootstrapServerIfBootstrapServersAreEmpty() {
        CONNECTIONS_WITH_EMPTY_SERVERS.forEach(this::shouldOnlyContainDefaultBootstrapServer);
    }

    @Test
    public void shouldAddConnectionUriAsBootstrapServerIfBootstrapServersDontMatchValidPattern() {
        CONNECTIONS_WITH_INVALID_PATTERN.forEach(this::shouldOnlyContainDefaultBootstrapServer);
    }

    @Test
    public void shouldAddCorrectListOfBootstrapServers() {
        shouldOnlyContainDefaultBootstrapServer(connectionWithBootstrapServers(DEFAULT_SERVER));
        shouldContainBootstrapServers(connectionWithBootstrapServers(BOOTSTRAP_SERVERS));
    }

    @Test
    public void shouldContainEachBootstrapServerOnlyOnce() {
        final Connection connectionWithDuplicateBootstrapServers = connectionWithBootstrapServers(
                BOOTSTRAP_SERVERS + "," + BOOTSTRAP_SERVERS
        );
        shouldContainBootstrapServers(connectionWithDuplicateBootstrapServers);
    }

    private void shouldOnlyContainDefaultBootstrapServer(final Connection connection) {
        final Map<String, String> properties = bootstrapServerSpecificConfig.apply(connection);
        final List<String> servers = getBootstrapServers(properties);
        assertThat(servers).isEqualTo(Collections.singletonList(DEFAULT_SERVER));
    }

    private void shouldContainBootstrapServers(final Connection connection) {
        final Map<String, String> properties = bootstrapServerSpecificConfig.apply(connection);
        final List<String> servers = getBootstrapServers(properties);
        assertThat(servers).containsExactlyInAnyOrder(BOOTSTRAP_SERVERS_ARRAY);
    }

    private static List<String> getBootstrapServers(final Map<String, String> properties) {
        return Arrays.asList(properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG).split(","));
    }

    private static Connection connectionWithBootstrapServers(@Nullable final String bootstrapServers) {
        final Map<String, String> specificConfig = new HashMap<>();
        if (null != bootstrapServers) {
            specificConfig.put(BOOTSTRAP_SERVERS_CONFIG_KEY, bootstrapServers);
        }
        return ConnectivityModelFactory.newConnectionBuilder(TestConstants.createRandomConnectionId(),
                ConnectionType.KAFKA, ConnectivityStatus.OPEN, DEFAULT_URI)
                .targets(singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address("target")
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .topics(Topic.LIVE_EVENTS)
                        .build()))
                .specificConfig(specificConfig)
                .build();
    }

}
