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

import javax.net.ssl.SSLSocketFactory;

import akka.stream.alpakka.mqtt.MqttConnectionSettings;
import scala.Some;

/**
  * Extends the {@code MqttConnectionSettings} by an {@code SSLSocketFactory}.
  */
final class SocketFactoryExtension {

    private SocketFactoryExtension() {
        throw new AssertionError();
    }

    /**
     * Extend the MQTT connection settings by adding an SSL socket factory.
     *
     * @param s the MQTT connection settings.
     * @param sf the SSL socket factory.
     * @return a copy of {@code s} with {@code sf}.
     */
    static MqttConnectionSettings withSocketFactory(final MqttConnectionSettings s, final SSLSocketFactory sf) {
        // with Alpakka-MQTT 1.0 this will become:
        //        return s.withSocketFactory(sf);
        return s.copy(s.broker(), s.clientId(), s.persistence(), s.auth(), Some.apply(sf), s.cleanSession(), s.will(),
                s.automaticReconnect(), s.keepAliveInterval(), s.connectionTimeout(), s.disconnectQuiesceTimeout(),
                s.disconnectTimeout(), s.maxInFlight(), s.mqttVersion(), s.serverUris(), s.sslHostnameVerifier(),
                s.sslProperties());
    }

}
