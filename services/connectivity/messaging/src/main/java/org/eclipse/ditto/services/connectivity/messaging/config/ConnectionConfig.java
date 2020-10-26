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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.time.Duration;
import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.supervision.WithSupervisorConfig;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;
import org.eclipse.ditto.services.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithActivityCheckConfig;

/**
 * Provides configuration settings for Connectivity service's connection behaviour.
 */
@Immutable
public interface ConnectionConfig extends WithSupervisorConfig, WithActivityCheckConfig {

    /**
     * Returns the amount of time for how long the connection actor waits for response from client actors.
     * By default this value is very high because connection establishment can take very long.
     * If the timeout was set too low early, the connection would not be subscribed for events properly.
     *
     * @return the timeout.
     */
    Duration getClientActorAskTimeout();

    /**
     * @return the list of allowed hostnames to which outgoing connections are allowed. This list overrides the list
     * of blocked hostnames.
     */
    Collection<String> getAllowedHostnames();

    /**
     * @return the list of blocked hostnames to which outgoing connections are prevented.
     * Outgoing connections to private, wildcard, loopback and multicast addresses are also prevented
     * when the list is nonempty.
     */
    Collection<String> getBlockedHostnames();

    /**
     * Returns the config of the connection snapshotting behaviour.
     *
     * @return the config.
     */
    SnapshotConfig getSnapshotConfig();

    /**
     * Returns the config specific to Acknowledgements for connections.
     *
     * @return the config.
     */
    AcknowledgementConfig getAcknowledgementConfig();

    /**
     * Returns the config specific to the AMQP 1.0 protocol.
     *
     * @return the config.
     */
    Amqp10Config getAmqp10Config();

    /**
     * Returns the config specific to the AMQP 0.9.1 protocol.
     *
     * @return the config.
     * @since 1.2.0
     */
    Amqp091Config getAmqp091Config();

    /**
     * Returns the config specific to the MQTT protocol.
     *
     * @return the config.
     */
    MqttConfig getMqttConfig();

    /**
     * Returns the Kafka configuration settings.
     *
     * @return the config.
     */
    KafkaConfig getKafkaConfig();

    /**
     * Returns the configuration for connection type http-push.
     *
     * @return the config.
     */
    HttpPushConfig getHttpPushConfig();

    /**
     * Returns the acknowledgement label declaration interval.
     *
     * @return how often to declare acknowledgement labels for as long as it is not successful.
     */
    Duration getAckLabelDeclareInterval();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ConnectionConfig}.
     */
    enum ConnectionConfigValue implements KnownConfigValue {

        /**
         * The amount of time for how long the connection actor waits for response from client actors.
         */
        CLIENT_ACTOR_ASK_TIMEOUT("client-actor-ask-timeout", Duration.ofSeconds(60L)),

        /**
         * A comma separated list of allowed hostnames to which http requests will be sent.
         */
        ALLOWED_HOSTNAMES("allowed-hostnames", ""),

        /**
         * A comma separated list of blocked hostnames to which no http requests will be sent out.
         */
        BLOCKED_HOSTNAMES("blocked-hostnames", ""),

        /**
         * How often to attempt acknowledgement label declaration for as long as it is not successful.
         */
        ACK_LABEL_DECLARE_INTERVAL("ack-label-declare-interval", Duration.ofSeconds(10L));

        private final String path;
        private final Object defaultValue;

        private ConnectionConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

}
