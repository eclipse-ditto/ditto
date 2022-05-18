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

import akka.actor.ActorSystem;
import akka.http.javadsl.server.RequestContext;

/**
 * Enforces authorization in order to establish a Streaming connection.
 * If the authorization check is successful nothing will happen, else a
 * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException DittoRuntimeException} is thrown.
 */
public interface StreamingAuthorizationEnforcer extends DittoExtensionPoint {

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
        return SseExtensionId.INSTANCE.get(actorSystem);
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
        return WsExtensionId.INSTANCE.get(actorSystem);
    }

    final class WsExtensionId extends DittoExtensionPoint.ExtensionId<StreamingAuthorizationEnforcer> {

        private static final String CONFIG_PATH = "ditto.gateway.streaming.websocket.authorization-enforcer";
        private static final WsExtensionId INSTANCE = new WsExtensionId(StreamingAuthorizationEnforcer.class);

        private WsExtensionId(final Class<StreamingAuthorizationEnforcer> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

    final class SseExtensionId extends DittoExtensionPoint.ExtensionId<StreamingAuthorizationEnforcer> {

        private static final String CONFIG_PATH = "ditto.gateway.streaming.sse.authorization-enforcer";
        private static final SseExtensionId INSTANCE = new SseExtensionId(StreamingAuthorizationEnforcer.class);

        private SseExtensionId(final Class<StreamingAuthorizationEnforcer> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

}
