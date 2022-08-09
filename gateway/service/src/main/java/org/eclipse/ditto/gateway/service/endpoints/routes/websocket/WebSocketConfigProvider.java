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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.BiFunction;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.util.config.streaming.WebsocketConfig;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Provides a method to customize a given {@link org.eclipse.ditto.gateway.service.util.config.streaming.WebsocketConfig}.
 */
public interface WebSocketConfigProvider
        extends DittoExtensionPoint, BiFunction<DittoHeaders, WebsocketConfig, WebsocketConfig> {

    /**
     * Loads the implementation of {@code WebSocketConfigProvider} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code WebSocketConfigProvider} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code WebSocketConfigProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static WebSocketConfigProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<WebSocketConfigProvider> {

        private static final String CONFIG_KEY = "websocket-config-provider";

        private ExtensionId(final ExtensionIdConfig<WebSocketConfigProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<WebSocketConfigProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(WebSocketConfigProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
