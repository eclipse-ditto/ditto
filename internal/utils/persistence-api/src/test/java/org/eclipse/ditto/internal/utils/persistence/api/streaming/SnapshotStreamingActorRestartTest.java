/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.api.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Kill;
import org.apache.pekko.actor.OneForOneStrategy;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.SupervisorStrategy;
import org.apache.pekko.japi.pf.DeciderBuilder;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

/**
 * Verifies that {@link SnapshotStreamingActor#props(java.util.function.Function, java.util.function.Function,
 * java.util.function.Supplier)} rebuilds fresh {@link SnapshotStreamingActor.StreamingResources} for every
 * incarnation (supervised restarts included), and that the previous incarnation's resource is closed while the
 * live incarnation's resource stays open.
 */
public final class SnapshotStreamingActorRestartTest {

    private static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        // SnapshotStreamingActor resolves its pubSubMediator via DistributedPubSub, which requires the
        // cluster actor provider (the actor system used in production always has it configured).
        system = ActorSystem.create("SnapshotStreamingActorRestartTest",
                ConfigFactory.parseMap(Map.of("pekko.actor.provider", "cluster")));
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(system);
    }

    /** Parent that restarts its child on ANY exception (mirrors DittoRootActor's restart cases). */
    private static final class RestartingParent extends AbstractActor {

        private final Props childProps;
        private ActorRef child;

        private RestartingParent(final Props childProps) {
            this.childProps = childProps;
        }

        @Override
        public void preStart() {
            child = getContext().actorOf(childProps, "streaming");
        }

        @Override
        public SupervisorStrategy supervisorStrategy() {
            return new OneForOneStrategy(DeciderBuilder.matchAny(e -> SupervisorStrategy.restart()).build());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchAny(msg -> child.forward(msg, getContext())).build();
        }
    }

    @Test
    public void restartBuildsFreshResourcesAndClosesOldOnes() {
        final AtomicInteger built = new AtomicInteger();
        final List<AtomicBoolean> closedFlags = new CopyOnWriteArrayList<>();
        final DittoReadJournal readJournal = Mockito.mock(DittoReadJournal.class);

        final Props props = SnapshotStreamingActor.props(
                pid -> EntityId.of(EntityType.of("thing"), pid.substring("thing:".length())),
                id -> "thing:" + id,
                () -> {
                    built.incrementAndGet();
                    final AtomicBoolean closed = new AtomicBoolean();
                    closedFlags.add(closed);
                    return new SnapshotStreamingActor.StreamingResources(readJournal, () -> closed.set(true));
                });

        final ActorRef parent = system.actorOf(Props.create(RestartingParent.class, () ->
                new RestartingParent(props)));
        // force a restart of the child
        system.actorSelection(parent.path().child("streaming")).tell(Kill.getInstance(), ActorRef.noSender());

        Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(built.get()).as("a fresh resource set per incarnation").isEqualTo(2);
            assertThat(closedFlags.get(0)).as("first incarnation's resource closed").isTrue();
            assertThat(closedFlags.get(1)).as("live incarnation's resource NOT closed").isFalse();
        });
    }

}
