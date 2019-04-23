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
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.utils.akka.controlflow.Pipe;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.entry.Entry;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.javadsl.Flow;

/**
 * Actor to authorize signals by enforcing policies or ACLs on signals.
 */
public final class EnforcerActor extends AbstractEnforcerActor {

    /**
     * The name of this actor in the actorSystem.
     */
    public static final String ACTOR_NAME = "enforcer";

    private final Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> handler;

    private EnforcerActor(final ActorRef pubSubMediator, final ActorRef conciergeForwarder,
            final Executor enforcerExecutor,
            final Duration askTimeout,
            final Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> handler,
            final int bufferSize,
            final int parallelism,
            @Nullable final Cache<EntityId, Entry<EntityId>> thingIdCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache) {
        super(pubSubMediator, conciergeForwarder, enforcerExecutor, askTimeout, bufferSize, parallelism,
                thingIdCache, aclEnforcerCache, policyEnforcerCache);
        this.handler = handler;
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
     * @param bufferSize the buffer size used for the Source queue.
     * @param parallelism parallelism to use for processing messages in parallel.
     * @param thingIdCache the cache for Thing IDs to either ACL or Policy ID.
     * @param aclEnforcerCache the ACL cache.
     * @param policyEnforcerCache the Policy cache.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final Duration askTimeout,
            final ActorRef conciergeForwarder,
            final Executor enforcerExecutor,
            final int bufferSize,
            final int parallelism,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
            @Nullable final Cache<EntityId, Entry<EntityId>> thingIdCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache) {

        // create the sink exactly once per props and share it across all actors to not waste memory
        final Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> messageHandler =
                assembleHandler(enforcementProviders, preEnforcer);

        return Props.create(EnforcerActor.class, () ->
                new EnforcerActor(pubSubMediator, conciergeForwarder, enforcerExecutor, askTimeout, messageHandler,
                        bufferSize, parallelism, thingIdCache, aclEnforcerCache, policyEnforcerCache));
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor. Caution: The actor does not terminate itself
     * after a period of inactivity.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param askTimeout the ask timeout duration: the duration to wait for entity shard regions.
     * @param conciergeForwarder an actorRef to concierge forwarder.
     * @param enforcerExecutor the Executor to run async tasks on during enforcement.
     * @param bufferSize the buffer size used for the Source queue.
     * @param parallelism parallelism to use for processing messages in parallel.
     * @param thingIdCache the cache for Thing IDs to either ACL or Policy ID.
     * @param aclEnforcerCache the ACL cache.
     * @param policyEnforcerCache the Policy cache.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final Duration askTimeout,
            final ActorRef conciergeForwarder,
            final Executor enforcerExecutor,
            final int bufferSize,
            final int parallelism,
            @Nullable final Cache<EntityId, Entry<EntityId>> thingIdCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache) {

        return props(pubSubMediator, enforcementProviders, askTimeout, conciergeForwarder, enforcerExecutor,
                bufferSize, parallelism, null, thingIdCache, aclEnforcerCache, policyEnforcerCache);
    }

    @Override
    protected Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> getHandler() {
        return handler;
    }

    /**
     * Create the sink that defines the behavior of this enforcer actor. Do NOT call this or similar methods inside an
     * actor instance; otherwise the stream components will waste huge amounts of heap space.
     *
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @return a handler as {@link Flow} of {@link Contextual} messages.
     */
    private static Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> assembleHandler(
            final Set<EnforcementProvider<?>> enforcementProviders,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        final Graph<FlowShape<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>>, NotUsed> preEnforcerFlow =
                Optional.ofNullable(preEnforcer).map(PreEnforcer::fromFunctionWithContext).orElseGet(Flow::create);

        final Graph<FlowShape<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>>, NotUsed> enforcerFlow =
                Pipe.joinFlows(enforcementProviders.stream()
                        .map(EnforcementProvider::toContextualFlow)
                        .collect(Collectors.toList()));

        return Flow.<Contextual<WithDittoHeaders>>create()
                .via(preEnforcerFlow)
                .via(enforcerFlow);
    }
}
