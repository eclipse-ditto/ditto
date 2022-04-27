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

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.streaming.actors.StreamSupervisor;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;

/**
 * Provides the means to supervise a particular WebSocket stream.
 */
public abstract class WebSocketSupervisor extends DittoExtensionPoint implements StreamSupervisor {

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    protected WebSocketSupervisor(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    /**
     * Loads the implementation of {@code WebSocketSupervisor} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code WebSocketSupervisor} should be loaded.
     * @return the {@code WebSocketSupervisor} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    public static WebSocketSupervisor get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(
                actorSystem.settings().config())).getStreamingConfig().getWebsocketConfig().getConnectionSupervisor();

        return new ExtensionId<>(implementation, WebSocketSupervisor.class).get(actorSystem);
    }

}
