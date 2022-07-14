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
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the {@link ClientConfig}.
 */
@Immutable
public final class DefaultClientConfig implements ClientConfig {

    private static final String CONFIG_PATH = "client";

    private final Duration initTimeout;
    private final Duration connectingMinTimeout;
    private final Duration connectingMaxTimeout;
    private final Duration disconnectingMaxTimeout;
    private final Duration disconnectAnnouncementTimeout;
    private final Duration subscriptionManagerTimeout;
    private final int connectingMaxTries;
    private final Duration testingTimeout;
    private final Duration minBackoff;
    private final Duration maxBackoff;
    private final Duration clientActorRefsNotificationDelay;
    private final Duration subscriptionRefreshDelay;

    private DefaultClientConfig(final ScopedConfig config) {
        initTimeout = config.getNonNegativeDurationOrThrow(ClientConfigValue.INIT_TIMEOUT);
        connectingMinTimeout = config.getNonNegativeDurationOrThrow(ClientConfigValue.CONNECTING_MIN_TIMEOUT);
        connectingMaxTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(ClientConfigValue.CONNECTING_MAX_TIMEOUT);
        disconnectingMaxTimeout =
                config.getNonNegativeAndNonZeroDurationOrThrow(ClientConfigValue.DISCONNECTING_MAX_TIMEOUT);
        disconnectAnnouncementTimeout = config.getNonNegativeDurationOrThrow(
                ClientConfigValue.DISCONNECT_ANNOUNCEMENT_TIMEOUT);
        subscriptionManagerTimeout =
                config.getNonNegativeDurationOrThrow(ClientConfigValue.SUBSCRIPTION_MANAGER_TIMEOUT);
        connectingMaxTries = config.getPositiveIntOrThrow(ClientConfigValue.CONNECTING_MAX_TRIES);
        testingTimeout = config.getNonNegativeAndNonZeroDurationOrThrow(ClientConfigValue.TESTING_TIMEOUT);
        minBackoff = config.getNonNegativeDurationOrThrow(ClientConfigValue.MIN_BACKOFF);
        maxBackoff = config.getNonNegativeAndNonZeroDurationOrThrow(ClientConfigValue.MAX_BACKOFF);
        clientActorRefsNotificationDelay =
                config.getNonNegativeDurationOrThrow(ClientConfigValue.CLIENT_ACTOR_REFS_NOTIFICATION_DELAY);
        subscriptionRefreshDelay = config.getNonNegativeDurationOrThrow(ClientConfigValue.SUBSCRIPTION_REFRESH_DELAY);
    }

    /**
     * Returns an instance of {@code DefaultClientConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultClientConfig of(final Config config) {
        return new DefaultClientConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ClientConfigValue.values()));
    }

    @Override
    public Duration getConnectingMinTimeout() {
        return connectingMinTimeout;
    }

    @Override
    public Duration getConnectingMaxTimeout() {
        return connectingMaxTimeout;
    }

    @Override
    public Duration getDisconnectingMaxTimeout() {
        return disconnectingMaxTimeout;
    }

    @Override
    public Duration getDisconnectAnnouncementTimeout() {
        return disconnectAnnouncementTimeout;
    }

    @Override
    public Duration getSubscriptionManagerTimeout() {
        return subscriptionManagerTimeout;
    }

    @Override
    public int getConnectingMaxTries() {
        return connectingMaxTries;
    }

    @Override
    public Duration getTestingTimeout() {
        return testingTimeout;
    }

    @Override
    public Duration getClientActorRefsNotificationDelay() {
        return clientActorRefsNotificationDelay;
    }

    @Override
    public Duration getSubscriptionRefreshDelay() {
        return subscriptionRefreshDelay;
    }

    @Override
    public Duration getMinBackoff() {
        return minBackoff;
    }

    @Override
    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultClientConfig that = (DefaultClientConfig) o;
        return Objects.equals(initTimeout, that.initTimeout) &&
                Objects.equals(connectingMinTimeout, that.connectingMinTimeout) &&
                Objects.equals(connectingMaxTimeout, that.connectingMaxTimeout) &&
                Objects.equals(disconnectingMaxTimeout, that.disconnectingMaxTimeout) &&
                Objects.equals(disconnectAnnouncementTimeout, that.disconnectAnnouncementTimeout) &&
                Objects.equals(connectingMaxTries, that.connectingMaxTries) &&
                Objects.equals(testingTimeout, that.testingTimeout) &&
                Objects.equals(minBackoff, that.minBackoff) &&
                Objects.equals(maxBackoff, that.maxBackoff) &&
                Objects.equals(subscriptionManagerTimeout, that.subscriptionManagerTimeout) &&
                Objects.equals(clientActorRefsNotificationDelay, that.clientActorRefsNotificationDelay) &&
                Objects.equals(subscriptionRefreshDelay, that.subscriptionRefreshDelay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initTimeout, connectingMinTimeout, connectingMaxTimeout, disconnectingMaxTimeout,
                disconnectAnnouncementTimeout, connectingMaxTries, testingTimeout, minBackoff, maxBackoff,
                subscriptionManagerTimeout, clientActorRefsNotificationDelay, subscriptionRefreshDelay);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "initTimeout=" + initTimeout +
                ", connectingMinTimeout=" + connectingMinTimeout +
                ", connectingMaxTimeout=" + connectingMaxTimeout +
                ", disconnectingMaxTimeout=" + disconnectingMaxTimeout +
                ", disconnectAnnouncementTimeout=" + disconnectAnnouncementTimeout +
                ", connectingMaxTries=" + connectingMaxTries +
                ", testingTimeout=" + testingTimeout +
                ", minBackoff=" + minBackoff +
                ", maxBackoff=" + maxBackoff +
                ", subscriptionManagerTimeout=" + subscriptionManagerTimeout +
                ", clientActorRefsNotificationDelay=" + clientActorRefsNotificationDelay +
                ", subscriptionRefreshDelay=" + subscriptionRefreshDelay +
                "]";
    }

}
