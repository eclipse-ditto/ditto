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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.Expiry;

/**
 * Caffeine {@link Expiry} with the semantics of "expire-after-write" combined with "expire-after-access", where the
 * write expiry of each written entry is randomly varied by a jitter fraction.
 * <p>
 * Without jitter, all entries written at the same time (e.g. the hot entries loaded right after a restart) expire
 * and get reloaded at the same time again and again, as they are accessed too often to expire after access.
 * The jitter spreads those reloads out over time.
 * <p>
 * Caffeine does not allow combining a variable expiry with its fixed write/access expiry, and only provides the
 * remaining duration on access. The write deadline of each entry is therefore kept in a map which must be cleaned up
 * via {@link #onRemoval(Object, Object)} when an entry is removed from the cache.
 *
 * @param <K> the type of the cache keys.
 * @param <V> the type of the cache values.
 */
final class JitteredWriteExpiry<K, V> implements Expiry<K, V> {

    private final long expireAfterWriteNanos;
    private final long expireAfterAccessNanos;
    private final double jitter;
    private final DoubleSupplier randomSupplier;
    private final Map<K, WriteDeadline> writeDeadlines;

    private JitteredWriteExpiry(final Duration expireAfterWrite,
            final double jitter,
            final Duration expireAfterAccess,
            final DoubleSupplier randomSupplier) {

        expireAfterWriteNanos = expireAfterWrite.toNanos();
        expireAfterAccessNanos = expireAfterAccess.toNanos();
        this.jitter = jitter;
        this.randomSupplier = randomSupplier;
        writeDeadlines = new ConcurrentHashMap<>();
    }

    /**
     * Returns a new instance of {@code JitteredWriteExpiry}.
     *
     * @param expireAfterWrite the duration after which a written entry expires before applying the jitter.
     * @param jitter the fraction by which the write expiry is randomly varied, between 0.0 and 1.0 (exclusive).
     * @param expireAfterAccess the duration after which an accessed entry expires.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the instance.
     */
    static <K, V> JitteredWriteExpiry<K, V> of(final Duration expireAfterWrite,
            final double jitter,
            final Duration expireAfterAccess) {

        return of(expireAfterWrite, jitter, expireAfterAccess, () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * Returns a new instance of {@code JitteredWriteExpiry} using the given source of randomness.
     *
     * @param expireAfterWrite the duration after which a written entry expires before applying the jitter.
     * @param jitter the fraction by which the write expiry is randomly varied, between 0.0 and 1.0 (exclusive).
     * @param expireAfterAccess the duration after which an accessed entry expires.
     * @param randomSupplier supplies random values between 0.0 (inclusive) and 1.0 (exclusive).
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the instance.
     */
    static <K, V> JitteredWriteExpiry<K, V> of(final Duration expireAfterWrite,
            final double jitter,
            final Duration expireAfterAccess,
            final DoubleSupplier randomSupplier) {

        if (jitter < 0.0 || jitter >= 1.0) {
            throw new IllegalArgumentException("The jitter must be between 0.0 (inclusive) and 1.0 (exclusive) " +
                    "but it was <" + jitter + ">!");
        }
        return new JitteredWriteExpiry<>(expireAfterWrite, jitter, expireAfterAccess, randomSupplier);
    }

    @Override
    public long expireAfterCreate(final K key, final V value, final long currentTime) {
        return startWriteExpiry(key, value, currentTime);
    }

    @Override
    public long expireAfterUpdate(final K key, final V value, final long currentTime, final long currentDuration) {
        return startWriteExpiry(key, value, currentTime);
    }

    @Override
    public long expireAfterRead(final K key, final V value, final long currentTime, final long currentDuration) {
        final WriteDeadline writeDeadline = writeDeadlines.get(key);
        if (null == writeDeadline || writeDeadline.value() != value) {
            // the entry was replaced concurrently - keep the expiry it already has
            return currentDuration;
        }
        return Math.max(0L, Math.min(writeDeadline.nanoTime() - currentTime, expireAfterAccessNanos));
    }

    /**
     * Removes the write deadline of a removed cache entry, unless the key has meanwhile been written again.
     *
     * @param key the key of the removed entry.
     * @param value the value of the removed entry or {@code null} if it was not available.
     */
    void onRemoval(@Nullable final K key, @Nullable final V value) {
        if (null != key) {
            writeDeadlines.computeIfPresent(key,
                    (k, writeDeadline) -> writeDeadline.value() == value ? null : writeDeadline);
        }
    }

    int getTrackedEntriesCount() {
        return writeDeadlines.size();
    }

    private long startWriteExpiry(final K key, final V value, final long currentTime) {
        final long writeExpiry = jitteredWriteExpiryNanos();
        writeDeadlines.put(key, new WriteDeadline(value, currentTime + writeExpiry));
        return Math.min(writeExpiry, expireAfterAccessNanos);
    }

    private long jitteredWriteExpiryNanos() {
        final double factor = 1.0 + jitter * (2.0 * randomSupplier.getAsDouble() - 1.0);
        return (long) (expireAfterWriteNanos * factor);
    }

    private record WriteDeadline(Object value, long nanoTime) {}

}
