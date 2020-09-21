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

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.awaitility.Awaitility;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.services.utils.pubsub.actors.AbstractUpdater;
import org.eclipse.ditto.services.utils.pubsub.actors.SubUpdater;
import org.eclipse.ditto.signals.acks.base.AcknowledgementLabelNotUniqueException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Attributes;
import akka.testkit.TestActor;
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
            final AbstractUpdater.SubAck subAck =
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
            final AbstractUpdater.SubAck unsubAck =
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
    public void removeSubscriberOfRemovedClusterMember() {
        disableLogging();
        new TestKit(system1) {{
            final DistributedPub<String> pub = factory1.startDistributedPub();
            final DistributedSub sub = factory2.startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber = TestProbe.apply(system2);
            cluster1.subscribe(getRef(), ClusterEvent.MemberRemoved.class);
            expectMsgClass(ClusterEvent.CurrentClusterState.class);

            // GIVEN: a pub-sub channel is set up
            sub.subscribeWithAck(singleton("hello"), subscriber.ref()).toCompletableFuture().join();
            pub.publish("hello", publisher.ref());
            subscriber.expectMsg("hello");

            // WHEN: remote actor system is removed from cluster
            cluster2.leave(cluster2.selfAddress());
            expectMsgClass(java.time.Duration.ofSeconds(10L), ClusterEvent.MemberRemoved.class);

            // THEN: the subscriber is removed
            Awaitility.await().untilAsserted(() ->
                    assertThat(factory1.getSubscribers("hello").toCompletableFuture().join())
                            .describedAs("subscriber should be removed from ddata")
                            .isEmpty());
        }};
    }

    @Test
    public void startSeveralTimes() {
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
            final AbstractUpdater.SubAck subAck =
                    sub.subscribeWithAck(singleton("hello"), subscriber.ref()).toCompletableFuture().join();
            assertThat(subAck.getRequest()).isInstanceOf(SubUpdater.Subscribe.class);
            assertThat(subAck.getRequest().getTopics()).containsExactlyInAnyOrder("hello");

            pub.publish("hello", publisher.ref());
            subscriber.expectMsg(Duration.create(5, TimeUnit.SECONDS), "hello");
        }};
    }

    @Test
    public void failAckDeclarationDueToLocalConflict() {
        new TestKit(system1) {{
            // GIVEN: 2 subscribers exist in the same actor system
            final DistributedSub sub = factory1.startDistributedSub();
            final TestProbe subscriber1 = TestProbe.apply(system1);
            final TestProbe subscriber2 = TestProbe.apply(system1);

            // WHEN: the first subscriber declares ack labels
            // THEN: the declaration should succeed
            await(sub.declareAcknowledgementLabels(acks("lorem", "ipsum"), subscriber1.ref()));

            // WHEN: the second subscriber declares intersecting labels
            // THEN: the declaration should fail
            final CompletionStage<?> declareAckLabelFuture =
                    sub.declareAcknowledgementLabels(acks("ipsum", "lorem"), subscriber2.ref());
            assertThat(awaitSilently(system1, declareAckLabelFuture))
                    .hasFailedWithThrowableThat()
                    .isInstanceOf(AcknowledgementLabelNotUniqueException.class);
        }};
    }

    @Test
    public void failAckDeclarationDueToRemoteConflict() {
        new TestKit(system1) {{
            // GIVEN: 2 subscribers exist in the same actor system and 1 exist in a remote system
            final DistributedSub sub1 = factory1.startDistributedSub();
            final DistributedSub sub2 = factory2.startDistributedSub();

            final TestProbe subscriber1 = TestProbe.apply("subscriber1", system1);
            final TestProbe subscriber2 = TestProbe.apply("subscriber2", system2);
            final TestProbe subscriber3 = TestProbe.apply("subscriber3", system1);
            final TestProbe subscriber4 = TestProbe.apply("subscriber4", system1);

            // GIVEN: "sit" is declared by a subscriber on system2
            await(sub2.declareAcknowledgementLabels(acks("dolor", "sit"), subscriber2.ref()));

            // GIVEN: a full clock cycle has passed so that system1 receives the most up-to-date labels
            // 2 subscription futures are required because WriteLocal does not impact ReadAll immediately???
            await(sub1.declareAcknowledgementLabels(acks("lorem", "ipsum"), subscriber1.ref()));
            await(sub1.declareAcknowledgementLabels(acks("consectetuer"), subscriber4.ref()));

            // WHEN: another subscriber from system1 declares conflicting labels with the subscriber from system2
            // THEN: the declaration should fail
            final CompletionStage<?> declareAckLabelFuture =
                    sub1.declareAcknowledgementLabels(acks("sit", "amet"), subscriber3.ref());
            assertThat(awaitSilently(system1, declareAckLabelFuture))
                    .hasFailedWithThrowableThat()
                    .isInstanceOf(AcknowledgementLabelNotUniqueException.class);
        }};
    }

    @Test
    public void raceAgainstLocalSubscriber() {
        new TestKit(system1) {{
            // comment-out the next line to get future failure logs
            disableLogging();
            // repeat the test to catch timing issues
            final DistributedSub sub1 = factory1.startDistributedSub();
            for (int i = 0; i < 10; ++i) {
                // GIVEN: 2 subscribers exist in the same actor system
                final TestProbe subscriber1 = TestProbe.apply("subscriber1", system1);
                final TestProbe subscriber2 = TestProbe.apply("subscriber2", system1);

                // WHEN: 2 subscribers declare intersecting labels simultaneously
                final CompletionStage<?> future1 =
                        sub1.declareAcknowledgementLabels(acks("lorem" + i, "ipsum" + i), subscriber1.ref());
                final CompletionStage<?> future2 =
                        sub1.declareAcknowledgementLabels(acks("ipsum" + i, "dolor" + i), subscriber2.ref());

                // THEN: exactly one of them fails
                await(future1.handle((result1, error1) -> await(future2.handle((result2, error2) -> {
                    if (error1 == null) {
                        assertThat(error2).isNotNull();
                    } else {
                        assertThat(error2).isNull();
                    }
                    return null;
                }))));
            }
        }};
    }

    @Test
    public void raceAgainstRemoteSubscriber() {
        new TestKit(system1) {{
            // comment-out the next line to get future failure logs
            disableLogging();
            final DistributedSub sub1 = factory1.startDistributedSub();
            final DistributedSub sub2 = factory2.startDistributedSub();

            // run the test many times to catch timing issues
            for (int i = 0; i < 10; ++i) {
                final TestProbe eitherSubscriberProbe = TestProbe.apply(system1);
                final TestActor.AutoPilot autoPilot = new TestActor.AutoPilot() {

                    @Override
                    public TestActor.AutoPilot run(final ActorRef sender, final Object msg) {
                        eitherSubscriberProbe.ref().tell(msg, sender);
                        return this;
                    }
                };

                // GIVEN: 2 subscribers exist in different actor systems
                final TestProbe subscriber1 = TestProbe.apply("subscriber1", system1);
                final TestProbe subscriber2 = TestProbe.apply("subscriber2", system2);
                subscriber1.setAutoPilot(autoPilot);
                subscriber2.setAutoPilot(autoPilot);

                // WHEN: 2 subscribers declare intersecting labels simultaneously
                final CompletionStage<?> future1 =
                        sub1.declareAcknowledgementLabels(acks("lorem" + i, "ipsum" + i), subscriber1.ref());
                final CompletionStage<?> future2 =
                        sub2.declareAcknowledgementLabels(acks("ipsum" + i, "dolor" + i), subscriber2.ref());

                // THEN: exactly one of them fails, or both succeeds and one subscriber gets an exception later.
                await(future1.handle((result1, error1) -> await(future2.handle((result2, error2) -> {
                    if (error1 == null && error2 == null) {
                        eitherSubscriberProbe.expectMsgClass(AcknowledgementLabelNotUniqueException.class);
                    } else if (error1 != null) {
                        assertThat(error2).isNull();
                    }
                    return null;
                }))));
            }
        }};
    }

    private void disableLogging() {
        system1.eventStream().setLogLevel(Attributes.logLevelOff());
        system2.eventStream().setLogLevel(Attributes.logLevelOff());
    }

    private static ActorContext newContext(final ActorSystem actorSystem) {
        return TestActorRef.create(actorSystem, Props.create(NopActor.class)).underlyingActor().context();
    }

    private static Set<AcknowledgementLabel> acks(final String... labels) {
        return Arrays.stream(labels).map(AcknowledgementLabel::of).collect(Collectors.toSet());
    }

    private static <T> CompletionStage<T> await(final CompletionStage<T> stage) {
        final CompletableFuture<Object> future = stage.toCompletableFuture().thenApply(x -> x);
        future.completeOnTimeout(new TimeoutException(), 30L, TimeUnit.SECONDS);
        future.thenCompose(x -> {
            if (x instanceof Throwable) {
                return CompletableFuture.failedStage((Throwable) x);
            } else {
                return CompletableFuture.completedStage(x);
            }
        }).join();
        return stage;
    }

    private static <T> CompletionStage<T> awaitSilently(final ActorSystem system, final CompletionStage<T> stage) {
        try {
            return await(stage);
        } catch (final Throwable e) {
            system.log().info("Future failed: {}", e);
        }
        return stage;
    }

    private static final class NopActor extends AbstractActor {

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create().build();
        }
    }
}
