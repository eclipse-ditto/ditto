/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import org.awaitility.Awaitility;
import org.eclipse.ditto.services.utils.pubsub.actors.SubUpdater;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Attributes;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.Duration;

/**
 * Tests Ditto pub-sub as a whole.
 */
public final class PubSubFactoryTest {

    private ActorSystem system1;
    private ActorSystem system2;
    private Cluster cluster1;
    private Cluster cluster2;
    private TestPubSubFactory factory1;
    private TestPubSubFactory factory2;

    private Config getTestConf() {
        return ConfigFactory.load("pubsub-factory-test.conf");
    }

    @Before
    public void setUpCluster() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        system1 = ActorSystem.create("actorSystem", getTestConf());
        system2 = ActorSystem.create("actorSystem", getTestConf());
        cluster1 = Cluster.get(system1);
        cluster2 = Cluster.get(system2);
        cluster1.registerOnMemberUp(latch::countDown);
        cluster2.registerOnMemberUp(latch::countDown);
        cluster1.join(cluster1.selfAddress());
        cluster2.join(cluster1.selfAddress());
        factory1 = TestPubSubFactory.of(newContext(system1));
        factory2 = TestPubSubFactory.of(newContext(system2));
        // wait for both members to be UP
        latch.await();
    }

    @After
    public void shutdownCluster() {
        disableLogging();
        TestKit.shutdownActorSystem(system1);
        TestKit.shutdownActorSystem(system2);
    }

    @Test
    public void subscribeAndPublishAndUnsubscribe() {
        new TestKit(system2) {{
            final DistributedPub<String> pub = factory1.startDistributedPub();
            final DistributedSub sub = factory2.startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber = TestProbe.apply(system2);

            // WHEN: actor subscribes to a topic with acknowledgement
            final SubUpdater.Acknowledgement subAck =
                    sub.subscribeWithAck(singleton("hello"), subscriber.ref()).toCompletableFuture().join();

            // THEN: subscription is acknowledged
            assertThat(subAck.getRequest()).isInstanceOf(SubUpdater.Subscribe.class);
            assertThat(subAck.getRequest().getTopics()).containsExactlyInAnyOrder("hello");

            // WHEN: a message is published on the subscribed topic
            pub.publish("hello", publisher.ref());

            // THEN: the subscriber receives it from the original sender's address
            subscriber.expectMsg("hello");
            assertThat(subscriber.sender().path().address()).isEqualTo(cluster1.selfAddress());
            assertThat(subscriber.sender().path().toStringWithoutAddress())
                    .isEqualTo(publisher.ref().path().toStringWithoutAddress());

            // WHEN: subscription is relinquished
            final SubUpdater.Acknowledgement unsubAck =
                    sub.unsubscribeWithAck(asList("hello", "world"), subscriber.ref()).toCompletableFuture().join();
            assertThat(unsubAck.getRequest()).isInstanceOf(SubUpdater.Unsubscribe.class);
            assertThat(unsubAck.getRequest().getTopics()).containsExactlyInAnyOrder("hello", "world");

            // THEN: the subscriber does not receive published messages any more
            pub.publish("hello", publisher.ref());
            pub.publish("hello world", publisher.ref());
            subscriber.expectNoMessage();
        }};
    }

    @Test
    public void broadcastMessageToManySubscribers() {
        new TestKit(system2) {{
            final DistributedPub<String> pub = factory1.startDistributedPub();
            final DistributedSub sub1 = factory1.startDistributedSub();
            final DistributedSub sub2 = factory2.startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber1 = TestProbe.apply(system1);
            final TestProbe subscriber2 = TestProbe.apply(system2);
            final TestProbe subscriber3 = TestProbe.apply(system1);
            final TestProbe subscriber4 = TestProbe.apply(system2);

            // GIVEN: subscribers of different topics exist on both actor systems
            CompletableFuture.allOf(
                    sub1.subscribeWithAck(asList("he", "av'n", "has", "no", "rage", "nor"), subscriber1.ref())
                            .toCompletableFuture(),
                    sub2.subscribeWithAck(asList("hell", "a", "fury"), subscriber2.ref())
                            .toCompletableFuture(),
                    sub1.subscribeWithAck(asList("like", "a", "woman", "scorn'd"), subscriber3.ref())
                            .toCompletableFuture(),
                    sub2.subscribeWithAck(asList("exeunt", "omnes"), subscriber4.ref()).toCompletableFuture()
            ).join();

            // WHEN: many messages are published
            final int messages = 100;
            IntStream.range(0, messages).forEach(i -> pub.publish("hello" + i, publisher.ref()));

            // THEN: subscribers with relevant topics get the messages in the order they were published.
            IntStream.range(0, messages).forEach(i -> {
                subscriber1.expectMsg("hello" + i);
                subscriber2.expectMsg("hello" + i);
            });

            // THEN: subscribers without relevant topics get no message.
            subscriber3.expectNoMsg(Duration.Zero());
            subscriber4.expectNoMsg(Duration.Zero());
        }};
    }

    @Test
    public void watchForLocalActorTermination() {
        new TestKit(system2) {{
            final DistributedPub<String> pub = factory1.startDistributedPub();
            final DistributedSub sub = factory2.startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber = TestProbe.apply(system2);
            watch(subscriber.ref());

            // GIVEN: a pub-sub channel is set up
            sub.subscribeWithAck(singleton("hello"), subscriber.ref()).toCompletableFuture().join();
            pub.publish("hello", publisher.ref());
            subscriber.expectMsg("hello");

            // WHEN: subscriber terminates
            system2.stop(subscriber.ref());
            expectMsgClass(Terminated.class);

            // THEN: the subscriber is removed
            Awaitility.await().untilAsserted(() ->
                    assertThat(factory1.getSubscribers("hello").toCompletableFuture().join())
                            .describedAs("subscriber should be removed from ddata after termination")
                            .isEmpty()
            );
        }};
    }

    // Can't test recovery after disassociation---no actor system can join a cluster twice.
    @Test
    public void removeSubscriberOfRemovedClusterMember() throws Exception {
        new TestKit(system2) {{
            final DistributedPub<String> pub = factory1.startDistributedPub();
            final DistributedSub sub = factory2.startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber = TestProbe.apply(system2);

            // GIVEN: a pub-sub channel is set up
            sub.subscribeWithAck(singleton("hello"), subscriber.ref()).toCompletableFuture().join();
            pub.publish("hello", publisher.ref());
            subscriber.expectMsg("hello");

            // WHEN: remote actor system is removed from cluster
            final CountDownLatch latch = new CountDownLatch(1);
            cluster2.registerOnMemberRemoved(latch::countDown);
            cluster2.leave(cluster2.selfAddress());
            latch.await();

            // THEN: the subscriber is removed
            Awaitility.await().untilAsserted(() -> {
                assertThat(factory1.getSubscribers("hello").toCompletableFuture().join())
                        .describedAs("subscriber should be removed from ddata")
                        .isEmpty();
            });
        }};
    }

    @Test
    public void startSeveralTimes() throws Exception {
        // This test simulates the situation where the root actor of a Ditto service restarts several times.
        new TestKit(system2) {{
            // GIVEN: many pub- and sub-factories start under different actors.
            for (int i = 0; i < 10; ++i) {
                TestPubSubFactory.of(newContext(system1));
                TestPubSubFactory.of(newContext(system2));
            }

            // WHEN: another pair of pub-sub factories were created.
            final DistributedPub<String> pub = TestPubSubFactory.of(newContext(system1)).startDistributedPub();
            final DistributedSub sub = TestPubSubFactory.of(newContext(system2)).startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber = TestProbe.apply(system2);

            // THEN: they fulfill their function.
            final SubUpdater.Acknowledgement subAck =
                    sub.subscribeWithAck(singleton("hello"), subscriber.ref()).toCompletableFuture().join();
            assertThat(subAck.getRequest()).isInstanceOf(SubUpdater.Subscribe.class);
            assertThat(subAck.getRequest().getTopics()).containsExactlyInAnyOrder("hello");

            Thread.sleep(500L); // give local subscriber a chance to receive most updated subscriptions
            pub.publish("hello", publisher.ref());
            subscriber.expectMsg("hello");
        }};
    }

    private void disableLogging() {
        system1.eventStream().setLogLevel(Attributes.logLevelOff());
        system2.eventStream().setLogLevel(Attributes.logLevelOff());
    }

    private static ActorContext newContext(final ActorSystem actorSystem) {
        return TestActorRef.create(actorSystem, Props.create(NopActor.class)).underlyingActor().context();
    }

    private static final class NopActor extends AbstractActor {

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create().build();
        }
    }
}
