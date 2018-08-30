/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.MqttSource;

import akka.Done;
import akka.japi.Pair;
import akka.stream.alpakka.mqtt.MqttConnectionSettings;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.stream.alpakka.mqtt.MqttSourceSettings;
import akka.stream.alpakka.mqtt.javadsl.MqttSink;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.collection.JavaConverters;
import scala.collection.Seq;

/**
 * Create MQTT sources and sinks.
 */
final class DefaultMqttConnectionFactory implements MqttConnectionFactory {

    private final Connection connection;
    private final MqttConnectionSettings settings;

    DefaultMqttConnectionFactory(final Connection connection) {
        this.connection = connection;
        settings = MqttConnectionSettingsFactory.getInstance().createMqttConnectionSettings(connection);
    }

    @Override
    public String connectionId() {
        return connection.getId();
    }

    @Override
    public Source<MqttMessage, CompletionStage<Done>> newSource(final MqttSource mqttSource,
            final int bufferSize) {

        final String clientId = connectionId() + "-source" + mqttSource.getIndex();
        final MqttSourceSettings sourceSettings =
                MqttSourceSettings.create(settings.withClientId(clientId))
                        .withSubscriptions(getSubscriptions(mqttSource));
        return akka.stream.alpakka.mqtt.javadsl.MqttSource.atMostOnce(sourceSettings, bufferSize);
    }

    @Override
    public Sink<MqttMessage, CompletionStage<Done>> newSink() {
        final String clientId = connectionId() + "-publisher";
        return MqttSink.create(settings.withClientId(clientId), MqttQoS.atMostOnce());
    }

    private static Seq<Pair<String, MqttQoS>> getSubscriptions(final MqttSource mqttSource) {
        final int qos = mqttSource.getQos();
        final MqttQoS mqttQos = MqttValidator.getQoS(qos);
        final List<Pair<String, MqttQoS>> subscriptions =
                mqttSource.getAddresses()
                        .stream()
                        .map(sourceAddress -> Pair.create(sourceAddress, mqttQos))
                        .collect(Collectors.toList());
        return JavaConverters.asScalaBuffer(subscriptions);
    }
}
