/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.Consume;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.Pipe;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.stream.Attributes;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SinkShape;
import akka.stream.SourceShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Source;

/**
 * Create processing units of Akka stream graph before enforcement from an asynchronous function that may abort
 * enforcement by throwing exceptions.
 */
public final class PreEnforcer {

    private static final Attributes INFO_LEVEL =
            Attributes.createLogLevels(Logging.InfoLevel(), Logging.DebugLevel(), Logging.ErrorLevel());

    private static final Attributes ERROR_LEVEL =
            Attributes.createLogLevels(Logging.ErrorLevel(), Logging.DebugLevel(), Logging.ErrorLevel());

    private static final Logger FALLBACK_LOGGER = LoggerFactory.getLogger(PreEnforcer.class);

    private PreEnforcer() {}

    /**
     * Create a processing unit from a function without reply address for errors.
     *
     * @param processor function to call.
     * @return Akka stream graph.
     */
    public static Graph<FlowShape<WithSender, WithSender>, NotUsed> fromFunction(
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> processor) {

        return PreEnforcer.fromFunction(ActorRef.noSender(), processor);
    }

    static Graph<FlowShape<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>>, NotUsed> fromFunctionWithContext(
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> processor) {

        final Graph<FanOutShape2<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>>, NotUsed>
                multiplexer =
                Filter.multiplexBy(Optional::of);

        final Flow<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, NotUsed> preEnforcementFlow =
                Flow.<Contextual<WithDittoHeaders>>create()
                        .flatMapConcat(contextual -> {
                            final Supplier<CompletionStage<WithSender>> futureSupplier = () ->
                                    processor.apply(contextual.getMessage())
                                            .thenApply(result -> WithSender.of(result, contextual.getSender()));

                            final CompletionStage<WithSender> futureResult =
                                    handleErrorNowOrLater(futureSupplier, contextual, contextual.getSender(), contextual.getSelf());

                            return Source.fromCompletionStage(futureResult)
                                    .log("PreEnforcer")
                                    .flatMapConcat(PreEnforcer::keepResultAndLogErrors)
                                    .map(WithSender::getMessage)
                                    .map(msg -> contextual.withMessage((WithDittoHeaders) msg));
                        });

        return GraphDSL.create(builder -> {

            final FanOutShape2<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>> fanout =
                    builder.add(multiplexer);

            final FlowShape<Contextual<WithDittoHeaders>, Contextual<WithDittoHeaders>> enforce = builder.add(preEnforcementFlow);

            final SinkShape<Contextual<WithDittoHeaders>> logUnhandled =
                    builder.add(Flow.<Contextual<WithDittoHeaders>, WithSender>fromFunction(x -> x).to(unhandled()));

            builder.from(fanout.out0()).toInlet(enforce.in());
            builder.from(fanout.out1()).to(logUnhandled);

            return FlowShape.of(fanout.in(), enforce.out());
        });

    }

    /**
     * Create a processing unit from a function.
     *
     * @param self reference to the actor carrying the pre-enforcement.
     * @param processor function to call.
     * @return Akka stream graph.
     */
    private static Graph<FlowShape<WithSender, WithSender>, NotUsed> fromFunction(
            @Nullable final ActorRef self,
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> processor) {

        final Attributes logLevels =
                Attributes.createLogLevels(Logging.DebugLevel(), Logging.DebugLevel(), Logging.ErrorLevel());

        final Flow<WithSender<WithDittoHeaders>, WithSender, NotUsed> flow =
                Flow.<WithSender<WithDittoHeaders>>create()
                        .mapAsync(1, wrapped -> {
                            final Supplier<CompletionStage<WithSender>> futureSupplier = () ->
                                    processor.apply(wrapped.getMessage())
                                            .thenApply(result -> WithSender.of(result, wrapped.getSender()));

                            return handleErrorNowOrLater(futureSupplier, wrapped, wrapped.getSender(), self);
                        })
                        .log("PreEnforcer")
                        .withAttributes(logLevels)
                        .flatMapConcat(PreEnforcer::keepResultAndLogErrors);

        return Pipe.joinUnhandledSink(
                Pipe.joinFilteredFlow(Filter.of(WithDittoHeaders.class), flow), unhandled());
    }

    private static CompletionStage<WithSender> handleErrorNowOrLater(
            final Supplier<CompletionStage<WithSender>> futureSupplier,
            final WithSender wrapped,
            final ActorRef sender,
            @Nullable final ActorRef self) {

        try {
            return futureSupplier.get()
                    .exceptionally(error -> WithSender.of(handleError(error, wrapped, self), sender));
        } catch (final RuntimeException ex) {
            return CompletableFuture.completedFuture(WithSender.of(handleError(ex, wrapped, self), sender));
        }
    }

    private static Graph<SourceShape<WithSender>, NotUsed> keepResultAndLogErrors(final Object result) {
        if (result instanceof WithSender) {
            return Source.single((WithSender) result);
        } else if (result instanceof DittoRuntimeException) {
            return Source.single(result)
                    .log("PreEnforcer replied DittoRuntimeException")
                    .withAttributes(INFO_LEVEL)
                    .flatMapConcat(x -> Source.empty());
        } else {
            return Source.single(result)
                    .log("PreEnforcer encountered unexpected exception")
                    .withAttributes(ERROR_LEVEL)
                    .flatMapConcat(x -> Source.empty());
        }
    }

    private static WithDittoHeaders handleError(final Throwable error,
            final WithSender wrapped,
            @Nullable final ActorRef self) {

        final Throwable rootCause = extractRootCause(error);
        final ActorRef sender = wrapped.getSender();
        final DittoHeaders dittoHeaders = wrapped.getMessage().getDittoHeaders();

        if (rootCause instanceof DittoRuntimeException) {
            sender.tell(rootCause, self);
            return (WithDittoHeaders) rootCause;
        } else {
            FALLBACK_LOGGER.error("Unexpected non-DittoRuntimeException error - responding with " +
                            "GatewayInternalErrorException: {} - {} - {}", error.getClass().getSimpleName(), error.getMessage(),
                    error);
            final GatewayInternalErrorException responseEx =
                    GatewayInternalErrorException.newBuilder()
                            .dittoHeaders(dittoHeaders)
                            .cause(rootCause)
                            .build();
            sender.tell(responseEx, self);
            return responseEx;
        }
    }

    private static Throwable extractRootCause(final Throwable t) {
        if (t instanceof CompletionException) {
            return extractRootCause(t.getCause());
        }
        return t;
    }

    private static Graph<SinkShape<WithSender>, NotUsed> unhandled() {
        return Consume.untyped(wrapped ->
                FALLBACK_LOGGER.warn("Unexpected message <{}> from <{}>", wrapped.getMessage(), wrapped.getSender()));
    }
}
