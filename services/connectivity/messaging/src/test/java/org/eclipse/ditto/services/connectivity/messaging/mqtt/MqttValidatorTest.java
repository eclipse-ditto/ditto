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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
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
        MqttValidator.newInstance().validate(createConnectionWithSource("ditto/topic/+/123"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(createConnectionWithSource("ditto/#"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(createConnectionWithSource("#"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(createConnectionWithSource("+"), DittoHeaders.empty());
    }

    @Test
    public void testInvalidSourceAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(createConnectionWithSource("ditto/topic/+123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(createConnectionWithSource("ditto/#/123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(createConnectionWithSource("##"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(createConnectionWithSource(""));
    }

    @Test
    public void testValidTargetAddress() {
        MqttValidator.newInstance().validate(createConnectionWithTarget("ditto/mqtt/topic"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(createConnectionWithTarget("ditto"), DittoHeaders.empty());
        MqttValidator.newInstance().validate(createConnectionWithTarget("ditto/{{thing:id}}"), DittoHeaders.empty());
    }

    @Test
    public void testInvalidTargetAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(createConnectionWithTarget(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(createConnectionWithTarget("ditto/+/mqtt"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(createConnectionWithTarget("ditto/#"));
    }

    private Connection createConnectionWithSource(final String source) {
        return ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectionStatus.OPEN, "tcp://localhost:1883")
                .sources(singletonList(ConnectivityModelFactory.newSource(singleton(source), 2,
                        TestConstants.Authorization.AUTHORIZATION_CONTEXT)))
                .build();
    }

    private Connection createConnectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder("mqtt", ConnectionType.MQTT,
                ConnectionStatus.OPEN, "tcp://localhost:1883")
                .targets(singleton(
                        ConnectivityModelFactory.newTarget(target, TestConstants.Authorization.AUTHORIZATION_CONTEXT,
                                Topic.LIVE_EVENTS)))
                .build();
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> MqttValidator.newInstance().validate(connection, DittoHeaders.empty()));
    }
}
