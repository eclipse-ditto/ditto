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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.service.DittoExtensionIds;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.streaming.actors.StreamSupervisor;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Provides the means to supervise a particular WebSocket stream.
 */
public interface WebSocketSupervisor extends DittoExtensionPoint, StreamSupervisor {

    /**
     * Loads the implementation of {@code WebSocketSupervisor} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code WebSocketSupervisor} should be loaded.
     * @return the {@code WebSocketSupervisor} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static WebSocketSupervisor get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<WebSocketSupervisor> {

        private static final String CONFIG_KEY = "websocket-connection-supervisor";
        private static final String CONFIG_PATH = "ditto.extensions." + CONFIG_KEY;

        private ExtensionId(final ExtensionIdConfig<WebSocketSupervisor> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<WebSocketSupervisor> computeConfig(final Config config) {
            return ExtensionIdConfig.of(WebSocketSupervisor.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

}
