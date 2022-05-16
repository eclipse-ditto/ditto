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
package org.eclipse.ditto.gateway.service.util.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CloudEventsConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultClaimMessageConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultCloudEventsConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultCommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultMessageConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultPublicHealthConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.GatewayHttpConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.MessageConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.PublicHealthConfig;
import org.eclipse.ditto.gateway.service.util.config.health.DefaultHealthCheckConfig;
import org.eclipse.ditto.gateway.service.util.config.health.HealthCheckConfig;
import org.eclipse.ditto.gateway.service.util.config.security.AuthenticationConfig;
import org.eclipse.ditto.gateway.service.util.config.security.CachesConfig;
import org.eclipse.ditto.gateway.service.util.config.security.DefaultAuthenticationConfig;
import org.eclipse.ditto.gateway.service.util.config.security.DefaultCachesConfig;
import org.eclipse.ditto.gateway.service.util.config.streaming.DefaultStreamingConfig;
import org.eclipse.ditto.gateway.service.util.config.streaming.StreamingConfig;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;
import org.eclipse.ditto.internal.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.internal.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.internal.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;

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
    private final CommandConfig commandConfig;
    private final MessageConfig messageConfig;
    private final MessageConfig claimMessageConfig;
    private final AuthenticationConfig authenticationConfig;
    private final StreamingConfig streamingConfig;
    private final PublicHealthConfig publicHealthConfig;
    private final DefaultCloudEventsConfig cloudEventsConfig;

    private DittoGatewayConfig(final ScopedConfig dittoScopedConfig) {

        dittoServiceConfig = DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH);
        protocolConfig = DefaultProtocolConfig.of(dittoScopedConfig);
        httpConfig = GatewayHttpConfig.of(dittoServiceConfig);
        cachesConfig = DefaultCachesConfig.of(dittoServiceConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(dittoServiceConfig);
        commandConfig = DefaultCommandConfig.of(dittoServiceConfig);
        messageConfig = DefaultMessageConfig.of(dittoServiceConfig);
        claimMessageConfig = DefaultClaimMessageConfig.of(dittoServiceConfig);
        authenticationConfig = DefaultAuthenticationConfig.of(dittoServiceConfig);
        streamingConfig = DefaultStreamingConfig.of(dittoServiceConfig);
        publicHealthConfig = DefaultPublicHealthConfig.of(dittoServiceConfig);
        cloudEventsConfig = DefaultCloudEventsConfig.of(dittoServiceConfig);
    }

    /**
     * Returns an instance of {@code DittoGatewayConfig} based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the service config at the {@code "ditto"} config
     * path.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoGatewayConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoGatewayConfig(dittoScopedConfig);
    }

    @Override
    public CachesConfig getCachesConfig() {
        return cachesConfig;
    }

    @Override
    public StreamingConfig getStreamingConfig() {
        return streamingConfig;
    }

    @Override
    public HealthCheckConfig getHealthCheckConfig() {
        return healthCheckConfig;
    }

    @Override
    public CommandConfig getCommandConfig() {
        return commandConfig;
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
    public TracingConfig getTracingConfig() {
        return dittoServiceConfig.getTracingConfig();
    }

    @Override
    public ProtocolConfig getProtocolConfig() {
        return protocolConfig;
    }

    @Override
    public CloudEventsConfig getCloudEventsConfig() {
        return cloudEventsConfig;
    }

    /**
     * @return always {@value #CONFIG_PATH}.
     */
    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

}
