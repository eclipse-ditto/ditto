/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.util.enforcement;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.GraphActor;
import org.eclipse.ditto.services.utils.akka.controlflow.Pipe;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Utility class to create actors that enforce authorization.
 */
public final class EnforcerActor {

    private static final Duration askTimeout = Duration.ofSeconds(10); // TODO: make configurable

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders) {

        return props(pubSubMediator, enforcementProviders, null);
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            @Nullable Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcerFunction =
                preEnforcer != null ? preEnforcer : CompletableFuture::completedFuture;

        return GraphActor.partialWithLog((actorContext, log) -> {
            final Enforcement.Context enforcementContext =
                    new Enforcement.Context(pubSubMediator, askTimeout).with(actorContext, log);

            return Pipe.joinFlow(
                    PreEnforcer.fromFunction(actorContext.self(), preEnforcerFunction),
                    Pipe.joinFlows(enforcementProviders.stream()
                            .map(provider -> provider.toGraph(enforcementContext))
                            .collect(Collectors.toList())));
        });
    }
}
