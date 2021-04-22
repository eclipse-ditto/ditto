/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.actors.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.services.concierge.actors.ShardRegions;
import org.eclipse.ditto.services.concierge.actors.cleanup.messages.CreditDecision;
import org.eclipse.ditto.services.concierge.common.PersistenceCleanupConfig;
import org.eclipse.ditto.things.api.ThingSnapshotTaken;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link EventSnapshotCleanupCoordinator}.
 */
public final class EventSnapshotCleanupCoordinatorTest {

    private ActorSystem actorSystem;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create();
    }

    @After
    public void cleanUp() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void delayCleanUpByRequestAccordingToCredit() {
        final var snapshotTakenEvents = IntStream.range(0, 5)
                .mapToObj(i -> ThingId.of("thing:" + i))
                .map(thingId -> ThingSnapshotTaken.newBuilder(thingId,
                        1,
                        PersistenceLifecycle.ACTIVE,
                        JsonFactory.newObject(Thing.JsonFields.ID.getPointer(), JsonValue.of(thingId.toString()))
                ).timestamp(Instant.now()).build())
                .collect(Collectors.toList());

        final PersistenceCleanupConfig config = PersistenceCleanupConfig.fromConfig(
                ConfigFactory.parseString("credit-decision={credit-for-requests=2,interval=1h}," +
                        "enabled=true," +
                        "quiet-period=1h,cleanup-timeout=5m,parallelism=1," +
                        "keep={credit-decisions=1,actions=1,events=1}"));
        final ShardRegions shardRegions = Mockito.mock(ShardRegions.class);

        new TestKit(actorSystem) {{
            Mockito.when(shardRegions.things()).thenReturn(getRef());
            final Props props = EventSnapshotCleanupCoordinator.props(config, getRef(), shardRegions);
            final ActorRef underTest = actorSystem.actorOf(props);
            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            final CreditDecision creditDecision = CreditDecision.yes(2, "2 credit");
            underTest.tell(creditDecision, ActorRef.noSender());

            snapshotTakenEvents.forEach(request -> underTest.tell(request, ActorRef.noSender()));
            underTest.tell(snapshotTakenEvents.get(0), ActorRef.noSender());

            final var cleanupPersistence1 = expectMsgClass(CleanupPersistence.class);
            assertThat((CharSequence) cleanupPersistence1.getEntityId()).isEqualTo(getThingId(snapshotTakenEvents, 0));

            final var cleanupPersistence2 = expectMsgClass(CleanupPersistence.class);
            assertThat((CharSequence) cleanupPersistence2.getEntityId()).isEqualTo(getThingId(snapshotTakenEvents, 1));

            expectNoMessage();

            underTest.tell(creditDecision, ActorRef.noSender());

            final var cleanupPersistence3 = expectMsgClass(CleanupPersistence.class);
            assertThat((CharSequence) cleanupPersistence3.getEntityId()).isEqualTo(getThingId(snapshotTakenEvents, 2));

            final var cleanupPersistence4 = expectMsgClass(CleanupPersistence.class);
            assertThat((CharSequence) cleanupPersistence4.getEntityId()).isEqualTo(getThingId(snapshotTakenEvents, 3));

            expectNoMessage();
        }};
    }

    private static ThingId getThingId(final List<ThingSnapshotTaken> snapshotTakenEvents, final int index) {
        final var snapshotTaken = snapshotTakenEvents.get(index);
        return snapshotTaken.getEntityId();
    }

}
