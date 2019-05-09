/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.starter.config;

import java.io.Serializable;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceConfig;
import org.eclipse.ditto.services.base.config.LimitsConfig;
import org.eclipse.ditto.services.base.config.MetricsConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.AuthenticationConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.CachesConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultAuthenticationConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultCachesConfig;
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
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.config.WithConfigPath;
import org.eclipse.ditto.services.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.services.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig;

/**
 * This class is the default implementation of the Gateway config.
 */
@Immutable
public final class DittoGatewayConfig implements GatewayConfig, Serializable, WithConfigPath {

    private static final String CONFIG_PATH = "gateway";

    private static final long serialVersionUID = -3502379617636376609L;

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

    private DittoGatewayConfig(final DittoServiceConfig dittoServiceConfig, final ProtocolConfig protocolConfig,
            final ScopedConfig scopedConfig) {

        this.dittoServiceConfig = dittoServiceConfig;
        this.protocolConfig = protocolConfig;
        httpConfig = GatewayHttpConfig.of(scopedConfig);
        cachesConfig = DefaultCachesConfig.of(scopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(scopedConfig);
        messageConfig = DefaultMessageConfig.of(scopedConfig);
        claimMessageConfig = DefaultClaimMessageConfig.of(scopedConfig);
        authenticationConfig = DefaultAuthenticationConfig.of(scopedConfig);
        webSocketConfig = DefaultWebSocketConfig.of(scopedConfig);
        publicHealthConfig = DefaultPublicHealthConfig.of(scopedConfig);
    }

    /**
     * Returns an instance of {@code DittoGatewayConfig} based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the Gateway service config at
     * {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoGatewayConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoGatewayConfig(DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH),
                DefaultProtocolConfig.of(dittoScopedConfig),
                DefaultScopedConfig.newInstance(dittoScopedConfig, CONFIG_PATH)
        );
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
