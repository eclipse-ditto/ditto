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
package org.eclipse.ditto.internal.utils.pubsub;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.PubSubTerminatedException;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.internal.utils.pubsub.actors.ActorEvent;
import org.eclipse.ditto.internal.utils.pubsub.api.LocalAcksChanged;
import org.eclipse.ditto.internal.utils.pubsub.api.SubAck;
import org.eclipse.ditto.internal.utils.pubsub.api.Subscribe;
import org.eclipse.ditto.internal.utils.pubsub.api.Unsubscribe;
import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
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

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private ActorSystem system1;
    private ActorSystem system2;
    private ActorSystem system3;
    private Cluster cluster1;
    private Cluster cluster2;
    private Cluster cluster3;
    private TestPubSubFactory factory1;
    private TestPubSubFactory factory2;
    private TestPubSubFactory factory3;
    private DistributedAcks distributedAcks1;
    private DistributedAcks distributedAcks2;
    private DistributedAcks distributedAcks3;
    private AckExtractor<Acknowledgement> ackExtractor;
    private Map<String, EntityId> thingIdMap;
    private Map<String, DittoHeaders> dittoHeadersMap;

//    @BeforeClass
//    public static void beforeClass() {
//        DittoTracing.init(Mockito.mock(TracingConfig.class));
//    }
//
//    @AfterClass
//    public static void afterClass() {
//        DittoTracing.reset();
//    }

    @Before
    public void setUpCluster() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final var testConf = ConfigFactory.load("pubsub-factory-test.conf");
        system1 = ActorSystem.create("actorSystem", testConf);
        system2 = ActorSystem.create("actorSystem", testConf);
        system3 = ActorSystem.create("actorSystem", testConf);
        cluster1 = Cluster.get(system1);
        cluster2 = Cluster.get(system2);
        cluster3 = Cluster.get(system3);
        cluster1.registerOnMemberUp(latch::countDown);
        cluster2.registerOnMemberUp(latch::countDown);
        cluster3.registerOnMemberUp(latch::countDown);
        cluster1.join(cluster1.selfAddress());
        cluster2.join(cluster1.selfAddress());
        cluster3.join(cluster1.selfAddress());
        final ActorContext context1 = newContext(system1);
        final ActorContext context2 = newContext(system2);
        final ActorContext context3 = newContext(system3);
        distributedAcks1 = TestPubSubFactory.startDistributedAcks(context1);
        distributedAcks2 = TestPubSubFactory.startDistributedAcks(context2);
        distributedAcks3 = TestPubSubFactory.startDistributedAcks(context3);
        thingIdMap = new ConcurrentHashMap<>();
        dittoHeadersMap = new ConcurrentHashMap<>();
        ackExtractor = AckExtractor.of(
                s -> thingIdMap.getOrDefault(s.getLabel().toString(), EntityId.of(EntityType.of("thing"), "pub.sub.test:thing-id")),
                s -> dittoHeadersMap.getOrDefault(s.getLabel().toString(), DittoHeaders.empty())
        );
        factory1 = TestPubSubFactory.of(context1, ackExtractor, distributedAcks1);
        factory2 = TestPubSubFactory.of(context2, ackExtractor, distributedAcks2);
        factory3 = TestPubSubFactory.of(context3, ackExtractor, distributedAcks3);
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
            final DistributedPub<Acknowledgement> pub = factory1.startDistributedPub();
            final DistributedSub sub = factory2.startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber = TestProbe.apply(system2);

            // WHEN: actor subscribes to a topic with acknowledgement
            final SubAck subAck =
                    sub.subscribeWithFilterAndGroup(singleton("hello"), subscriber.ref(), null, null, false).toCompletableFuture().join();

            // THEN: subscription is acknowledged
            assertThat(subAck.getRequest()).isInstanceOf(Subscribe.class);
            assertThat(subAck.getRequest().getTopics()).containsExactlyInAnyOrder("hello");

            // WHEN: a message is published on the subscribed topic
            pub.publish(signal("hello"), "", publisher.ref());

            // THEN: the subscriber receives it from the original sender's address
            subscriber.expectMsg(signal("hello"));
            assertThat(subscriber.sender().path().address()).isEqualTo(cluster1.selfAddress());
            assertThat(subscriber.sender().path().toStringWithoutAddress())
                    .isEqualTo(publisher.ref().path().toStringWithoutAddress());

            // WHEN: subscription is relinquished
            final SubAck unsubAck =
                    sub.unsubscribeWithAck(asList("hello", "world"), subscriber.ref()).toCompletableFuture().join();
            assertThat(unsubAck.getRequest()).isInstanceOf(Unsubscribe.class);
            assertThat(unsubAck.getRequest().getTopics()).containsExactlyInAnyOrder("hello", "world");

            // THEN: the subscriber does not receive published messages any more
            pub.publish(signal("hello"), "", publisher.ref());
            pub.publish(signal("hello-world"), "", publisher.ref());
            subscriber.expectNoMessage();

            // WHEN: actor subscribes to the topic again
            sub.subscribeWithFilterAndGroup(singleton("hello"), subscriber.ref(), null, null, false)
                    .toCompletableFuture()
                    .join();
            // THEN: it receives published message again
            pub.publish(signal("hello"), "", publisher.ref());
            subscriber.expectMsg(signal("hello"));
        }};
    }

    @Test
    public void broadcastMessageToManySubscribers() {
        new TestKit(system2) {{
            final DistributedPub<Acknowledgement> pub = factory1.startDistributedPub();
            final DistributedSub sub1 = factory1.startDistributedSub();
            final DistributedSub sub2 = factory2.startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber1 = TestProbe.apply(system1);
            final TestProbe subscriber2 = TestProbe.apply(system2);
            final TestProbe subscriber3 = TestProbe.apply(system1);
            final TestProbe subscriber4 = TestProbe.apply(system2);

            // GIVEN: subscribers of different topics exist on both actor systems
            await(sub1.subscribeWithFilterAndGroup(asList("he", "av'n", "has", "no", "rage", "nor"), subscriber1.ref(), null, null,
                    false));
            await(sub2.subscribeWithFilterAndGroup(asList("hell", "a", "fury"), subscriber2.ref(), null, null, false));
            await(sub1.subscribeWithFilterAndGroup(asList("like", "a", "woman", "scorn'd"), subscriber3.ref(), null, null,
                    false));
            await(sub2.subscribeWithFilterAndGroup(asList("exeunt", "omnes"), subscriber4.ref(), null, null, false).toCompletableFuture());

            // WHEN: many messages are published
            final int messages = 100;
            IntStream.range(0, messages).forEach(i -> pub.publish(signal("hello" + i), "", publisher.ref()));

            // THEN: subscribers with relevant topics get the messages in the order they were published.
            IntStream.range(0, messages).forEach(i -> {
                subscriber1.expectMsg(signal("hello" + i));
                subscriber2.expectMsg(signal("hello" + i));
            });

            // THEN: subscribers without relevant topics get no message.
            subscriber3.expectNoMsg(Duration.Zero());
            subscriber4.expectNoMsg(Duration.Zero());
        }};
    }

    @Test
    public void watchForLocalActorTermination() {
        new TestKit(system2) {{
            final DistributedPub<Acknowledgement> pub = factory1.startDistributedPub();
            final DistributedSub sub = factory2.startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber = TestProbe.apply(system2);
            watch(subscriber.ref());

            // GIVEN: a pub-sub channel is set up
            sub.subscribeWithFilterAndGroup(singleton("hello"), subscriber.ref(), null, null, false).toCompletableFuture().join();
            pub.publish(signal("hello"), "", publisher.ref());
            subscriber.expectMsg(signal("hello"));

            // WHEN: subscriber terminates
            system2.stop(subscriber.ref());
            expectMsgClass(Terminated.class);

            // THEN: the subscriber is removed
            Awaitility.await().untilAsserted(() ->
                    assertThat(factory1.getSubscribers())
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
            final DistributedPub<Acknowledgement> pub = factory1.startDistributedPub();
            final DistributedSub sub = factory2.startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber = TestProbe.apply(system2);
            cluster1.subscribe(getRef(), ClusterEvent.MemberRemoved.class);
            expectMsgClass(ClusterEvent.CurrentClusterState.class);

            // GIVEN: a pub-sub channel is set up
            sub.subscribeWithFilterAndGroup(singleton("hello"), subscriber.ref(), null, null, false).toCompletableFuture().join();
            pub.publish(signal("hello"), "", publisher.ref());
            subscriber.expectMsg(signal("hello"));

            // WHEN: remote actor system is removed from cluster
            cluster2.leave(cluster2.selfAddress());
            expectMsgClass(java.time.Duration.ofSeconds(10L), ClusterEvent.MemberRemoved.class);

            // THEN: the subscriber is removed
            Awaitility.await().untilAsserted(() ->
                    assertThat(factory1.getSubscribers())
                            .describedAs("subscriber should be removed from ddata")
                            .isEmpty()
            );
        }};
    }

    @Test
    public void startSeveralTimes() {
        // This test simulates the situation where the root actor of a Ditto service restarts several times.
        new TestKit(system2) {{
            // GIVEN: many pub- and sub-factories start under different actors.
            for (int i = 0; i < 10; ++i) {
                TestPubSubFactory.of(newContext(system1), ackExtractor, distributedAcks1);
                TestPubSubFactory.of(newContext(system2), ackExtractor, distributedAcks2);
            }

            // WHEN: another pair of pub-sub factories were created.
            final DistributedPub<Acknowledgement> pub =
                    TestPubSubFactory.of(newContext(system1), ackExtractor, distributedAcks1).startDistributedPub();
            final DistributedSub sub =
                    TestPubSubFactory.of(newContext(system2), ackExtractor, distributedAcks2).startDistributedSub();
            final TestProbe publisher = TestProbe.apply(system1);
            final TestProbe subscriber = TestProbe.apply(system2);

            // THEN: they fulfill their function.
            final SubAck subAck =
                    sub.subscribeWithFilterAndGroup(singleton("hello"), subscriber.ref(), null, null, false).toCompletableFuture().join();
            assertThat(subAck.getRequest()).isInstanceOf(Subscribe.class);
            assertThat(subAck.getRequest().getTopics()).containsExactlyInAnyOrder("hello");

            pub.publish(signal("hello"), "", publisher.ref());
            subscriber.expectMsg(Duration.create(5, TimeUnit.SECONDS), signal("hello"));
        }};
    }

    @Test
    public void failAckDeclarationDueToLocalConflict() {
        new TestKit(system1) {{
            // GIVEN: 2 subscribers exist in the same actor system
            final TestProbe subscriber1 = TestProbe.apply(system1);
            final TestProbe subscriber2 = TestProbe.apply(system1);

            // WHEN: the first subscriber declares ack labels
            // THEN: the declaration should succeed
            await(factory1.getDistributedAcks()
                    .declareAcknowledgementLabels(acks("lorem", "ipsum"), subscriber1.ref()));

            // WHEN: the second subscriber declares intersecting labels
            // THEN: the declaration should fail
            final CompletionStage<?> declareAckLabelFuture =
                    factory1.getDistributedAcks()
                            .declareAcknowledgementLabels(acks("ipsum", "lorem"), subscriber2.ref());
            assertThat(awaitSilently(system1, declareAckLabelFuture))
                    .hasFailedWithThrowableThat()
                    .isInstanceOf(AcknowledgementLabelNotUniqueException.class);
        }};
    }

    @Test
    public void removeAcknowledgementLabelDeclaration() {
        new TestKit(system1) {{
            // GIVEN: 2 subscribers exist in the same actor system
            final TestProbe subscriber1 = TestProbe.apply(system1);
            final TestProbe subscriber2 = TestProbe.apply(system1);

            // WHEN: the first subscriber declares ack labels then relinquishes them
            await(factory1.getDistributedAcks()
                    .declareAcknowledgementLabels(acks("lorem", "ipsum"), subscriber1.ref()));
            factory1.getDistributedAcks().removeAcknowledgementLabelDeclaration(subscriber1.ref());

            // THEN: another subscriber should be able to claim the ack labels right away
            await(factory1.getDistributedAcks()
                    .declareAcknowledgementLabels(acks("ipsum", "lorem"), subscriber2.ref()));
        }};
    }

    @Test
    public void receiveLocalDeclaredAcks() {
        new TestKit(system1) {{
            final TestProbe subscriber1 = TestProbe.apply("subscriber1", system1);
            await(factory1.getDistributedAcks()
                    .declareAcknowledgementLabels(acks("lorem"), subscriber1.ref()));
            factory1.getDistributedAcks().receiveLocalDeclaredAcks(getRef());
            final LocalAcksChanged event =
                    expectMsgClass(java.time.Duration.ofSeconds(10L), LocalAcksChanged.class);
            assertThat(event.getSnapshot().getKeys("lorem")).contains(subscriber1.ref());
        }};
    }

    @Test
    public void publisherSendsWeakAckForDeclaredAndUnauthorizedLabels() {
        new TestKit(system1) {{
            final TestProbe publisher = TestProbe.apply("publisher", system1);
            final TestProbe subscriber = TestProbe.apply("subscriber", system2);

            final DistributedPub<Acknowledgement> pub = factory1.startDistributedPub();
            final DistributedSub sub = factory2.startDistributedSub();

            // GIVEN: subscriber declares the requested acknowledgement
            await(factory2.getDistributedAcks().declareAcknowledgementLabels(acks("ack"), subscriber.ref()));
            await(sub.subscribeWithFilterAndGroup(List.of("subscriber-topic"), subscriber.ref(), null, null, false));

            // ensure ddata is replicated to publisher
            waitForHeartBeats(system2, factory2);

            // WHEN: message with the subscriber's declared ack and a different topic is published
            final String publisherTopic = "publisher-topic";
            thingIdMap.put(publisherTopic, EntityId.of(EntityType.of("thing"), "thing:id"));
            dittoHeadersMap.put(publisherTopic,
                    DittoHeaders.newBuilder().acknowledgementRequest(
                            AcknowledgementRequest.parseAcknowledgementRequest("ack"),
                            AcknowledgementRequest.parseAcknowledgementRequest("no-declaration")
                    )
                            .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                    publisher.ref().path().toSerializationFormatWithAddress(
                                            Cluster.get(system1).selfUniqueAddress().address()
                                    ))
                            .build()
            );
            pub.publishWithAcks(signal(publisherTopic), "", ackExtractor, ActorRef.noSender());

            // THEN: the publisher receives a weak acknowledgement for the ack request with a declared label
            final Acknowledgements weakAcks = publisher.expectMsgClass(Acknowledgements.class);
            assertThat(weakAcks.getAcknowledgement(AcknowledgementLabel.of("ack")))
                    .isNotEmpty()
                    .satisfies(optional -> assertThat(optional.orElseThrow().isWeak())
                            .describedAs("Should be weak ack: " + optional.orElseThrow())
                            .isTrue());

            // THEN: the publisher does not receive a weak acknowledgement for the ack request without a declared label
            assertThat(weakAcks.getAcknowledgement(AcknowledgementLabel.of("no-declaration"))).isEmpty();
        }};
    }

    @Test
    public void subscriberSendsWeakAckToDeclaredAndUnauthorizedLabels() {
        new TestKit(system1) {{
            final TestProbe publisher = TestProbe.apply("publisher", system1);
            final TestProbe subscriber1 = TestProbe.apply("subscriber1", system2);
            final TestProbe subscriber2 = TestProbe.apply("subscriber2", system2);

            final DistributedPub<Acknowledgement> pub = factory1.startDistributedPub();
            final DistributedSub sub = factory2.startDistributedSub();

            // GIVEN: different subscribers declare the requested acknowledgement and subscribe for the publisher topic
            final String publisherTopic = "publisher-topic";
            await(factory2.getDistributedAcks().declareAcknowledgementLabels(acks("ack"), subscriber1.ref()));
            await(sub.subscribeWithFilterAndGroup(List.of(publisherTopic), subscriber2.ref(), null, null, false));

            // ensure ddata is replicated to publisher
            waitForHeartBeats(system2, factory2);

            // WHEN: message with the subscriber's declared ack and a different topic is published
            final EntityId thingId = EntityId.of(EntityType.of("thing"), "thing:id");
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().acknowledgementRequest(
                    AcknowledgementRequest.parseAcknowledgementRequest("ack"),
                    AcknowledgementRequest.parseAcknowledgementRequest("no-declaration")
            )
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                            publisher.ref().path().toSerializationFormatWithAddress(
                                    Cluster.get(system1).selfUniqueAddress().address()
                            ))
                    .build();
            thingIdMap.put(publisherTopic, thingId);
            dittoHeadersMap.put(publisherTopic, dittoHeaders);
            pub.publishWithAcks(signal(publisherTopic), "", ackExtractor, ActorRef.noSender());

            // THEN: the publisher receives a weak acknowledgement for the ack request with a declared label
            final Acknowledgements weakAcks = publisher.expectMsgClass(Acknowledgements.class);
            assertThat(weakAcks.getAcknowledgement(AcknowledgementLabel.of("ack")))
                    .isNotEmpty()
                    .satisfies(optional -> assertThat(optional.orElseThrow().isWeak())
                            .describedAs("Should be weak ack: " + optional.orElseThrow())
                            .isTrue());

            // THEN: the publisher does not receive a weak acknowledgement for the ack request without a declared label
            assertThat(weakAcks.getAcknowledgement(AcknowledgementLabel.of("no-declaration"))).isEmpty();
        }};
    }

    @Test
    public void failAckDeclarationDueToRemoteConflict() {
        new TestKit(system1) {{
            // GIVEN: 2 subscribers exist in different actor systems
            final TestProbe subscriber1 = TestProbe.apply("subscriber1", system1);
            final TestProbe subscriber2 = TestProbe.apply("subscriber2", system2);

            // GIVEN: "sit" is declared by a subscriber on system2
            await(factory2.getDistributedAcks().declareAcknowledgementLabels(acks("dolor", "sit"),
                    subscriber2.ref()));

            // GIVEN: the update is replicated to system1
            waitForHeartBeats(system2, factory2);

            // WHEN: another subscriber from system1 declares conflicting labels with the subscriber from system2
            // THEN: the declaration should fail
            final CompletionStage<?> declareAckLabelFuture =
                    factory1.getDistributedAcks()
                            .declareAcknowledgementLabels(acks("sit", "amet"), subscriber1.ref());
            assertThat(awaitSilently(system1, declareAckLabelFuture))
                    .hasFailedWithThrowableThat()
                    .isInstanceOf(AcknowledgementLabelNotUniqueException.class);
        }};
    }

    @Test
    public void raceAgainstLocalAckLabelDeclaration() {
        new TestKit(system1) {{
            // comment-out the next line to get future failure logs
            disableLogging();
            // repeat the test to catch timing issues
            for (int i = 0; i < 10; ++i) {
                // GIVEN: 2 subscribers exist in the same actor system
                final TestProbe subscriber1 = TestProbe.apply("subscriber1", system1);
                final TestProbe subscriber2 = TestProbe.apply("subscriber2", system1);

                // WHEN: 2 subscribers declare intersecting labels simultaneously
                final CompletionStage<?> future1 =
                        factory1.getDistributedAcks()
                                .declareAcknowledgementLabels(acks("lorem" + i, "ipsum" + i), subscriber1.ref());
                final CompletionStage<?> future2 =
                        factory1.getDistributedAcks()
                                .declareAcknowledgementLabels(acks("ipsum" + i, "dolor" + i), subscriber2.ref());

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
    public void raceAgainstRemoteAckLabelDeclaration() {
        new TestKit(system1) {{
            // comment-out the next line to get future failure logs
            disableLogging();

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
                        factory1.getDistributedAcks()
                                .declareAcknowledgementLabels(acks("lorem" + i, "ipsum" + i), subscriber1.ref());
                final CompletionStage<?> future2 =
                        factory2.getDistributedAcks()
                                .declareAcknowledgementLabels(acks("ipsum" + i, "dolor" + i), subscriber2.ref());

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

    @Test
    public void publishToEachMemberOfAGroup() {
        new TestKit(system1) {{
            final TestProbe publisher = TestProbe.apply("publisher", system1);
            final TestProbe subscriber1 = TestProbe.apply("subscriber1", system1);
            final TestProbe subscriber2 = TestProbe.apply("subscriber2", system2);
            final TestProbe subscriber3 = TestProbe.apply("subscriber3", system1);
            final TestProbe subscriber4 = TestProbe.apply("subscriber4", system2);
            final TestProbe subscriber5 = TestProbe.apply("subscriber5", system3);
            final TestProbe subscriber6 = TestProbe.apply("subscriber6", system3);

            final DistributedPub<Acknowledgement> pub1 = factory1.startDistributedPub();
            final DistributedPub<Acknowledgement> pub2 = factory2.startDistributedPub();
            final DistributedSub sub1 = factory1.startDistributedSub();
            final DistributedSub sub2 = factory2.startDistributedSub();
            final DistributedSub sub3 = factory3.startDistributedSub();

            // GIVEN: subscribers subscribe to the same topic as a group
            final String topic = "topic";
            await(sub1.subscribeWithFilterAndGroup(List.of(topic), subscriber1.ref(), null, "group", false));
            await(sub2.subscribeWithFilterAndGroup(List.of(topic), subscriber2.ref(), null, "group", false));
            await(sub1.subscribeWithFilterAndGroup(List.of(topic), subscriber3.ref(), null, "group", false));
            await(sub2.subscribeWithFilterAndGroup(List.of(topic), subscriber4.ref(), null, "group", false));
            await(sub3.subscribeWithFilterAndGroup(List.of(topic), subscriber5.ref(), null, "group", false));
            await(sub3.subscribeWithFilterAndGroup(List.of(topic), subscriber6.ref(), null, "group", false));

            // WHEN: signals are published with different entity IDs differing by 1 in the last byte
            pub1.publish(signal(topic, 0), "0", publisher.ref());
            pub1.publish(signal(topic, 1), "1", publisher.ref());
            pub2.publish(signal(topic, 2), "2", publisher.ref());
            pub2.publish(signal(topic, 3), "3", publisher.ref());
            pub2.publish(signal(topic, 4), "4", publisher.ref());
            pub1.publish(signal(topic, 5), "5", publisher.ref());

            // THEN: exactly 1 subscriber gets each message.
            final Acknowledgement received1 = subscriber1.expectMsgClass(Acknowledgement.class);
            final Acknowledgement received2 = subscriber2.expectMsgClass(Acknowledgement.class);
            final Acknowledgement received3 = subscriber3.expectMsgClass(Acknowledgement.class);
            final Acknowledgement received4 = subscriber4.expectMsgClass(Acknowledgement.class);
            final Acknowledgement received5 = subscriber5.expectMsgClass(Acknowledgement.class);
            final Acknowledgement received6 = subscriber6.expectMsgClass(Acknowledgement.class);
            final Set<EntityId> thingIdSet =
                    Set.of(received1.getEntityId(), received2.getEntityId(), received3.getEntityId(),
                            received4.getEntityId(), received5.getEntityId(), received6.getEntityId());
            assertThat(thingIdSet)
                    .describedAs("Signals received by subscribers should have distinct entity IDs")
                    .hasSize(6);

            // THEN: any subscriber receives no further messages.
            subscriber1.expectNoMessage();
        }};
    }

    @Test
    public void publishToSubscribersWithAndWithoutGroup() {
        new TestKit(system1) {{
            final TestProbe publisher = TestProbe.apply("publisher", system1);
            final TestProbe subscriber1 = TestProbe.apply("subscriber1", system1);
            final TestProbe subscriber2 = TestProbe.apply("subscriber2", system2);
            final TestProbe subscriber3 = TestProbe.apply("subscriber3", system1);
            final TestProbe subscriber4 = TestProbe.apply("subscriber4", system2);

            final DistributedPub<Acknowledgement> pub = factory1.startDistributedPub();
            final DistributedSub sub1 = factory1.startDistributedSub();
            final DistributedSub sub2 = factory2.startDistributedSub();

            // GIVEN: some subscribers subscribe to the topic as a group, others without a group
            final String topic = "topic";
            await(distributedAcks1.declareAcknowledgementLabels(acks("ack"), subscriber1.ref(), "group"));
            await(distributedAcks2.declareAcknowledgementLabels(acks("ack"), subscriber2.ref(), "group"));
            await(sub1.subscribeWithFilterAndGroup(List.of(topic), subscriber1.ref(), null, "group", false));
            await(sub2.subscribeWithFilterAndGroup(List.of(topic), subscriber2.ref(), null, "group", false));
            await(sub1.subscribeWithFilterAndGroup(List.of(topic), subscriber3.ref(), null, null, false));
            await(sub2.subscribeWithFilterAndGroup(List.of(topic), subscriber4.ref(), null, null, false));

            // WHEN: signals are published with different entity IDs differing by 1 in the last byte
            final EntityId thingId = EntityId.of(EntityType.of("thing"), "thing:id");
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().acknowledgementRequest(
                    AcknowledgementRequest.parseAcknowledgementRequest("ack")
            ).build();
            thingIdMap.put(topic, thingId);
            dittoHeadersMap.put(topic, dittoHeaders);
            pub.publish(signal(topic, 0), "0", publisher.ref());
            pub.publish(signal(topic, 1), "1", publisher.ref());

            // THEN: exactly 1 subscriber in the group gets each message.
            final Acknowledgement received1 = subscriber1.expectMsgClass(Acknowledgement.class);
            final Acknowledgement received2 = subscriber2.expectMsgClass(Acknowledgement.class);
            assertThat((CharSequence) received1.getEntityId()).isNotEqualTo(received2.getEntityId());

            // THEN: subscribers without groups receive both messages
            for (int i = 0; i < 2; ++i) {
                subscriber3.expectMsgClass(Acknowledgement.class);
                subscriber4.expectMsgClass(Acknowledgement.class);
            }

            // THEN: publisher receives no weak acknowledgement
            publisher.expectNoMessage();
        }};
    }

    @Test
    public void ackUpdaterInformOfSubUpdaterTermination() throws Exception {
        new TestKit(system1) {{
            final TestProbe subscriber = TestProbe.apply("subscriber", system1);
            final DistributedSubImpl sub = (DistributedSubImpl) factory1.startDistributedSub();

            // GIVEN: subscriber declares ack labels and subscribe for a topic
            await(distributedAcks1.declareAcknowledgementLabels(acks("ack"), subscriber.ref(), "group"));
            await(sub.subscribeWithFilterAndGroup(List.of("topic"), subscriber.ref(), null, "group", false));

            // WHEN: children of SubSupervisor terminate
            sub.subSupervisor.tell(ActorEvent.DEBUG_KILL_CHILDREN, getRef());

            // THEN: subscriber is informed of the error
            subscriber.expectMsg(PubSubTerminatedException.getInstance());

            // THEN: distributed pubsub recovers after restart
            TimeUnit.MILLISECONDS.sleep(PubSubConfig.of(system1).getRestartDelay().multipliedBy(3L).toMillis());
            await(distributedAcks1.declareAcknowledgementLabels(acks("ack"), subscriber.ref(), "group"));
            await(sub.subscribeWithFilterAndGroup(List.of("topic"), subscriber.ref(), null, "group", false));
        }};
    }

    @Test
    public void subUpdaterInformOfAckUpdaterTermination() throws Exception {
        new TestKit(system1) {{
            final TestProbe subscriber = TestProbe.apply("subscriber", system1);
            final DistributedSub sub = factory1.startDistributedSub();

            // GIVEN: subscriber declares ack labels and subscribe for a topic
            await(distributedAcks1.declareAcknowledgementLabels(acks("ack"), subscriber.ref(), "group"));
            await(sub.subscribeWithFilterAndGroup(List.of("topic"), subscriber.ref(), null, "group", false));

            // WHEN: ackUpdater terminates
            final TestProbe localUpdateProbe = TestProbe.apply("localUpdate", system1);
            distributedAcks1.receiveLocalDeclaredAcks(localUpdateProbe.ref());
            localUpdateProbe.expectMsgClass(LocalAcksChanged.class);
            localUpdateProbe.lastSender().tell(PoisonPill.getInstance(), getRef());

            // THEN: subscriber is informed of the error
            subscriber.expectMsg(PubSubTerminatedException.getInstance());

            // THEN: distributed pubsub recovers after restart
            TimeUnit.MILLISECONDS.sleep(PubSubConfig.of(system1).getRestartDelay().multipliedBy(3L).toMillis());
            await(distributedAcks1.declareAcknowledgementLabels(acks("ack"), subscriber.ref(), "group"));
            await(sub.subscribeWithFilterAndGroup(List.of("topic"), subscriber.ref(), null, "group", false));
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

    private static void waitForHeartBeats(final ActorSystem system, final TestPubSubFactory factory) {
        final int howManyHeartBeats = 5;
        final TestProbe probe = TestProbe.apply(system);
        factory.getDistributedAcks().receiveLocalDeclaredAcks(probe.ref());
        for (int i = 0; i < howManyHeartBeats; ++i) {
            probe.expectMsgClass(LocalAcksChanged.class);
        }
        system.stop(probe.ref());
    }

    private static Acknowledgement signal(final String string) {
        return Acknowledgement.of(AcknowledgementLabel.of(string), EntityId.of(EntityType.of("thing"), "pub.sub.ack.test:thing-id"),
                HttpStatus.OK,
                DittoHeaders.empty());
    }

    private static Acknowledgement signal(final String string, final int seq) {
        return Acknowledgement.of(AcknowledgementLabel.of(string), EntityId.of(EntityType.of("thing"), "ns:" + seq), HttpStatus.OK,
                DittoHeaders.empty());
    }

}
