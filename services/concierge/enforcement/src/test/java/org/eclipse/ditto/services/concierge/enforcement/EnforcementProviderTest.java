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
package org.eclipse.ditto.services.concierge.enforcement;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.concierge.enforcement.EnforcementProvider}.
 */
public final class EnforcementProviderTest {

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private TestPublisher.Probe<Contextual<WithDittoHeaders>> sourceProbe;
    private TestSubscriber.Probe<Contextual<WithDittoHeaders>> sinkProbe;
    private Source<Contextual<WithDittoHeaders>, NotUsed> testSource;
    private Sink<Contextual<WithDittoHeaders>, NotUsed> testSink;

    @Before
    public void init() {
        actorSystem = ActorSystem.create();
        materializer = ActorMaterializer.create(actorSystem);
        final Pair<TestPublisher.Probe<Contextual<WithDittoHeaders>>, Source<Contextual<WithDittoHeaders>, NotUsed>>
                sourcePair = TestSource.<Contextual<WithDittoHeaders>>probe(actorSystem).preMaterialize(materializer);
        final Pair<TestSubscriber.Probe<Contextual<WithDittoHeaders>>, Sink<Contextual<WithDittoHeaders>, NotUsed>>
                sinkPair = TestSink.<Contextual<WithDittoHeaders>>probe(actorSystem).preMaterialize(materializer);
        sourceProbe = sourcePair.first();
        sinkProbe = sinkPair.first();
        testSource = sourcePair.second();
        testSink = sinkPair.second();
    }

    @After
    public void cleanup() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void parallelAsksArePossible() {
        new TestKit(actorSystem) {{
            // GIVEN: an EnforcementProvider that asks the test kit for contextual results
            final DittoDiagnosticLoggingAdapter log = Mockito.mock(DittoDiagnosticLoggingAdapter.class);
            final Cache<String, ResponseReceiver> cache = Mockito.mock(Cache.class);
            final ThingDeleted signal = ThingDeleted.of(ThingId.dummy(), 1L, DittoHeaders.empty());
            final Contextual<WithDittoHeaders> contextual = new Contextual<>(signal, getRef(), getRef(),
                    getRef(), getRef(), Duration.ofMinutes(9L), log, null, null,
                    null, null, cache);
            final EnforcementProvider<ThingDeleted> underTest = new EnforcementProvider<>() {
                @Override
                public Class<ThingDeleted> getCommandClass() {
                    return ThingDeleted.class;
                }

                @Override
                public AbstractEnforcement<ThingDeleted> createEnforcement(final Contextual<ThingDeleted> context) {
                    return new AbstractEnforcement<>(contextual.withMessage(signal)) {
                        @Override
                        public CompletionStage<Contextual<WithDittoHeaders>> enforce() {
                            return Patterns.ask(context.getSender(), context.getMessage(), context.getAskTimeout())
                                    .thenApply(reply -> (Contextual<WithDittoHeaders>) reply);
                        }
                    };
                }
            };

            testSource.via(underTest.toContextualFlow(100)).runWith(testSink, materializer);

            // WHEN: multiple elements are requested downstream
            sinkProbe.request(99L);
            sourceProbe.expectRequest();
            sourceProbe.sendNext(contextual).sendNext(contextual).sendComplete();

            // THEN: the ask-futures run in parallel
            expectMsg(signal);
            final ActorRef sender1 = getLastSender();
            expectMsg(signal);
            final ActorRef sender2 = getLastSender();
            sender1.tell(contextual, getRef());
            sender2.tell(contextual, getRef());
            sinkProbe.expectNext(contextual, contextual).expectComplete();
        }};
    }
}
