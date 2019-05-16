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
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.services.utils.cache.entry.Entry;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.javadsl.Broadcast;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Sink;

/**
 * Actor to authorize signals by enforcing policies or ACLs on signals.
 */
public final class EnforcerActor extends AbstractEnforcerActor {

    /**
     * The name of this actor in the actorSystem.
     */
    public static final String ACTOR_NAME = "enforcer";

    private final Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> handler;
    private final Sink<Contextual<WithDittoHeaders>, CompletionStage<Done>> sink;

    private EnforcerActor(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final ActorRef conciergeForwarder,
            final Duration askTimeout,
            final int bufferSize,
            final int parallelism,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
            @Nullable final Cache<EntityId, Entry<EntityId>> thingIdCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache) {

        super(pubSubMediator, conciergeForwarder, askTimeout, bufferSize, parallelism,
                thingIdCache, aclEnforcerCache, policyEnforcerCache);

        handler = assembleHandler(enforcementProviders, preEnforcer);
        sink = assembleSink();
    }

    /**
     * Creates Akka configuration object Props for this enforcer actor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param askTimeout the ask timeout duration: the duration to wait for entity shard regions.
     * @param conciergeForwarder an actorRef to concierge forwarder.
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
            final int bufferSize,
            final int parallelism,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
            @Nullable final Cache<EntityId, Entry<EntityId>> thingIdCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache) {

        return Props.create(EnforcerActor.class, () ->
                new EnforcerActor(pubSubMediator, enforcementProviders, conciergeForwarder, askTimeout,
                        bufferSize, parallelism, preEnforcer, thingIdCache, aclEnforcerCache, policyEnforcerCache));
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor. Caution: The actor does not terminate itself
     * after a period of inactivity.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param askTimeout the ask timeout duration: the duration to wait for entity shard regions.
     * @param conciergeForwarder an actorRef to concierge forwarder.
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
            final int bufferSize,
            final int parallelism,
            @Nullable final Cache<EntityId, Entry<EntityId>> thingIdCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache) {

        return props(pubSubMediator, enforcementProviders, askTimeout, conciergeForwarder,
                bufferSize, parallelism, null, thingIdCache, aclEnforcerCache, policyEnforcerCache);
    }

    @Override
    protected Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> processMessageFlow() {
        return handler;
    }

    @Override
    protected Sink<Contextual<WithDittoHeaders>, ?> processedMessageSink() {
        return sink;
    }

    /**
     * Create the flow that defines the behavior of this enforcer actor by enhancing the passed in {@link Contextual}
     * e.g. with a message and receiver which in the end (in the {@link #assembleSink()}) are processed.
     *
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @return a handler as {@link Flow} of {@link Contextual} messages.
     */
    private Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> assembleHandler(
            final Set<EnforcementProvider<?>> enforcementProviders,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        final Graph<FlowShape<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>>, NotUsed> preEnforcerFlow =
                Optional.ofNullable(preEnforcer)
                        .map(PreEnforcer::fromFunctionWithContext)
                        .orElseGet(Flow::create);


        final Graph<FlowShape<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>>, NotUsed> enforcerFlow = GraphDSL.create(
                Broadcast.<Contextual<WithDittoHeaders>>create(enforcementProviders.size()),
                Merge.<Contextual<WithDittoHeaders>>create(enforcementProviders.size(), true),
                (notUsed1, notUsed2) -> notUsed1,
                (builder, bcast, merge) -> {

                    enforcementProviders.forEach(enforcementProvider ->
                            builder.from(bcast)
                                    .via(builder.add(enforcementProvider.toContextualFlow()))
                                    .toFanIn(merge)
                    );

                    return FlowShape.of(bcast.in(), merge.out());
                });

        return Flow.<Contextual<WithDittoHeaders>>create()
                .via(preEnforcerFlow)
                .via(enforcerFlow);
    }

    /**
     * Create the sink that defines the outcome of this enforcer actor's stream.
     *
     * @return the Sink receiving the enriched {@link Contextual} to finally process.
     */
    private Sink<Contextual<WithDittoHeaders>, CompletionStage<Done>> assembleSink() {
        return Sink.foreach(theContextual -> {
            LogUtil.enhanceLogWithCorrelationId(log, theContextual.getMessage());
            final Optional<ActorRef> receiverOpt = theContextual.getReceiver();
            if (receiverOpt.isPresent()) {
                final ActorRef receiver = receiverOpt.get();
                final Object wrappedMsg = theContextual.getReceiverWrapperFunction().apply(theContextual.getMessage());
                log.debug("About to send contextual message <{}> to receiver: <{}>", wrappedMsg, receiver);
                receiver.tell(wrappedMsg, theContextual.getSender());
            } else {
                log.debug("No receiver found in Contextual - as a result just ignoring it: <{}>", theContextual);
            }
        });
    }
}
