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
package org.eclipse.ditto.services.gateway.starter.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultClaimMessageConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultMessageConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultPublicHealthConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultWebSocketConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.GatewayHttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.MessageConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.PublicHealthConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.WebSocketConfig;
import org.eclipse.ditto.services.gateway.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.gateway.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.gateway.security.config.AuthenticationConfig;
import org.eclipse.ditto.services.gateway.security.config.CachesConfig;
import org.eclipse.ditto.services.gateway.security.config.DefaultAuthenticationConfig;
import org.eclipse.ditto.services.gateway.security.config.DefaultCachesConfig;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.config.WithConfigPath;
import org.eclipse.ditto.services.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.services.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig;

/**
 * This class is the default implementation of the Gateway config.
 */
@Immutable
public final class DittoGatewayConfig implements GatewayConfig, WithConfigPath {

    private static final String CONFIG_PATH = "gateway";

    private final DittoServiceConfig dittoServiceConfig;
    private final ProtocolConfig protocolConfig;
    private final HttpConfig httpConfig;
    private final CachesConfig cachesConfig;
    private final HealthCheckConfig healthCheckConfig;
    private final MessageConfig messageConfig;
    private final MessageConfig claimMessageConfig;
    private final AuthenticationConfig authenticationConfig;
    private final WebSocketConfig webSocketConfig;
    private final PublicHealthConfig publicHealthConfig;

    private DittoGatewayConfig(final ScopedConfig dittoScopedConfig) {

        this.dittoServiceConfig = DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH);
        protocolConfig = DefaultProtocolConfig.of(dittoScopedConfig);
        httpConfig = GatewayHttpConfig.of(dittoServiceConfig);
        cachesConfig = DefaultCachesConfig.of(dittoServiceConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(dittoServiceConfig);
        messageConfig = DefaultMessageConfig.of(dittoServiceConfig);
        claimMessageConfig = DefaultClaimMessageConfig.of(dittoServiceConfig);
        authenticationConfig = DefaultAuthenticationConfig.of(dittoServiceConfig);
        webSocketConfig = DefaultWebSocketConfig.of(dittoServiceConfig);
        publicHealthConfig = DefaultPublicHealthConfig.of(dittoServiceConfig);
    }

    /**
     * Returns an instance of {@code DittoGatewayConfig} based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the service config at the {@code "ditto"} config
     * path.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoGatewayConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoGatewayConfig(dittoScopedConfig);
    }

    @Override
    public CachesConfig getCachesConfig() {
        return cachesConfig;
    }

    @Override
    public WebSocketConfig getWebSocketConfig() {
        return webSocketConfig;
    }

    @Override
    public HealthCheckConfig getHealthCheckConfig() {
        return healthCheckConfig;
    }

    @Override
    public MessageConfig getMessageConfig() {
        return messageConfig;
    }

    @Override
    public MessageConfig getClaimMessageConfig() {
        return claimMessageConfig;
    }

    @Override
    public AuthenticationConfig getAuthenticationConfig() {
        return authenticationConfig;
    }

    @Override
    public PublicHealthConfig getPublicHealthConfig() {
        return publicHealthConfig;
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return dittoServiceConfig.getClusterConfig();
    }

    @Override
    public LimitsConfig getLimitsConfig() {
        return dittoServiceConfig.getLimitsConfig();
    }

    @Override
    public HttpConfig getHttpConfig() {
        return httpConfig;
    }

    @Override
    public MetricsConfig getMetricsConfig() {
        return dittoServiceConfig.getMetricsConfig();
    }

    @Override
    public ProtocolConfig getProtocolConfig() {
        return protocolConfig;
    }

    /**
     * @return always {@value #CONFIG_PATH}.
     */
    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }
}
