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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.alpakka;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.StreamCorruptedException;
import java.util.List;
import java.util.function.BiFunction;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.AbstractMqttClientActorTest;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;

@RunWith(MockitoJUnitRunner.class)
public final class MqttClientActorTest extends AbstractMqttClientActorTest<MqttMessage> {

    @Override
    protected MqttMessage mqttMessage(final String topic, final String payload) {
        return MqttMessage.create(topic, ByteString.fromArray(payload.getBytes(UTF_8)));
    }

    @Override
    protected Props createClientActor(final ActorRef conciergeForwarder) {
        return mqttClientActor(getConnection(), conciergeForwarder, MockMqttConnectionFactory.with(conciergeForwarder));
    }

    @Override
    protected Props createFailingClientActor(final ActorRef conciergeForwarder) {
        return mqttClientActor(getConnection(), conciergeForwarder, MockMqttConnectionFactory.withError(conciergeForwarder, new StreamCorruptedException("Psalms 38:5")));
    }

    @Override
    protected Props createClientActorWithMessages(final Connection connection,
            final ActorRef testProbe, final List<MqttMessage> messages) {
        return mqttClientActor(connection, testProbe,
                MockMqttConnectionFactory.with(testProbe, messages));
    }

    @Override
    protected String extractPayload(final MqttMessage message) {
        return message.payload().utf8String();
    }

    @Override
    protected String extractTopic(final MqttMessage message) {
        return message.topic();
    }

    @Override
    protected Class<MqttMessage> getMessageClass() {
        return MqttMessage.class;
    }

    private static Props mqttClientActor(final Connection connection, final ActorRef conciergeForwarder,
            final BiFunction<Connection, DittoHeaders, MqttConnectionFactory> factoryCreator) {

        return Props.create(MqttClientActor.class, connection, connection.getConnectionStatus(), conciergeForwarder,
                factoryCreator);
    }
}
