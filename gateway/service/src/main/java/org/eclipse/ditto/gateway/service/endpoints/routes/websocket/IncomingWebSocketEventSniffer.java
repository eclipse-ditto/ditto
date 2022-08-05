/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;


import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.stream.javadsl.Flow;

/**
 * Extension to sniff incoming events over websocket.
 */
public interface IncomingWebSocketEventSniffer extends DittoExtensionPoint {

    /**
     * Create an async flow for event sniffing.
     *
     * @param request the HTTP request that started the event stream.
     * @return flow to pass events through with a wiretap attached over an async barrier to the sink for sniffed events.
     */
    Flow<String, String, NotUsed> toAsyncFlow(final HttpRequest request);

    /**
     * Loads the implementation of {@code IncomingWebSocketEventSniffer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code IncomingWebSocketEventSniffer} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code IncomingWebSocketEventSniffer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static IncomingWebSocketEventSniffer get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<IncomingWebSocketEventSniffer> {

        private static final String CONFIG_KEY = "incoming-websocket-event-sniffer";

        private ExtensionId(final ExtensionIdConfig<IncomingWebSocketEventSniffer> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<IncomingWebSocketEventSniffer> computeConfig(final Config config) {
            return ExtensionIdConfig.of(IncomingWebSocketEventSniffer.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
