/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;

import akka.Done;
import akka.japi.Pair;
import akka.stream.alpakka.mqtt.MqttConnectionSettings;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.stream.alpakka.mqtt.MqttSourceSettings;
import akka.stream.alpakka.mqtt.javadsl.MqttSink;
import akka.stream.javadsl.Sink;
import scala.collection.JavaConverters;
import scala.collection.Seq;

/**
 * Create MQTT sources and sinks.
 */
final class DefaultMqttConnectionFactory implements MqttConnectionFactory {

    // user should set qos for sources. the default is qos=2 for convenience
    private static final Integer DEFAULT_SOURCE_QOS = 2;

    private final Connection connection;
    private final MqttConnectionSettings settings;

    DefaultMqttConnectionFactory(final Connection connection, final DittoHeaders dittoHeaders) {
        this.connection = connection;
        settings = MqttConnectionSettingsFactory.getInstance().createMqttConnectionSettings(connection, dittoHeaders);
    }

    @Override
    public String connectionId() {
        return connection.getId();
    }

    @Override
    public akka.stream.javadsl.Source<MqttMessage, CompletionStage<Done>> newSource(final Source mqttSource,
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

    private static Seq<Pair<String, MqttQoS>> getSubscriptions(final Source mqttSource) {
        final int qos = mqttSource.getQos().orElse(DEFAULT_SOURCE_QOS);
        final MqttQoS mqttQos = MqttValidator.getQoS(qos);
        final List<Pair<String, MqttQoS>> subscriptions =
                mqttSource.getAddresses()
                        .stream()
                        .map(sourceAddress -> Pair.create(sourceAddress, mqttQos))
                        .collect(Collectors.toList());
        return JavaConverters.asScalaBuffer(subscriptions);
    }
}
