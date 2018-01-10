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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.mockito.Mockito;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * Test constants.
 */
@Immutable
public final class TestUtils {

    private TestUtils() {
        throw new AssertionError();
    }

    /**
     * Creates a mocked shared region factory that allows you to modify the returned actor Refs. It will create the
     * correct Actors using the original shardRegionFactory but return the modified Actor.
     *
     * @param actorRefModifier The modifying function to use on the actorRefs returned by the original
     * shardRegionFactory.
     * @param originalShardRegionFactory The original {@link org.eclipse.ditto.services.thingsearch.updater.actors.ShardRegionFactory}
     * used the generate the shard region Actors.
     * @return The mocked ShardRegionFactory.
     */
    static ShardRegionFactory getMockedShardRegionFactory(
            final Function<ActorRef, ActorRef> actorRefModifier,
            final ShardRegionFactory originalShardRegionFactory) {
        return getMockedShardRegionFactory(actorRefModifier, actorRefModifier, actorRefModifier,
                originalShardRegionFactory);
    }

    /**
     * Creates a mocked shared region factory that allows you to modify the returned actor Refs. It will create the
     * correct Actors using the original shardRegionFactory but return the modified Actor.
     *
     * @param policiesShardModifier The modifying function to use on the policies shard region actor returned by the
     * original shardRegionFactory.
     * @param searchUpdaterShardModifier The modifying function to use on the searchupdater shard region actor returned
     * by the  original shardRegionFactory.
     * @param topologiesShardModifier The modifying function to use on the topologies shard region actor returned by the
     * original shardRegionFactory.
     * @param originalShardRegionFactory The original {@link org.eclipse.ditto.services.thingsearch.updater.actors.ShardRegionFactory}
     * used the generate the shard region Actors.
     * @return The mocked ShardRegionFactory.
     */
    private static ShardRegionFactory getMockedShardRegionFactory(
            final Function<ActorRef, ActorRef> policiesShardModifier,
            final Function<ActorRef, ActorRef> searchUpdaterShardModifier,
            final Function<ActorRef, ActorRef> topologiesShardModifier,
            final ShardRegionFactory originalShardRegionFactory) {
        final ShardRegionFactory shardRegionFactory = Mockito.mock(ShardRegionFactory.class);
        when(shardRegionFactory.getPoliciesShardRegion(anyInt()))
                .thenAnswer(invocation -> {
                    final int numberOfShards = invocation.getArgument(0);
                    return policiesShardModifier.apply(
                            originalShardRegionFactory.getPoliciesShardRegion(numberOfShards));
                });
        when(shardRegionFactory.getSearchUpdaterShardRegion(anyInt(), any(Props.class)))
                .thenAnswer(invocation -> {
                    final int numberOfShards = invocation.getArgument(0);
                    final Props props = invocation.getArgument(1);
                    return searchUpdaterShardModifier.apply(
                            originalShardRegionFactory.getSearchUpdaterShardRegion(numberOfShards, props)
                    );
                });
        when(shardRegionFactory.getThingsShardRegion(anyInt()))
                .thenAnswer(invocation -> {
                    final int numberOfShards = invocation.getArgument(0);
                    return topologiesShardModifier.apply(
                            originalShardRegionFactory.getThingsShardRegion(numberOfShards));
                });
        return shardRegionFactory;
    }

    /**
     * Props for creating an Actor that will forward all messages to all {@code receivers}.
     *
     * @param receivers The Actors to which all messages should be forwarded.
     * @return The props.
     */
    static Props getForwarderActorProps(final ActorRef... receivers) {
        return ForwarderActor.props(receivers);
    }

    /**
     * Actor that will forward any message to its inner actors.
     */
    private static class ForwarderActor extends AbstractActor {

        private final List<ActorRef> receivers;

        private ForwarderActor(final ActorRef... receivers) {
            this.receivers = Arrays.asList(receivers);
        }

        /**
         * Props for creating an Actor that will forward all messages to all {@code receivers}.
         *
         * @param receivers The Actors to which all messages should be forwarded.
         * @return The props.
         */
        static Props props(final ActorRef... receivers) {
            return Props.create(ForwarderActor.class, () -> new ForwarderActor(receivers));
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .matchAny(m -> receivers.forEach(r -> r.forward(m, getContext())))
                    .build();
        }
    }
}
