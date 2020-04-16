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

import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;

import akka.actor.ActorSystem;

/**
 * Connection specification for Mqtt 3.1.1 protocol.
 */
@Immutable
public final class Mqtt3Validator extends AbstractMqttValidator {

    /**
     * Create a new {@code Mqtt3Validator}.
     *
     * @return a new instance.
     */
    public static Mqtt3Validator newInstance() {
        return new Mqtt3Validator();
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

  private static void validateClientCount(final Connection connection, final DittoHeaders dittoHeaders) {
    if (connection.getClientCount() > 1) {
      throw ConnectionConfigurationInvalidException
              .newBuilder("Client count limited to 1 for MQTT 3.1.1 connections.")
              .description("MQTT 3.1.1 does not support load-balancing; starting more than 1 client will only " +
                      "result in duplicate incoming messages.")
              .dittoHeaders(dittoHeaders)
              .build();
    }
  }

    @Override
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {
        super.validateSource(source, dittoHeaders, sourceDescription);

        if (source.getHeaderMapping().isPresent()) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                    "Header mapping is not supported for MQTT 3.1.1 sources.")
                    .dittoHeaders(dittoHeaders).build();
        }
        validateConsumerCount(source, dittoHeaders);
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {
        super.validateTarget(target, dittoHeaders, targetDescription);

        if (target.getHeaderMapping().isPresent()) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                    "Header mapping is not supported for MQTT 3.1.1 targets.")
                    .dittoHeaders(dittoHeaders).build();
        }
    }

    private static void validateConsumerCount(final Source source, final DittoHeaders dittoHeaders) {
        if (source.getConsumerCount() > 1) {
            throw ConnectionConfigurationInvalidException
                    .newBuilder("Consumer count limited to 1 for MQTT 3.1.1 connections.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }
}
