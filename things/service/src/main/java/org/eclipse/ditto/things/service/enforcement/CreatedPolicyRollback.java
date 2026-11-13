/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.RetryStrategy;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyResponse;
import org.eclipse.ditto.things.model.ThingId;

import scala.concurrent.ExecutionContext;

/**
 * Deletes a policy that was created for a thing whose creation then failed.
 * <p>
 * While the policy's persistence actor restarts after a failed write, e.g. under persistence overload, the policies
 * service answers such a deletion as unavailable or not at all. The deletion is therefore retried as the given
 * configuration says, for as long as the answer is a server error or missing. It ends when the policy is deleted or
 * was already absent, when the policies service rejects the deletion with a client error, or when the attempts are
 * used up. Only a confirmed deletion is logged as such; a failed rollback is logged as an error that names the policy
 * left without its thing.
 * </p>
 */
public final class CreatedPolicyRollback {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(CreatedPolicyRollback.class);

    private CreatedPolicyRollback() {
        throw new AssertionError();
    }

    /**
     * How a rollback ended.
     */
    public enum Outcome {

        /**
         * The policies service confirmed the deletion.
         */
        DELETED,

        /**
         * The policy did not exist (any more), e.g. because an earlier, unanswered attempt deleted it.
         */
        ALREADY_ABSENT,

        /**
         * The policies service refused the deletion with a client error; retrying would not change that.
         */
        REJECTED,

        /**
         * The policies service stayed unavailable or silent for all attempts.
         */
        GAVE_UP
    }

    /**
     * The result of a rollback.
     *
     * @param outcome how the rollback ended.
     * @param attempts how many deletions were sent.
     * @param lastAnswer the last answer of the policies service or the failure of the last ask.
     */
    public record Result(Outcome outcome, int attempts, @Nullable Object lastAnswer) {}

    /**
     * Deletes the policy. The returned stage never completes exceptionally; the outcome is logged.
     *
     * @param policiesShardRegion where to send the deletion.
     * @param deletePolicy the deletion, carrying the headers to use (sudo, correlation ID).
     * @param thingId the thing whose creation is rolled back.
     * @param config the ask timeout of each attempt, the retry strategy, and the number and delays of the retries.
     * @param actorSystem provides the scheduler and the dispatcher of the retries.
     * @return the result of the rollback.
     */
    public static CompletionStage<Result> deletePolicy(final ActorRef policiesShardRegion,
            final DeletePolicy deletePolicy,
            final ThingId thingId,
            final AskWithRetryConfig config,
            final ActorSystem actorSystem) {

        final AtomicInteger attempts = new AtomicInteger();
        final Callable<CompletionStage<Object>> attempt = () -> {
            attempts.incrementAndGet();
            // a failed or unanswered ask is an answer as well: the retry predicate decides on it
            return Patterns.ask(policiesShardRegion, deletePolicy, config.getAskTimeout())
                    .handle((answer, error) -> null != error ? unwrap(error) : answer);
        };
        CompletionStage<Object> lastAnswer;
        try {
            lastAnswer = retry(attempt, config, actorSystem);
        } catch (final RuntimeException e) { // e.g. a back-off configuration that Pekko rejects
            lastAnswer = CompletableFuture.failedFuture(e);
        }
        return lastAnswer
                .handle((answer, error) -> toResult(null != error ? unwrap(error) : answer, attempts.get()))
                .thenApply(result -> logged(result, deletePolicy, thingId));
    }

    /**
     * Whether the answer to a deletion is worth another attempt: a server error such as
     * {@code PolicyUnavailableException}, or no answer at all (e.g. an ask timeout).
     *
     * @param answer the answer or the failure of the ask.
     * @return whether to retry.
     */
    static boolean isWorthRetrying(@Nullable final Object answer) {
        if (answer instanceof DittoRuntimeException dittoRuntimeException) {
            return dittoRuntimeException.getHttpStatus().isServerError();
        }
        return answer instanceof Throwable;
    }

    private static CompletionStage<Object> retry(final Callable<CompletionStage<Object>> attempt,
            final AskWithRetryConfig config,
            final ActorSystem actorSystem) {

        final BiPredicate<Object, Throwable> shouldRetry =
                (answer, error) -> isWorthRetrying(null != error ? unwrap(error) : answer);
        final int retries = config.getRetryAttempts();
        final ExecutionContext executionContext =
                actorSystem.dispatchers().lookup(AskWithRetry.ASK_WITH_RETRY_DISPATCHER);
        final RetryStrategy retryStrategy = retries > 0 ? config.getRetryStrategy() : RetryStrategy.OFF;
        return switch (retryStrategy) {
            case NO_DELAY -> Patterns.retry(attempt, shouldRetry, retries, executionContext);
            case FIXED_DELAY -> Patterns.retry(attempt, shouldRetry, retries, config.getFixedDelay(),
                    actorSystem.getScheduler(), executionContext);
            case BACKOFF_DELAY -> Patterns.retry(attempt, shouldRetry, retries, config.getBackoffDelayMin(),
                    config.getBackoffDelayMax(), config.getBackoffDelayRandomFactor(), actorSystem.getScheduler(),
                    executionContext);
            case OFF -> callOnce(attempt);
        };
    }

    private static CompletionStage<Object> callOnce(final Callable<CompletionStage<Object>> attempt) {
        try {
            return attempt.call();
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static Result toResult(@Nullable final Object lastAnswer, final int attempts) {
        final Outcome outcome;
        if (lastAnswer instanceof DeletePolicyResponse) {
            outcome = Outcome.DELETED;
        } else if (lastAnswer instanceof PolicyNotAccessibleException) {
            outcome = Outcome.ALREADY_ABSENT;
        } else if (isWorthRetrying(lastAnswer)) {
            outcome = Outcome.GAVE_UP;
        } else {
            outcome = Outcome.REJECTED;
        }
        return new Result(outcome, attempts, lastAnswer);
    }

    private static Result logged(final Result result, final DeletePolicy deletePolicy, final ThingId thingId) {
        final ThreadSafeDittoLogger logger = LOGGER.withCorrelationId(deletePolicy.getDittoHeaders());
        switch (result.outcome()) {
            case DELETED -> logger.info("Policy <{}> deleted after rolling back the creation of Thing <{}> " +
                    "(attempt {}).", deletePolicy.getEntityId(), thingId, result.attempts());
            case ALREADY_ABSENT -> logger.info("Policy <{}> was already absent when rolling back the creation of " +
                    "Thing <{}> (attempt {}).", deletePolicy.getEntityId(), thingId, result.attempts());
            case REJECTED, GAVE_UP -> logger.error("Rollback of the creation of Thing <{}> failed: Policy <{}> " +
                            "could not be deleted in {} attempt(s) and remains without its Thing. Last answer: <{}>",
                    thingId, deletePolicy.getEntityId(), result.attempts(), result.lastAnswer());
        }
        return result;
    }

    private static Throwable unwrap(final Throwable error) {
        return error instanceof CompletionException && null != error.getCause() ? unwrap(error.getCause()) : error;
    }

}
