/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.mqtt.IllegalReceiveMaximumValueException;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;

/**
 * Tests {@link MqttSpecificConfig}.
 */
public final class MqttSpecificConfigTest {

    private MqttConfig mqttConfig;
    private Connection connection;

    @Before
    public void setup() throws IllegalReceiveMaximumValueException {
        mqttConfig = Mockito.mock(MqttConfig.class);
        when(mqttConfig.getReconnectForRedeliveryDelay()).thenReturn(Duration.ofSeconds(2));
        when(mqttConfig.shouldUseSeparatePublisherClient()).thenReturn(false);
        when(mqttConfig.shouldReconnectForRedelivery()).thenReturn(false);
        connection = Mockito.mock(Connection.class);
    }

    @Test
    public void parseMqttSpecificConfig() throws IllegalKeepAliveIntervalSecondsException {

        // GIVEN
        final Map<String, String> configuredSpecificConfig = new HashMap<>();
        configuredSpecificConfig.put("reconnectForRedelivery", "false");
        configuredSpecificConfig.put("separatePublisherClient", "false");
        configuredSpecificConfig.put("clientId", "consumer-client-id");
        configuredSpecificConfig.put("publisherId", "publisher-client-id");
        configuredSpecificConfig.put("reconnectForRedeliveryDelay", "4m");
        configuredSpecificConfig.put("keepAlive", "30s");
        configuredSpecificConfig.put("lastWillTopic", "lastWillTopic");
        configuredSpecificConfig.put("lastWillQos", "1");
        configuredSpecificConfig.put("lastWillMessage", "last will message");
        configuredSpecificConfig.put("lastWillRetain", "true");

        // WHEN
        when(connection.getSpecificConfig()).thenReturn(configuredSpecificConfig);
        final var specificConfig = MqttSpecificConfig.fromConnection(connection, mqttConfig);

        // THEN
        assertThat(specificConfig.reconnectForRedelivery()).isFalse();
        assertThat(specificConfig.isSeparatePublisherClient()).isFalse();
        assertThat(specificConfig.getMqttClientId()).contains("consumer-client-id");
        assertThat(specificConfig.getMqttPublisherId()).contains("publisher-client-id");
        assertThat(specificConfig.getReconnectForDeliveryDelay())
                .isEqualTo(ReconnectDelay.ofOrLowerBoundary(Duration.ofMinutes(4L)));
        assertThat(specificConfig.getKeepAliveIntervalOrDefault())
                .isEqualTo(KeepAliveInterval.of(Duration.ofSeconds(30L)));
        assertThat(specificConfig.getMqttLastWillTopic()).contains(MqttTopic.of("lastWillTopic"));
        assertThat(specificConfig.getLastWillQosOrThrow()).isEqualTo(MqttQos.AT_LEAST_ONCE);
        assertThat(specificConfig.getMqttWillRetain()).isTrue();
        assertThat(specificConfig.getMqttWillMessage()).contains("last will message");
    }

    @Test
    public void defaultConfig() throws IllegalKeepAliveIntervalSecondsException {
        when(connection.getSpecificConfig()).thenReturn(Collections.emptyMap());
        final var specificConfig = MqttSpecificConfig.fromConnection(connection, mqttConfig);

        assertThat(specificConfig.reconnectForRedelivery()).isFalse();
        assertThat(specificConfig.isSeparatePublisherClient()).isFalse();
        assertThat(specificConfig.getMqttClientId()).isEmpty();
        assertThat(specificConfig.getMqttPublisherId()).isEmpty();
        assertThat(specificConfig.getReconnectForDeliveryDelay())
                .isEqualTo(ReconnectDelay.ofOrLowerBoundary(Duration.ofSeconds(2L)));
        assertThat(specificConfig.getKeepAliveIntervalOrDefault()).isEqualTo(KeepAliveInterval.defaultKeepAlive());
        assertThat(specificConfig.getMqttLastWillTopic()).isEmpty();
        assertThat(specificConfig.getLastWillQosOrThrow()).isEqualTo(MqttSpecificConfig.DEFAULT_LAST_WILL_QOS);
        assertThat(specificConfig.getMqttWillMessage()).isEmpty();
        assertThat(specificConfig.getMqttWillRetain()).isFalse();
    }

}
