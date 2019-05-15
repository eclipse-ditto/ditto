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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.supervision.WithSupervisorConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's connection behaviour.
 * <p>
 * Java serialization is supported for {@code ConnectionConfig}.
 * </p>
 */
@Immutable
public interface ConnectionConfig extends WithSupervisorConfig {

    /**
     * Returns the delay between subscribing to Akka pub/sub and responding to the command that triggered the
     * subscription.
     * The delay gives Akka pub/sub a chance to reach consensus in the cluster before clients start expecting
     * messages and events.
     * The default value is {@code 5s}.
     *
     * @return the timeout.
     */
    Duration getFlushPendingResponsesTimeout();

    /**
     * Returns the amount of time for how long the connection actor waits for response from client actors.
     * By default this value is very high because connection establishment can take very long.
     * If the timeout was set too low early, the connection would not be subscribed for events properly.
     *
     * @return the timeout.
     */
    Duration getClientActorAskTimeout();

    /**
     * Returns the config of the connection snapshotting behaviour.
     *
     * @return the config.
     */
    SnapshotConfig getSnapshotConfig();

    /**
     * Returns the config specific to the MQTT protocol.
     *
     * @return the config.
     */
    MqttConfig getMqttConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ConnectionConfig}.
     */
    enum ConnectionConfigValue implements KnownConfigValue {

        /**
         * The delay between subscribing to Akka pub/sub and responding to the command that triggered the subscription.
         */
        FLUSH_PENDING_RESPONSES_TIMEOUT("flush-pending-responses-timeout", Duration.ofSeconds(5L)),

        /**
         * The amount of time for how long the connection actor waits for response from client actors.
         */
        CLIENT_ACTOR_ASK_TIMEOUT("client-actor-ask-timeout", Duration.ofSeconds(60L));

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

    /**
     * Provides configuration settings for the snapshotting behaviour.
     * <p>
     * Java serialization is supported for {@code SnapshotConfig}.
     * </p>
     */
    @Immutable
    interface SnapshotConfig {

        /**
         * Returns the amount of changes after which a snapshot of the connection status is created.
         *
         * @return the snapshot threshold.
         */
        int getThreshold();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code SnapshotConfig}.
         */
        enum SnapshotConfigValue implements KnownConfigValue {

            /**
             * The amount of changes after which a snapshot of the connection status is created.
             */
            THRESHOLD("threshold", 10);

            private final String path;
            private final Object defaultValue;

            private SnapshotConfigValue(final String thePath, final Object theDefaultValue) {
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

    /**
     * Provides configuration settings of the MQTT protocol.
     * <p>
     * Java serialization is supported for {@code MqttConfig}.
     * </p>
     */
    @Immutable
    interface MqttConfig {

        /**
         * Returns the maximum number of buffered messages for each MQTT source.
         *
         * @return the buffer size.
         */
        int getSourceBufferSize();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code MqttConfig}.
         */
        enum MqttConfigValue implements KnownConfigValue {

            /**
             * The maximum number of buffered messages for each MQTT source.
             */
            SOURCE_BUFFER_SIZE("source-buffer-size", 8);

            private final String path;
            private final Object defaultValue;

            private MqttConfigValue(final String thePath, final Object theDefaultValue) {
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

}
