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

import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newHeadersPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newThingPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newTopicPathPlaceholder;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;

import akka.actor.ActorSystem;

/**
 * Connection specification for Mqtt protocol.
 */
@Immutable
public final class Mqtt5Validator extends AbstractMqttValidator {

  /**
   * Create a new {@code MqttConnectionSpec}.
   *
   * @return a new instance.
   */
  public static Mqtt5Validator newInstance() {
    return new Mqtt5Validator();
  }

  @Override
  public ConnectionType type() {
    return ConnectionType.MQTT_5;
  }

  @Override
  public void validate(final Connection connection, final DittoHeaders dittoHeaders, final ActorSystem actorSystem) {
    validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "MQTT 5");
    validateClientCount(connection, dittoHeaders);
    validateAddresses(connection, dittoHeaders);
    validateSourceConfigs(connection, dittoHeaders);
    validateTargetConfigs(connection, dittoHeaders);
    validatePayloadMappings(connection, actorSystem, dittoHeaders);
  }

  @Override
  protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
          final Supplier<String> sourceDescription) {
      final Optional<Integer> qos = source.getQos();
      if (!(qos.isPresent())) {
          final String message =
                  MessageFormat.format("MQTT Source {0} must contain QoS value.", sourceDescription.get());
          throw ConnectionConfigurationInvalidException.newBuilder(message)
                  .dittoHeaders(dittoHeaders)
                  .build();
      }

      validateSourceQoS(qos.get(), dittoHeaders, sourceDescription);
      validateSourceEnforcement(source.getEnforcement().orElse(null), dittoHeaders, sourceDescription);
      validateConsumerCount(source, dittoHeaders);
  }

  @Override
  protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
          final Supplier<String> targetDescription) {
      final Optional<Integer> qos = target.getQos();
      if (!(qos.isPresent())) {
          final String message =
                  MessageFormat.format("MQTT Target {0} must contain QoS value.", targetDescription.get());
          throw ConnectionConfigurationInvalidException.newBuilder(message)
                  .dittoHeaders(dittoHeaders)
                  .build();
      }

      validateTargetQoS(qos.get(), dittoHeaders, targetDescription);
      validateTemplate(target.getAddress(), dittoHeaders, newThingPlaceholder(), newTopicPathPlaceholder(),
              newHeadersPlaceholder());
  }
}