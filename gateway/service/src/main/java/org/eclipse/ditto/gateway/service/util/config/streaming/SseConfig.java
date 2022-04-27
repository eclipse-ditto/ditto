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
package org.eclipse.ditto.gateway.service.util.config.streaming;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of SSE.
 */
public interface SseConfig {

    /**
     * Config path relative to its parent.
     */
    String CONFIG_PATH = "sse";

    /**
     * Returns the throttling config for SSE.
     *
     * @return the throttling config.
     */
    ThrottlingConfig getThrottlingConfig();

    /**
     * Returns the full qualified classname of the {@code org.eclipse.ditto.gateway.service.endpoints.routes.sse.SseAuthorizationEnforcer}
     * implementation to use for custom authorizations.
     *
     * @return the full qualified classname of the {@code SseAuthorizationEnforcer} implementation to use.
     * @since 3.0.0
     */
    String getAuthorizationEnforcer();

    /**
     * Returns the full qualified classname of the {@code org.eclipse.ditto.gateway.service.endpoints.routes.sse.SseConnectionSupervisor}
     * implementation to use for supervising SSE connections.
     *
     * @return the full qualified classname of the {@code SseConnectionSupervisor} implementation to use.
     * @since 3.0.0
     */
    String getConnectionSupervisor();

    enum SseConfigValue implements KnownConfigValue {

        AUTHORIZATION_ENFORCER("authorization-enforcer",
                "org.eclipse.ditto.gateway.service.endpoints.routes.sse.NoOpSseAuthorizationEnforcer"),

        CONNECTION_SUPERVISOR("connection-supervisor",
                "org.eclipse.ditto.gateway.service.endpoints.routes.sse.NoOpSseConnectionSupervisor");

        private final String path;
        private final Object defaultValue;

        SseConfigValue(final String thePath, final Object theDefaultValue) {
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
