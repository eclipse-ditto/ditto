/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.internal.utils.pubsub.LiteralDDataProvider;
import org.eclipse.ditto.internal.utils.pubsub.api.AcksDeclared;
import org.eclipse.ditto.internal.utils.pubsub.api.DeclareAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.ReceiveRemoteAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.RemoteAcksChanged;
import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataUpdate;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.AbstractConfigAwareDDataProvider;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralDData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.Replicator;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link AckUpdater}.
 */
public final class AckUpdaterTest {

    private static final AbstractConfigAwareDDataProvider PROVIDER =
            LiteralDDataProvider.of("dc-default", "ack");

    private ActorSystem system1;
    private ActorSystem system2;
    private DData<Address, String, LiteralUpdate> ddata1;
    private DData<Address, String, LiteralUpdate> ddata2;

    @Before
    public void setUpCluster() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        system1 = ActorSystem.create("actorSystem", getTestConf());
        system2 = ActorSystem.create("actorSystem", getTestConf());
        final Cluster cluster1 = Cluster.get(system1);
        final Cluster cluster2 = Cluster.get(system2);
        cluster1.registerOnMemberUp(latch::countDown);
        cluster2.registerOnMemberUp(latch::countDown);
        cluster1.join(cluster1.selfAddress());
        cluster2.join(cluster1.selfAddress());
        ddata1 = LiteralDData.of(system1, PROVIDER);
        ddata2 = LiteralDData.of(system2, PROVIDER);
        // wait for both members to be UP
        latch.await();
    }

    @After
    public void shutdownCluster() {
        TestKit.shutdownActorSystem(system1);
        TestKit.shutdownActorSystem(system2);
    }

    @Test
    public void localConflict() {
        new TestKit(system1) {{
            final ActorRef underTest = system1.actorOf(AckSupervisor.props(ddata1));
            final TestProbe s1 = TestProbe.apply("s1", system1);
            final TestProbe s2 = TestProbe.apply("s2", system1);

            // WHEN: a group of ack labels are declared
            underTest.tell(DeclareAcks.of(s1.ref(), "g1", Set.of("a1", "a2")), getRef());
            expectMsgClass(AcksDeclared.class);

            // THEN: a different group of ack labels may not be declared with the same group name
            underTest.tell(DeclareAcks.of(s2.ref(), "g1", Set.of("a2", "a3")), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);

            // THEN: intersecting ack labels may not be declared without any group
            underTest.tell(DeclareAcks.of(s2.ref(), null, Set.of("a2", "a3")), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);

            // THEN: it is an error to declare intersecting ack labels under a different group.
            underTest.tell(DeclareAcks.of(s1.ref(), "g2", Set.of("a2", "a3")), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);
        }};
    }

    @Test
    public void remoteConflict() {
        new TestKit(system1) {{
            final ActorRef underTest = system1.actorOf(AckSupervisor.props(ddata1));
            final ActorRef ackUpdater2 = system2.actorOf(AckSupervisor.props(ddata2));
            final TestProbe s1 = TestProbe.apply("s1", system1);
            final TestProbe s2 = TestProbe.apply("s2", system2);

            // GIVEN: a group of ack labels are declared on a remote node
            ackUpdater2.tell(DeclareAcks.of(s2.ref(), "g1", Set.of("a1", "a2")), s2.ref());
            s2.expectMsgClass(AcksDeclared.class);

            // WHEN: ddata is replicated
            waitForHeartBeats(system1, this);

            // THEN: it is an error to declare different ack labels under the same group name.
            underTest.tell(DeclareAcks.of(s1.ref(), "g1", Set.of("a2", "a3")), getRef());
            expectMsgClass(Duration.of(5l, ChronoUnit.SECONDS), AcknowledgementLabelNotUniqueException.class);

            // THEN: it is an error to declare intersecting ack labels without a group.
            underTest.tell(DeclareAcks.of(s1.ref(), null, Set.of("a2", "a3")), getRef());
            expectMsgClass(Duration.of(5l, ChronoUnit.SECONDS), AcknowledgementLabelNotUniqueException.class);

            // THEN: it is an error to declare intersecting ack labels under a different group.
            underTest.tell(DeclareAcks.of(s1.ref(), "g2", Set.of("a2", "a3")), getRef());
            expectMsgClass(Duration.of(5l, ChronoUnit.SECONDS), AcknowledgementLabelNotUniqueException.class);
        }};
    }

    @Test
    public void localDeclarationsWithoutGroupDoNotOverrideEachOther() {
        new TestKit(system1) {{
            final ActorRef underTest = system1.actorOf(AckSupervisor.props(ddata1));
            final TestProbe s1 = TestProbe.apply("s1", system1);
            final TestProbe s2 = TestProbe.apply("s2", system1);
            final TestProbe s3 = TestProbe.apply("s3", system1);

            // GIVEN: ack labels are declared
            underTest.tell(DeclareAcks.of(s1.ref(), null, Set.of("a1")), getRef());
            expectMsgClass(AcksDeclared.class);

            // WHEN: a disjoint
            underTest.tell(DeclareAcks.of(s2.ref(), null, Set.of("a2")), getRef());
            expectMsgClass(AcksDeclared.class);

            // THEN:
            underTest.tell(DeclareAcks.of(s3.ref(), null, Set.of("a1")), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);
            underTest.tell(DeclareAcks.of(s3.ref(), null, Set.of("a2")), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);
        }};
    }

    @Test
    public void declarationsInSeveralSystemsMaintainKnownRemoteDeclaredAcks() {
        new TestKit(system1) {{
            final ActorRef underTest1 = system1.actorOf(AckSupervisor.props(ddata1));
            final ActorRef underTest2 = system2.actorOf(AckSupervisor.props(ddata2));
            final TestProbe s1a = TestProbe.apply("s1-a", system1);
            final TestProbe s1b = TestProbe.apply("s1-b", system1);
            final TestProbe s2a = TestProbe.apply("s2-a", system2);
            final TestProbe s2b = TestProbe.apply("s2-b", system2);

            underTest1.tell(ReceiveRemoteAcks.of(getRef()), getRef());

            final String someGroup = "some-group";
            final String groupedTopic = "grouped-topic";
            final String standaloneTopic1 = "standalone-topic-1";
            final String standaloneTopic2 = "standalone-topic-2";

            // GIVEN: ack labels are declared on cluster node 1
            underTest1.tell(DeclareAcks.of(s1a.ref(), someGroup, Set.of(groupedTopic)), getRef());
            expectMsgClass(AcksDeclared.class);
            assertThat(expectMsgClass(RemoteAcksChanged.class).contains(groupedTopic)).isTrue();

            // GIVEN:
            //  ack labels are declared on cluster node 2
            underTest2.tell(DeclareAcks.of(s2a.ref(), someGroup, Set.of(groupedTopic)), getRef());
            expectMsgClass(AcksDeclared.class);
            assertThat(expectMsgClass(RemoteAcksChanged.class).contains(groupedTopic)).isTrue();
            //  ack labels are declared on cluster node 1 without group
            underTest1.tell(DeclareAcks.of(s1a.ref(), null, Set.of(standaloneTopic1, standaloneTopic2)),
                    getRef());
            expectMsgClass(AcksDeclared.class);
            final RemoteAcksChanged remoteAcksChanged = expectMsgClass(RemoteAcksChanged.class);
            assertThat(remoteAcksChanged.contains(groupedTopic)).isTrue();
            assertThat(remoteAcksChanged.contains(standaloneTopic1)).isTrue();
            assertThat(remoteAcksChanged.contains(standaloneTopic2)).isTrue();

            // WHEN: ddata is replicated
            waitForHeartBeats(system1, this);
            waitForHeartBeats(system2, this);

            // THEN:
            //  without group those ack labels may not be declared again:
            underTest1.tell(DeclareAcks.of(s1b.ref(), null, Set.of(standaloneTopic1)), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);
            underTest2.tell(DeclareAcks.of(s2b.ref(), null, Set.of(standaloneTopic2)), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);
            //  with same group as above an ack labels may be declared again:
            underTest2.tell(DeclareAcks.of(s2b.ref(), someGroup, Set.of(groupedTopic)), getRef());
            expectMsgClass(AcksDeclared.class);
        }};
    }

    @Test
    public void clusterStateOutOfSync() {
        new TestKit(system1) {{
            // GIVEN: distributed data contains entry for nonexistent cluster member and does not contain entry for
            // the current cluster member
            final var config = PubSubConfig.of(system1);
            final var ownAddress = Cluster.get(system1).selfAddress();
            final var nonexistentAddress = Address.apply("akka", "unknown-system");
            final var addressMap = Map.of(nonexistentAddress, "unknown-value");
            final DData<Address, String, LiteralUpdate> ddata = mockDistributedData(addressMap);
            final ActorRef underTest = system1.actorOf(AckUpdater.props(config, ownAddress, ddata));
            underTest.tell(DeclareAcks.of(underTest, "group", Set.of("label")), getRef());
            Mockito.verify(ddata.getWriter(), Mockito.timeout(5000)).put(eq(ownAddress), any(), any());

            // WHEN: AckUpdater is requested to sync against the cluster state
            underTest.tell(ClusterStateSyncBehavior.Control.SYNC_CLUSTER_STATE, ActorRef.noSender());

            // THEN: AckUpdater removes the extraneous entry and inserts its entry
            Mockito.verify(ddata.getReader(), Mockito.timeout(5000))
                    .getAllShards(eq((Replicator.ReadConsistency) Replicator.readLocal()));
            Mockito.verify(ddata.getWriter(), Mockito.timeout(5000))
                    .removeAddress(eq(nonexistentAddress), eq((Replicator.WriteConsistency) Replicator.writeLocal()));
            Mockito.verify(ddata.getWriter(), Mockito.timeout(5000).atLeast(2))
                    .put(eq(ownAddress), any(), any());
        }};
    }

    @Test
    public void clusterStateInSync() throws Exception {
        new TestKit(system1) {{
            // GIVEN: distributed data contains entry for the current cluster member and no extraneous entries
            final var config = PubSubConfig.of(system1);
            final var ownAddress = Cluster.get(system1).selfAddress();
            final var addressMap = Map.of(ownAddress, "unknown-value");
            final DData<Address, String, LiteralUpdate> ddata = mockDistributedData(addressMap);
            final ActorRef underTest = system1.actorOf(AckUpdater.props(config, ownAddress, ddata));

            // WHEN: AckUpdater is requested to sync against the cluster state
            underTest.tell(ClusterStateSyncBehavior.Control.SYNC_CLUSTER_STATE, ActorRef.noSender());

            // THEN: AckUpdater does not modify ddata more than needed
            Mockito.verify(ddata.getReader(), Mockito.timeout(5000))
                    .getAllShards(eq((Replicator.ReadConsistency) Replicator.readLocal()));
            Thread.sleep(3000);
            Mockito.verify(ddata.getWriter(), Mockito.never()).put(eq(ownAddress), any(), any());
            Mockito.verify(ddata.getWriter(), Mockito.never()).removeAddress(any(), any());
        }};
    }

    private Config getTestConf() {
        return ConfigFactory.load("pubsub-factory-test.conf");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <A, B, C extends DDataUpdate<?>> DData<A, B, C> mockDistributedData(final Map<A, B> result) {
        final ORMultiMap map = Mockito.mock(ORMultiMap.class);
        Mockito.when(map.getEntries()).thenReturn(result);
        final var mock = Mockito.mock(DData.class);
        final var reader = Mockito.mock(DDataReader.class);
        final var writer = Mockito.mock(DDataWriter.class);
        Mockito.when(mock.getReader()).thenReturn(reader);
        Mockito.when(mock.getWriter()).thenReturn(writer);
        Mockito.when(reader.get(any(), any())).thenReturn(CompletableFuture.completedStage(Optional.of(map)));
        Mockito.when(reader.getAllShards(any())).thenReturn(CompletableFuture.completedStage(List.of(map)));
        Mockito.when(writer.put(any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
        return mock;
    }

    private static void waitForHeartBeats(final ActorSystem system, final TestKit kit) {
        final int howManyHeartBeats = 5;
        final Duration heartbeatInterval = kit.dilated(PubSubConfig.of(system).getUpdateInterval());
        try {
            TimeUnit.MILLISECONDS.sleep(heartbeatInterval.multipliedBy(howManyHeartBeats).toMillis());
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

}
