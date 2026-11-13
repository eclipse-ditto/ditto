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
package org.eclipse.ditto.internal.utils.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

/**
 * Unit test for {@link JitteredWriteExpiry}.
 */
public final class JitteredWriteExpiryTest {

    private static final Duration WRITE = Duration.ofHours(24);
    private static final Duration ACCESS = Duration.ofHours(2);
    private static final long HOUR = Duration.ofHours(1).toNanos();
    private static final String KEY = "key";

    private final AtomicReference<Double> random = new AtomicReference<>(0.5);
    private final JitteredWriteExpiry<String, Object> underTest =
            JitteredWriteExpiry.of(WRITE, 0.2, ACCESS, random::get);

    @Test
    public void rejectsJitterOutOfRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> JitteredWriteExpiry.of(WRITE, -0.1, ACCESS));
        assertThatIllegalArgumentException().isThrownBy(() -> JitteredWriteExpiry.of(WRITE, 1.0, ACCESS));
    }

    @Test
    public void createExpiresAfterAccessDurationIfShorterThanWriteDuration() {
        assertThat(underTest.expireAfterCreate(KEY, new Object(), 0L)).isEqualTo(ACCESS.toNanos());
    }

    @Test
    public void writeExpiryIsVariedByJitter() {
        final JitteredWriteExpiry<String, Object> withLongAccess =
                JitteredWriteExpiry.of(WRITE, 0.2, Duration.ofDays(7), random::get);

        random.set(0.0);
        assertThat(withLongAccess.expireAfterCreate(KEY, new Object(), 0L)).isEqualTo(Math.round(19.2 * HOUR));
        random.set(0.5);
        assertThat(withLongAccess.expireAfterCreate(KEY, new Object(), 0L)).isEqualTo(24 * HOUR);
        random.set(0.999_999);
        assertThat(withLongAccess.expireAfterCreate(KEY, new Object(), 0L))
                .isGreaterThan(28 * HOUR)
                .isLessThan(Math.round(28.8 * HOUR));
    }

    @Test
    public void readProlongsByAccessDurationButNotBeyondWriteDeadline() {
        random.set(0.0); // write deadline at 19.2h
        final Object value = new Object();
        underTest.expireAfterCreate(KEY, value, 0L);

        assertThat(underTest.expireAfterRead(KEY, value, HOUR, HOUR)).isEqualTo(ACCESS.toNanos());
        assertThat(underTest.expireAfterRead(KEY, value, 18 * HOUR, HOUR)).isEqualTo(Math.round(1.2 * HOUR));
        assertThat(underTest.expireAfterRead(KEY, value, 20 * HOUR, 0L)).isZero();
    }

    @Test
    public void updateStartsNewWriteDeadline() {
        random.set(0.0);
        final Object oldValue = new Object();
        underTest.expireAfterCreate(KEY, oldValue, 0L);

        final Object newValue = new Object();
        underTest.expireAfterUpdate(KEY, newValue, 18 * HOUR, HOUR);

        assertThat(underTest.expireAfterRead(KEY, newValue, 30 * HOUR, HOUR)).isEqualTo(ACCESS.toNanos());
    }

    @Test
    public void readOfReplacedValueKeepsCurrentDuration() {
        underTest.expireAfterCreate(KEY, new Object(), 0L);

        assertThat(underTest.expireAfterRead(KEY, new Object(), HOUR, 42L)).isEqualTo(42L);
    }

    @Test
    public void removalForgetsWriteDeadline() {
        final Object value = new Object();
        underTest.expireAfterCreate(KEY, value, 0L);

        underTest.onRemoval(KEY, value);

        assertThat(underTest.getTrackedEntriesCount()).isZero();
    }

    @Test
    public void removalOfReplacedValueKeepsWriteDeadlineOfNewValue() {
        final Object oldValue = new Object();
        underTest.expireAfterCreate(KEY, oldValue, 0L);
        final Object newValue = new Object();
        underTest.expireAfterUpdate(KEY, newValue, HOUR, HOUR);

        underTest.onRemoval(KEY, oldValue);

        assertThat(underTest.getTrackedEntriesCount()).isOne();
        assertThat(underTest.expireAfterRead(KEY, newValue, 2 * HOUR, HOUR)).isEqualTo(ACCESS.toNanos());
    }

    @Test
    public void asyncCaffeineCacheReloadsAfterJitteredWriteExpiryAndForgetsRemovedEntries() {
        random.set(0.0); // write deadline at 19.2h
        final AtomicLong ticker = new AtomicLong();
        final AtomicInteger loads = new AtomicInteger();
        final AsyncLoadingCache<String, Object> cache = Caffeine.newBuilder()
                .ticker(ticker::get)
                .executor(Runnable::run)
                .expireAfter(underTest)
                .removalListener((String key, Object value, RemovalCause cause) -> underTest.onRemoval(key, value))
                .buildAsync((key, executor) -> {
                    loads.incrementAndGet();
                    return CompletableFuture.completedFuture(new Object());
                });

        final Object first = cache.get(KEY).join();
        for (long hour = 1; hour <= 19; hour++) {
            ticker.set(hour * HOUR);
            assertThat(cache.get(KEY).join()).as("hour %d", hour).isSameAs(first);
        }
        ticker.set(Math.round(19.3 * HOUR));
        assertThat(cache.get(KEY).join()).isNotSameAs(first);
        assertThat(loads).hasValue(2);

        cache.synchronous().invalidate(KEY);
        cache.synchronous().cleanUp();
        assertThat(underTest.getTrackedEntriesCount()).isZero();
    }

    @Test
    public void asyncCaffeineCacheStartsWriteExpiryWhenLoadCompletesAfterGet() {
        random.set(0.0); // write deadline 19.2h after the load completed
        final AtomicLong ticker = new AtomicLong();
        final AtomicInteger loads = new AtomicInteger();
        final CompletableFuture<Object> pendingLoad = new CompletableFuture<>();
        final AsyncLoadingCache<String, Object> cache = Caffeine.newBuilder()
                .ticker(ticker::get)
                .executor(Runnable::run)
                .expireAfter(underTest)
                .removalListener((String key, Object value, RemovalCause cause) -> underTest.onRemoval(key, value))
                .buildAsync((key, executor) -> {
                    if (loads.incrementAndGet() == 1) {
                        return pendingLoad;
                    }
                    return CompletableFuture.completedFuture(new Object());
                });

        final CompletableFuture<Object> firstGet = cache.get(KEY);
        assertThat(firstGet).isNotDone();
        assertThat(underTest.getTrackedEntriesCount()).isZero();

        ticker.set(HOUR);
        final Object first = new Object();
        pendingLoad.complete(first);
        assertThat(firstGet.join()).isSameAs(first);
        assertThat(underTest.getTrackedEntriesCount()).isOne();

        // prolonged on access up to the write deadline at 20.2h
        for (long hour = 2; hour <= 20; hour++) {
            ticker.set(hour * HOUR);
            assertThat(cache.get(KEY).join()).as("hour %d", hour).isSameAs(first);
        }
        ticker.set(Math.round(20.3 * HOUR));
        assertThat(cache.get(KEY).join()).isNotSameAs(first);
        assertThat(loads).hasValue(2);
    }

}
