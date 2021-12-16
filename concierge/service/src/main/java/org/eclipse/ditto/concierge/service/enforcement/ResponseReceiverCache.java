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
package org.eclipse.ditto.concierge.service.enforcement;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.models.signal.SignalInformationPoint;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CaffeineCache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import akka.actor.ActorRef;
import akka.japi.Pair;

/**
 * A cache of response receivers and their associated correlation ID.
 * <p>
 * Each cache entry gets evicted after becoming expired.
 * <p>
 * To put a response receiver to this cache a {@link Command} has to be provided as key.
 * The command is necessary because of its headers.
 * The command headers are required to contain the mandatory correlation ID.
 * Optionally they provide the timeout of the command.
 * If the command headers provide a timeout, it is used as expiry for the cache entry, otherwise a fall-back expiry
 * is used.
 */
@NotThreadSafe
final class ResponseReceiverCache {

    private static final Duration DEFAULT_ENTRY_EXPIRY = Duration.ofMinutes(2L);
    private static final ResponseReceiverCache DEFAULT_INSTANCE = newInstance();

    private final Duration fallBackEntryExpiry;
    private final Cache<CorrelationIdKey, Pair<ActorRef, AuthorizationContext>> cache;

    private ResponseReceiverCache(final Duration fallBackEntryExpiry,
            final Cache<CorrelationIdKey, Pair<ActorRef, AuthorizationContext>> cache) {

        this.fallBackEntryExpiry = fallBackEntryExpiry;
        this.cache = cache;
    }

    /**
     * Returns a static default instance of {@code ResponseReceiverCache} with a hard-coded fall-back entry expiry.
     *
     * @return the instance.
     */
    static ResponseReceiverCache getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * Returns a new instance of {@code ResponseReceiverCache} with a hard-coded fall-back entry expiry.
     *
     * @return the instance.
     */
    static ResponseReceiverCache newInstance() {
        return newInstance(DEFAULT_ENTRY_EXPIRY);
    }

    /**
     * Returns a new instance of {@code ResponseReceiverCache} with the specified fall-back entry expiry.
     *
     * @param fallBackEntryExpiry the expiry to be used for cache entries of commands without a timeout.
     * @return the instance.
     * @throws NullPointerException if {@code fallBackEntryExpiry} is {@code null}.
     * @throws IllegalArgumentException if {@code fallBackEntryExpiry} is not positive.
     */
    static ResponseReceiverCache newInstance(final Duration fallBackEntryExpiry) {
        ConditionChecker.checkArgument(checkNotNull(fallBackEntryExpiry, "fallBackEntryExpiry"),
                Predicate.not(Duration::isZero).and(Predicate.not(Duration::isNegative)),
                () -> "The fallBackEntryExpiry must be positive.");

        return new ResponseReceiverCache(fallBackEntryExpiry, createCache(fallBackEntryExpiry));
    }

    private static Cache<CorrelationIdKey, Pair<ActorRef, AuthorizationContext>> createCache(
            final Duration fallBackEntryExpiry
    ) {
        return CaffeineCache.of(Caffeine.newBuilder().expireAfter(new CorrelationIdKeyExpiry(fallBackEntryExpiry)));
    }

    /**
     * Puts the specified response receiver for the correlation ID of the signal's correlation ID.
     *
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the headers of {@code signal} do not contain a correlation ID.
     */
    public void putCommand(final Signal<?> signal, final Pair<ActorRef, AuthorizationContext> responseReceiver) {
        cache.put(getCorrelationIdKeyForInsertion(checkNotNull(signal, "command")),
                checkNotNull(responseReceiver, "responseReceiver"));
    }

    private CorrelationIdKey getCorrelationIdKeyForInsertion(final WithDittoHeaders command) {
        final var commandDittoHeaders = command.getDittoHeaders();

        return CorrelationIdKey.forCacheInsertion(getCorrelationIdOrThrow(commandDittoHeaders),
                getExpiry(commandDittoHeaders));
    }

    private static String getCorrelationIdOrThrow(final DittoHeaders commandDittoHeaders) {
        return commandDittoHeaders.getCorrelationId()
                .orElseThrow(() -> new IllegalArgumentException("Command headers have no correlation ID."));
    }

    private Duration getExpiry(final DittoHeaders commandDittoHeaders) {
        return commandDittoHeaders.getTimeout().orElse(fallBackEntryExpiry);
    }

    /**
     * Returns the cached response receiver for the specified correlation ID argument.
     *
     * @param correlationId the correlation ID to get the cached response receiver for.
     * @return when successful, the cached response receiver for {@code correlationId} or an empty {@code Optional}.
     * @throws NullPointerException if {@code correlationId} is {@code null}.
     * @throws IllegalArgumentException if {@code correlationId} is empty or blank.
     */
    public CompletableFuture<Optional<Pair<ActorRef, AuthorizationContext>>> get(final CharSequence correlationId) {
        final var correlationIdString = String.valueOf(checkNotNull(correlationId, "correlationId"));
        ConditionChecker.checkArgument(correlationIdString,
                Predicate.not(String::isBlank),
                () -> "The correlationId must not be blank.");

        return cache.get(CorrelationIdKey.forCacheRetrieval(correlationIdString));
    }

