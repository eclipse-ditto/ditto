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
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.Pipe;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.PFBuilder;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;

/**
 * Actor to authorize signals by enforcing policies or ACLs on signals.
 */
public final class EnforcerActor extends AbstractEnforcerActor {

    private final Flow<Contextual<Object>, Contextual<Object>, NotUsed> handler;

    private EnforcerActor(final ActorRef pubSubMediator, final ActorRef conciergeForwarder,
            final Executor enforcerExecutor, final Duration askTimeout,
            final Flow<Contextual<Object>, Contextual<Object>, NotUsed> handler) {
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
        final Flow<Contextual<Object>, Contextual<Object>, NotUsed> messageHandler =
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
    protected Flow<Contextual<Object>, Contextual<Object>, NotUsed> getHandler() {
        return handler;
    }

    /**
     * Create the sink that defines the behavior of this enforcer actor. Do NOT call this or similar methods inside an
     * actor instance; otherwise the stream components will waste huge amounts of heap space.
     *
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @param activityCheckInterval how often to check for actor activity for termination after an idle period.
     * @return a handler as {@link Flow} of {@link Contextual} messages.
     */
    private static Flow<Contextual<Object>, Contextual<Object>, NotUsed> assembleHandler(
            final Set<EnforcementProvider<?>> enforcementProviders,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
            @Nullable final Duration activityCheckInterval) {

        final Flow<Contextual<Object>, Contextual<Object>, NotUsed> activityChecker;
        if (activityCheckInterval != null) {
            activityChecker = Flow.<Contextual<Object>>create()
                    .filterNot(ctx -> ctx.getMessage() instanceof NotUsed)
                    .idleTimeout(activityCheckInterval)
                    .recoverWithRetries(1,
                            new PFBuilder<Throwable, Graph<SourceShape<Contextual<Object>>, NotUsed>>()
                                    .match(TimeoutException.class,
                                            timeout -> Source.empty()) // complete the source on timeout
                                    .build());
        } else {
            activityChecker = Flow.<Contextual<Object>>create()
                    .filterNot(ctx -> ctx.getMessage() instanceof NotUsed);
        }

        final Graph<FlowShape<Contextual<Object>, Contextual<Object>>, NotUsed> preEnforcerFlow =
                Optional.ofNullable(preEnforcer).map(PreEnforcer::fromFunctionWithContext).orElseGet(Flow::create);

        final Graph<FlowShape<Contextual<Object>, Contextual<Object>>, NotUsed> enforcerFlow =
                Pipe.joinFlows(enforcementProviders.stream()
                        .map(EnforcementProvider::toContextualFlow)
                        .collect(Collectors.toList()));

        return Flow.fromGraph(activityChecker)
                .via(preEnforcerFlow)
                .via(enforcerFlow);
    }
}
