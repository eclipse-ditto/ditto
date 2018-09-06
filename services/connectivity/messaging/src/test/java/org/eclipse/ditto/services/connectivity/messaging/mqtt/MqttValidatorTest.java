/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MqttSource;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.junit.Test;

/**
 * Tests {@link MqttValidator}.
 */
public final class MqttValidatorTest {

    @Test
    public void testImmutability() {
        assertInstancesOf(MqttValidator.class, areImmutable());
    }

    @Test
    public void testValidSourceAddress() {
        MqttValidator.newInstance().validate(connectionWithSource("ditto/topic/+/123"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(connectionWithSource("ditto/#"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(connectionWithSource("#"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(connectionWithSource("+"), DittoHeaders.empty());
    }

    @Test
    public void testInvalidSourceAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("ditto/topic/+123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("ditto/#/123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("##"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource(""));
    }

    @Test
    public void testValidTargetAddress() {
        MqttValidator.newInstance().validate(connectionWithTarget("ditto/mqtt/topic"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(connectionWithTarget("ditto"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(connectionWithTarget("ditto/{{thing:id}}"), DittoHeaders.empty());
    }

    @Test
    public void testInvalidTargetAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto/+/mqtt"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto/#"));
    }

    @Test
    public void testWithDefaultSource() {
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectionStatus.OPEN, "tcp://localhost:1883")
                .sources(TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT)
                .build();
        verifyConnectionConfigurationInvalidExceptionIsThrown(connection);
    }

    @Test
    public void testInvalidSourceTopicFilters() {
        final MqttSource mqttSourceWithValidFilter =
                ConnectivityModelFactory.newFilteredMqttSource(1, 0, AUTHORIZATION_CONTEXT,
                        "things/+/{{ thing:id }}/#", 1, "#");
        final MqttSource mqttSourceWithInvalidFilter =
                ConnectivityModelFactory.newFilteredMqttSource(1, 0, AUTHORIZATION_CONTEXT,
                        "things/#/{{ thing:id }}/+", 1, "#");
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectionStatus.OPEN, "tcp://localhost:1883")
                .sources(Arrays.asList(mqttSourceWithValidFilter, mqttSourceWithInvalidFilter))
                .build();

        verifyConnectionConfigurationInvalidExceptionIsThrown(connection);
    }

    private Connection connectionWithSource(final String source) {
        final MqttSource mqttSource =
                ConnectivityModelFactory.newFilteredMqttSource(1, 0, AUTHORIZATION_CONTEXT,
                        "things/{{ thing:id }}", 1, source);
        return ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectionStatus.OPEN, "tcp://localhost:1883")
                .sources(singletonList(mqttSource))
                .build();
    }

    private Connection connectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectionStatus.OPEN, "tcp://localhost:1883")
                .targets(singleton(
                        ConnectivityModelFactory.newMqttTarget(target, AUTHORIZATION_CONTEXT, 1, Topic.LIVE_EVENTS)))
                .build();
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> MqttValidator.newInstance().validate(connection, DittoHeaders.empty()));
    }
}
