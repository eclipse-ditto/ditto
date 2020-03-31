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
package org.eclipse.ditto.services.utils.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.thingsearch.query.StreamThings;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionComplete;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionHasNext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.AskTimeoutException;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link org.eclipse.ditto.services.utils.search.SubscriptionManager}.
 */
public final class SubscriptionManagerTest {

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private TestProbe pubSubMediatorProbe;
    private TestProbe conciergeForwarderProbe;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create();
        materializer = ActorMaterializer.create(actorSystem);
        pubSubMediatorProbe = TestProbe.apply("pubSubMediator", actorSystem);
        conciergeForwarderProbe = TestProbe.apply("conciergeForwarder", actorSystem);
    }

    @After
    public void shutdown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void noSuchSubscription() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = createSubscriptionManager();
            underTest.tell(RequestSubscription.of("nonexistentSid", 1L, DittoHeaders.empty()), getRef());
            expectMsgClass(SubscriptionFailed.class);
        }};
    }

    @Test
    public void illegalPageSize() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = createSubscriptionManager();
            underTest.tell(CreateSubscription.of(null, "size(0)", null, null, DittoHeaders.empty()), getRef());
            underTest.tell(request(expectMsgClass(SubscriptionCreated.class).getSubscriptionId()), getRef());
            expectMsgClass(SubscriptionFailed.class);
        }};
    }

    @Test
    public void illegalDemand() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = createSubscriptionManager();
            underTest.tell(createSubscription(1), getRef());
            final String sid = expectMsgClass(SubscriptionCreated.class).getSubscriptionId();
            underTest.tell(RequestSubscription.of(sid, 0L, DittoHeaders.empty()), getRef());
            expectMsgClass(SubscriptionFailed.class);
        }};
    }

    @Test
    public void parallelSessions() {
        // Suppressing logs due to stacktrace for test4. Comment out to see logs.
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());

        final ActorRef underTest = createSubscriptionManager();

        final TestProbe probe1 = TestProbe.apply("test1", actorSystem);
        final TestProbe probe2 = TestProbe.apply("test2", actorSystem);
        final TestProbe probe3 = TestProbe.apply("test3", actorSystem);
        final TestProbe probe4 = TestProbe.apply("test4", actorSystem);

        // expect success with results
        underTest.tell(createSubscription(1), probe1.ref());
        // expect success with prefix
        final String prefix = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        underTest.tell(createSubscription(2).setPrefix(prefix), probe2.ref());
        // expect cancellation
        underTest.tell(createSubscription(3), probe3.ref());
        // expect upstream failure
        underTest.tell(createSubscription(4), probe4.ref());

        final String sid1 = probe1.expectMsgClass(SubscriptionCreated.class).getSubscriptionId();
        final String sid2 = probe2.expectMsgClass(SubscriptionCreated.class).getSubscriptionId();
        final String sid3 = probe3.expectMsgClass(SubscriptionCreated.class).getSubscriptionId();
        final String sid4 = probe4.expectMsgClass(SubscriptionCreated.class).getSubscriptionId();

        final List<?> sources = List.of(
                Source.single("t:1"),
                Source.single("t:2"),
                Source.from(List.of("t:3", "t:4")),
                new AskTimeoutException("mock error")
        );

        underTest.tell(request(sid1), probe1.ref());
        underTest.tell(request(sid2), probe2.ref());
        underTest.tell(request(sid3), probe3.ref());
        underTest.tell(request(sid4), probe4.ref());

        // there should be no upstream request until downstream requests.
        for (int i = 0; i < 4; ++i) {
            final StreamThings streamThings = conciergeForwarderProbe.expectMsgClass(StreamThings.class);
            final ActorRef sender = conciergeForwarderProbe.sender();
            final Object source = sources.get(getTag(streamThings) - 1);
            if (source instanceof Source) {
                ((Source<?, ?>) source).runWith(StreamRefs.sourceRef(), materializer)
                        .thenAccept(sourceRef -> sender.tell(sourceRef, ActorRef.noSender()));
            } else {
                sender.tell(source, ActorRef.noSender());
            }
        }

        probe1.expectMsg(hasNext(sid1, "t:1"));
        probe1.expectMsg(subscriptionComplete(sid1));

        probe2.expectMsg(hasNext(sid2, "t:2"));
        probe2.expectMsg(subscriptionComplete(sid2));

        probe3.expectMsg(hasNext(sid3, "t:3"));
        underTest.tell(CancelSubscription.of(sid3, DittoHeaders.empty()), probe3.ref());

        assertThat(probe4.expectMsgClass(SubscriptionFailed.class).getError())
                .isInstanceOf(GatewayInternalErrorException.class);

        CompletableFuture.allOf(
                CompletableFuture.runAsync(
                        () -> probe3.expectNoMessage(FiniteDuration.apply(250L, TimeUnit.MILLISECONDS))),
                CompletableFuture.runAsync(
                        () -> probe4.expectNoMessage(FiniteDuration.apply(250L, TimeUnit.MILLISECONDS)))
        ).join();
    }

    private SubscriptionComplete subscriptionComplete(final String subscriptionId) {
        return SubscriptionComplete.of(subscriptionId, DittoHeaders.empty());
    }

    private SubscriptionHasNext hasNext(final String subscriptionId, final String thingId) {
        return SubscriptionHasNext.of(subscriptionId,
                JsonArray.of(JsonObject.newBuilder().set(Thing.JsonFields.ID, thingId).build()),
                DittoHeaders.empty());
    }

    private static RequestSubscription request(final String subscriptionId) {
        return RequestSubscription.of(subscriptionId, 1L, DittoHeaders.empty());
    }

    private int getTag(final StreamThings streamThings) {
        return streamThings.getFilter()
                .map(filter -> {
                    int i = "exists(attributes/tag".length();
                    return filter.substring(i, i + 1);
                })
                .map(Integer::valueOf)
                .orElse(0);
    }

    private static CreateSubscription createSubscription(final int i) {
        return CreateSubscription.of("exists(attributes/tag" + i + ")",
                "size(1)",
                JsonFieldSelector.newInstance(Thing.JsonFields.ID.getPointer()),
                null,
                DittoHeaders.empty()
        );
    }

    /**
     * Create a subscription manager without retries to test upstream failures quickly.
     *
     * @return reference of the subscription manager.
     */
    private ActorRef createSubscriptionManager() {
        return actorSystem.actorOf(Props.create(SubscriptionManager.class, () ->
                new SubscriptionManager(Duration.ofMinutes(5L), pubSubMediatorProbe.ref(),
                        conciergeForwarderProbe.ref(), materializer))
        );
    }
}
