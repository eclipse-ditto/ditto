/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cacheloaders;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Scheduler;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;
import scala.compat.java8.FutureConverters;

/**
 * Helper/Pattern class providing an "ask with retry" pattern based on a provided {@link AskWithRetryConfig}.
 */
public final class AskWithRetry {

    private static final DittoRuntimeException DUMMY_DRE = new DittoRuntimeException("dummy",
            HttpStatus.INTERNAL_SERVER_ERROR, DittoHeaders.empty(), null, null, null, null) {
        @Override
        public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }
    };

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory.getThreadSafeLogger(AskWithRetry.class);

    private AskWithRetry() {
        throw new AssertionError();
    }

    /**
     * Dispatcher name to use for AskWithRetry.
     */
    public static final String ASK_WITH_RETRY_DISPATCHER = "ask-with-retry-dispatcher";

    /**
     * Performs the "ask with retry" pattern by asking the passed in {@code actorToAsk} the passed in {@code message},
     * mapping a successful response with the provided {@code responseMapper} and retrying the operation on Exceptions
     * which are not {@link DittoRuntimeException}s based on the given {@code config}.
     *
     * @param actorToAsk the actor to ask the message.
     * @param message the message to ask.
     * @param config the "ask with retry" configuration to apply, e.g. whether to do retries at all,
     * with which timeouts, with how many retries and delays, etc.
     * @param actorSystem the actorSystem for looking up the scheduler and dispatcher to use.
     * @param responseMapper a function converting the response of the asked message.
     * @param <M> the type of the message to ask.
     * @param <A> the type of the answer.
     * @return a CompletionStage which is completed by applying the passed in {@code responseMapper} function on the
     * response of the asked message or which is completed exceptionally with the Exception.
     */
    public static <M, A> CompletionStage<A> askWithRetry(final ActorRef actorToAsk,
            final M message,
            final AskWithRetryConfig config,
            final ActorSystem actorSystem,
            final Function<Object, A> responseMapper) {

        return askWithRetry(actorToAsk,
                message,
                config,
                actorSystem.getScheduler(),
                actorSystem.dispatchers().lookup(ASK_WITH_RETRY_DISPATCHER),
                responseMapper
        );
    }

    /**
     * Performs the "ask with retry" pattern by asking the passed in {@code actorToAsk} the passed in {@code message},
     * mapping a successful response with the provided {@code responseMapper} and retrying the operation on Exceptions
     * which are not {@link DittoRuntimeException}s based on the given {@code config}.
     *
     * @param actorToAsk the actor to ask the message.
     * @param message the message to ask.
     * @param config the "ask with retry" configuration to apply, e.g. whether to do retries at all,
     * with which timeouts, with how many retries and delays, etc.
     * @param scheduler the scheduler to use for retrying the ask.
     * @param executor the executor to use for retrying the ask.
     * @param responseMapper a function converting the response of the asked message.
     * @param <M> the type of the message to ask.
     * @param <A> the type of the answer.
     * @return a CompletionStage which is completed by applying the passed in {@code responseMapper} function on the
     * response of the asked message or which is completed exceptionally with the Exception.
     * @throws java.lang.NullPointerException if any of the passed arguments was {@code null}.
     */
    public static <M, A> CompletionStage<A> askWithRetry(final ActorRef actorToAsk,
            final M message,
            final AskWithRetryConfig config,
            final Scheduler scheduler,
            final Executor executor,
            final Function<Object, A> responseMapper) {

        checkNotNull(actorToAsk, "actorToAsk");
        checkNotNull(message, "message");
        checkNotNull(config, "config");
        checkNotNull(scheduler, "scheduler");
        checkNotNull(executor, "executor");
        checkNotNull(responseMapper, "responseMapper");

        final DittoHeaders dittoHeaders;
        if (message instanceof WithDittoHeaders withDittoHeaders) {
            dittoHeaders = withDittoHeaders.getDittoHeaders();
        } else {
            dittoHeaders = null;
        }

        final int retryAttempts = config.getRetryAttempts();
        final Callable<CompletionStage<AskResult<A>>> askHandleCallable = () ->
                createAskHandle(actorToAsk, message, dittoHeaders, responseMapper, config.getAskTimeout());

        final CompletionStage<AskResult<A>> stage;
        if (retryAttempts == 0) {
            stage = createAskHandle(actorToAsk, message, dittoHeaders, responseMapper, config.getAskTimeout());
        } else {
            switch (config.getRetryStrategy()) {
                case BACKOFF_DELAY:
                    stage = Patterns.retry(askHandleCallable,
                            retryAttempts,
                            config.getBackoffDelayMin(),
                            config.getBackoffDelayMax(),
                            config.getBackoffDelayRandomFactor(),
                            scheduler,
                            FutureConverters.fromExecutor(executor)
                    );
                    break;
                case FIXED_DELAY:
                    stage = Patterns.retry(askHandleCallable,
                            retryAttempts,
                            config.getFixedDelay(),
                            scheduler,
                            FutureConverters.fromExecutor(executor)
                    );
                    break;
                case NO_DELAY:
                    stage = Patterns.retry(askHandleCallable,
                            retryAttempts,
                            FutureConverters.fromExecutor(executor)
                    );
                    break;
                case OFF:
                default:
                    stage = createAskHandle(actorToAsk, message, dittoHeaders, responseMapper, config.getAskTimeout());
            }
        }

        return stage.handle(handleRetryResult(dittoHeaders));
    }

    private static <M, A> CompletionStage<AskResult<A>> createAskHandle(final ActorRef actorToAsk,
            final M message,
            @Nullable final DittoHeaders dittoHeaders,
            final Function<Object, A> responseMapper,
            final Duration askTimeout) {

        return Patterns.ask(actorToAsk, message, askTimeout)
                .handle((response, throwable) -> {
                    if (null != throwable) {
                        final var dre = DittoRuntimeException.asDittoRuntimeException(throwable,
                                cause -> DUMMY_DRE); // throwable was no DittoRuntimeException when DUMMY_DRE is used
                        if (dre != DUMMY_DRE) {
                            // we have a real DittoRuntimeException:
                            return new AskFailure<>(dre);
                        }
                        if (throwable instanceof AskTimeoutException) {
                            ThreadSafeDittoLogger l = LOGGER;
                            if (null != dittoHeaders) {
                                l = LOGGER.withCorrelationId(dittoHeaders);
                            }
                            l.warn("Got AskTimeout during ask for message <{}> - retrying.. : <{}>",
                                    message.getClass().getSimpleName(), throwable.getMessage());
                        }
                        // all non-known RuntimeException should be handled by the "Patterns.retry" with a retry:
                        throw new UnknownAskRuntimeException(throwable);
                    } else {
                        try {
                            return new AskSuccess<>(responseMapper.apply(response));
                        } catch (final DittoRuntimeException dre) {
                            return new AskFailure<>(dre);
                        }
                    }
                });
    }

    private static <A> BiFunction<AskResult<A>, Throwable, A> handleRetryResult(
            @Nullable final DittoHeaders dittoHeaders) {

        return (askResult, throwable) -> {
            if (null != throwable) {
                final Throwable cause;
                if (throwable instanceof UnknownAskRuntimeException) {
                    cause = throwable.getCause();
                } else {
                    cause = throwable;
                }
                throw DittoRuntimeException.asDittoRuntimeException(cause,
                        t -> {
                            final DittoRuntimeExceptionBuilder<AskException> exceptionBuilder =
                                    AskException.newBuilder().cause(t);
                            if (null != dittoHeaders) {
                                exceptionBuilder.dittoHeaders(dittoHeaders);
                            }
                            return exceptionBuilder.build();
                        });
            } else {
                if (askResult.getDittoRuntimeException().isPresent()) {
                    throw askResult.getDittoRuntimeException().get();
                } else {
                    return askResult.getAnswer().orElse(null);
                }
            }
        };
    }

    private interface AskResult<A> {

        Optional<A> getAnswer();

        Optional<DittoRuntimeException> getDittoRuntimeException();
    }

    private static final class AskSuccess<A> implements AskResult<A> {

        private final A answer;

        AskSuccess(final A answer) {
            this.answer = answer;
        }

        @Override
        public Optional<A> getAnswer() {
            return Optional.of(answer);
        }

        @Override
        public Optional<DittoRuntimeException> getDittoRuntimeException() {
            return Optional.empty();
        }
    }

    private static final class AskFailure<A> implements AskResult<A> {

        private final DittoRuntimeException dittoRuntimeException;

        AskFailure(final DittoRuntimeException dittoRuntimeException) {
            this.dittoRuntimeException = dittoRuntimeException;
        }

        @Override
        public Optional<A> getAnswer() {
            return Optional.empty();
        }

        @Override
        public Optional<DittoRuntimeException> getDittoRuntimeException() {
            return Optional.of(dittoRuntimeException);
        }
    }

    private static final class UnknownAskRuntimeException extends RuntimeException {

        UnknownAskRuntimeException(final Throwable cause) {
            super(cause);
        }
    }

}
