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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.concierge.actors.ShardRegions;
import org.eclipse.ditto.services.concierge.actors.cleanup.messages.CreditDecision;
import org.eclipse.ditto.services.concierge.common.PersistenceCleanupConfig;
import org.eclipse.ditto.services.models.things.DittoThingSnapshotTaken;
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
        new TestKit(actorSystem) {{
            final PersistenceCleanupConfig config = PersistenceCleanupConfig.fromConfig(
                    ConfigFactory.parseString("credit-decision={credit-for-requests=2,interval=1h}," +
                            "enabled=true," +
                            "quiet-period=1h,cleanup-timeout=5m,parallelism=1," +
                            "keep={credit-decisions=1,actions=1,events=1}"));
            final ShardRegions shardRegions = Mockito.mock(ShardRegions.class);
            Mockito.when(shardRegions.things()).thenReturn(getRef());
            final Props props = EventSnapshotCleanupCoordinator.props(config, getRef(), shardRegions);
            final ActorRef underTest = actorSystem.actorOf(props);
            expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            final CreditDecision creditDecision = CreditDecision.yes(2, "2 credit");
            underTest.tell(creditDecision, ActorRef.noSender());

            final List<ThingId> ids =
                    IntStream.range(0, 5).mapToObj(i -> ThingId.of("thing:" + i)).collect(Collectors.toList());
            ids.stream().map(DittoThingSnapshotTaken::of)
                    .forEach(request -> underTest.tell(request, ActorRef.noSender()));
            underTest.tell(DittoThingSnapshotTaken.of(ids.get(0)), ActorRef.noSender());

            assertThat((CharSequence) expectMsgClass(CleanupPersistence.class).getEntityId()).isEqualTo(ids.get(0));
            assertThat((CharSequence) expectMsgClass(CleanupPersistence.class).getEntityId()).isEqualTo(ids.get(1));
            expectNoMessage();

            underTest.tell(creditDecision, ActorRef.noSender());

            assertThat((CharSequence) expectMsgClass(CleanupPersistence.class).getEntityId()).isEqualTo(ids.get(2));
            assertThat((CharSequence) expectMsgClass(CleanupPersistence.class).getEntityId()).isEqualTo(ids.get(3));
            expectNoMessage();
        }};
    }
}
