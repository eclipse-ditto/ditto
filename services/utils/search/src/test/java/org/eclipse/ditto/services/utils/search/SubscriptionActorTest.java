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

import static org.assertj.core.api.Java6Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.SubscriptionTimeoutException;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionComplete;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionHasNext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Subscriber;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.utils.search.SubscriptionActor}.
 */
public final class SubscriptionActorTest {

    private int i = 0;
    private ActorSystem actorSystem;
    private ActorMaterializer materializer;

    @Before
    public void init() {
        final Config config = ConfigFactory.parseString("akka.log-dead-letters=0");
        actorSystem = ActorSystem.create(getClass().getSimpleName() + i++, config);
        materializer = ActorMaterializer.create(actorSystem);
    }

    @After
    public void cleanup() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void emptyResults() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(newSubscriptionActor(Duration.ofMinutes(1L), this));
            connect(underTest, Source.empty(), this);
            expectMsg(SubscriptionComplete.of(underTest.path().name(), DittoHeaders.empty()));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void twoPages() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(newSubscriptionActor(Duration.ofMinutes(1L), this));
            final String subscriptionId = underTest.path().name();
            connect(underTest, Source.from(List.of(JsonArray.of(1), JsonArray.of(2))), this);
            underTest.tell(RequestSubscription.of(subscriptionId, 2L, DittoHeaders.empty()), getRef());
            expectMsg(SubscriptionHasNext.of(subscriptionId, JsonArray.of(1), DittoHeaders.empty()));
            expectMsg(SubscriptionHasNext.of(subscriptionId, JsonArray.of(2), DittoHeaders.empty()));
            expectMsg(SubscriptionComplete.of(underTest.path().name(), DittoHeaders.empty()));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void cancellation() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(newSubscriptionActor(Duration.ofMinutes(1L), this));
            final String subscriptionId = underTest.path().name();
            connect(underTest, Source.from(List.of(JsonArray.of(1), JsonArray.of(2))), this);
            underTest.tell(RequestSubscription.of(subscriptionId, 1L, DittoHeaders.empty()), getRef());
            expectMsg(SubscriptionHasNext.of(subscriptionId, JsonArray.of(1), DittoHeaders.empty()));
            underTest.tell(CancelSubscription.of(subscriptionId, DittoHeaders.empty()), getRef());
            expectTerminated(underTest);
        }};
    }

    @Test
    public void failure() {
        // comment the next line to get logs for debugging
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(newSubscriptionActor(Duration.ofMinutes(1L), this));
            final DittoRuntimeException error =
                    InvalidRqlExpressionException.fromMessage("mock error", DittoHeaders.empty());
            connect(underTest, Source.failed(error), this);
            expectMsg(SubscriptionFailed.of(underTest.path().name(), error, DittoHeaders.empty()));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void partialFailure() {
        // comment the next line to get logs for debugging
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(newSubscriptionActor(Duration.ofMinutes(1L), this));
            final String subscriptionId = underTest.path().name();
            final DittoRuntimeException error =
                    InvalidRqlExpressionException.fromMessage("mock error", DittoHeaders.empty());
            // not possible to use Source.concat -- it forces the second source immediately.
            final Source<JsonArray, NotUsed> lazilyFailingSource =
                    Source.from(List.of(Source.single(JsonArray.of(1)),
                            Source.lazily(() -> Source.<JsonArray>failed(error))))
                            .flatMapConcat(x -> x);
            connect(underTest, lazilyFailingSource, this);
            underTest.tell(RequestSubscription.of(subscriptionId, 1L, DittoHeaders.empty()), getRef());
            expectMsg(SubscriptionHasNext.of(subscriptionId, JsonArray.of(1), DittoHeaders.empty()));
            expectMsg(SubscriptionFailed.of(subscriptionId, error, DittoHeaders.empty()));
        }};
    }

    @Test
    public void timeout() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(newSubscriptionActor(Duration.ZERO, this));
            connect(underTest, Source.single(JsonArray.of(1)), this);
            final SubscriptionFailed subscriptionFailed = expectMsgClass(SubscriptionFailed.class);
            assertThat(subscriptionFailed.getError().getErrorCode()).isEqualTo(SubscriptionTimeoutException.ERROR_CODE);
            expectTerminated(underTest);
        }};
    }

    private ActorRef newSubscriptionActor(final Duration timeout, final TestKit testKit) {
        final Props propsForTest = SubscriptionActor.props(timeout, testKit.getRef(), DittoHeaders.empty());
        return actorSystem.actorOf(propsForTest, String.valueOf(Integer.MIN_VALUE));
    }

    private void connect(final ActorRef subscriptionActor, final Source<JsonArray, ?> pageSource,
            final TestKit testKit) {
        final Subscriber<JsonArray> subscriber = SubscriptionActor.asSubscriber(subscriptionActor);
        pageSource.runWith(Sink.fromSubscriber(subscriber), materializer);
        testKit.expectMsgClass(SubscriptionCreated.class);
    }

}
