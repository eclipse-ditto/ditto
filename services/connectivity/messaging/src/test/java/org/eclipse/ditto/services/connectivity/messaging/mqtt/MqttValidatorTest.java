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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newEnforcement;
import static org.eclipse.ditto.model.connectivity.ConnectivityModelFactory.newSourceAddressEnforcement;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Source;
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
        MqttValidator.newInstance().validate(connectionWithTarget("ditto/{{topic:full}}"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(connectionWithTarget("ditto/{{header:x}}"), DittoHeaders.empty());
    }

    @Test
    public void testInvalidTargetAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto/+/mqtt"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto/#"));
    }

    @Test
    public void testInvalidHeaderMapping() {

        final Connection connection = ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .sources(singletonList(ConnectivityModelFactory.newSourceBuilder()
                        .headerMapping(TestConstants.HEADER_MAPPING)
                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                        .build()))
                .build();

        verifyConnectionConfigurationInvalidExceptionIsThrown(connection);
    }

    @Test
    public void testWithDefaultSource() {
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .sources(TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT)
                .build();
        verifyConnectionConfigurationInvalidExceptionIsThrown(connection);
    }

    @Test
    public void testInvalidSourceTopicFilters() {
        final Source mqttSourceWithValidFilter =
                ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .enforcement(newSourceAddressEnforcement("things/+/{{ thing:id }}/#"))
                        .address("#")
                        .qos(1)
                        .build();
        final Source mqttSourceWithInvalidFilter =
                ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .enforcement(newSourceAddressEnforcement("things/#/{{ thing:id }}/+"))
                        .address("#")
                        .qos(1)
                        .build();

        testInvalidSourceTopicFilters(mqttSourceWithValidFilter, mqttSourceWithInvalidFilter);
    }

    @Test
    public void testInvalidEnforcementOrigin() {

        final Source mqttSourceWithInvalidFilter =
                ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .enforcement(newEnforcement("{{ header:device_id }}", "things/{{ thing:id }}/+"))
                        .address("#")
                        .qos(1)
                        .build();

        testInvalidSourceTopicFilters(mqttSourceWithInvalidFilter);
    }

    private void testInvalidSourceTopicFilters(final Source... sources) {
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .sources(Arrays.asList(sources))
                .build();
        verifyConnectionConfigurationInvalidExceptionIsThrown(connection);
    }

    private Connection connectionWithSource(final String source) {
        final Source mqttSource =
                ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .enforcement(ConnectivityModelFactory.newSourceAddressEnforcement(
                                TestConstants.asSet("things/{{ thing:id }}")))
                        .address(source)
                        .qos(1)
                        .build();

        return ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .sources(singletonList(mqttSource))
                .build();
    }

    private Connection connectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singleton(
                        ConnectivityModelFactory.newTarget(target, AUTHORIZATION_CONTEXT, null, 1, Topic.LIVE_EVENTS)))
                .build();
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> MqttValidator.newInstance().validate(connection, DittoHeaders.empty()));
    }
}
