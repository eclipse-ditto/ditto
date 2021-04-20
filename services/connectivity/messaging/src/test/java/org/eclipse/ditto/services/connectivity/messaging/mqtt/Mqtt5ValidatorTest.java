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
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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
import org.eclipse.ditto.services.connectivity.config.MqttConfig;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link Mqtt5Validator}.
 */
public final class Mqtt5ValidatorTest {

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();

    private static ActorSystem actorSystem;
    private MqttConfig mqttConfig;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Before
    public void setup() {
        mqttConfig = Mockito.mock(MqttConfig.class);
        when(mqttConfig.shouldReconnectForRedelivery()).thenReturn(false);
        when(mqttConfig.shouldUseSeparatePublisherClient()).thenReturn(false);
        when(mqttConfig.getReconnectForRedeliveryDelay()).thenReturn(Duration.ofSeconds(2));
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(Mqtt5Validator.class, areImmutable());
    }

    @Test
    public void testValidSourceAddress() {
        Mqtt5Validator.newInstance(mqttConfig).validate(connectionWithSource("ditto/topic/+/123"), DittoHeaders.empty(),
                actorSystem);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithSource("ditto/#"), DittoHeaders.empty(), actorSystem);
        Mqtt5Validator.newInstance(mqttConfig).validate(connectionWithSource("#"), DittoHeaders.empty(), actorSystem);
        Mqtt5Validator.newInstance(mqttConfig).validate(connectionWithSource("+"), DittoHeaders.empty(), actorSystem);
    }

    @Test
    public void testInvalidSourceAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("ditto/topic/+123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("ditto/#/123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("##"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource(""));
    }

    @Test
    public void testClientCountGreaterOneIsPossible() {
        final Connection connectionWithClientCount2 =
                connectionWithSource("valid").toBuilder().clientCount(2).build();
        Mqtt5Validator.newInstance(mqttConfig).validate(connectionWithClientCount2, DittoHeaders.empty(), actorSystem);
    }

    @Test
    public void testConsumerCountGreaterOneIsPossible() {
        final Source sourceWithInvalidConsumerCount =
                ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .address("ditto")
                        .qos(1)
                        .consumerCount(2)
                        .build();
        final Connection connectionWithConsumerCount2 =
                connectionWithSource(sourceWithInvalidConsumerCount).toBuilder().clientCount(2).build();
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithConsumerCount2, DittoHeaders.empty(), actorSystem);
    }

    @Test
    public void testInsecureProtocolAndCertificates() {
        final Connection connectionWithInsecureProtocolAndTrustedCertificates =
                connectionWithSource("eclipse").toBuilder().trustedCertificates("123").build();

        Assertions.assertThatExceptionOfType(ConnectionUriInvalidException.class)
                .isThrownBy(() -> Mqtt5Validator.newInstance(mqttConfig)
                        .validate(connectionWithInsecureProtocolAndTrustedCertificates, DittoHeaders.empty(),
                                actorSystem));
    }

    @Test
    public void testValidTargetAddress() {
        Mqtt5Validator.newInstance(mqttConfig).validate(connectionWithTarget("ditto/mqtt/topic"), DittoHeaders.empty(),
                actorSystem);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto"), DittoHeaders.empty(), actorSystem);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto/{{thing:id}}"), DittoHeaders.empty(),
                        actorSystem);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto/{{topic:full}}"), DittoHeaders.empty(),
                        actorSystem);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto/{{header:x}}"), DittoHeaders.empty(),
                        actorSystem);
    }

    @Test
    public void testInvalidTargetAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget(""));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto/+/mqtt"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithTarget("ditto/#"));
    }

    @Test
    public void testWithDefaultSource() {
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT_5,
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
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT_5,
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
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT_5,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .sources(singletonList(mqttSource))
                .build();
    }

    private Connection connectionWithTarget(final String target) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID, ConnectionType.MQTT_5,
                ConnectivityStatus.OPEN, "tcp://localhost:1883")
                .targets(singletonList(
                        ConnectivityModelFactory.newTarget(target, AUTHORIZATION_CONTEXT, null, 1, Topic.LIVE_EVENTS)))
                .build();
    }

    private void verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> Mqtt5Validator.newInstance(mqttConfig)
                        .validate(connection, DittoHeaders.empty(), actorSystem));
    }
}
