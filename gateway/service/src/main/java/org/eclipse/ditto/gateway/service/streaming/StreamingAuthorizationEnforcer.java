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
package org.eclipse.ditto.gateway.service.streaming;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;
import akka.http.javadsl.server.RequestContext;

/**
 * Enforces authorization in order to establish a Streaming connection.
 * If the authorization check is successful nothing will happen, else a
 * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException DittoRuntimeException} is thrown.
 */
public interface StreamingAuthorizationEnforcer extends DittoExtensionPoint {

    String CONFIG_PATH_WS = "ditto.gateway.streaming.websocket.authorization-enforcer";
    String CONFIG_PATH_SSE = "ditto.gateway.streaming.sse.authorization-enforcer";

    /**
     * Ensures that the establishment of a SSE connection is authorized for the given arguments.
     *
     * @param requestContext the context of the HTTP request for opening the connection.
     * @param dittoHeaders the DittoHeaders with authentication information for opening the connection.
     * @return a successful future if validation succeeds or a failed future if validation fails.
     * @throws NullPointerException if any argument is {@code null}.
     */
    CompletionStage<DittoHeaders> checkAuthorization(RequestContext requestContext, DittoHeaders dittoHeaders);

    /**
     * Loads the implementation of {@code SseAuthorizationEnforcer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SseAuthorizationEnforcer} should be loaded.
     * @return the {@code SseAuthorizationEnforcer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static StreamingAuthorizationEnforcer sse(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = actorSystem.settings().config().getString(CONFIG_PATH_SSE);
        return new ExtensionId<>(implementation, StreamingAuthorizationEnforcer.class).get(actorSystem);
    }

    /**
     * Loads the implementation of {@code WebSocketAuthorizationEnforcer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code WebSocketAuthorizationEnforcer} should be loaded.
     * @return the {@code WebSocketAuthorizationEnforcer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static StreamingAuthorizationEnforcer ws(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = actorSystem.settings().config().getString(CONFIG_PATH_WS);
        return new ExtensionId<>(implementation, StreamingAuthorizationEnforcer.class).get(actorSystem);
    }

}
