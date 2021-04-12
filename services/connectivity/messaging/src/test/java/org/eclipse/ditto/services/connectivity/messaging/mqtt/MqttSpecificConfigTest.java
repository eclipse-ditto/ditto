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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig}.
 */
public final class MqttSpecificConfigTest {

    @Test
    public void parseMqttSpecificConfig() {
        // GIVEN
        final Map<String, String> configuredSpecificConfig = new HashMap<>();
        configuredSpecificConfig.put("reconnectForRedelivery", "false");
        configuredSpecificConfig.put("separatePublisherClient", "false");
        configuredSpecificConfig.put("clientId", "consumer-client-id");
        configuredSpecificConfig.put("publisherId", "publisher-client-id");
        configuredSpecificConfig.put("reconnectForRedeliveryDelay", "4m");
        configuredSpecificConfig.put("lastWillTopic", "lastWillTopic");
        configuredSpecificConfig.put("lastWillQos", "1");
        configuredSpecificConfig.put("lastWillMessage", "last will message");
        configuredSpecificConfig.put("lastWillRetain", "true");

        // WHEN
        final MqttSpecificConfig specificConfig = new MqttSpecificConfig(configuredSpecificConfig);

        // THEN
        assertThat(specificConfig.reconnectForRedelivery()).isFalse();
        assertThat(specificConfig.separatePublisherClient()).isFalse();
        assertThat(specificConfig.getMqttClientId()).contains("consumer-client-id");
        assertThat(specificConfig.getMqttPublisherId()).contains("publisher-client-id");
        assertThat(specificConfig.getReconnectForDeliveryDelay()).isEqualTo(Duration.ofMinutes(4L));

        assertThat(specificConfig.getMqttWillTopic()).contains("lastWillTopic");
        assertThat(specificConfig.getMqttWillQos()).isEqualTo(1);
        assertThat(specificConfig.getMqttWillRetain()).isEqualTo(true);
        assertThat(specificConfig.getMqttWillMessage()).contains("last will message");
    }

    @Test
    public void defaultConfig() {
        final MqttSpecificConfig specificConfig = new MqttSpecificConfig(Collections.emptyMap());
        assertThat(specificConfig.reconnectForRedelivery()).isTrue();
        assertThat(specificConfig.separatePublisherClient()).isTrue();
        assertThat(specificConfig.getMqttClientId()).isEmpty();
        assertThat(specificConfig.getMqttPublisherId()).isEmpty();
        assertThat(specificConfig.getReconnectForDeliveryDelay()).isEqualTo(Duration.ofSeconds(2L));
        assertThat(specificConfig.getMqttWillTopic()).isEmpty();
        assertThat(specificConfig.getMqttWillQos()).isEqualTo(0);
        assertThat(specificConfig.getMqttWillMessage()).isEmpty();
        assertThat(specificConfig.getMqttWillRetain()).isFalse();
    }
}
