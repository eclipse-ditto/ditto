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
package org.eclipse.ditto.services.connectivity.messaging.mqtt

import akka.stream.alpakka.mqtt.MqttConnectionSettings
import javax.net.ssl.SSLSocketFactory

/**
  * Extends the MqttConnectionSettings by adding a {@code withSocketFactory} method.
  */
object SocketFactoryExtension {

  implicit class SocketFactoryExt(val s: MqttConnectionSettings) {
    def withSocketFactory(sf: SSLSocketFactory): MqttConnectionSettings =
      s.copy(s.broker, s.clientId, s.persistence, s.auth, Some(sf), s.cleanSession, s.will, s.automaticReconnect, s.keepAliveInterval, s.connectionTimeout, s.disconnectQuiesceTimeout, s.disconnectTimeout, s.maxInFlight, s.mqttVersion, s.serverUris, s.sslHostnameVerifier, s.sslProperties)
  }

}