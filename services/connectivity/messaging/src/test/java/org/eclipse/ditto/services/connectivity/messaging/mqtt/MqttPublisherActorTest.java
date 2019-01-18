/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.qpid.jms.message.JmsMessage;
import org.awaitility.Awaitility;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.AbstractPublisherActorTest;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.javadsl.Sink;
import akka.testkit.TestProbe;

public class MqttPublisherActorTest extends AbstractPublisherActorTest<JmsMessage> {

    public static final String OUTBOUND_ADDRESS = "mqtt/eclipse/ditto";
    private TestProbe probe;
    private MqttConnectionFactory mqttConnectionFactory;
    private List<MqttMessage> received = new LinkedList<>();

    @Override
    protected void setupMocks(final TestProbe probe) {
        this.probe = probe;
        mqttConnectionFactory = mock(MqttConnectionFactory.class);
        when(mqttConnectionFactory.newSink()).thenReturn(Sink.foreach(received::add));
    }

    @Override
    protected void publisherCreated(final ActorRef publisherActor) {
        //
    }

    @Override
    protected Props getPublisherActorProps() {
        return MqttPublisherActor.props("theConnection", Collections.emptySet(), mqttConnectionFactory, probe.ref(), false);
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return ConnectivityModelFactory.newTarget(target, OUTBOUND_ADDRESS, 0);
    }

    @Override
    protected void verifyPublishedMessage() {
        Awaitility.await().until(() -> received.size()>0);
        assertThat(received).hasSize(1);
        final MqttMessage mqttMessage = received.get(0);
        assertThat(mqttMessage.topic()).isEqualTo(getOutboundAddress());
        assertThat(mqttMessage.payload().utf8String()).isEqualTo("payload");
        // MQTT 3.1.1 does not support headers - the workaround with property bag is not (yet) implemented
    }

    protected String getOutboundAddress() {
        return OUTBOUND_ADDRESS;
    }

}