    /**
     * Insert a response receiver for a live or message command.
     *
     * @param command the command.
     * @param receiverCreator creator of the receiver actor.
     * @param responseHandler handler of the response.
     * @param <T> type of results of the response handler.
     * @return the result of the response handler.
     */
    public <S extends Signal<?>, T> CompletionStage<T> insertResponseReceiverConflictFree(final S command,
            final Function<S, ActorRef> receiverCreator,
            final BiFunction<S, ActorRef, T> responseHandler) {

        return insertResponseReceiverConflictFreeWithFuture(command, receiverCreator,
                responseHandler.andThen(CompletableFuture::completedStage));
    }

    /**
     * Insert a response receiver for a live or message command.
     *
     * @param command the command.
     * @param receiverCreator creator of the receiver actor.
     * @param responseHandler handler of the response.
     * @param <T> type of results of the response handler.
     * @return the result of the response handler.
     */
    public <S extends Signal<?>, T> CompletionStage<T> insertResponseReceiverConflictFreeWithFuture(final S command,
            final Function<S, ActorRef> receiverCreator,
            final BiFunction<S, ActorRef, CompletionStage<T>> responseHandler) {

        return setUniqueCorrelationIdForGlobalDispatching(command, false)
                .thenCompose(commandWithUniqueCorrelationId -> {
                    final ActorRef receiver = receiverCreator.apply(commandWithUniqueCorrelationId);
                    // TODO change pair simply to receiver
                    final var responseReceiver =
                            Pair.create(receiver, command.getDittoHeaders().getAuthorizationContext());
                    putCommand(commandWithUniqueCorrelationId, responseReceiver);
                    return responseHandler.apply(commandWithUniqueCorrelationId, receiver);
                });
    }

    @SuppressWarnings("unchecked")
    private <S extends Signal<?>> CompletionStage<S> setUniqueCorrelationIdForGlobalDispatching(
            final S signal, final boolean refreshCorrelationId) {

        final String correlationId;
        if (refreshCorrelationId) {
            correlationId = UUID.randomUUID().toString();
        } else {
            correlationId = SignalInformationPoint.getCorrelationId(signal)
                    .orElseGet(() -> UUID.randomUUID().toString());
        }

        return get(correlationId).thenCompose(entry -> {
            if (entry.isPresent()) {
                return setUniqueCorrelationIdForGlobalDispatching(signal, true);
            }
            final S result = (S) signal.setDittoHeaders(DittoHeaders.newBuilder(signal.getDittoHeaders())
                    .correlationId(correlationId)
                    .build());
            return CompletableFuture.completedStage(result);
        });
    }

    @Immutable
    private static final class CorrelationIdKey {

        private final String correlationId;
        @Nullable private final Duration expiry;

        private CorrelationIdKey(final String correlationId, @Nullable final Duration expiry) {
            this.correlationId = correlationId;
            this.expiry = expiry;
        }

        static CorrelationIdKey forCacheInsertion(final String correlationId, final Duration expiry) {
            return new CorrelationIdKey(correlationId, expiry);
        }

        static CorrelationIdKey forCacheRetrieval(final String correlationId) {
            return new CorrelationIdKey(correlationId, null);
        }

        // Only use correlation ID here because this is the only value that
        // is used for cache entry retrieval.
        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final var that = (CorrelationIdKey) o;
            return Objects.equals(correlationId, that.correlationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(correlationId);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "correlationId=" + correlationId +
                    ", expiry=" + expiry +
                    "]";
        }

    }

    @Immutable
    private static final class CorrelationIdKeyExpiry
            implements Expiry<CorrelationIdKey, Pair<ActorRef, AuthorizationContext>> {

        private final Duration fallBackEntryExpiry;

        private CorrelationIdKeyExpiry(final Duration fallBackEntryExpiry) {
            this.fallBackEntryExpiry = fallBackEntryExpiry;
        }

        @Override
        public long expireAfterCreate(final CorrelationIdKey key,
                final Pair<ActorRef, AuthorizationContext> value,
                final long currentTime) {

            final var entryExpiry = Objects.requireNonNullElse(key.expiry, fallBackEntryExpiry);

            return entryExpiry.toNanos(); // it is crucial to return nanoseconds here
        }

        @Override
        public long expireAfterUpdate(final CorrelationIdKey key,
                final Pair<ActorRef, AuthorizationContext> value,
                final long currentTime,
                final long currentDuration) {

            return currentDuration;
        }

        @Override
        public long expireAfterRead(final CorrelationIdKey key,
                final Pair<ActorRef, AuthorizationContext> value,
                final long currentTime,
                final long currentDuration) {

            return currentDuration;
        }

    }

}
