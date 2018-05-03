/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.controlflow.components;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.stream.ActorMaterializer;
import akka.stream.FanInShape2;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Test {@code PipeWithIdleRoutine}.
 */
public final class PipeWithIdleRoutineTest {

    private static final Object TICK = new Tick();

    private ActorSystem actorSystem;

    @Before
    public void setUpBase() {
        actorSystem = ActorSystem.create("AkkaTestSystem");
    }

    @After
    public void tearDownBase() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void messagesPassThroughPipe() {
        new TestKit(actorSystem) {{
            final List<Object> input = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            final List<Object> output = test(getRef(), input);
            assertThat(output).isEqualTo(output);
        }};
    }

    @Test
    public void numberOfConsecutiveTicksAreEqualToNumberOfConsumerCalls() {
        final int[] consumerCounter = new int[]{0};
        final BiConsumer<WithSender<Tick>, LoggingAdapter> consumer = (x, y) -> ++consumerCounter[0];
        new TestKit(actorSystem) {{
            final List<Object> input = Arrays.asList(1, 2, TICK, 3, TICK, TICK, 4, 5, 6, TICK, 7, TICK, TICK, 8, 9, 10);
            final List<Object> output = test(getRef(), consumer, input);
            assertThat(output).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
            assertThat(consumerCounter[0]).isEqualTo(2);
        }};
    }

    private List<Object> test(final ActorRef recipient, final Iterable<Object> messages) {
        return test(recipient,
                (withSender, log) -> withSender.getSender().tell(withSender.getMessage(), ActorRef.noSender()),
                messages);
    }

    private List<Object> test(final ActorRef recipient,
            final BiConsumer<WithSender<Tick>, LoggingAdapter> consumer,
            final Iterable<Object> messages) {
        final Source<WithSender, NotUsed> messageSource = Source.from(messages)
                .map(message -> WithSender.of(message, recipient));
        final Graph<FlowShape<WithSender, WithSender>, NotUsed> filterFlow =
                GraphDSL.create(builder -> {
                    final FanOutShape2<WithSender, WithSender<Tick>, WithSender> filter =
                            builder.add(Filter.of(Tick.class));
                    final FanInShape2<WithSender, WithSender<Tick>, WithSender> pipeWithIdleRoutine =
                            builder.add(PipeWithIdleRoutine.of(consumer));
                    builder.from(filter.out1()).toInlet(pipeWithIdleRoutine.in0());
                    builder.from(filter.out0()).toInlet(pipeWithIdleRoutine.in1());
                    return FlowShape.of(filter.in(), pipeWithIdleRoutine.out());
                });
        final Sink<Object, CompletionStage<List<Object>>> sink = Sink.seq();
        final CompletableFuture<List<Object>> result = messageSource.via(filterFlow)
                .map(WithSender::getMessage)
                .toMat(sink, Keep.right())
                .run(ActorMaterializer.create(actorSystem))
                .toCompletableFuture();

        try {
            return result.get();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Tick {}
}
