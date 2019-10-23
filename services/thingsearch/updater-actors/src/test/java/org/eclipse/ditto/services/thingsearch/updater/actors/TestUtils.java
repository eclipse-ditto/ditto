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
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Attributes;

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
     * @param originalShardRegionFactory The original {@link ShardRegionFactory}
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
     * @param thingShardModifier The modifying function to use on the thing shard region actor returned by the
     * original shardRegionFactory.
     * @param originalShardRegionFactory The original {@link ShardRegionFactory}
     * used the generate the shard region Actors.
     * @return The mocked ShardRegionFactory.
     */
    private static ShardRegionFactory getMockedShardRegionFactory(
            final Function<ActorRef, ActorRef> policiesShardModifier,
            final Function<ActorRef, ActorRef> searchUpdaterShardModifier,
            final Function<ActorRef, ActorRef> thingShardModifier,
            final ShardRegionFactory originalShardRegionFactory) {
        final ShardRegionFactory shardRegionFactory = Mockito.mock(ShardRegionFactory.class);
        when(shardRegionFactory.getPoliciesShardRegion(anyInt()))
                .thenAnswer(invocation -> {
                    final int numberOfShards = invocation.getArgument(0);
                    return policiesShardModifier.apply(
                            originalShardRegionFactory.getPoliciesShardRegion(numberOfShards));
                });
        when(shardRegionFactory.getSearchUpdaterShardRegion(anyInt(), any(Props.class), any()))
                .thenAnswer(invocation -> {
                    final int numberOfShards = invocation.getArgument(0);
                    final Props props = invocation.getArgument(1);
                    return searchUpdaterShardModifier.apply(
                            originalShardRegionFactory.getSearchUpdaterShardRegion(numberOfShards, props,
                                    SearchUpdaterRootActor.CLUSTER_ROLE)
                    );
                });
        when(shardRegionFactory.getThingsShardRegion(anyInt()))
                .thenAnswer(invocation -> {
                    final int numberOfShards = invocation.getArgument(0);
                    return thingShardModifier.apply(
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
     * Disable logging for 1 actor system to prevent stack trace printing. Comment out to debug the test.
     *
     * @param actorSystem the actor system.
     */
    static void disableLogging(final ActorSystem actorSystem) {
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());
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
