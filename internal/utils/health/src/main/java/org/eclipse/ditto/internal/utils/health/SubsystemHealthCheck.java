/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.health;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;

/**
 * Health check supplier for Pekko Management checking whether the root actor's health checking actor reports success.
 * The health check will report failure if health checking actor replies with status DOWN or times out.
 */
public final class SubsystemHealthCheck implements Supplier<CompletionStage<Boolean>> {

    /**
     * The message to ask the health checking actor.
     */
    private static final RetrieveHealth RETRIEVE_HEALTH_ASK_MESSAGE = RetrieveHealth.newInstance();

    /**
     * Default timeout for waiting for response from health checking actor.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Health checking actor selection path.
     */
    private static final String HEALTH_CHECKING_ACTOR_PATH =
            "/user/*Root/" + DefaultHealthCheckingActorFactory.ACTOR_NAME;

    private final ActorSelection healthCheckingActor;
    private final Duration timeout;

    /**
     * Constructs subsystem health check with default timeout.
     *
     * @param system actor system to check health of
     */
    public SubsystemHealthCheck(final ActorSystem system) {
        this(system, TIMEOUT);
    }

    /**
     * Constructs subsystem health check with custom timeout.
     *
     * @param system actor system to check health of
     * @param timeout timeout
     */
    public SubsystemHealthCheck(final ActorSystem system, final Duration timeout) {
        healthCheckingActor = system.actorSelection(HEALTH_CHECKING_ACTOR_PATH);
        this.timeout = timeout;
    }

    @Override
    public CompletionStage<Boolean> get() {
        return Patterns.ask(healthCheckingActor, RETRIEVE_HEALTH_ASK_MESSAGE, timeout)
                .handle((answer, throwable) -> answer instanceof StatusInfo statusInfo
                        && statusInfo.getStatus() != StatusInfo.Status.DOWN);
    }

}
