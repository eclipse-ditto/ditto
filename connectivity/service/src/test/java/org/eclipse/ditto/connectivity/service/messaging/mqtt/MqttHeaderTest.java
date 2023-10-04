/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.hivemq.client.mqtt.MqttVersion;

import java.util.List;

/**
 * Unit test for {@link MqttHeader}
 */
public class MqttHeaderTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void getHeaderNamesReturnsExpectedHeadersForMqtt3() {
        final var expected = List.of(
                MqttHeader.MQTT_TOPIC.getName(),
                MqttHeader.MQTT_QOS.getName(),
                MqttHeader.MQTT_RETAIN.getName());
        final var actual = MqttHeader.getHeaderNames(MqttVersion.MQTT_3_1_1);

        softly.assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void getHeaderNamesReturnsExpectedHeadersForMqtt5() {
        final var expected = List.of(
                MqttHeader.MQTT_TOPIC.getName(),
                MqttHeader.MQTT_QOS.getName(),
                MqttHeader.MQTT_RETAIN.getName(),
                MqttHeader.MQTT_MESSAGE_EXPIRY_INTERVAL.getName());
        final var actual = MqttHeader.getHeaderNames(MqttVersion.MQTT_5_0);

        softly.assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void getAllHeaderNamesReturnsExpectedHeaders() {
        final var expected = List.of(
                MqttHeader.MQTT_TOPIC.getName(),
                MqttHeader.MQTT_QOS.getName(),
                MqttHeader.MQTT_RETAIN.getName(),
                MqttHeader.MQTT_MESSAGE_EXPIRY_INTERVAL.getName());
        final var actual = MqttHeader.getAllHeaderNames();

        softly.assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }
}