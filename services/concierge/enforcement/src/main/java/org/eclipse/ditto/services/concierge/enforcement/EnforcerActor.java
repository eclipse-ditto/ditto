/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.enforcement;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.Pipe;
import org.eclipse.ditto.services.utils.akka.controlflow.components.ActivityChecker;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

/**
 * Actor to authorize signals by enforcing policies or ACLs on signals.
 */
public final class EnforcerActor extends AbstractEnforcerActor {

    private final Sink<Contextual<Object>, ?> handler;

    private EnforcerActor(final ActorRef pubSubMediator, final ActorRef conciergeForwarder,
            final Executor enforcerExecutor, final Duration askTimeout, final Sink<Contextual<Object>, ?> handler) {
        super(pubSubMediator, conciergeForwarder, enforcerExecutor, askTimeout);
        this.handler = handler;

        // send self the context to initialize activity checker.
        getSelf().tell(NotUsed.getInstance(), getSelf());
    }

    /**
     * Creates Akka configuration object Props for this enforcer actor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param askTimeout the ask timeout duration: the duration to wait for entity shard regions.
     * @param conciergeForwarder an actorRef to concierge forwarder.
     * @param enforcerExecutor the Executor to run async tasks on during enforcement.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @param activityCheckInterval how often to check for actor activity for termination after an idle period.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final Duration askTimeout,
            final ActorRef conciergeForwarder,
            final Executor enforcerExecutor,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
            @Nullable final Duration activityCheckInterval) {

        // create the sink exactly once per props and share it across all actors to not waste memory
        final Sink<Contextual<Object>, ?> messageHandler =
                assembleHandler(enforcementProviders, preEnforcer, activityCheckInterval);

        return Props.create(EnforcerActor.class, () ->
                new EnforcerActor(pubSubMediator, conciergeForwarder, enforcerExecutor, askTimeout, messageHandler));
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor. Caution: The actor does not terminate itself
     * after a period of inactivity.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link org.eclipse.ditto.services.concierge.enforcement.EnforcementProvider}s.
     * @param askTimeout the ask timeout duration: the duration to wait for entity shard regions.
     * @param conciergeForwarder an actorRef to concierge forwarder.
     * @param enforcerExecutor the Executor to run async tasks on during enforcement.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final Duration askTimeout,
            final ActorRef conciergeForwarder,
            final Executor enforcerExecutor) {

        return props(pubSubMediator, enforcementProviders, askTimeout, conciergeForwarder, enforcerExecutor,
                null, null);
    }

    @Override
    protected Sink<Contextual<Object>, ?> getHandler() {
        return handler;
    }

    /**
     * Create the sink that defines the behavior of this enforcer actor. Do NOT call this or similar methods inside an
     * actor instance; otherwise the steam components will waste huge amounts of heap space.
     */
    private static Sink<Contextual<Object>, ?> assembleHandler(final Set<EnforcementProvider<?>> enforcementProviders,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
            @Nullable final Duration activityCheckInterval) {

        final Graph<FlowShape<Contextual<Object>, Contextual<Object>>, NotUsed> activityChecker =
                Flow.<Contextual<Object>>create()
                        .via(ActivityChecker.ofNullable(activityCheckInterval, Contextual::getSelf, Contextual::getLog))
                        .filter(ctx -> !(ctx.getMessage() instanceof NotUsed));

        final Graph<FlowShape<Contextual<Object>, Contextual<Object>>, NotUsed> preEnforcerFlow =
                Optional.ofNullable(preEnforcer).map(PreEnforcer::fromFunctionWithContext).orElseGet(Flow::create);

        final Graph<FlowShape<Contextual<Object>, Contextual<Object>>, NotUsed> enforcerFlow =
                Pipe.joinFlows(enforcementProviders.stream()
                        .map(EnforcementProvider::toContextualFlow)
                        .collect(Collectors.toList()));

        return Flow.fromGraph(activityChecker)
                .via(preEnforcerFlow)
                .via(enforcerFlow)
                .to(Sink.foreach(ctx -> ctx.getLog().warning("unhandled: <{}>", ctx.getMessage())));
    }
}
