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
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.sharding.ShardRegion;
import akka.pattern.CircuitBreaker;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

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
    private TestProbe shardMessageReceiver;
    private ShardRegionFactory shardRegionFactory;
    private Cache<String, Object> namespaceCache;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", ConfigFactory.load("test"));
        shardMessageReceiver = TestProbe.apply(actorSystem);
        shardRegionFactory = TestUtils.getMockedShardRegionFactory(
                original -> actorSystem.actorOf(TestUtils.getForwarderActorProps(original, shardMessageReceiver.ref())),
                ShardRegionFactory.getInstance(actorSystem)
        );
        namespaceCache = CaffeineCache.of(Caffeine.newBuilder());
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

    @Test
    public void blockAndAcknowledgeMessagesByNamespace() {
        final PolicyEvent notBlockedPolicyEvent =
                PolicyDeleted.of("not.blocked:policy", 8L, Instant.now(), KNOWN_HEADERS);
        final PolicyEvent blockedPolicyEvent =
                PolicyDeleted.of("blocked:policy", 9L, Instant.now(), KNOWN_HEADERS);
        final Set<String> thingIds = new HashSet<>(Arrays.asList("not.blocked:thing", "blocked:thing1"));
        final ThingEvent thingEvent = ThingDeleted.of("blocked:thing2", 10L, KNOWN_HEADERS);
        final ThingTag thingTag = ThingTag.of("blocked:thing3", 11L);
        final PolicyReferenceTag refTag = PolicyReferenceTag.of("blocked:thing4", PolicyTag.of(KNOWN_POLICY_ID, 12L));

        namespaceCache.asMap().put("blocked", "blocked");

        new TestKit(actorSystem) {{
            when(persistence.getThingIdsForPolicy(anyString())).thenReturn(Source.single(thingIds));

            final ActorRef underTest = createThingsUpdater();

            // events blocked silently
            underTest.tell(thingEvent, getRef());
            underTest.tell(blockedPolicyEvent, getRef());

            // policy event only forwarded to not.blocked:thing1
            underTest.tell(notBlockedPolicyEvent, getRef());
            expectShardedMessage(shardMessageReceiver, notBlockedPolicyEvent, "not.blocked:thing");

            // thing tag blocked with acknowledgement
            underTest.tell(thingTag, getRef());
            expectMsg(StreamAck.success(thingTag.asIdentifierString()));

            // policy tag blocked with acknowledgement
            underTest.tell(refTag, getRef());
            expectMsg(StreamAck.success(refTag.asIdentifierString()));

            // check that blocked messages are not forwarded to shard region
            shardMessageReceiver.expectNoMessage(FiniteDuration.create(1L, TimeUnit.SECONDS));
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
                namespaceCache));
    }

    private ThingsSearchUpdaterPersistence waitUntil() {
        return verify(persistence, Mockito.timeout(2000L));
    }
}
