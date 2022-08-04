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

import org.eclipse.ditto.base.service.config.ServiceSpecificConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CloudEventsConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.MessageConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.PublicHealthConfig;
import org.eclipse.ditto.gateway.service.util.config.health.HealthCheckConfig;
import org.eclipse.ditto.gateway.service.util.config.security.AuthenticationConfig;
import org.eclipse.ditto.gateway.service.util.config.security.CachesConfig;
import org.eclipse.ditto.gateway.service.util.config.streaming.StreamingConfig;
import org.eclipse.ditto.internal.utils.protocol.config.WithProtocolConfig;

/**
 * Provides configuration settings for the Gateway service.
 */
public interface GatewayConfig extends ServiceSpecificConfig, WithProtocolConfig {

    @Override
    HttpConfig getHttpConfig();

    /**
     * Returns the configuration settings for the caches of the Gateway service.
     *
     * @return the config.
     */
    CachesConfig getCachesConfig();

    /**
     * Returns the configuration settings of the Gateway's streaming capability including web socket.
     *
     * @return the config.
     */
    StreamingConfig getStreamingConfig();

    /**
     * Returns the health check config of the Gateway service.
     *
     * @return the config.
     */
    HealthCheckConfig getHealthCheckConfig();

    /**
     * Returns the config for commands in the gateway.
     *
     * @return the config.
     * @since 1.1.0
     */
    CommandConfig getCommandConfig();

    /**
     * Returns the config for the {@code /messages} resource of the gateway.
     *
     * @return the config.
     */
    MessageConfig getMessageConfig();

    /**
     * Returns the config for the {@code /inbox/claim} resource of the gateway.
     *
     * @return the config.
     */
    MessageConfig getClaimMessageConfig();

    /**
     * Returns the authentication configuration.
     *
     * @return the config.
     */
    AuthenticationConfig getAuthenticationConfig();

    /**
     * Returns the configuration of the public health endpoint of the Gateway service.
     *
     * @return the config.
     */
    PublicHealthConfig getPublicHealthConfig();

    /**
     * Returns the configuration for the cloud events endpoint.
     *
     * @return the config.
     */
    CloudEventsConfig getCloudEventsConfig();

}
