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
package org.eclipse.ditto.gateway.service.health;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;

/**
 * Health check supplier for Akka Management checking whether the {@code GatewayRootActor} already started the HTTP
 * route.
 */
public final class GatewayHttpReadinessCheck implements Supplier<CompletionStage<Boolean>> {

    /**
     * The message to ask the {@code GatewayRootActor} (must be handled by it!).
     */
    public static final String READINESS_ASK_MESSAGE = "http-ready?";

    /**
     * The response to the above message sent by the {@code GatewayRootActor}.
     */
    public static final String READINESS_ASK_MESSAGE_RESPONSE = "ready";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ActorSelection rootActor;

    public GatewayHttpReadinessCheck(final ActorSystem system) {
        rootActor = system.actorSelection("/user/gatewayRoot");
    }

    @Override
    public CompletionStage<Boolean> get() {
        return Patterns.ask(rootActor, READINESS_ASK_MESSAGE, TIMEOUT)
                .handle((answer, throwable) -> answer.equals(READINESS_ASK_MESSAGE_RESPONSE));
    }
}
