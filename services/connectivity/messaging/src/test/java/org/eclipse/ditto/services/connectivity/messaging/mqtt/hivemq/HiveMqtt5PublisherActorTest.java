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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.awaitility.Awaitility;
import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.actor.Props;
import akka.testkit.TestProbe;

public class HiveMqtt5PublisherActorTest extends AbstractPublisherActorTest {

    private static final String OUTBOUND_ADDRESS = "mqtt/eclipse/ditto";
    private List<Mqtt5Publish> received = new LinkedList<>();
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
        return HiveMqtt5PublisherActor.props(TestConstants.createConnection(), mqtt5Client, false);
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return ConnectivityModelFactory.newTarget(target, OUTBOUND_ADDRESS, 0);
    }

    @Override
    protected void verifyPublishedMessage() {
        Awaitility.await().until(() -> received.size() > 0);
        assertThat(received).hasSize(1);
        final Mqtt5Publish mqttMessage = received.get(0);
        assertThat(mqttMessage.getTopic().toString()).isEqualTo(getOutboundAddress());
        assertThat(mqttMessage.getPayload().map(ByteBufferUtils::toUtf8String).orElse(null)).isEqualTo("payload");
        assertThat(mqttMessage.getUserProperties().asList().isEmpty()).isFalse();
        assertThat(mqttMessage.getUserProperties().asList().stream()
                .filter(prop -> !TestConstants.HEADER_MAPPING.getMapping().containsKey(prop.getName().toString()))
                .count()).isEqualTo(0);
        assertThat(mqttMessage.getUserProperties().asList().stream().anyMatch(
                prop -> prop.getName().toString().equals("eclipse") && prop.getValue().toString().equals("ditto"))).isTrue();
        assertThat(ByteBufferUtils.toUtf8String(mqttMessage.getCorrelationData().get())).isEqualTo(TestConstants.CORRELATION_ID);
        assertThat(mqttMessage.getContentType().get().toString()).isEqualTo("application/vnd.eclipse.ditto+json");
    }

    @Override
    protected void verifyPublishedMessageToReplyTarget() {
        Awaitility.await().until(() -> received.size() > 0);
        assertThat(received).hasSize(1);
        final Mqtt5Publish mqttMessage = received.get(0);
        assertThat(mqttMessage.getTopic().toString()).isEqualTo("replyTarget/thing:id");
    }

    protected String getOutboundAddress() {
        return OUTBOUND_ADDRESS;
    }

}
