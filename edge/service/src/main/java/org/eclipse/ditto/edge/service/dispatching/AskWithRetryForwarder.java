/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.dispatching;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.edge.service.EdgeServiceTimeoutException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;

import akka.actor.ActorRef;
import akka.actor.Scheduler;
import akka.pattern.AskTimeoutException;

/**
 * Forwards commands from the edges to a specified ActorRef, waiting for a response. Uses retry mechanism if the
 * response doesn't arrive.
 */
public final class AskWithRetryForwarder {

    private final static ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(AskWithRetryForwarder.class);

    private final Scheduler scheduler;
    private final Executor executor;
    private final AskWithRetryConfig askWithRetryConfig;

    private AskWithRetryForwarder(final Scheduler scheduler, final Executor executor,
            final AskWithRetryConfig askWithRetryConfig) {

        this.scheduler = scheduler;
        this.executor = executor;
        this.askWithRetryConfig = askWithRetryConfig;
    }

    static AskWithRetryForwarder newInstance(final Scheduler scheduler, final Executor executor,
            final AskWithRetryConfig askWithRetryConfig) {

        return new AskWithRetryForwarder(scheduler, executor, askWithRetryConfig);
    }

    /**
     * Asks the given {@code actorToAsk} for a response by telling {@code command}.
     * This method uses {@link AskWithRetry}.
     *
     * @param actorToAsk the actor that should be asked.
     * @param command the command that is used to ask.
     * @return A completion stage which either completes with a filtered response of type {@link R} or fails with a
     * {@link DittoRuntimeException}.
     */
    <R extends Signal<?>, C extends Signal<?>> CompletionStage<R> ask(final ActorRef actorToAsk, final C command) {

        return AskWithRetry.askWithRetry(actorToAsk, command, askWithRetryConfig, scheduler,
                executor, getResponseCaster(command));
    }

    /**
     * Returns a mapping function, which casts an Object response to the command response class.
     *
     * @return the mapping function.
     */
    @SuppressWarnings("unchecked")
    private <R extends Signal<?>,  C extends Signal<?>> Function<Object, R> getResponseCaster(final C command) {
        return response -> {
            if (CommandResponse.class.isAssignableFrom(response.getClass())) {
                return (R) response;
            } else if (response instanceof AskException || response instanceof AskTimeoutException) {
                final Optional<DittoRuntimeException> dittoRuntimeException =
                        handleAskTimeoutForCommand(command, (Throwable) response);
                if (dittoRuntimeException.isPresent()) {
                    throw dittoRuntimeException.get();
                } else {
                    return null;
                }
            } else {
                throw reportErrorOrResponse(command, response, null);
            }
        };
    }

    /**
     * Report unexpected error or unknown response.
     */
    private <C extends Signal<?>> DittoRuntimeException reportErrorOrResponse(final C command,
            @Nullable final Object response,
            @Nullable final Throwable error) {

        if (error != null) {
            return reportError(command, error);
        } else if (response instanceof Throwable) {
            return reportError(command, (Throwable) response);
        } else if (response != null) {
            return reportUnknownResponse(command, response);
        } else {
            return reportError(command, new NullPointerException("Response and error were null."));
        }
    }

    /**
     * Reports an error differently based on type of the error. If the error is of type
     * {@link DittoRuntimeException}, it is returned as is
     * (without modification), otherwise it is wrapped inside a {@link DittoInternalErrorException}.
     *
     * @param throwable the error.
     * @return DittoRuntimeException suitable for transmission of the error.
     */
    private <C extends Signal<?>> DittoRuntimeException reportError(final C command,
            @Nullable final Throwable throwable) {
        final Throwable error = throwable == null
                ? new NullPointerException("Result and error are both null")
                : throwable;
        final var dre = DittoRuntimeException.asDittoRuntimeException(
                error, t -> reportUnexpectedError(command, t));
        LOGGER.info(" - {}: {}", dre.getClass().getSimpleName(), dre.getMessage());
        return dre;
    }


    /**
     * Report unexpected error.
     */
    private <C extends Signal<?>> DittoRuntimeException reportUnexpectedError(final C command, final Throwable error) {
        LOGGER.error("Unexpected error", error);

        return DittoInternalErrorException.newBuilder()
                .cause(error)
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    /**
     * Report unknown response.
     */
    private <C extends Signal<?>> DittoInternalErrorException reportUnknownResponse(final C command,
            final Object response) {

        LOGGER.error("Unexpected response: <{}>", response);
        return DittoInternalErrorException.newBuilder().dittoHeaders(command.getDittoHeaders()).build();
    }

    /**
     * Report timeout.
     *
     * @param command the original command.
     * @param askTimeout the timeout exception.
     */
    private <C extends Signal<?>>Optional<DittoRuntimeException> handleAskTimeoutForCommand(final C command,
            final Throwable askTimeout) {

        LOGGER.withCorrelationId(command.getDittoHeaders()).error("Encountered timeout in edge forwarding", askTimeout);
        return Optional.of(EdgeServiceTimeoutException.newBuilder()
                .dittoHeaders(command.getDittoHeaders())
                .build());
    }

}
