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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

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
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectionUriInvalidException;
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

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();

    @Test
    public void testImmutability() {
        assertInstancesOf(MqttValidator.class, areImmutable());
    }

    @Test
    public void testValidSourceAddress() {
        MqttValidator.newInstance().validate(connectionWithSource("ditto/topic/+/123"), DittoHeaders.empty(), null);
        MqttValidator.newInstance().validate(connectionWithSource("ditto/#"), DittoHeaders.empty(), null);
        MqttValidator.newInstance().validate(connectionWithSource("#"), DittoHeaders.empty(), null);
        MqttValidator.newInstance().validate(connectionWithSource("+"), DittoHeaders.empty(), null);
    }

    @Test
    public void testInvalidSourceAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("ditto/topic/+123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("ditto/#/123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("##"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource(""));
    }

    @Test
    public void testInvalidClientCount() {
        final Connection connectionWithInvalidClientCount =
                connectionWithSource("valid").toBuilder().clientCount(2).build();
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithInvalidClientCount);
    }

    @Test
    public void testInvalidConsumerCount() {
        final Source sourceWithInvalidConsumerCount =
                ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .address("ditto")
                        .qos(1)
                        .consumerCount(2)
                        .build();
        final Connection connectionWithInvalidConsumerCount =
                connectionWithSource(sourceWithInvalidConsumerCount).toBuilder().clientCount(2).build();
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithInvalidConsumerCount);
    }

    @Test
    public void testInsecureProtocolAndCertificates() {
        final Connection connectionWithInsecureProtocolAndTrustedCertificates =
                connectionWithSource("eclipse").toBuilder().trustedCertificates("123").build();

        Assertions.assertThatExceptionOfType(ConnectionUriInvalidException.class)
                .isThrownBy(() -> MqttValidator.newInstance()
                        .validate(connectionWithInsecureProtocolAndTrustedCertificates, DittoHeaders.empty(), null));
    }

    @Test
    public void testValidTargetAddress() {
        MqttValidator.newInstance().validate(connectionWithTarget("ditto/mqtt/topic"), DittoHeaders.empty(), null);
        MqttValidator.newInstance().validate(connectionWithTarget("ditto"), DittoHeaders.empty(), null);
        MqttValidator.newInstance().validate(connectionWithTarget("ditto/{{thing:id}}"), DittoHeaders.empty(), null);
        MqttValidator.newInstance().validate(connectionWithTarget("ditto/{{topic:full}}"), DittoHeaders.empty(), null);
        MqttValidator.newInstance().validate(connectionWithTarget("ditto/{{header:x}}"), DittoHeaders.empty(), null);
    }

    @Test
    public void testInvalidTargetAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto/+/mqtt"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto/#"));
    }

    @Test
    public void testInvalidHeaderMapping() {

        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT,
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
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT,
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
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT,
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

        return connectionWithSource(mqttSource);
    }

    private Connection connectionWithSource(final Source mqttSource) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .sources(singletonList(mqttSource))
                .build();
    }

    private Connection connectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(
                        ConnectivityModelFactory.newTarget(target, AUTHORIZATION_CONTEXT, null, 1, Topic.LIVE_EVENTS)))
                .build();
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> MqttValidator.newInstance().validate(connection, DittoHeaders.empty(), null));
    }
}
