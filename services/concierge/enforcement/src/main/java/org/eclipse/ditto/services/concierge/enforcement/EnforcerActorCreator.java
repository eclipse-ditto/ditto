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
package org.eclipse.ditto.services.concierge.enforcement;

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
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.services.utils.akka.controlflow.components.ActivityChecker;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Flow;

/**
 * Creator for Actors that enforce authorization.
 */
public final class EnforcerActorCreator {

    private EnforcerActorCreator() {
        throw new AssertionError();
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor. Caution: The actor does not terminate itself
     * after a period of inactivity.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param askTimeout the ask timeout duration: the duration to wait for entity shard regions.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final Duration askTimeout) {

        return props(pubSubMediator, enforcementProviders, askTimeout, null, null);
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param askTimeout the ask timeout duration: the duration to wait for entity shard regions.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @param activityCheckInterval how often to check for actor activity for termination after an idle period.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final Duration askTimeout,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
            @Nullable final Duration activityCheckInterval) {

        final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcerFunction =
                preEnforcer != null ? preEnforcer : CompletableFuture::completedFuture;

        return GraphActor.partial((actorContext, log) -> {
            final AbstractEnforcement.Context enforcementContext =
                    new AbstractEnforcement.Context(pubSubMediator, askTimeout).with(actorContext, log);

            return Flow.<WithSender>create()
                    .via(ActivityChecker.ofNullable(activityCheckInterval, actorContext.self()))
                    .via(PreEnforcer.fromFunction(actorContext.self(), preEnforcerFunction))
                    .via(Pipe.joinFlows(enforcementProviders.stream()
                            .map(provider -> provider.toGraph(enforcementContext))
                            .collect(Collectors.toList())));
        });
    }
}
