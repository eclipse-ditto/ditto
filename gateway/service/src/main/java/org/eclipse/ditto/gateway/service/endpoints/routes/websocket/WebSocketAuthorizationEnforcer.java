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

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;

/**
 * Enforces authorization in order to establish a WebSocket connection.
 * If the authorization check is successful the headers are given back, possibly with new information, else a
 * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException DittoRuntimeException} is thrown.
 */
public interface WebSocketAuthorizationEnforcer extends DittoExtensionPoint {

    CompletionStage<DittoHeaders> checkAuthorization(DittoHeaders dittoHeaders);

    /**
     * Loads the implementation of {@code WebSocketAuthorizationEnforcer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code WebSocketAuthorizationEnforcer} should be loaded.
     * @return the {@code WebSocketAuthorizationEnforcer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static WebSocketAuthorizationEnforcer get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(
                actorSystem.settings().config())).getStreamingConfig().getWebsocketConfig().getAuthorizationEnforcer();

        return new ExtensionId<>(implementation, WebSocketAuthorizationEnforcer.class).get(actorSystem);
    }

}
