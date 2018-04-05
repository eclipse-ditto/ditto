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
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.Inlet;
import akka.stream.SinkShape;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.MergeHub;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * Actor whose behavior is defined entirely by an Akka stream graph.
 */
public final class GraphActor extends AbstractActor {

    private final ActorMaterializer materializer;
    private final Sink<WithSender, NotUsed> messageHandler;

    private GraphActor(final Function<ActorContext, Graph<SinkShape<WithSender>, NotUsed>> graphCreator) {
        materializer = ActorMaterializer.create(getContext());
        messageHandler = MergeHub.of(WithSender.class).to(graphCreator.apply(getContext())).run(materializer);
    }

    private GraphActor(
            final BiFunction<ActorContext, LoggingAdapter, Graph<SinkShape<WithSender>, NotUsed>> graphCreator) {
        final LoggingAdapter log = LogUtil.obtain(this);
        materializer = ActorMaterializer.create(getContext());
        messageHandler = MergeHub.of(WithSender.class).to(graphCreator.apply(getContext(), log)).run(materializer);
    }

    /**
     * Build an actor from an Akka stream graph capable of handling all messages.
     *
     * @param graphCreator creator of graph from this actor's context.
     * @return Props to create this actor with.
     */
    public static Props total(final Function<ActorContext, Graph<SinkShape<WithSender>, NotUsed>> graphCreator) {
        return Props.create(GraphActor.class, () -> new GraphActor(graphCreator));
    }

    /**
     * Build an actor from an Akka stream graph not handling all messages. Unhandled messages are logged as warnings.
     *
     * @param partialCreator creator of graph that handles some messages.
     * @return Props to create this actor with.
     */
    public static Props partial(
            final Function<ActorContext, Graph<FlowShape<WithSender, WithSender>, NotUsed>> partialCreator) {

        return Props.create(GraphActor.class, () -> new GraphActor(actorContext ->
                GraphDSL.create(builder -> {
                    final FlowShape<WithSender, WithSender> flow = builder.add(partialCreator.apply(actorContext));
                    final SinkShape<WithSender> sink = builder.add(new Unhandled());

                    builder.from(flow.out()).to(sink);

                    return SinkShape.of(flow.in());
                })));
    }

    /**
     * Build an actor from an Akka stream graph not handling all messages while providing a logger. Unhandled
     * messages are logged as warnings.
     *
     * @param partialCreator creator of graph from this actor's context and logger.
     * @return Props to create this actor with.
     */
    public static Props partialWithLog(
            final BiFunction<ActorContext, LoggingAdapter, Graph<FlowShape<WithSender, WithSender>, NotUsed>>
                    partialCreator) {

        return Props.create(GraphActor.class, () -> new GraphActor((actorContext, log) ->
                GraphDSL.create(builder -> {
                    final FlowShape<WithSender, WithSender> flow =
                            builder.add(partialCreator.apply(actorContext, log));
                    final SinkShape<WithSender> sink = builder.add(new Unhandled());

                    builder.from(flow.out()).to(sink);

                    return SinkShape.of(flow.in());
                })));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(message -> {
                    final WithSender wrapped = WithSender.of(message, getSender());
                    Source.single(wrapped).runWith(messageHandler, materializer);
                })
                .build();
    }

    /**
     * Log unhandled messages.
     */
    public static final class Unhandled extends GraphStage<SinkShape<WithSender>> {

        private final SinkShape<WithSender> shape = SinkShape.of(Inlet.create("input"));

        @Override
        public SinkShape<WithSender> shape() {
            return shape;
        }

        @Override
        public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
            return new ControlFlowLogic(shape) {
                {
                    when(shape.in(), wrapped ->
                            log().warning("Unexpected message <{}> from <{}>",
                                    wrapped.message(), wrapped.sender()));
                }
            };
        }
    }
}
