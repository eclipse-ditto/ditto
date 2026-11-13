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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.schema;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * The retry around one component's schema bootstrap: every attempt is bounded by the attempt timeout; a failure the
 * {@link BootstrapFailureClassifier} retries is retried after an exponential, jittered backoff for as long as the
 * retry budget lasts; every other failure ends the bootstrap at once, with its original type. Completing the abort
 * signal ends the wait at once and cancels an attempt in flight. Logs one WARN per retried failure, one INFO when it
 * succeeded after retries and one ERROR when it gives up; stack traces only at DEBUG.
 */
final class SchemaBootstrapRetry {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaBootstrapRetry.class);

    /** Every backoff delay is varied by up to this fraction in both directions. */
    static final double JITTER = 0.2;

    private final String component;
    private final PostgresConfig.BootstrapConfig config;
    private final LongSupplier nanoClock;
    private final DoubleSupplier random;

    SchemaBootstrapRetry(final String component, final PostgresConfig.BootstrapConfig config,
            final LongSupplier nanoClock, final DoubleSupplier random) {
        this.component = component;
        this.config = config;
        this.nanoClock = nanoClock;
        this.random = random;
    }

    static SchemaBootstrapRetry of(final String component, final PostgresConfig.BootstrapConfig config) {
        return new SchemaBootstrapRetry(component, config, System::nanoTime,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * @param attempt supplies one attempt; called once per attempt.
     * @param abortSignal completing it ends the wait with a {@link SchemaBootstrapAbortedException}.
     * @param <T> the attempt's result type.
     * @return the result of the first successful attempt, or the error that ended the bootstrap.
     */
    <T> Mono<T> run(final Supplier<Mono<T>> attempt, final Mono<Void> abortSignal) {
        final Mono<T> retrying = Mono.defer(() -> {
            final long start = nanoClock.getAsLong();
            final AtomicInteger attempts = new AtomicInteger();
            return Mono.defer(() -> {
                        attempts.incrementAndGet();
                        return attempt.get();
                    })
                    .timeout(config.getAttemptTimeout())
                    .retryWhen(Retry.from(signals -> signals.concatMap(signal ->
                            onFailure(signal.failure(), attempts.get(), elapsedSince(start)))))
                    .doOnSuccess(result -> {
                        if (attempts.get() > 1) {
                            LOGGER.info("PostgreSQL schema bootstrap of component '{}' succeeded after {} attempts in "
                                    + "{}.", component, attempts.get(), seconds(elapsedSince(start)));
                        }
                    });
        });
        final Mono<T> aborted = abortSignal.then(Mono.error(() -> new SchemaBootstrapAbortedException(component)));
        // The abort signal is subscribed first: an abort that already happened prevents the first attempt.
        return Mono.firstWithSignal(List.of(aborted, retrying));
    }

    private Mono<Long> onFailure(final Throwable failure, final int attempt, final Duration elapsed) {
        final BootstrapFailureClassifier.Verdict verdict = BootstrapFailureClassifier.classify(failure);
        final PostgresConfig.BootstrapConfig.RetryConfig retry = config.getRetryConfig();
        LOGGER.debug("PostgreSQL schema bootstrap of component '{}': attempt {} failed.", component, attempt, failure);
        if (failure instanceof TimeoutException timeout) {
            final SchemaBootstrapTimeoutException stuck =
                    new SchemaBootstrapTimeoutException(component, config.getAttemptTimeout(), timeout);
            LOGGER.error(stuck.getMessage());
            return Mono.error(stuck);
        }
        if (!verdict.retryable()) {
            LOGGER.error("PostgreSQL schema bootstrap of component '{}' failed on attempt {} after {} and is not "
                    + "retried: {}", component, attempt, seconds(elapsed), verdict.reason());
            return Mono.error(failure);
        }
        if (!retry.isEnabled()) {
            LOGGER.error("PostgreSQL schema bootstrap of component '{}' failed and is not retried "
                    + "(ditto.postgresql.bootstrap.retry.enabled = false): {}", component, verdict.reason());
            return Mono.error(failure);
        }
        final Optional<Duration> delay = nextDelay(attempt, elapsed, retry, random.getAsDouble());
        if (delay.isEmpty()) {
            final SchemaBootstrapUnavailableException giveUp = new SchemaBootstrapUnavailableException(component,
                    attempt, elapsed, retry.getMaxDuration(), verdict.reason(), failure);
            LOGGER.error(giveUp.getMessage());
            return Mono.error(giveUp);
        }
        LOGGER.warn("PostgreSQL is not available for the schema bootstrap of component '{}' (attempt {}, {} elapsed): "
                        + "{}. Retrying in {}; {} of ditto.postgresql.bootstrap.retry.max-duration left.", component,
                attempt, seconds(elapsed), verdict.reason(), seconds(delay.get()),
                retry.getMaxDuration().isZero() ? "no limit" : seconds(retry.getMaxDuration().minus(elapsed)));
        return Mono.delay(delay.get());
    }

    private Duration elapsedSince(final long start) {
        return Duration.ofNanos(nanoClock.getAsLong() - start);
    }

    /**
     * The delay before the next attempt, or empty when the budget is used up. The delay is
     * {@code min(minBackoff · 2^(failedAttempts − 1), maxBackoff)} varied by ±{@link #JITTER}, and never longer than
     * the rest of the budget, so the last attempt starts at the budget's end.
     *
     * @param failedAttempts the number of attempts that failed so far, at least 1.
     * @param elapsed the time since the first attempt started.
     * @param retry the retry settings.
     * @param randomSample a sample from {@code [0, 1)}; 0.5 means no jitter.
     * @return the delay, or empty to give up.
     */
    static Optional<Duration> nextDelay(final int failedAttempts, final Duration elapsed,
            final PostgresConfig.BootstrapConfig.RetryConfig retry, final double randomSample) {
        final boolean limited = !retry.getMaxDuration().isZero();
        final Duration remaining = retry.getMaxDuration().minus(elapsed);
        if (limited && (remaining.isNegative() || remaining.isZero())) {
            return Optional.empty();
        }
        final Duration backoff = backoff(failedAttempts, retry.getMinBackoff(), retry.getMaxBackoff());
        final double factor = 1.0 + JITTER * (2.0 * randomSample - 1.0);
        final Duration jittered = Duration.ofNanos(Math.round(backoff.toNanos() * factor));
        return Optional.of(limited && jittered.compareTo(remaining) > 0 ? remaining : jittered);
    }

    static Duration backoff(final int failedAttempts, final Duration minBackoff, final Duration maxBackoff) {
        Duration backoff = minBackoff;
        for (int i = 1; i < failedAttempts && backoff.compareTo(maxBackoff) < 0; i++) {
            backoff = backoff.multipliedBy(2L);
        }
        return backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
    }

    static String seconds(final Duration duration) {
        return String.format(Locale.ROOT, "%.1f s", duration.toMillis() / 1000.0);
    }

}
