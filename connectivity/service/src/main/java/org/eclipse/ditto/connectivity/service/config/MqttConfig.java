/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.connectivity.model.mqtt.ReceiveMaximum;
import org.eclipse.ditto.connectivity.model.mqtt.SessionExpiryInterval;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the MQTT protocol.
 */
@Immutable
public interface MqttConfig {

    /**
     * Returns the number of threads to use for the underlying event loop of the MQTT client.
     * When configured to {@code 0}, the size is determined based on {@code the available processor cores * 2}.
     *
     * @return the amount of event loop threads.
     * @since 2.0.0
     */
    int getEventLoopThreads();

    /**
     * Indicates whether subscriber CONN messages should set clean-session or clean-start flag to true.
     *
     * @return the default setting of cleanSession.
     * @since 2.0.0
     */
    boolean isCleanSession();

    /**
     * Indicates whether the client should reconnect to enforce a redelivery for a failed acknowledgement.
     *
     * @return true if the client should reconnect, false if not.
     * @since 2.0.0
     */
    boolean shouldReconnectForRedelivery();

    /**
     * Duration how long to wait for the next reconnect after a failed acknowledgement.
     *
     * @return the amount of time that a reconnect will be delayed after a failed acknowledgement.
     * @since 2.0.0
     */
    Duration getReconnectForRedeliveryDelay();

    /**
     * Duration how long messages should be buffered by the broker after disconnect.
     *
     * @return the session expiry interval.
     * @since 3.0.0
     */
    SessionExpiryInterval getSessionExpiryInterval();

    /**
     * Indicates whether a separate client should be used for publishing. This could be useful when
     * {@link #shouldReconnectForRedelivery()} returns true to avoid that the publisher has downtimes.
     *
     * @return true if a separate client should be used, false if not.
     * @since 2.0.0
     */
    boolean shouldUseSeparatePublisherClient();

    /**
     * Returns the reconnect backoff configuration to apply when reconnecting failed MQTT connections.
     *
     * @return the reconnect backoff configuration to apply.
     * @since 2.0.0
     */
    BackOffConfig getReconnectBackOffConfig();

    /**
     * Returns the minimum reconnect timeout for when the MQTT broker closed the MQTT connection.
     *
     * @return the minimum reconnect timeout for MQTT broker initiated connection closing.
     * @since 2.1.0
     */
    Duration getReconnectMinTimeoutForMqttBrokerInitiatedDisconnect();

    /**
     * @return maximum number of messages buffered at the publisher actor before dropping them.
     * @since 2.2.0
     */
    int getMaxQueueSize();

    /**
     * Returns the {@code ThrottlingConfig} for the MQTT consumer part.
     *
     * @return the MQTT consumer ThrottlingConfig.
     * @since 2.4.0
     */
    ThrottlingConfig getConsumerThrottlingConfig();

    /**
     * Returns the client Receive Maximum for MQTT 5, i.e. the number of QoS 1 and Qos2 publications the broker is
     * willing to process concurrently for the client.
     *
     * @return a ReceiveMaximum with the configured value or {@link ReceiveMaximum#defaultReceiveMaximum()}.
     */
    ReceiveMaximum getClientReceiveMaximum();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code MqttConfig}.
     */
    enum MqttConfigValue implements KnownConfigValue {

        /**
         * How many messages to buffer in the publisher actor before dropping them.
         */
        MAX_QUEUE_SIZE("max-queue-size", 1000),

        /**
         * The number of threads to use for the underlying event loop of the MQTT client.
         */
        EVENT_LOOP_THREADS("event-loop-threads", 0),

        /**
         * Indicates whether subscriber CONN messages should set clean-session or clean-start flag to true.
         */
        CLEAN_SESSION("clean-session", false),

        /**
         * Indicates whether the client should reconnect to enforce a redelivery for a failed acknowledgement.
         */
        RECONNECT_FOR_REDELIVERY("reconnect-for-redelivery", false),

        /**
         * The amount of time that a reconnect will be delayed after a failed acknowledgement.
         */
        RECONNECT_FOR_REDELIVERY_DELAY("reconnect-for-redelivery-delay", Duration.ofSeconds(10)),

        /**
         * The amount of time that messages of session will be buffered by the broker after disconnect.
         */
        SESSION_EXPIRY_INTERVAL("session-expiry-interval", Duration.ofSeconds(60)),

        /**
         * Indicates whether a separate client should be used for publishing. This could be useful when
         * {@link #shouldReconnectForRedelivery()} returns true to avoid that the publisher has downtimes.
         */
        SEPARATE_PUBLISHER_CLIENT("separate-publisher-client", false),

        /**
         * The minimum reconnect timeout for when the MQTT broker closed the MQTT connection.
         *
         * @since 2.1.0
         */
        RECONNECT_MIN_TIMEOUT_FOR_MQTT_BROKER_INITIATED_DISCONNECT(
                "reconnect.min-timeout-for-mqtt-broker-initiated-disconnect",
                Duration.ofSeconds(1)),

        /**
         * The client Receive Maximum for MQTT 5, i.e. the number of QoS 1 and Qos2 publications the broker is willing
         * to process concurrently for the client.
         */
        CLIENT_RECEIVE_MAXIMUM("receive-maximum-client", ReceiveMaximum.DEFAULT_VALUE);

        private final String path;
        private final Object defaultValue;

        MqttConfigValue(final String thePath, final Object theDefaultValue) {
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
