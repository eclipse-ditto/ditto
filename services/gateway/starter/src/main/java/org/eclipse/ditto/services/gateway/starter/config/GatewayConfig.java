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

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.AuthenticationConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.CachesConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.MessageConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.PublicHealthConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.WebSocketConfig;
import org.eclipse.ditto.services.gateway.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.protocol.config.WithProtocolConfig;

/**
 * Provides configuration settings for the Gateway service.
 * <p>
 * Java serialization is supported for {@code GatewayConfig}.
 * </p>
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
     * Returns the configuration settings of the Gateway's web socket.
     *
     * @return the config.
     */
    WebSocketConfig getWebSocketConfig();

    /**
     * Returns the health check config of the Gateway service.
     *
     * @return the config.
     */
    HealthCheckConfig getHealthCheckConfig();

    /**
     * Returns the config for the {@code /messages} resource of the Things service.
     *
     * @return the config.
     */
    MessageConfig getMessageConfig();

    /**
     * Returns the config for the {@code /inbox/claim} resource of the Things service.
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

}
