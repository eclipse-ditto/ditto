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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;

import akka.actor.ActorSystem;

/**
 * Connection specification for Mqtt protocol.
 */
@Immutable
public final class MqttValidator extends AbstractMqttValidator {

  /**
   * Create a new {@code MqttConnectionSpec}.
   *
   * @return a new instance.
   */
  public static MqttValidator newInstance() {
    return new MqttValidator();
  }

  @Override
  public ConnectionType type() {
    return ConnectionType.MQTT;
  }

  @Override
  public void validate(final Connection connection, final DittoHeaders dittoHeaders, final ActorSystem actorSystem) {
    validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "MQTT 3.1.1");
    validateClientCount(connection, dittoHeaders);
    validateAddresses(connection, dittoHeaders);
    validateSourceConfigs(connection, dittoHeaders);
    validateTargetConfigs(connection, dittoHeaders);
    validatePayloadMappings(connection, actorSystem, dittoHeaders);
  }
}
