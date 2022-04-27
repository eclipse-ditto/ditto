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
package org.eclipse.ditto.gateway.service.endpoints.routes.sse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

import akka.actor.ActorSystem;
import akka.http.javadsl.server.RequestContext;

/**
 * Null implementation for {@link SseAuthorizationEnforcer}.
 */
public final class NoOpSseAuthorizationEnforcer extends SseAuthorizationEnforcer {

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    public NoOpSseAuthorizationEnforcer(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    @Override
    public CompletionStage<Void> checkAuthorization(final RequestContext requestContext,
            final DittoHeaders dittoHeaders) {
        return CompletableFuture.completedStage(null);
    }

}
