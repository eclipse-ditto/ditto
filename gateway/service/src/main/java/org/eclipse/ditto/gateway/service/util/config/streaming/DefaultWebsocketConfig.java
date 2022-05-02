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
package org.eclipse.ditto.gateway.service.util.config.streaming;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the web socket config.
 */
@Immutable
final class DefaultWebsocketConfig implements WebsocketConfig {

    private final int subscriberBackpressureQueueSize;
    private final int publisherBackpressureBufferSize;
    private final double throttlingRejectionFactor;
    private final ThrottlingConfig throttlingConfig;
    private final String authorizationEnforcer;
    private final String configProvider;
    private final String connectionSupervisor;
    private final String incomingEventSniffer;
    private final String outgoingEventSniffer;

    private DefaultWebsocketConfig(final ScopedConfig scopedConfig) {
        subscriberBackpressureQueueSize =
                scopedConfig.getPositiveIntOrThrow(WebsocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE);
        publisherBackpressureBufferSize =
                scopedConfig.getPositiveIntOrThrow(WebsocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE);
        throttlingRejectionFactor =
                scopedConfig.getNonNegativeDoubleOrThrow(WebsocketConfigValue.THROTTLING_REJECTION_FACTOR);
        throttlingConfig = ThrottlingConfig.of(scopedConfig);
        authorizationEnforcer = scopedConfig.getString(WebsocketConfigValue.AUTHORIZATION_ENFORCER.getConfigPath());
        configProvider = scopedConfig.getString(WebsocketConfigValue.CONFIG_PROVIDER.getConfigPath());
        connectionSupervisor = scopedConfig.getString(WebsocketConfigValue.CONNECTION_SUPERVISOR.getConfigPath());
        incomingEventSniffer = scopedConfig.getString(WebsocketConfigValue.INCOMING_EVENT_SNIFFER.getConfigPath());
        outgoingEventSniffer = scopedConfig.getString(WebsocketConfigValue.OUTGOING_EVENT_SNIFFER.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultWebSocketConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the web socket config at "websocket".
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static WebsocketConfig of(final Config config) {
        return new DefaultWebsocketConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, WebsocketConfigValue.values()));
    }

    @Override
    public int getSubscriberBackpressureQueueSize() {
        return subscriberBackpressureQueueSize;
    }

    @Override
    public int getPublisherBackpressureBufferSize() {
        return publisherBackpressureBufferSize;
    }

    @Override
    public double getThrottlingRejectionFactor() {
        return throttlingRejectionFactor;
    }

    @Override
    public ThrottlingConfig getThrottlingConfig() {
        return throttlingConfig;
    }

    @Override
    public String getAuthorizationEnforcer() {
        return authorizationEnforcer;
    }

    @Override
    public String getConfigProvider() {
        return configProvider;
    }

    @Override
    public String getConnectionSupervisor() {
        return connectionSupervisor;
    }

    @Override
    public String getIncomingEventSniffer() {
        return incomingEventSniffer;
    }

    @Override
    public String getOutgoingEventSniffer() {
        return outgoingEventSniffer;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultWebsocketConfig that = (DefaultWebsocketConfig) o;
        return subscriberBackpressureQueueSize == that.subscriberBackpressureQueueSize &&
                publisherBackpressureBufferSize == that.publisherBackpressureBufferSize &&
                Double.compare(throttlingRejectionFactor, that.throttlingRejectionFactor) == 0 &&
                Objects.equals(throttlingConfig, that.throttlingConfig) &&
                Objects.equals(authorizationEnforcer, that.authorizationEnforcer) &&
                Objects.equals(configProvider, that.configProvider) &&
                Objects.equals(connectionSupervisor, that.connectionSupervisor) &&
                Objects.equals(incomingEventSniffer, that.incomingEventSniffer) &&
                Objects.equals(outgoingEventSniffer, that.outgoingEventSniffer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriberBackpressureQueueSize, publisherBackpressureBufferSize,
                throttlingRejectionFactor, throttlingConfig, authorizationEnforcer, configProvider,
                connectionSupervisor, incomingEventSniffer, outgoingEventSniffer);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "subscriberBackpressureQueueSize=" + subscriberBackpressureQueueSize +
                ", publisherBackpressureBufferSize=" + publisherBackpressureBufferSize +
                ", throttlingRejectionFactor=" + throttlingRejectionFactor +
                ", throttlingConfig=" + throttlingConfig +
                ", authorizationEnforcer=" + authorizationEnforcer +
                ", configProvider=" + configProvider +
                ", connectionSupervisor=" + connectionSupervisor +
                ", incomingEventSniffer=" + incomingEventSniffer +
                ", outgoingEventSniffer=" + outgoingEventSniffer +
                "]";
    }

}
