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

import static java.util.Collections.singletonList;
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
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.SourceBuilder;
import org.eclipse.ditto.connectivity.model.TargetBuilder;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests {@link Mqtt3Validator}.
 */
public final class Mqtt3ValidatorTest extends AbstractMqttValidatorTest {

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
        assertInstancesOf(Mqtt3Validator.class, areImmutable());
    }

    @Test
    public void testValidSourceAddress() {
        Mqtt3Validator.newInstance(mqttConfig).validate(connectionWithSource("ditto/topic/+/123"), DittoHeaders.empty(),
                actorSystem, connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig)
                .validate(connectionWithSource("test%2Fde/#"), DittoHeaders.empty(), actorSystem, connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig)
                .validate(connectionWithSource("!$%&/#"), DittoHeaders.empty(), actorSystem, connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig)
                .validate(connectionWithSource("ditto/#"), DittoHeaders.empty(), actorSystem, connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig).validate(connectionWithSource("#"), DittoHeaders.empty(), actorSystem,
                connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig).validate(connectionWithSource("+"), DittoHeaders.empty(), actorSystem,
                connectivityConfig);
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
                .isThrownBy(() -> Mqtt3Validator.newInstance(mqttConfig)
                        .validate(connectionWithInsecureProtocolAndTrustedCertificates, DittoHeaders.empty(),
                                actorSystem, connectivityConfig));
    }

    @Test
    public void testValidTargetAddress() {
        Mqtt3Validator.newInstance(mqttConfig).validate(connectionWithTarget("ditto/mqtt/topic"), DittoHeaders.empty(),
                actorSystem, connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto"), DittoHeaders.empty(), actorSystem, connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto/{{thing:id}}"), DittoHeaders.empty(), actorSystem,
                        connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto/{{topic:full}}"), DittoHeaders.empty(), actorSystem,
                        connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("ditto/{{header:x}}"), DittoHeaders.empty(), actorSystem,
                        connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("device/{{entity:id}}"), DittoHeaders.empty(), actorSystem,
                        connectivityConfig);
        Mqtt3Validator.newInstance(mqttConfig)
                .validate(connectionWithTarget("connection/{{connection:id}}"), DittoHeaders.empty(), actorSystem,
                        connectivityConfig);
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
    public void testInvalidSourceEnforcementFilters() {
        final Source mqttSourceWithValidFilter = ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .enforcement(newSourceAddressEnforcement("things/+/{{ thing:id }}/#"))
                .address("#")
                .qos(1)
                .build();
        final Source mqttSourceWithInvalidFilter = ConnectivityModelFactory.newSourceBuilder()
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

    @Test
    public void testInvalidSourceMappingValue() {
        final HeaderMapping invalidHeaderMapping = ConnectivityModelFactory.newHeaderMapping(Map.of(
                "topic", "{{ header:mqtt.topic }}",
                "invalid", "{{ header:invalid }}",
                "qos", "{{ header:mqtt.qos }}"
        ));
        testSourceMapping(invalidHeaderMapping, "header:invalid");
    }

    @Test
    public void testValidSourceMappingKeys() {
        final HeaderMapping validHeaderMapping = ConnectivityModelFactory.newHeaderMapping(Map.of(
                "mqtt.topic", "{{ header:mqtt.topic }}",
                "mqtt.qos", "{{ header:mqtt.qos }}",
                "mqtt.retain", "{{ header:mqtt.retain }}",
                "custom.topic", "{{ header:mqtt.topic }}",
                "custom.qos", "{{ header:mqtt.qos }}",
                "custom.retain", "{{ header:mqtt.retain }}"
        ));
        final Connection connection = getConnectionWithSourceHeaderMapping(validHeaderMapping);
        Mqtt3Validator.newInstance(mqttConfig).validate(connection, DittoHeaders.empty(), actorSystem,
                connectivityConfig);
    }

    @Test
    public void testAllowedTargetMappingKeys() {
        final HeaderMapping validHeaderMapping = ConnectivityModelFactory.newHeaderMapping(Map.of(
                "mqtt.topic", "{{ header:mqtt.topic }}",
                "mqtt.qos", "{{ header:mqtt.custom.qos }}",
                "mqtt.retain", "false"
        ));
        final Connection connection = getConnectionWithTargetMapping(validHeaderMapping);
        Mqtt3Validator.newInstance(mqttConfig).validate(connection, DittoHeaders.empty(), actorSystem,
                connectivityConfig);
    }

    @Test
    public void testInvalidTargetMappingKeys() {
        final HeaderMapping invalidHeaderMapping =
                ConnectivityModelFactory.newHeaderMapping(Map.of("topic", "{{ header:mqtt.topic }}"));
        testTargetMapping(invalidHeaderMapping, "topic");
    }

    @Test
    public void testInvalidPlaceholderTargetMapping() {
        final HeaderMapping invalidHeaderMapping = ConnectivityModelFactory.newHeaderMapping(Map.of(
                "mqtt.topic", "{{ thing:id }}"
        ));
        testTargetMapping(invalidHeaderMapping, "thing:id");
    }

    private void testSourceMapping(final HeaderMapping headerMapping, final String containedInMessage) {
        final Connection connectionWithHeaderMapping =
                getConnectionWithSourceHeaderMapping(headerMapping);

        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithHeaderMapping).withMessageContaining(
                containedInMessage);
    }

    private Connection getConnectionWithSourceHeaderMapping(final HeaderMapping headerMapping) {
        final Connection connection = connectionWithSource("ditto/#");
        return connection.toBuilder()
                .setSources(connection.getSources().stream()
                        .map(ConnectivityModelFactory::newSourceBuilder)
                        .map(sb -> sb.headerMapping(headerMapping))
                        .map(SourceBuilder::build)
                        .toList()).build();
    }

    private void testTargetMapping(final HeaderMapping headerMapping, final String containedInMessage) {
        final Connection connectionWithHeaderMapping = getConnectionWithTargetMapping(headerMapping);
        verifyConnectionConfigurationInvalidExceptionIsThrown(connectionWithHeaderMapping)
                .withMessageContaining(containedInMessage);
    }

    private Connection getConnectionWithTargetMapping(final HeaderMapping headerMapping) {
        final Connection connection = connectionWithTarget("ditto");
        return connection.toBuilder()
                .setTargets(connection.getTargets().stream()
                        .map(ConnectivityModelFactory::newTargetBuilder)
                        .map(sb -> sb.headerMapping(headerMapping))
                        .map(TargetBuilder::build)
                        .toList()).build();
    }

    @Override
    protected ThrowableAssertAlternative<ConnectionConfigurationInvalidException>
    verifyConnectionConfigurationInvalidExceptionIsThrown(final Connection connection) {
        return Assertions.assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> Mqtt3Validator.newInstance(mqttConfig)
                        .validate(connection, DittoHeaders.empty(), actorSystem, connectivityConfig));
    }
}
