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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.ShardedMessageEnvelope;
import org.eclipse.ditto.internal.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.internal.utils.ddata.DistributedData;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.api.PolicyReferenceTag;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoUpdateThing;
import org.eclipse.ditto.thingsearch.api.events.ThingsOutOfSync;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultUpdaterConfig;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
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
    private static final Config TEST_CONFIG = ConfigFactory.load("test");

    private ActorSystem actorSystem;
    private TestProbe shardMessageReceiver;
    private ShardRegionFactory shardRegionFactory;
    private BlockedNamespaces blockedNamespaces;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TEST_CONFIG);
        shardMessageReceiver = TestProbe.apply(actorSystem);
        shardRegionFactory = getMockedShardRegionFactory(shardMessageReceiver.ref());
        // create blocked namespaces cache without role and with the default replicator name
        blockedNamespaces =
                BlockedNamespaces.create(DistributedData.createConfig(actorSystem, "replicator", ""), actorSystem);
    }

    @After
    public void tearDown() {
        if (Objects.nonNull(actorSystem)) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void thingEventIsForwarded() {
        final ThingEvent<?> event = ThingDeleted.of(KNOWN_THING_ID, KNOWN_REVISION, Instant.now(), KNOWN_HEADERS, null);
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
            expectShardedMessage(shardMessageReceiver, message, message.getThingId());
        }};
    }

    @Test
    public void forwardUpdateThingOnCommand() {
        new TestKit(actorSystem) {{
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
            final ActorRef underTest = createThingsUpdater();
            final Collection<ThingId> thingIds = IntStream.range(0, 10)
                    .mapToObj(i -> ThingId.of("a:" + i))
                    .toList();
            underTest.tell(ThingsOutOfSync.of(thingIds, dittoHeaders), getRef());

            // command order not guaranteed due to namespace blocking
            final Set<EntityId> expectedIds = new HashSet<>(thingIds);
            for (final NamespacedEntityId ignored : thingIds) {
                final ShardedMessageEnvelope envelope =
                        shardMessageReceiver.expectMsgClass(ShardedMessageEnvelope.class);
                final EntityId envelopeId = envelope.getEntityId();
                assertThat(expectedIds).contains(envelopeId);
                expectedIds.remove(envelopeId);
                assertThat(envelope.getDittoHeaders()).isEqualTo(dittoHeaders);
                assertThat(envelope.getMessage())
                        .isEqualTo(
                                SudoUpdateThing.of(ThingId.of(envelopeId), UpdateReason.BACKGROUND_SYNC, dittoHeaders)
                                        .toJson());
            }
        }};
    }

    @Test
    public void shardRegionStateIsForwarded() {
        final ShardRegion.GetShardRegionState$ shardRegionState =
                (ShardRegion.GetShardRegionState$) ShardRegion.getShardRegionStateInstance();
        new TestKit(actorSystem) {{
            final ActorRef underTest = createThingsUpdater();
            underTest.tell(shardRegionState, getRef());
            shardMessageReceiver.expectMsg(shardRegionState);
        }};
    }

    @Test
    public void blockAndAcknowledgeMessagesByNamespace() throws Exception {
        final String blockedNamespace = "blocked";
        final ThingEvent<?> thingEvent = ThingDeleted.of(ThingId.of(blockedNamespace, "thing2"), 10L,
                Instant.now(), KNOWN_HEADERS, null);
        final PolicyReferenceTag refTag =
                PolicyReferenceTag.of(ThingId.of(blockedNamespace + ":thing4"),
                        PolicyTag.of(KNOWN_POLICY_ID, 12L));

        blockedNamespaces.add(blockedNamespace).toCompletableFuture().get();

        new TestKit(actorSystem) {{
            final ActorRef underTest = createThingsUpdater();

            // events blocked silently
            underTest.tell(thingEvent, getRef());

            // policy tag blocked with acknowledgement
            underTest.tell(refTag, getRef());
            expectMsg(StreamAck.success(refTag.asIdentifierString()));

            // check that blocked messages are not forwarded to shard region
            shardMessageReceiver.expectNoMessage(FiniteDuration.create(1L, TimeUnit.SECONDS));
        }};
    }

    private static void expectShardedMessage(final TestProbe probe, final Jsonifiable<?> event, final EntityId id) {
        final ShardedMessageEnvelope envelope = probe.expectMsgClass(ShardedMessageEnvelope.class);

        assertThat(envelope.getMessage()).isEqualTo(event.toJson());
        assertThat((CharSequence) envelope.getEntityId()).isEqualTo(id);
    }

    private ActorRef createThingsUpdater() {
        // updater not configured in test.conf; using default config with event processing disabled
        // so that actor does not poll updater shard region for stats
        final UpdaterConfig config =
                DefaultUpdaterConfig.of(ConfigFactory.parseString("updater.event-processing-active=false"));
        final ActorRef thingsShardRegion = shardRegionFactory.getThingsShardRegion(NUMBER_OF_SHARDS);
        final TestProbe pubSubMediatorProbe = TestProbe.apply(actorSystem);
        return actorSystem.actorOf(
                ThingsUpdater.props(thingsShardRegion, config, blockedNamespaces, pubSubMediatorProbe.ref()));
    }

    /**
     * Creates a mocked shared region factory that allows you to modify the returned actor Refs. It will create the
     * correct Actors using the original shardRegionFactory but return the modified Actor.
     *
     * @return The mocked ShardRegionFactory.
     */
    private static ShardRegionFactory getMockedShardRegionFactory(final ActorRef shardMessageReceiver) {

        final ShardRegionFactory shardRegionFactory = Mockito.mock(ShardRegionFactory.class);
        when(shardRegionFactory.getPoliciesShardRegion(anyInt()))
                .thenAnswer(invocation -> shardMessageReceiver);
        when(shardRegionFactory.getSearchUpdaterShardRegion(anyInt(), any(Props.class), any()))
                .thenAnswer(invocation -> shardMessageReceiver);
        when(shardRegionFactory.getThingsShardRegion(anyInt()))
                .thenAnswer(invocation -> shardMessageReceiver);
        return shardRegionFactory;
    }

}
