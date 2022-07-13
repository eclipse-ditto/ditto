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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.model.ConnectivityModelFactory.newEnforcement;
import static org.eclipse.ditto.connectivity.model.ConnectivityModelFactory.newSourceAddressEnforcement;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssertAlternative;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectionUriInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests {@link Mqtt5Validator}.
 */
public final class Mqtt5ValidatorTest extends AbstractMqttValidatorTest {

    private MqttConfig mqttConfig;

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
                actorSystem, connectivityConfig);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithSource("ditto/#"), DittoHeaders.empty(), actorSystem, connectivityConfig);
        Mqtt5Validator.newInstance(mqttConfig).validate(connectionWithSource("#"), DittoHeaders.empty(), actorSystem,
                connectivityConfig);
        Mqtt5Validator.newInstance(mqttConfig).validate(connectionWithSource("+"), DittoHeaders.empty(), actorSystem,
                connectivityConfig);
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
        Mqtt5Validator.newInstance(mqttConfig).validate(connectionWithClientCount2, DittoHeaders.empty(), actorSystem,
                connectivityConfig);
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
                .validate(connectionWithConsumerCount2, DittoHeaders.empty(), actorSystem, connectivityConfig);
    }

    @Test
    public void testInsecureProtocolAndCertificates() {
        final Connection connectionWithInsecureProtocolAndTrustedCertificates =
                connectionWithSource("eclipse").toBuilder().trustedCertificates("123").build();

        Assertions.assertThatExceptionOfType(ConnectionUriInvalidException.class)
                .isThrownBy(() -> Mqtt5Validator.newInstance(mqttConfig)
                        .validate(connectionWithInsecureProtocolAndTrustedCertificates, DittoHeaders.empty(),
                                actorSystem, connectivityConfig));
    }

    @Test
    public void testValidTargetAddress() {
        Mqtt5Validator.newInstance(mqttConfig).validate(connectionWithTarget("ditto/mqtt/topic"), DittoHeaders.empty(),
                actorSystem, connectivityConfig);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto"), DittoHeaders.empty(), actorSystem, connectivityConfig);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto/{{thing:id}}"), DittoHeaders.empty(),
                        actorSystem, connectivityConfig);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto/{{topic:full}}"), DittoHeaders.empty(),
                        actorSystem, connectivityConfig);
        Mqtt5Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto/{{header:x}}"), DittoHeaders.empty(),
                        actorSystem, connectivityConfig);
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
                        .enforcement(newSourceAddressEnforcement("things/+/{{ thing:wasd }}/#"))
                        .address("#")
                        .qos(1)
                        .build();

        testInvalidSourceEnforcementFilters(mqttSourceWithValidFilter, mqttSourceWithInvalidFilter);
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

        testInvalidSourceEnforcementFilters(mqttSourceWithInvalidFilter);
    }


    @Test
    public void testValidLastWill() {
        final Connection connection = connectionWithSource("ditto/#").toBuilder()
                .specificConfig(Map.of(
                        MqttSpecificConfig.LAST_WILL_TOPIC, "last/will",
                        MqttSpecificConfig.LAST_WILL_QOS, "1",
                        MqttSpecificConfig.LAST_WILL_RETAIN, "false",
                        MqttSpecificConfig.LAST_WILL_MESSAGE, "the message"
                ))
                .build();

        Mqtt3Validator.newInstance(mqttConfig).validate(connection, DittoHeaders.empty(), actorSystem,
                connectivityConfig);
    }

    @Test
    public void testInvalidLastWillTopic() {
        final String invalidTopic = "last/will/#";
        final Connection connection = connectionWithSource("ditto/#").toBuilder()
                .specificConfig(Map.of(MqttSpecificConfig.LAST_WILL_TOPIC, invalidTopic))
                .build();

        verifyConnectionConfigurationInvalidExceptionIsThrown(connection)
                .withMessageContaining(invalidTopic);
    }

    @Test
    public void testInvalidLastWillQos() {
        final var invalidQosCode = 5;
        final Connection connection = connectionWithSource("ditto/#").toBuilder()
                .specificConfig(Map.of(
                        MqttSpecificConfig.LAST_WILL_TOPIC, "topic",
                        MqttSpecificConfig.LAST_WILL_QOS, String.valueOf(invalidQosCode)
                ))
                .build();


        verifyConnectionConfigurationInvalidExceptionIsThrown(connection)
                .withMessage("<%d> is not a valid MQTT QoS code.", invalidQosCode)
                .satisfies(dittoRuntimeException -> assertThat(dittoRuntimeException.getDescription())
                        .hasValue(String.format("Please provide a valid MQTT QoS code for config key <%s>.",
                                MqttSpecificConfig.LAST_WILL_QOS)));
    }

    @Override
    protected ThrowableAssertAlternative<ConnectionConfigurationInvalidException>
    verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        return Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> Mqtt5Validator.newInstance(mqttConfig)
                        .validate(connection, DittoHeaders.empty(), actorSystem, connectivityConfig));
    }
}
