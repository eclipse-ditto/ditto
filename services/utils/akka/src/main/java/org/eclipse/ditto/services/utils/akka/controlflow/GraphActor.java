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

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.javadsl.MergeHub;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.stage.GraphStage;

/**
 * Actor whose behavior is defined entirely by an Akka stream graph.
 */
public final class GraphActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorMaterializer materializer;
    private final Sink<WithSender, NotUsed> messageHandler;

    private GraphActor(final Function<ActorContext, Graph<SinkShape<WithSender>, NotUsed>> graphCreator) {
        materializer = ActorMaterializer.create(getContext());
        messageHandler = MergeHub.of(WithSender.class).to(graphCreator.apply(getContext())).run(materializer);
    }

    private GraphActor(final BiFunction<ActorContext,
                    DiagnosticLoggingAdapter, Graph<SinkShape<WithSender>, NotUsed>> graphCreator) {
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
     * Build an actor from an Akka stream graph not handling all messages. Unhandled
     * messages are logged as warnings.
     *
     * @param partialCreator creator of graph from this actor's context.
     * @return Props to create this actor with.
     * @see #partial(BiFunction) for accessing the created instance's logger during graph creation.
     */
    public static Props partial(
            final Function<ActorContext, Graph<FlowShape<WithSender, WithSender>, NotUsed>> partialCreator) {

        final BiFunction<ActorContext, DiagnosticLoggingAdapter, Graph<FlowShape<WithSender, WithSender>, NotUsed>>
                partialCreatorWithLog = (actorContext, unusedLog) -> partialCreator.apply(actorContext);
        return partial(partialCreatorWithLog);
    }

    /**
     * Build an actor from an Akka stream graph not handling all messages. Unhandled
     * messages are logged as warnings.
     *
     * @param partialCreator creator of graph from this actor's context and logger.
     * @return Props to create this actor with.
     * @see #partial(Function) when you don't need the instance's logger during graph creation.
     */
    public static Props partial(
            final BiFunction<ActorContext, DiagnosticLoggingAdapter, Graph<FlowShape<WithSender, WithSender>, NotUsed>>
                    partialCreator) {

        return Props.create(GraphActor.class,
                () -> new GraphActor((actorContext, providedLog) ->
                        Pipe.joinSink(partialCreator.apply(actorContext, providedLog), unhandled(providedLog))
                )
        );
    }

    /**
     * @param log the DiagnosticLoggingAdapter to use to log the warning
     * @return Graph stage to log unhandled messages at level WARNING.
     */
    public static GraphStage<SinkShape<WithSender>> unhandled(final DiagnosticLoggingAdapter log) {
        return Consume.untyped(wrapped ->
                log.warning("Unexpected message <{}> from <{}>", wrapped.getMessage(), wrapped.getSender()));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(message -> {
                    if (message instanceof WithDittoHeaders) {
                        LogUtil.enhanceLogWithCorrelationId(log, (WithDittoHeaders<?>) message);
                    }
                    log.debug("Received message: <{}>.", message);
                    final WithSender wrapped = WithSender.of(message, getSender());
                    Source.single(wrapped).runWith(messageHandler, materializer);
                })
                .build();
    }
}
