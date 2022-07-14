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

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's client.
 */
public interface ClientConfig {

    /**
     * Initial timeout when connecting to a remote system. If the connection could not be established after this time,
     * the
     * service will try to reconnect. If a failure happened during connecting, then the service will wait for at least
     * this
     * time until it will try to reconnect. The max timeout is defined in {@link ClientConfig#getConnectingMaxTimeout()}.
     *
     * @return the minimum connecting timeout.
     */
    Duration getConnectingMinTimeout();

    /**
     * Max timeout (until reconnecting) when connecting to a remote system. See docs on {@link
     * ClientConfig#getConnectingMinTimeout()}
     * for more information on the concept.
     *
     * @return the maximum backoff.
     * @see ClientConfig#getConnectingMinTimeout()
     */
    Duration getConnectingMaxTimeout();

    /**
     * Max timeout (when actually disconnecting) until we're assuming disconnecting failed.
     *
     * @return the maximum disconnecting timeout.
     * @see ClientConfig#getDisconnectAnnouncementTimeout()
     */
    Duration getDisconnectingMaxTimeout();

    /**
     * Time that will be waited between sending a disconnect announcement and actually disconnecting.
     *
     * @return the timeout.
     */
    Duration getDisconnectAnnouncementTimeout();

    /**
     * Max timeout {@code SubscriptionManager} waits for search commands from {@code BaseClientActor}
     *
     * @return the maximum timeout.
     */
    Duration getSubscriptionManagerTimeout();

    /**
     * Minimum backoff duration after failure.
     *
     * @return the minimum backoff.
     */
    Duration getMinBackoff();

    /**
     * Maximum backoff duration after failure.
     *
     * @return the maximum backoff.
     */
    Duration getMaxBackoff();

    /**
     * How many times we will try to reconnect when connecting to a remote system.
     * <p>
     * The max time is about {@link ClientConfig#getConnectingMaxTimeout()} * this.
     *
     * @return the maximum retries for connecting.
     */
    int getConnectingMaxTries();

    /**
     * How long the service will wait for a successful connection when testing a new connection. If no response is
     * received after this duration, the test will be assumed a failure.
     *
     * @return the testing timeout.
     */
    Duration getTestingTimeout();

    /**
     * Min delay to refresh each client actor's knowledge of other client actors when client count of the connection
     * is above 1. The actual delay is random between 1x and 2x this value.
     *
     * @return the notification delay.
     */
    Duration getClientActorRefsNotificationDelay();

    /**
     * Min delay to refresh each client actor's knowledge of other client actors when client count of the connection
     * is above 1. The actual delay is random between 1x and 2x this value.
     *
     * @return the subscription refresh interval.
     */
    Duration getSubscriptionRefreshDelay();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code ClientConfig}.
     */
    enum ClientConfigValue implements KnownConfigValue {

        /**
         * The duration after the init process is triggered.
         */
        INIT_TIMEOUT("init-timeout", Duration.ofSeconds(5L)),

        /**
         * See documentation on {@link ClientConfig#getConnectingMinTimeout()}.
         */
        CONNECTING_MIN_TIMEOUT("connecting-min-timeout", Duration.ofSeconds(60L)),

        /**
         * See documentation on {@link ClientConfig#getConnectingMaxTimeout()}.
         */
        CONNECTING_MAX_TIMEOUT("connecting-max-timeout", Duration.ofMinutes(60L)),

        /**
         * See documentation on {@link ClientConfig#getDisconnectingMaxTimeout()}.
         */
        DISCONNECTING_MAX_TIMEOUT("disconnecting-max-timeout", Duration.ofSeconds(5L)),

        /**
         * See documentation on {@link ClientConfig#getDisconnectAnnouncementTimeout()}.
         */
        DISCONNECT_ANNOUNCEMENT_TIMEOUT("disconnect-announcement-timeout", Duration.ofSeconds(3L)),

        /**
         * See documentation on {@link ClientConfig#getSubscriptionManagerTimeout()}.
         */
        SUBSCRIPTION_MANAGER_TIMEOUT("subscription-manager-timeout", Duration.ofSeconds(60L)),

        /**
         * See documentation on {@link ClientConfig#getConnectingMaxTries()}.
         */
        CONNECTING_MAX_TRIES("connecting-max-tries", 50),

        /**
         * See documentation on {@link ClientConfig#getTestingTimeout()}.
         */
        TESTING_TIMEOUT("testing-timeout", Duration.ofSeconds(10L)),

        /**
         * See documentation on {@link ClientConfig#getConnectingMaxTimeout()}.
         */
        MIN_BACKOFF("min-backoff", Duration.ofSeconds(5L)),

        /**
         * See documentation on {@link ClientConfig#getConnectingMaxTries()}.
         */
        MAX_BACKOFF("max-backoff", Duration.ofMinutes(60L)),

        /**
         * See documentation on {@link ClientConfig#getClientActorRefsNotificationDelay()}.
         */
        CLIENT_ACTOR_REFS_NOTIFICATION_DELAY("client-actor-refs-notification-delay", Duration.ofMinutes(5L)),

        /**
         * See documentation on {@link ClientConfig#getSubscriptionRefreshDelay()}.
         */
        SUBSCRIPTION_REFRESH_DELAY("subscription-refresh-delay", Duration.ofMinutes(5L));

        private final String path;
        private final Object defaultValue;

        ClientConfigValue(final String thePath, final Object theDefaultValue) {
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
