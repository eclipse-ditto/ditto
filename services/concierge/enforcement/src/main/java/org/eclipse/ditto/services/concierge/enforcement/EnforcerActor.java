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

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
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
import akka.stream.javadsl.Keep;
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

    private final Sink<Contextual<WithDittoHeaders>, CompletionStage<Done>> sink;

    @SuppressWarnings("unused")
    private EnforcerActor(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final ActorRef conciergeForwarder,
            @Nullable final PreEnforcer preEnforcer,
            @Nullable final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache,
            @Nullable final Cache<EntityIdWithResourceType, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache) {

        super(pubSubMediator, conciergeForwarder, thingIdCache, aclEnforcerCache, policyEnforcerCache);
        final ActorRef enforcementScheduler =
                getContext().actorOf(EnforcementScheduler.props(), EnforcementScheduler.ACTOR_NAME);
        sink = assembleSink(enforcementProviders, preEnforcer, enforcementScheduler);
    }

    /**
     * Creates Akka configuration object Props for this enforcer actor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param conciergeForwarder an actorRef to concierge forwarder.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @param thingIdCache the cache for Thing IDs to either ACL or Policy ID.
     * @param aclEnforcerCache the ACL cache.
     * @param policyEnforcerCache the Policy cache.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final ActorRef conciergeForwarder,
            @Nullable final PreEnforcer preEnforcer,
            @Nullable final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache,
            @Nullable final Cache<EntityIdWithResourceType, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache) {

        return Props.create(EnforcerActor.class, pubSubMediator, enforcementProviders, conciergeForwarder, preEnforcer,
                thingIdCache, aclEnforcerCache, policyEnforcerCache);
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor. Caution: The actor does not terminate itself
     * after a period of inactivity.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param conciergeForwarder an actorRef to concierge forwarder.
     * @param thingIdCache the cache for Thing IDs to either ACL or Policy ID.
     * @param aclEnforcerCache the ACL cache.
     * @param policyEnforcerCache the Policy cache.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final ActorRef conciergeForwarder,
            @Nullable final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache,
            @Nullable final Cache<EntityIdWithResourceType, Entry<Enforcer>> aclEnforcerCache,
            @Nullable final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache) {

        return props(pubSubMediator, enforcementProviders, conciergeForwarder, null, thingIdCache, aclEnforcerCache,
                policyEnforcerCache);
    }

    @Override
    protected Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> processMessageFlow() {
        return Flow.create();
    }

    @Override
    protected Sink<Contextual<WithDittoHeaders>, ?> processedMessageSink() {
        return sink;
    }

    /**
     * Create the sink that defines the behavior of this enforcer actor by creating enforcement tasks for incoming
     * messages.
     *
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @return a handler as {@link Flow} of {@link Contextual} messages.
     */
    @SuppressWarnings("unchecked") // due to GraphDSL usage
    private Sink<Contextual<WithDittoHeaders>, CompletionStage<Done>> assembleSink(
            final Set<EnforcementProvider<?>> enforcementProviders,
            @Nullable final PreEnforcer preEnforcer,
            final ActorRef enforcementScheduler) {

        final PreEnforcer preEnforcerStep =
                preEnforcer != null ? preEnforcer : CompletableFuture::completedStage;
        final Graph<FlowShape<Contextual<WithDittoHeaders>, EnforcementTask>, NotUsed> enforcerFlow =
                GraphDSL.create(
                        Broadcast.<Contextual<WithDittoHeaders>>create(enforcementProviders.size()),
                        Merge.<EnforcementTask>create(enforcementProviders.size(), true),
                        (notUsed1, notUsed2) -> notUsed1,
                        (builder, bcast, merge) -> {
                            final ArrayList<EnforcementProvider<?>> providers = new ArrayList<>(enforcementProviders);
                            for (int i = 0; i < providers.size(); i++) {
                                builder.from(bcast.out(i))
                                        .via(builder.add(providers.get(i).createEnforcementTask(preEnforcerStep)))
                                        .toInlet(merge.in(i));
                            }

                            return FlowShape.of(bcast.in(), merge.out());
                        });

        return Flow.<Contextual<WithDittoHeaders>>create()
                .via(enforcerFlow)
                .toMat(Sink.foreach(task -> enforcementScheduler.tell(task, ActorRef.noSender())), Keep.right());
    }
}
