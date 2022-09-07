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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.DefaultAmqp10Config;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link AmqpSpecificConfig}.
 */
public final class AmqpSpecificConfigTest {

    @Test
    public void decodeDoublyEncodedUsernameAndPasswordSingle() {
        final var uri = "amqps://%2525u%2525s%2525e%2525r:%2525p%2525a%2525%252Bs%2525s@localhost:1234/";
        testDecoding(uri, "%2525u%2525s%2525e%2525r", "%2525p%2525a%2525%252Bs%2525s");
    }


    @Test
    public void decodeSinglyEncodedUsernameAndPasswordContainingPercentageSign() {
        final var uri = "amqps://%25u%25s%25e%25r:%25p%25a%25%2Bs%25s@localhost:1234/";
        testDecoding(uri, "%25u%25s%25e%25r", "%25p%25a%25%2Bs%25s");
    }

    @Test
    public void decodeSinglyEncodedUsernameAndPasswordDouble() {
        final var uri = "amqps://user:pa%2Bss@localhost:1234/";
        testDecoding(uri, "user", "pa%2Bss");
    }

    @Test
    public void decodeSinglyEncodedUsernameAndPasswordSingle() {
        final var uri = "amqps://user:pa%2Bss@localhost:1234/";
        testDecoding(uri, "user", "pa%2Bss");
    }

    private static void testDecoding(final String uri,
            final String expectedUsername,
            final String expectedPassword) {

        final var connection = TestConstants.createConnection()
                .toBuilder()
                .uri(uri)
                .build();

        final var underTest = AmqpSpecificConfig.withDefault("CID", connection, Map.of(),
                PlainCredentialsSupplier.fromUri());

        assertThat(underTest.render(uri))
                .isEqualTo("failover:(amqps://localhost:1234/?amqp.saslMechanisms=PLAIN)" +
                        "?jms.clientID=CID&jms.username=" + expectedUsername + "&jms.password=" + expectedPassword +
                        "&failover.startupMaxReconnectAttempts=5&failover.maxReconnectAttempts=-1" +
                        "&failover.initialReconnectDelay=128&failover.reconnectDelay=128" +
                        "&failover.maxReconnectDelay=900000&failover.reconnectBackOffMultiplier=2" +
                        "&failover.useReconnectBackOff=true");
    }

    @Test
    public void appendDefaultParameters() {
        final var connection = TestConstants.createConnection();
        final var amqp10Config = DefaultAmqp10Config.of(ConfigFactory.empty());
        final var defaultConfig = AmqpSpecificConfig.toDefaultConfig(amqp10Config);

        final var underTest = AmqpSpecificConfig.withDefault("CID", connection, defaultConfig,
                PlainCredentialsSupplier.fromUri());

        assertThat(underTest.render("amqps://localhost:1234/"))
                .isEqualTo("failover:(amqps://localhost:1234/?amqp.saslMechanisms=PLAIN)" +
                        "?jms.sendTimeout=60000&jms.prefetchPolicy.all=10&jms.connectTimeout=15000" +
                        "&jms.requestTimeout=5000&jms.clientID=CID" +
                        "&jms.username=username&jms.password=password" +
                        "&failover.startupMaxReconnectAttempts=5&failover.maxReconnectAttempts=-1" +
                        "&failover.initialReconnectDelay=128&failover.reconnectDelay=128" +
                        "&failover.maxReconnectDelay=900000&failover.reconnectBackOffMultiplier=2" +
                        "&failover.useReconnectBackOff=true");
    }

    @Test
    public void withoutFailover() {
        final var connection = TestConstants.createConnection().toBuilder().failoverEnabled(false).build();
        final var underTest = AmqpSpecificConfig.withDefault("CID", connection, Map.of(),
                PlainCredentialsSupplier.fromUri());
        assertThat(underTest.render("amqps://localhost:1234/"))
                .isEqualTo("amqps://localhost:1234/?amqp.saslMechanisms=PLAIN&jms.clientID=CID" +
                        "&jms.username=username&jms.password=password");
    }

    @Test
    public void withPlainCredentials() {
        final UserPasswordCredentials credentials = UserPasswordCredentials.newInstance("foo", "bar");
        final PlainCredentialsSupplier plainCredentialsSupplier =
                (connection) -> Optional.of(credentials);
        final Connection connection = TestConstants.createConnection();
        final AmqpSpecificConfig underTest = AmqpSpecificConfig.withDefault("CID", connection, Map.of(),
                plainCredentialsSupplier);
        assertThat(underTest.render("amqps://localhost:1234/"))
                .isEqualTo("failover:(amqps://localhost:1234/?amqp.saslMechanisms=PLAIN)" +
                        "?jms.clientID=CID&jms.username=foo&jms.password=bar" +
                        "&failover.startupMaxReconnectAttempts=5&failover.maxReconnectAttempts=-1" +
                        "&failover.initialReconnectDelay=128&failover.reconnectDelay=128" +
                        "&failover.maxReconnectDelay=900000&failover.reconnectBackOffMultiplier=2" +
                        "&failover.useReconnectBackOff=true");
    }

}
