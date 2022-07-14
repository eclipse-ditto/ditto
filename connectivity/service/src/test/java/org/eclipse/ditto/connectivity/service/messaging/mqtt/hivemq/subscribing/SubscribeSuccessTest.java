/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.stream.Stream;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;

import akka.stream.javadsl.Source;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SubscribeSuccess}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class SubscribeSuccessTest {

    private static final MqttTopic MQTT_TOPIC_SOURCE_STATUS = MqttTopic.of("source/status");
    private static final MqttTopic MQTT_TOPIC_SOURCE_TEMPERATURE = MqttTopic.of("source/thermostat/temperature");

    @Mock private org.eclipse.ditto.connectivity.model.Source connectionSource;

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubscribeSuccess.class)
                .usingGetClass()
                .withPrefabValues(Source.class,
                        Source.single(
                                GenericMqttPublish.builder(
                                        MQTT_TOPIC_SOURCE_STATUS,
                                        MqttQos.AT_LEAST_ONCE
                                ).build()
                        ),
                        Source.single(
                                GenericMqttPublish.builder(
                                        MQTT_TOPIC_SOURCE_TEMPERATURE,
                                        MqttQos.AT_MOST_ONCE
                                ).build()
                        ))
                .verify();
    }

    @Test
    public void newInstanceWithNullConnectionSourceThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SubscribeSuccess.newInstance(null, Source.empty()))
                .withMessage("The connectionSource must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullMqttPublishSourceThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SubscribeSuccess.newInstance(connectionSource, null))
                .withMessage("The mqttPublishSource must not be null!")
                .withNoCause();
    }

    @Test
    public void isSuccessReturnsTrue() {
        final var underTest = SubscribeSuccess.newInstance(connectionSource, Source.empty());

        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void isFailureReturnsFalse() {
        final var underTest = SubscribeSuccess.newInstance(connectionSource, Source.empty());

        assertThat(underTest.isFailure()).isFalse();
    }

    @Test
    public void getConnectionSourceReturnsExpected() {
        final var underTest = SubscribeSuccess.newInstance(connectionSource, Source.empty());

        assertThat(underTest.getConnectionSource()).isEqualTo(connectionSource);
    }

    @Test
    public void getMqttPublishSourceReturnsExpected() {
        final var mqttPublishSource = Source.fromJavaStream(
                () -> Stream.of(
                        GenericMqttPublish.builder(MQTT_TOPIC_SOURCE_STATUS, MqttQos.AT_LEAST_ONCE).build(),
                        GenericMqttPublish.builder(MQTT_TOPIC_SOURCE_TEMPERATURE, MqttQos.AT_MOST_ONCE).build()
                )
        );
        final var underTest = SubscribeSuccess.newInstance(connectionSource, mqttPublishSource);

        assertThat(underTest.getMqttPublishSourceOrThrow()).isEqualTo(mqttPublishSource);
    }

    @Test
    public void getErrorOrThrowThrowsException() {
        final var underTest = SubscribeSuccess.newInstance(connectionSource, Source.empty());

        assertThatIllegalStateException()
                .isThrownBy(underTest::getErrorOrThrow)
                .withMessage("Success cannot provide an error.")
                .withNoCause();
    }

}