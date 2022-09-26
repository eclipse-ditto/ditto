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
package org.eclipse.ditto.connectivity.service.config;

import java.time.Duration;
import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.WithSupervisorConfig;
import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.EventConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithSnapshotConfig;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.WithCleanupConfig;

/**
 * Provides configuration settings for Connectivity service's connection behaviour.
 */
@Immutable
public interface ConnectionConfig extends WithSupervisorConfig, WithActivityCheckConfig, WithCleanupConfig,
        WithSnapshotConfig {

    /**
     * Returns the amount of time for how long the connection actor waits for response from client actors.
     * By default this value is very high because connection establishment can take very long.
     * If the timeout was set too low early, the connection would not be subscribed for events properly.
     *
     * @return the timeout.
     */
    Duration getClientActorAskTimeout();

    /**
     * Returns how often the connection actor will retry starting a failing client actor. If exceeded, errors will
     * be escalated to the supervisor, which will effectively cause the whole connection to be restarted.
     */
    int getClientActorRestartsBeforeEscalation();

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
     * @return the list of blocked subnets to which outgoing connections are prevented.
     */
    Collection<String> getBlockedSubnets();

    /**
     * @return the regex to use for hostnames to which outgoing connections are prevented.
     */
    String getBlockedHostRegex();

    /**
     * Returns the config of the connection snapshotting behaviour.
     *
     * @return the config.
     */
    SnapshotConfig getSnapshotConfig();

    /**
     * Returns the config of the connection event journal behaviour.
     *
     * @return the config.
     */
    EventConfig getEventConfig();

    /**
     * Returns the maximum number of Targets within a connection.
     *
     * @return the config.
     */
    Integer getMaxNumberOfTargets();

    /**
     * Returns the maximum number of Sources within a connection.
     *
     * @return the config.
     */
    Integer getMaxNumberOfSources();

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
     * Returns the priority update  interval.
     *
     * @return how often to update the priority of a connection.
     */
    Duration getPriorityUpdateInterval();

    /**
     * Whether to start all client actors on one node.
     * Useful for single-connectivity-instance deployments.
     *
     * @return whether to start all client actors on the same node as the connection persistence actor.
     */
    boolean areAllClientActorsOnOneNode();

    /**
     * Get the timeout waiting for responses and acknowledgements during coordinated shutdown.
     *
     * @return The timeout.
     */
    Duration getShutdownTimeout();

    /**
     * Returns the configuration for connection fields encryption.
     *
     * @return the config.
     */
    FieldsEncryptionConfig getFieldsEncryptionConfig();

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
         * How often the connection actor will retry starting a failing client actor before escalation to the supervisor.
         */
        CLIENT_ACTOR_RESTARTS_BEFORE_ESCALATION("client-actor-restarts-before-escalation", 3),

        /**
         * Whether to start all client actors on 1 node.
         */
        ALL_CLIENT_ACTORS_ON_ONE_NODE("all-client-actors-on-one-node", false),

        /**
         * A comma separated list of allowed hostnames to which http requests will be sent.
         */
        ALLOWED_HOSTNAMES("allowed-hostnames", ""),

        /**
         * A comma separated list of blocked hostnames to which no http requests will be sent out.
         */
        BLOCKED_HOSTNAMES("blocked-hostnames", ""),

        /**
         * A comma separated list of blocked subnets to which no http requests will be sent out.
         */
        BLOCKED_SUBNETS("blocked-subnets", ""),

        /**
         * The regex to determine blocked hosts.
         */
        BLOCKED_HOST_REGEX("blocked-host-regex", ""),

        /**
         * The limitation number of sources within a connection.
         */
        MAX_SOURCE_NUMBER("max-source-number", 4),

        /**
         * The limitation number of targets within a connection.
         */
        MAX_TARGET_NUMBER("max-target-number", 4),

        /**
         * How often to attempt acknowledgement label declaration for as long as it is not successful.
         */
        ACK_LABEL_DECLARE_INTERVAL("ack-label-declare-interval", Duration.ofSeconds(10L)),

        /**
         * How often the priority of a connection is getting updated.
         */
        PRIORITY_UPDATE_INTERVAL("priority-update-interval", Duration.ofHours(24L)),

        /**
         * Timeout waiting for responses and acknowledgements during coordinated shutdown.
         */
        SHUTDOWN_TIMEOUT("shutdown-timeout", Duration.ofSeconds(3));


        private final String path;
        private final Object defaultValue;

        ConnectionConfigValue(final String thePath, final Object theDefaultValue) {
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
