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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.actor.Props;
import akka.testkit.TestProbe;

public class HiveMqtt5PublisherActorTest extends AbstractMqttPublisherActorTest {

    private static final String OUTBOUND_ADDRESS = "mqtt/eclipse/ditto";
    private final List<Mqtt5Publish> received = new LinkedList<>();
    private Mqtt5Client mqtt5Client;

    @Override
    protected void setupMocks(final TestProbe probe) {
        mqtt5Client = mock(Mqtt5Client.class);
        final Mqtt5AsyncClient asyncClient = mock(Mqtt5AsyncClient.class);
        when(mqtt5Client.toAsync()).thenReturn(asyncClient);
        when(asyncClient.publish(any(Mqtt5Publish.class))).thenAnswer(i -> {
            final Mqtt5Publish msg = i.getArgument(0);
            received.add(msg);
            return CompletableFuture.completedFuture(msg);
        });
    }

    @Override
    protected Props getPublisherActorProps() {
        return HiveMqtt5PublisherActor.props(TestConstants.createConnection(),
                mqtt5Client,
                false,
                mock(ConnectivityStatusResolver.class),
                ConnectivityConfig.of(actorSystem.settings().config()));
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return ConnectivityModelFactory.newTarget(target, OUTBOUND_ADDRESS, 0,
                AcknowledgementLabel.of("please-verify"));
    }

    @Override
    protected void verifyPublishedMessage() {
        verifyPublishedMessage(getOutboundAddress(), DEFAULT_RETAIN, DEFAULT_QOS, Collections.emptySet());
    }

    @Override
    protected void verifyPublishedMessageIsRetained() {
        verifyPublishedMessage(getOutboundAddress(), true, DEFAULT_QOS,
                Collections.singleton(MqttHeader.MQTT_RETAIN.getName()));
    }

    @Override
    protected void verifyPublishedMessageHasQos(final MqttQos expectedQos) {
        verifyPublishedMessage(getOutboundAddress(), DEFAULT_RETAIN, expectedQos,
                Collections.singleton(MqttHeader.MQTT_QOS.getName()));
    }

    @Override
    protected void verifyPublishedMessageHasTopic(final String expectedTopic) {
        verifyPublishedMessage(expectedTopic, DEFAULT_RETAIN, DEFAULT_QOS,
                Collections.singleton(MqttHeader.MQTT_TOPIC.getName()));
    }

    private void verifyPublishedMessage(final String topic, final boolean expectedRetain, final MqttQos expectedQos,
            final Collection<String> additionalExpectedUserProperties) {
        Awaitility.await().until(() -> !received.isEmpty());
        assertThat(received).hasSize(1);
        final Mqtt5Publish mqttMessage = received.get(0);
        assertThat(mqttMessage.getTopic().toString()).isEqualTo(topic);
        assertThat(mqttMessage.getPayload().map(ByteBufferUtils::toUtf8String).orElse(null)).isEqualTo("payload");
        assertThat(mqttMessage.getUserProperties().asList().isEmpty()).isFalse();
        assertThat(mqttMessage.getUserProperties().asList().stream()
                .filter(prop -> !TestConstants.HEADER_MAPPING.getMapping().containsKey(prop.getName().toString()))
                .filter(prop -> !additionalExpectedUserProperties.contains(prop.getName().toString()))
                .count()).isEqualTo(0);
        assertThat(mqttMessage.getUserProperties().asList().stream().anyMatch(
                prop -> prop.getName().toString().equals("eclipse") &&
                        prop.getValue().toString().equals("ditto"))).isTrue();
        assertThat(ByteBufferUtils.toUtf8String(mqttMessage.getCorrelationData().get())).isEqualTo(
                TestConstants.CORRELATION_ID);
        assertThat(mqttMessage.getContentType()).isEmpty();
        assertThat(mqttMessage.isRetain()).isEqualTo(expectedRetain);
        assertThat(mqttMessage.getQos()).isEqualTo(expectedQos);
    }

    @Override
    protected void verifyPublishedMessageToReplyTarget() {
        Awaitility.await().until(() -> !received.isEmpty());
        assertThat(received).hasSize(1);
        final Mqtt5Publish mqttMessage = received.get(0);
        assertThat(mqttMessage.getTopic().toString()).isEqualTo("replyTarget/thing:id");
    }

    @Override
    protected void verifyAcknowledgements(final Supplier<Acknowledgements> ackSupplier) {
        final CompletableFuture<Acknowledgements> acksFuture = CompletableFuture.supplyAsync(ackSupplier);
        final Acknowledgements acks = acksFuture.join();
        for (final Acknowledgement ack : acks.getSuccessfulAcknowledgements()) {
            assertThat(ack.getLabel().toString()).isEqualTo("please-verify");
            assertThat(ack.getHttpStatus()).isEqualTo(HttpStatus.OK);
        }
    }

    @Override
    protected String getOutboundAddress() {
        return OUTBOUND_ADDRESS;
    }

}
