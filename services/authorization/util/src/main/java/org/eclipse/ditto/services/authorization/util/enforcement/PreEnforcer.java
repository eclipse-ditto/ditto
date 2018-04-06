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
package org.eclipse.ditto.services.authorization.util.enforcement;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.ControlFlowLogic;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.GraphActor;
import org.eclipse.ditto.services.utils.akka.controlflow.Pipe;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.event.LoggingAdapter;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * Create processing units of Akka stream graph before enforcement from an asynchronous function that may abort
 * enforcement by throwing exceptions.
 */
public final class PreEnforcer {

    private PreEnforcer() {}

    /**
     * Create a processing unit from a function.
     *
     * @param self reference to the actor carrying the pre-enforcement.
     * @param processor function to call.
     */
    public static Graph<FlowShape<WithSender, WithSender>, NotUsed> fromFunction(
            final ActorRef self,
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> processor) {

        return Pipe.joinFilteredSink(
                Pipe.joinFilteredFlow(Filter.of(WithDittoHeaders.class), new TransformStage(self, processor)),
                GraphActor.unhandled());
    }


    private static final class TransformStage extends GraphStage<FlowShape<WithSender<WithDittoHeaders>, WithSender>> {

        private final ActorRef self;
        private final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> processor;

        private TransformStage(final ActorRef self,
                final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> processor) {
            this.self = self;
            this.processor = processor;
        }

        private final FlowShape<WithSender<WithDittoHeaders>, WithSender> shape =
                FlowShape.of(Inlet.create("input"), Outlet.create("output"));

        @Override
        public FlowShape<WithSender<WithDittoHeaders>, WithSender> shape() {
            return shape;
        }

        @Override
        public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
            return new ControlFlowLogic(shape) {
                {
                    initOutlets(shape);
                    when(shape.in(), wrapped -> {

                        final Optional<WithDittoHeaders> result =
                                Optional.ofNullable(processor.apply(wrapped.message())
                                        .exceptionally(t -> handleError(t, log(), wrapped))
                                        .toCompletableFuture()
                                        .get());

                        result.ifPresent(processedMessage ->
                                emit(shape.out(), wrapped.withMessage(processedMessage)));
                    });
                }
            };
        }

        @Nullable
        private WithDittoHeaders handleError(final Throwable error, final LoggingAdapter log,
                final WithSender<WithDittoHeaders> wrapped) {

            final Throwable rootCause = extractRootCause(error);
            final ActorRef sender = wrapped.sender();
            final DittoHeaders dittoHeaders = wrapped.message().getDittoHeaders();

            if (rootCause instanceof DittoRuntimeException) {
                log.debug("Got DittoRuntimeException, sending back to sender: <{}>.",
                        rootCause);
                sender.tell(rootCause, self);
            } else {
                log.error(rootCause, "Got unexpected exception.");
                final GatewayInternalErrorException responseEx =
                        GatewayInternalErrorException.newBuilder()
                                .dittoHeaders(dittoHeaders)
                                .cause(rootCause)
                                .build();
                sender.tell(responseEx, self);
            }

            return null;
        }

        private static Throwable extractRootCause(final Throwable t) {
            if (t instanceof CompletionException) {
                return extractRootCause(t.getCause());
            }
            return t;
        }
    }
}
