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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.events.policies.PolicyDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.sharding.ShardRegion;
import akka.pattern.CircuitBreaker;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Test for {@link org.eclipse.ditto.services.thingsearch.updater.actors.ThingsUpdater}.
 */
@RunWith(MockitoJUnitRunner.class)
public class ThingsUpdaterTest {

    private static final int NUMBER_OF_SHARDS = 3;
    private static final long KNOWN_REVISION = 7L;
    private static final DittoHeaders KNOWN_HEADERS =
            DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_2).build();
    private static final String KNOWN_THING_ID = "namespace:aThing";
    private static final String KNOWN_POLICY_ID = "namespace:aPolicy";

    @Mock
    private ThingsSearchUpdaterPersistence persistence;

    private ActorSystem actorSystem;
    private TestProbe thingCache;
    private TestProbe policyCache;
    private TestProbe shardMessageReceiver;
    private ShardRegionFactory shardRegionFactory;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", ConfigFactory.load("test"));
        thingCache = TestProbe.apply(actorSystem);
        policyCache = TestProbe.apply(actorSystem);
        shardMessageReceiver = TestProbe.apply(actorSystem);
        shardRegionFactory = TestUtils.getMockedShardRegionFactory(
                original -> actorSystem.actorOf(TestUtils.getForwarderActorProps(original, shardMessageReceiver.ref())),
                ShardRegionFactory.getInstance(actorSystem)
        );
    }

    @After
    public void tearDown() {
        if (Objects.nonNull(actorSystem)) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void thingEventIsForwarded() {
        final ThingEvent event = ThingDeleted.of(KNOWN_THING_ID, KNOWN_REVISION, Instant.now(), KNOWN_HEADERS);
        new TestKit(actorSystem) {{
            final ActorRef underTest = createThingsUpdater();
            underTest.tell(event, getRef());
            expectShardedMessage(shardMessageReceiver, event, event.getId());
        }};
    }

    @Test
    public void policyEventIsForwarded() {
        final PolicyEvent event = PolicyDeleted.of(KNOWN_POLICY_ID, KNOWN_REVISION, Instant.now(), KNOWN_HEADERS);
        final Set<String> thingIds = new HashSet<>(
                Arrays.asList("com.thing:Thing1", "com.thing:Thing2", "com.thing:Thing3"));
        new TestKit(actorSystem) {{
            when(persistence.getThingIdsForPolicy(anyString())).thenReturn(Source.single(thingIds));

            final ActorRef underTest = createThingsUpdater();
            underTest.tell(event, getRef());

            waitUntil().getThingIdsForPolicy(KNOWN_POLICY_ID);
            thingIds.forEach(thingId -> expectShardedMessage(shardMessageReceiver, event, thingId));
        }};
    }

    @Test
    public void thingTagIsForwarded() {
        final EntityIdWithRevision event = ThingTag.of(KNOWN_THING_ID, KNOWN_REVISION);
        new TestKit(actorSystem) {{
            final ActorRef underTest = createThingsUpdater();
            underTest.tell(event, getRef());
            expectShardedMessage(shardMessageReceiver, event, event.getId());
        }};
    }

    @Test
    public void policyReferenceTagIsForwarded() {
        final PolicyReferenceTag message = PolicyReferenceTag.of(KNOWN_THING_ID, PolicyTag.of("a:b", 9L));
        new TestKit(actorSystem) {{
            final ActorRef underTest = createThingsUpdater();
            underTest.tell(message, getRef());
            expectShardedMessage(shardMessageReceiver, message, message.getEntityId());
        }};
    }

    @Test
    public void shardRegionStateIsForwarded() {
        final ShardRegion.GetShardRegionState$ shardRegionState = ShardRegion.getShardRegionStateInstance();
        new TestKit(actorSystem) {{
            final ActorRef underTest = createThingsUpdater();
            underTest.tell(shardRegionState, getRef());
            shardMessageReceiver.expectMsg(shardRegionState);
        }};
    }


    private void expectShardedMessage(final TestProbe probe, final Jsonifiable event, final String id) {
        final ShardedMessageEnvelope envelope = probe.expectMsgClass(ShardedMessageEnvelope.class);
        assertThat(envelope.getMessage())
                .isEqualTo(event.toJson());
        assertThat(envelope.getId())
                .isEqualTo(id);
    }

    private ActorRef createThingsUpdater() {
        final CircuitBreaker circuitBreaker =
                new CircuitBreaker(actorSystem.dispatcher(),
                        actorSystem.scheduler(),
                        5,
                        scala.concurrent.duration.Duration.create(30, "s"),
                        scala.concurrent.duration.Duration.create(1, "min"));
        final boolean eventProcessingActive = true;
        final Duration activityCheckInterval = Duration.ofSeconds(30L);
        return actorSystem.actorOf(ThingsUpdater.props(
                NUMBER_OF_SHARDS,
                shardRegionFactory,
                persistence,
                circuitBreaker,
                eventProcessingActive,
                activityCheckInterval,
                Integer.MAX_VALUE,
                thingCache.ref(),
                policyCache.ref()));
    }

    private ThingsSearchUpdaterPersistence waitUntil() {
        return verify(persistence, Mockito.timeout(2000L));
    }
}