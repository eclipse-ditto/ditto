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
package org.eclipse.ditto.internal.utils.pubsub;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorContext;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * End-to-end test of the pre-serialized pub/sub fan-out path (flag ON) across two clustered actor systems.
 * A signal published from {@code system1} must be delivered to a subscriber on {@code system2}, which forces the
 * remote path: {@code Publisher} emits a {@code PreSerializedPublishSignal}, Artery serializes it via the registered
 * serializer, and the receiving side reconstructs a {@code PublishSignal} for the {@code Subscriber}.
 */
public final class PreSerializeFanoutPubSubTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private ActorSystem system1;
    private ActorSystem system2;
    private Cluster cluster1;
    private TestPubSubFactory factory1;
    private TestPubSubFactory factory2;

    @Before
    public void setUpCluster() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final Config testConf = ConfigFactory.load("pubsub-preserialize-factory-test.conf");
        system1 = ActorSystem.create("actorSystem", testConf);
        system2 = ActorSystem.create("actorSystem", testConf);
        cluster1 = Cluster.get(system1);
        final Cluster cluster2 = Cluster.get(system2);
        cluster1.registerOnMemberUp(latch::countDown);
        cluster2.registerOnMemberUp(latch::countDown);
        cluster1.join(cluster1.selfAddress());
        cluster2.join(cluster1.selfAddress());
        final ActorContext context1 = newContext(system1);
        final ActorContext context2 = newContext(system2);
        final AckExtractor<Acknowledgement> ackExtractor = AckExtractor.of(
                s -> EntityId.of(EntityType.of("thing"), "pub.sub.test:thing-id"),
                s -> DittoHeaders.empty());
        factory1 = TestPubSubFactory.of(context1, ackExtractor, TestPubSubFactory.startDistributedAcks(context1));
        factory2 = TestPubSubFactory.of(context2, ackExtractor, TestPubSubFactory.startDistributedAcks(context2));
        latch.await();
    }

    @After
    public void shutdownCluster() {
        if (system1 != null) {
            TestKit.shutdownActorSystem(system1);
        }
        if (system2 != null) {
            TestKit.shutdownActorSystem(system2);
        }
    }

    @Test
    public void publishedSignalReachesLocalAndRemoteSubscribersWithPreSerializedFanout() {
        new TestKit(system2) {{
            // subscribe on BOTH nodes so the publication fans out to 2 destinations — the pre-serialize path is
            // only taken for fan-out > 1. This exercises the remote path (Artery serializes the envelope once and
            // reconstructs a PublishSignal) AND the local path (the same-node Subscriber receives the envelope
            // unserialized and converts it back to a PublishSignal).
            final DistributedPub<Acknowledgement> pub = factory1.startDistributedPub();
            final DistributedSub localSub = factory1.startDistributedSub();   // system1 = publisher's node
            final DistributedSub remoteSub = factory2.startDistributedSub();  // system2 = remote node
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe localSubscriber = TestProbe.apply(system1);
            final TestProbe remoteSubscriber = TestProbe.apply(system2);

            localSub.subscribeWithFilterAndGroup(singleton("hello"), localSubscriber.ref(), null, null, false)
                    .toCompletableFuture()
                    .join();
            remoteSub.subscribeWithFilterAndGroup(singleton("hello"), remoteSubscriber.ref(), null, null, false)
                    .toCompletableFuture()
                    .join();

            pub.publish(signal("hello"), "", publisher.ref());

            remoteSubscriber.expectMsg(signal("hello"));
            assertThat(remoteSubscriber.sender().path().address()).isEqualTo(cluster1.selfAddress());
            localSubscriber.expectMsg(signal("hello"));
        }};
    }

    private static ActorContext newContext(final ActorSystem actorSystem) {
        return TestActorRef.create(actorSystem, Props.create(NopActor.class)).underlyingActor().context();
    }

    private static Acknowledgement signal(final String label) {
        return Acknowledgement.of(AcknowledgementLabel.of(label),
                EntityId.of(EntityType.of("thing"), "pub.sub.test:thing-id"),
                HttpStatus.OK,
                DittoHeaders.empty());
    }

    /** Minimal actor whose only purpose is to provide an {@link ActorContext} for the pub/sub factories. */
    public static final class NopActor extends AbstractActor {

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create().build();
        }
    }
}
