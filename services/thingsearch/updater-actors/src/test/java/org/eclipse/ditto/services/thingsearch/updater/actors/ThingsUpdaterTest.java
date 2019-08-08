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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.id.PolicyId;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfigReader;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.sharding.ShardRegion;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Test for {@link ThingsUpdater}.
 */
public final class ThingsUpdaterTest {

    private static final int NUMBER_OF_SHARDS = 3;
    private static final long KNOWN_REVISION = 7L;
    private static final DittoHeaders KNOWN_HEADERS =
            DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_2).build();
    private static final ThingId KNOWN_THING_ID = ThingId.of("namespace", "aThing");
    private static final PolicyId KNOWN_POLICY_ID = PolicyId.of("namespace", "aPolicy");

    private ActorSystem actorSystem;
    private TestProbe shardMessageReceiver;
    private ShardRegionFactory shardRegionFactory;
    private BlockedNamespaces blockedNamespaces;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", ConfigFactory.load("test"));
        shardMessageReceiver = TestProbe.apply(actorSystem);
        shardRegionFactory = TestUtils.getMockedShardRegionFactory(
                original -> actorSystem.actorOf(TestUtils.getForwarderActorProps(original, shardMessageReceiver.ref())),
                ShardRegionFactory.getInstance(actorSystem)
        );
        // create blocked namespaces cache without role and with the default replicator name
        blockedNamespaces =
                BlockedNamespaces.of(DistributedDataConfigReader.of(actorSystem, "replicator", ""), actorSystem);
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
            expectShardedMessage(shardMessageReceiver, event, event.getThingEntityId());
        }};
    }

    @Test
    public void thingTagIsForwarded() {
        final EntityIdWithRevision event = ThingTag.of(KNOWN_THING_ID, KNOWN_REVISION);
        new TestKit(actorSystem) {{
            final ActorRef underTest = createThingsUpdater();
            underTest.tell(event, getRef());
            expectShardedMessage(shardMessageReceiver, event, event.getEntityId());
        }};
    }

    @Test
    public void policyReferenceTagIsForwarded() {
        final PolicyReferenceTag message =
                PolicyReferenceTag.of(KNOWN_THING_ID, PolicyTag.of(PolicyId.of("a", "b"), 9L));
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
    public void blockAndAcknowledgeMessagesByNamespace() throws Exception {
        final String blockedNamespace = "blocked";
        final ThingEvent thingEvent = ThingDeleted.of(ThingId.of(blockedNamespace, "thing2"), 10L, KNOWN_HEADERS);
        final ThingTag thingTag = ThingTag.of(ThingId.of(blockedNamespace, "thing3"), 11L);
        final PolicyReferenceTag refTag =
                PolicyReferenceTag.of(DefaultEntityId.of(blockedNamespace + ":thing4"),
                        PolicyTag.of(KNOWN_POLICY_ID, 12L));

        blockedNamespaces.add(blockedNamespace).toCompletableFuture().get();

        new TestKit(actorSystem) {{
            final ActorRef underTest = createThingsUpdater();

            // events blocked silently
            underTest.tell(thingEvent, getRef());

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

    private static void expectShardedMessage(final TestProbe probe, final Jsonifiable event, final EntityId id) {
        final ShardedMessageEnvelope envelope = probe.expectMsgClass(ShardedMessageEnvelope.class);

        assertThat(envelope.getMessage()).isEqualTo(event.toJson());
        assertThat((CharSequence) envelope.getEntityId()).isEqualTo(id);
    }

    private ActorRef createThingsUpdater() {
        final boolean eventProcessingActive = true;
        final ActorRef thingsShardRegion = shardRegionFactory.getThingsShardRegion(NUMBER_OF_SHARDS);
        return actorSystem.actorOf(
                ThingsUpdater.props(actorSystem.deadLetters(), thingsShardRegion, eventProcessingActive,
                        blockedNamespaces));
    }

}
