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
package org.eclipse.ditto.services.concierge.util.enforcement;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.akka.controlflow.GraphActor;
import org.eclipse.ditto.services.utils.akka.controlflow.Pipe;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.javadsl.Flow;
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

    private PreEnforcer() {}

    /**
     * Create a processing unit from a function without reply address for errors.
     *
     * @param processor function to call.
     * @return Akka stream graph.
     */
    public static Graph<FlowShape<WithSender, WithSender>, NotUsed> fromFunction(
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> processor) {

        return fromFunction(ActorRef.noSender(), processor);
    }

    /**
     * Create a processing unit from a function.
     *
     * @param self reference to the actor carrying the pre-enforcement.
     * @param processor function to call.
     * @return Akka stream graph.
     */
    public static Graph<FlowShape<WithSender, WithSender>, NotUsed> fromFunction(
            @Nullable final ActorRef self,
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> processor) {

        final Attributes logLevels =
                Attributes.createLogLevels(Logging.DebugLevel(), Logging.DebugLevel(), Logging.ErrorLevel());

        final Flow<WithSender<WithDittoHeaders>, WithSender, NotUsed> flow =
                Flow.<WithSender<WithDittoHeaders>>create()
                        .mapAsync(1, wrapped -> {
                            final Supplier<CompletionStage<Object>> futureSupplier = () ->
                                    processor.apply(wrapped.message())
                                            .<Object>thenApply(result -> WithSender.of(result, wrapped.sender()));

                            return handleErrorNowOrLater(futureSupplier, wrapped, self);
                        })
                        .log("PreEnforcer")
                        .withAttributes(logLevels)
                        .flatMapConcat(PreEnforcer::keepResultAndLogErrors);

        return Pipe.joinUnhandledSink(
                Pipe.joinFilteredFlow(Filter.of(WithDittoHeaders.class), flow),
                GraphActor.unhandled());
    }

    private static CompletionStage<Object> handleErrorNowOrLater(
            final Supplier<CompletionStage<Object>> futureSupplier,
            final WithSender<WithDittoHeaders> wrapped,
            final ActorRef self) {

        try {
            return futureSupplier.get()
                    .exceptionally(error -> handleError(error, wrapped, self));
        } catch (final Throwable error) {
            return CompletableFuture.completedFuture(handleError(error, wrapped, self));
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

    private static Object handleError(final Throwable error,
            final WithSender<WithDittoHeaders> wrapped,
            @Nullable final ActorRef self) {

        final Throwable rootCause = extractRootCause(error);
        final ActorRef sender = wrapped.sender();
        final DittoHeaders dittoHeaders = wrapped.message().getDittoHeaders();

        if (rootCause instanceof DittoRuntimeException) {
            sender.tell(rootCause, self);
        } else {
            final GatewayInternalErrorException responseEx =
                    GatewayInternalErrorException.newBuilder()
                            .dittoHeaders(dittoHeaders)
                            .cause(rootCause)
                            .build();
            sender.tell(responseEx, self);
        }

        return rootCause;
    }

    private static Throwable extractRootCause(final Throwable t) {
        if (t instanceof CompletionException) {
            return extractRootCause(t.getCause());
        }
        return t;
    }
}
