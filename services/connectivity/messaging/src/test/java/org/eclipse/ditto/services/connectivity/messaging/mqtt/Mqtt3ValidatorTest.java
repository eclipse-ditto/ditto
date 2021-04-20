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

import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectionUriInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.junit.Test;

/**
 * Tests {@link Mqtt3Validator}.
 */
public final class Mqtt3ValidatorTest extends AbstractMqttValidatorTest {

    @Test
    public void testImmutability() {
        assertInstancesOf(Mqtt3Validator.class, areImmutable());
    }

    @Test
    public void testValidSourceAddress() {
        Mqtt3Validator.newInstance().validate(connectionWithSource("ditto/topic/+/123"), DittoHeaders.empty(),
                actorSystem);
        Mqtt3Validator.newInstance().validate(connectionWithSource("test%2Fde/#"), DittoHeaders.empty(), actorSystem);
        Mqtt3Validator.newInstance().validate(connectionWithSource("!$%&/#"), DittoHeaders.empty(), actorSystem);
        Mqtt3Validator.newInstance().validate(connectionWithSource("ditto/#"), DittoHeaders.empty(), actorSystem);
        Mqtt3Validator.newInstance().validate(connectionWithSource("#"), DittoHeaders.empty(), actorSystem);
        Mqtt3Validator.newInstance().validate(connectionWithSource("+"), DittoHeaders.empty(), actorSystem);
    }

    @Test
    public void testInvalidSourceAddress() {
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("ditto/topic/+123"));
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithSource("ditto/+123"));
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
        final Source sourceWithInvalidConsumerCount = ConnectivityModelFactory.newSourceBuilder()
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
                .isThrownBy(() -> Mqtt3Validator.newInstance()
                        .validate(connectionWithInsecureProtocolAndTrustedCertificates, DittoHeaders.empty(),
                                actorSystem));
    }

    @Test
    public void testValidTargetAddress() {
        Mqtt3Validator.newInstance().validate(connectionWithTarget("ditto/mqtt/topic"), DittoHeaders.empty(),
                actorSystem);
        Mqtt3Validator.newInstance().validate(connectionWithTarget("ditto"), DittoHeaders.empty(), actorSystem);
        Mqtt3Validator.newInstance().validate(connectionWithTarget("ditto/{{thing:id}}"), DittoHeaders.empty(),
                actorSystem);
        Mqtt3Validator.newInstance().validate(connectionWithTarget("ditto/{{topic:full}}"), DittoHeaders.empty(),
                actorSystem);
        Mqtt3Validator.newInstance().validate(connectionWithTarget("ditto/{{header:x}}"), DittoHeaders.empty(),
                actorSystem);
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
        final Source mqttSourceWithValidFilter = ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .enforcement(newSourceAddressEnforcement("things/+/{{ thing:id }}/#"))
                .address("#")
                .qos(1)
                .build();
        final Source mqttSourceWithInvalidFilter = ConnectivityModelFactory.newSourceBuilder()
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

        Mqtt3Validator.newInstance().validate(connection, DittoHeaders.empty(), actorSystem);
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
        final Connection connection = connectionWithSource("ditto/#").toBuilder()
                .specificConfig(Map.of(
                        MqttSpecificConfig.LAST_WILL_TOPIC, "topic",
                        MqttSpecificConfig.LAST_WILL_QOS, "5"
                ))
                .build();

        verifyConnectionConfigurationInvalidExceptionIsThrown(connection)
                .withMessageContaining(MqttSpecificConfig.LAST_WILL_QOS);
    }

}
