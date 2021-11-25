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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.assertj.core.api.JUnitSoftAssertions;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.japi.Pair;

/**
 * Unit test for {@link ResponseReceiverCache}.
 */
public final class ResponseReceiverCacheTest {

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void newInstanceWithNullDurationThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ResponseReceiverCache.newInstance(null))
                .withMessage("The fallBackEntryExpiry must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNegativeDurationThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ResponseReceiverCache.newInstance(Duration.ofSeconds(-1)))
                .withMessage("The fallBackEntryExpiry must be positive.")
                .withNoCause();
    }

    @Test
    public void newInstanceWithZeroDurationThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ResponseReceiverCache.newInstance(Duration.ZERO))
                .withMessage("The fallBackEntryExpiry must be positive.")
                .withNoCause();
    }

    @Test
    public void putNullCommandThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.putCommand(null, Pair.create(null, null)))
                .withMessage("The command must not be null!")
                .withNoCause();
    }

    @Test
    public void putNullResponseReceiverThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance();
        final var command = Mockito.mock(Command.class);
        Mockito.when(command.getDittoHeaders())
                .thenReturn(DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build());

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.putCommand(command, null))
                .withMessage("The responseReceiver must not be null!")
                .withNoCause();
    }

    @Test
    public void getWithNullCorrelationIdThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.get(null))
                .withMessage("The correlationId must not be null!")
                .withNoCause();
    }

    @Test
    public void getWithEmptyCorrelationIdThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.get(""))
                .withMessage("The correlationId must not be blank.")
                .withNoCause();
    }

    @Test
    public void getWithBlankCorrelationIdThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.get(" "))
                .withMessage("The correlationId must not be blank.")
                .withNoCause();
    }

    @Test
    public void getExistingEntryWithinExpiryReturnsResponseReceiver() {
        final var expiry = Duration.ofMinutes(5L);
        final var command = Mockito.mock(Command.class);
        final var correlationId = testNameCorrelationId.getCorrelationId();
        Mockito.when(command.getDittoHeaders())
                .thenReturn(getDittoHeadersWithCorrelationIdAndTimeout(correlationId, expiry));
        final var responseReceiver = Pair.<ActorRef, AuthorizationContext>create(null, null);
        final var underTest = ResponseReceiverCache.newInstance();

        underTest.putCommand(command, responseReceiver);

        final var cacheEntryFuture = underTest.get(correlationId.toString());

        assertThat(cacheEntryFuture).succeedsWithin(expiry).isEqualTo(Optional.of(responseReceiver));
    }

    @Test
    public void getPreviouslyPutEntryAfterExpiryReturnsEmptyOptional() throws ExecutionException, InterruptedException {
        final var expiry = Duration.ofSeconds(1L);
        final var command = Mockito.mock(Command.class);
        final var correlationId = testNameCorrelationId.getCorrelationId();
        Mockito.when(command.getDittoHeaders())
                .thenReturn(getDittoHeadersWithCorrelationIdAndTimeout(correlationId, expiry));
        final var responseReceiver = Pair.<ActorRef, AuthorizationContext>create(null, null);
        final var underTest = ResponseReceiverCache.newInstance();

        underTest.putCommand(command, responseReceiver);

        final var cacheEntryFuture = Awaitility.await("get expired cache entry")
                .pollDelay(expiry.plusMillis(250L))
                .until(() -> underTest.get(correlationId), CompletableFuture::isDone);

        assertThat(cacheEntryFuture.get()).isEmpty();
    }

    @Test
    public void getEntryFromEmptyCacheReturnsEmptyOptional()
            throws ExecutionException, InterruptedException, TimeoutException {

        final var underTest = ResponseReceiverCache.newInstance();

        final var cacheEntryFuture = underTest.get(testNameCorrelationId.getCorrelationId());

        assertThat(cacheEntryFuture.get(5L, TimeUnit.SECONDS)).isEmpty();
    }

    @Test
    public void getEntriesWithDifferentExpiryReturnsExpected() {
        final var shortExpiry = Duration.ofSeconds(1L);
        final var longExpiry = Duration.ofMinutes(1L);

        final var expirySequence = List.of(longExpiry, shortExpiry, longExpiry, shortExpiry);
        final var correlationIds = IntStream.range(0, expirySequence.size())
                .mapToObj(String::valueOf)
                .map("-"::concat)
                .map(testNameCorrelationId::getCorrelationId)
                .collect(Collectors.toList());
        final var commands = IntStream.range(0, expirySequence.size())
                .mapToObj(index -> getDittoHeadersWithCorrelationIdAndTimeout(correlationIds.get(index),
                        expirySequence.get(index)))
                .map(dittoHeaders -> {
                    final var command = Mockito.mock(Command.class);
                    Mockito.when(command.getDittoHeaders()).thenReturn(dittoHeaders);
                    return command;
                })
                .collect(Collectors.toList());
        final var responseReceivers = expirySequence.stream()
                .map(_expiry -> Pair.<ActorRef, AuthorizationContext>create(null, null))
                .collect(Collectors.toList());

        final var underTest = ResponseReceiverCache.newInstance();
        IntStream.range(0, expirySequence.size())
                .forEach(index -> underTest.putCommand(commands.get(index), responseReceivers.get(index)));

        Awaitility.await()
                .pollDelay(shortExpiry.plusMillis(100L))
                .untilAsserted(() -> {
                    final var cacheEntryFutures = correlationIds.stream()
                            .map(underTest::get)
                            .collect(Collectors.toList());

                    IntStream.range(0, cacheEntryFutures.size())
                            .forEach(index -> {
                                final Optional<Pair<ActorRef, AuthorizationContext>> expected;
                                if (0 == index % 2) {
                                    expected = Optional.of(responseReceivers.get(index));
                                } else {
                                    expected = Optional.empty();
                                }
                                final var cacheEntryFuture = cacheEntryFutures.get(index);

                                softly.assertThat(cacheEntryFuture)
                                        .as(String.valueOf(correlationIds.get(index)))
                                        .succeedsWithin(longExpiry.toMillis(), TimeUnit.MILLISECONDS)
                                        .isEqualTo(expected);
                            });
                });
    }

    private static DittoHeaders getDittoHeadersWithCorrelationIdAndTimeout(final CharSequence correlationId,
            final Duration timeout) {

        return DittoHeaders.newBuilder().correlationId(correlationId).timeout(timeout).build();
    }

}
