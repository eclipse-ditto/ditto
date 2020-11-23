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
package org.eclipse.ditto.services.utils.pubsub.actors;

import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.services.utils.pubsub.LiteralDDataProvider;
import org.eclipse.ditto.services.utils.pubsub.api.AcksDeclared;
import org.eclipse.ditto.services.utils.pubsub.api.DeclareAcks;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.AbstractConfigAwareDDataProvider;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralDData;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import akka.cluster.ddata.Replicator;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.utils.pubsub.actors.AckUpdater}.
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

            // THEN: it is an error to declare intersecting ack labels under under a different group.
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
            waitForHeartBeats(system1, ddata1);

            // THEN: it is an error to declare different ack labels under the same group name.
            underTest.tell(DeclareAcks.of(s1.ref(), "g1", Set.of("a2", "a3")), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);

            // THEN: it is an error to declare intersecting ack labels without a group.
            underTest.tell(DeclareAcks.of(s1.ref(), null, Set.of("a2", "a3")), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);

            // THEN: it is an error to declare intersecting ack labels under under a different group.
            underTest.tell(DeclareAcks.of(s1.ref(), "g2", Set.of("a2", "a3")), getRef());
            expectMsgClass(AcknowledgementLabelNotUniqueException.class);
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

    private Config getTestConf() {
        return ConfigFactory.load("pubsub-factory-test.conf");
    }

    private static void waitForHeartBeats(final ActorSystem system, final DData<?, ?, ?> ackDData) {
        final int howManyHeartBeats = 5;
        final TestProbe probe = TestProbe.apply(system);
        ackDData.getReader().receiveChanges(probe.ref());
        for (int i = 0; i < howManyHeartBeats; ++i) {
            probe.expectMsgClass(Replicator.Changed.class);
        }
        system.stop(probe.ref());
    }

}
