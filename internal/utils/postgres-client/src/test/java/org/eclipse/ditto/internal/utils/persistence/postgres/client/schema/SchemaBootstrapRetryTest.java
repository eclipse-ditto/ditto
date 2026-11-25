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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Unit test for {@link SchemaBootstrapRetry}: the backoff schedule as a pure function, and the Reactor wiring (retry,
 * give-up, fatal failures, abort, attempt timeout) in real time at the millisecond scale.
 */
public final class SchemaBootstrapRetryTest {

    private static final String COMPONENT = "retry-test";
    private static final Duration WAIT = Duration.ofSeconds(10);

    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private Level originalLevel;

    @Before
    public void captureLog() {
        final Logger logger = (Logger) LoggerFactory.getLogger(SchemaBootstrapRetry.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);   // logback-test.xml roots at WARN
        log.start();
        logger.addAppender(log);
    }

    @After
    public void releaseLog() {
        final Logger logger = (Logger) LoggerFactory.getLogger(SchemaBootstrapRetry.class);
        logger.detachAppender(log);
        logger.setLevel(originalLevel);
        log.stop();
    }

    // ---- pure schedule ---------------------------------------------------------------------------------------------

    @Test
    public void backoffDoublesFromMinToMaxBackoff() {
        final Duration min = Duration.ofSeconds(1);
        final Duration max = Duration.ofSeconds(10);
        final List<Duration> delays = new ArrayList<>();
        for (int failed = 1; failed <= 7; failed++) {
            delays.add(SchemaBootstrapRetry.backoff(failed, min, max));
        }
        assertThat(delays).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4),
                Duration.ofSeconds(8), max, max, max);
        assertThat(SchemaBootstrapRetry.backoff(1_000_000, min, max)).isEqualTo(max);
    }

    @Test
    public void jitterStaysWithinTwentyPercent() {
        final PostgresConfig.BootstrapConfig.RetryConfig retry = retryConfig("max-duration = 0s");
        assertThat(SchemaBootstrapRetry.nextDelay(3, Duration.ZERO, retry, 0.0)).contains(Duration.ofMillis(3200));
        assertThat(SchemaBootstrapRetry.nextDelay(3, Duration.ZERO, retry, 0.5)).contains(Duration.ofSeconds(4));
        assertThat(SchemaBootstrapRetry.nextDelay(3, Duration.ZERO, retry, 1.0)).contains(Duration.ofMillis(4800));
    }

    @Test
    public void theLastDelayIsClampedToTheBudgetAndTheBudgetEndsTheRetries() {
        final PostgresConfig.BootstrapConfig.RetryConfig retry = retryConfig("max-duration = 60s");
        assertThat(SchemaBootstrapRetry.nextDelay(9, Duration.ofSeconds(55), retry, 0.5))
                .contains(Duration.ofSeconds(5));
        assertThat(SchemaBootstrapRetry.nextDelay(10, Duration.ofSeconds(60), retry, 0.5)).isEmpty();
        assertThat(SchemaBootstrapRetry.nextDelay(10, Duration.ofSeconds(61), retry, 0.5)).isEmpty();
        assertThat(SchemaBootstrapRetry.nextDelay(1_000, Duration.ofHours(5), retryConfig("max-duration = 0s"), 0.5))
                .as("0s = no limit").contains(Duration.ofSeconds(10));
    }

    @Test
    public void theDefaultScheduleMatchesTheDocumentedTimeline() {
        // connection refused (fails at once): attempts start at 0, 1, 3, 7, 15, 25, 35, 45, 55 and 60 s, then give up
        final PostgresConfig.BootstrapConfig.RetryConfig retry =
                DefaultPostgresConfig.of(ConfigFactory.empty()).getBootstrapConfig().getRetryConfig();
        final List<Long> starts = new ArrayList<>();
        Duration clock = Duration.ZERO;
        for (int failed = 1; ; failed++) {
            starts.add(clock.toSeconds());
            final Optional<Duration> delay = SchemaBootstrapRetry.nextDelay(failed, clock, retry, 0.5);
            if (delay.isEmpty()) {
                break;
            }
            clock = clock.plus(delay.get());
        }
        assertThat(starts).containsExactly(0L, 1L, 3L, 7L, 15L, 25L, 35L, 45L, 55L, 60L);
    }

    // ---- wiring ----------------------------------------------------------------------------------------------------

    @Test
    public void retryableFailuresAreRetriedUntilAnAttemptSucceeds() {
        final AtomicInteger calls = new AtomicInteger();
        final String result = retry("retry { min-backoff = 10ms, max-backoff = 40ms }")
                .run(() -> calls.incrementAndGet() <= 3 ? Mono.<String>error(refused()) : Mono.just("ok"), Mono.never())
                .block(WAIT);
        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(4);
        assertThat(messages(Level.WARN)).hasSize(3)
                .allSatisfy(message -> assertThat(message).contains(COMPONENT).contains("08003"));
        assertThat(messages(Level.INFO)).anySatisfy(message -> assertThat(message).contains("after 4 attempts"));
    }

    @Test
    public void exhaustingTheBudgetGivesUpWithTheLastFailureAsCause() {
        final List<R2dbcException> thrown = new CopyOnWriteArrayList<>();
        final long start = System.nanoTime();
        final Throwable error = catchThrowable(() ->
                retry("retry { max-duration = 300ms, min-backoff = 20ms, max-backoff = 50ms }")
                        .run(() -> Mono.error(() -> {
                            final R2dbcException failure = refused();
                            thrown.add(failure);
                            return failure;
                        }), Mono.never())
                        .block(WAIT));
        assertThat(error).isInstanceOf(SchemaBootstrapUnavailableException.class)
                .hasMessageContaining(COMPONENT)
                .hasMessageContaining("ditto.postgresql.bootstrap.retry.max-duration");
        assertThat(error.getCause()).isSameAs(thrown.get(thrown.size() - 1));
        assertThat(thrown).hasSizeGreaterThanOrEqualTo(3);
        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isBetween(Duration.ofMillis(300), Duration.ofSeconds(3));
        assertThat(messages(Level.ERROR)).hasSize(1);
    }

    @Test
    public void aFatalFailureIsPropagatedUnchangedWithoutAnotherAttempt() {
        final SchemaBootException refusal = new SchemaBootException("checksum mismatch");
        final AtomicInteger calls = new AtomicInteger();
        final Throwable error = catchThrowable(() -> retry("retry { min-backoff = 10ms }")
                .run(() -> calls.incrementAndGet() < 3 ? Mono.error(refused()) : Mono.error(refusal), Mono.never())
                .block(WAIT));
        assertThat(error).isSameAs(refusal);
        assertThat(calls).hasValue(3);
        assertThat(messages(Level.ERROR)).singleElement().asString().contains("not retried").contains("schema refused");
    }

    @Test
    public void disabledRetryMakesExactlyOneAttempt() {
        final R2dbcException failure = refused();
        final AtomicInteger calls = new AtomicInteger();
        final Throwable error = catchThrowable(() -> retry("retry.enabled = false")
                .run(() -> {
                    calls.incrementAndGet();
                    return Mono.error(failure);
                }, Mono.never())
                .block(WAIT));
        assertThat(error).isSameAs(failure);
        assertThat(calls).hasValue(1);
    }

    @Test
    public void zeroMaxDurationKeepsRetrying() {
        final AtomicInteger calls = new AtomicInteger();
        final String result = retry("retry { max-duration = 0s, min-backoff = 1ms, max-backoff = 1ms }")
                .run(() -> calls.incrementAndGet() <= 100 ? Mono.<String>error(refused()) : Mono.just("ok"),
                        Mono.never())
                .block(WAIT);
        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(101);
    }

    @Test
    public void anAbortDuringTheBackoffEndsTheWait() throws Exception {
        final Sinks.Empty<Void> abort = Sinks.empty();
        final AtomicInteger calls = new AtomicInteger();
        final CompletableFuture<Object> outcome =
                retry("retry { max-duration = 0s, min-backoff = 10s, max-backoff = 10s }")
                        .run(() -> {
                            calls.incrementAndGet();
                            return Mono.error(refused());
                        }, abort.asMono())
                        .toFuture();
        awaitTrue(() -> calls.get() == 1);
        abort.tryEmitEmpty();
        assertThatThrownBy(() -> outcome.get(1, TimeUnit.SECONDS))
                .hasCauseInstanceOf(SchemaBootstrapAbortedException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    public void anAbortDuringAnAttemptCancelsIt() {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final Sinks.Empty<Void> abort = Sinks.empty();
        final CompletableFuture<Object> outcome = retry("")
                .run(() -> Mono.never().doOnCancel(() -> cancelled.set(true)), abort.asMono())
                .toFuture();
        abort.tryEmitEmpty();
        assertThatThrownBy(() -> outcome.get(1, TimeUnit.SECONDS))
                .hasCauseInstanceOf(SchemaBootstrapAbortedException.class);
        assertThat(cancelled).isTrue();
    }

    @Test
    public void anAbortThatAlreadyHappenedPreventsTheFirstAttempt() {
        final AtomicInteger calls = new AtomicInteger();
        final Throwable error = catchThrowable(() -> retry("")
                .run(() -> {
                    calls.incrementAndGet();
                    return Mono.just("x");
                }, Mono.empty())
                .block(WAIT));
        assertThat(error).isInstanceOf(SchemaBootstrapAbortedException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    public void anAttemptTimeoutIsNotRetried() {
        final AtomicInteger calls = new AtomicInteger();
        final Throwable error = catchThrowable(() -> retry("attempt-timeout = 100ms\nretry.min-backoff = 10ms")
                .run(() -> {
                    calls.incrementAndGet();
                    return Mono.never();
                }, Mono.never())
                .block(WAIT));
        assertThat(error).isInstanceOf(SchemaBootstrapTimeoutException.class)
                .hasMessageContaining("ditto.postgresql.bootstrap.attempt-timeout")
                .hasMessageContaining("pg_locks")
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    public void theTimeoutHintNamesTheSchemaAdvisoryLock() {
        assertThat(SchemaBootstrapTimeoutException.advisoryLockQuery())
                .contains("classid = 3351").contains("objid = 1885097546").contains("objsubid = 1");
    }

    private static SchemaBootstrapRetry retry(final String bootstrapBlock) {
        return new SchemaBootstrapRetry(COMPONENT, DefaultPostgresConfig.of(ConfigFactory.parseString(
                        "postgresql.bootstrap {\n" + bootstrapBlock + "\n}")).getBootstrapConfig(),
                System::nanoTime, () -> 0.5);
    }

    private static PostgresConfig.BootstrapConfig.RetryConfig retryConfig(final String retryBlock) {
        return DefaultPostgresConfig.of(
                        ConfigFactory.parseString("postgresql.bootstrap.retry {\n" + retryBlock + "\n}"))
                .getBootstrapConfig().getRetryConfig();
    }

    private static R2dbcException refused() {
        return new R2dbcNonTransientResourceException("Cannot connect to localhost/127.0.0.1:1", "08003",
                new ConnectException("Connection refused"));
    }

    private List<String> messages(final Level level) {
        return log.list.stream().filter(e -> e.getLevel() == level).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static void awaitTrue(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + WAIT);
            }
            Thread.sleep(10L);
        }
    }
}
