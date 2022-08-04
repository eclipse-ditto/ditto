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
package org.eclipse.ditto.things.service.persistence.actors;

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
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Unit test for {@link ResponseReceiverCache}.
 */
public final class ResponseReceiverCacheTest {

    public static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            DittoAuthorizationContextType.UNSPECIFIED, AuthorizationSubject.newInstance("foo:bar"));

    private static ActorSystem actorSystem;

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void setupTest() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
    }

    @Test
    public void newInstanceWithNullDurationThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ResponseReceiverCache.newInstance(actorSystem, null))
                .withMessage("The fallBackEntryExpiry must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNegativeDurationThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ResponseReceiverCache.newInstance(actorSystem, Duration.ofSeconds(-1)))
                .withMessage("The fallBackEntryExpiry must be positive.")
                .withNoCause();
    }

    @Test
    public void newInstanceWithZeroDurationThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ResponseReceiverCache.newInstance(actorSystem, Duration.ZERO))
                .withMessage("The fallBackEntryExpiry must be positive.")
                .withNoCause();
    }

    @Test
    public void cacheNullSignalThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance(actorSystem);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.cacheSignalResponseReceiver(null, null))
                .withMessage("The signal must not be null!")
                .withNoCause();
    }

    @Test
    public void cacheNullResponseReceiverThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance(actorSystem);
        final var command = Mockito.mock(Command.class);
        Mockito.when(command.getDittoHeaders())
                .thenReturn(DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build());

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.cacheSignalResponseReceiver(command, null))
                .withMessage("The responseReceiver must not be null!")
                .withNoCause();
    }

    @Test
    public void getWithNullCorrelationIdThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance(actorSystem);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.get(null))
                .withMessage("The correlationId must not be null!")
                .withNoCause();
    }

    @Test
    public void getWithEmptyCorrelationIdThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance(actorSystem);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.get(""))
                .withMessage("The correlationId must not be blank.")
                .withNoCause();
    }

    @Test
    public void getWithBlankCorrelationIdThrowsException() {
        final var underTest = ResponseReceiverCache.newInstance(actorSystem);

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
        final var underTest = ResponseReceiverCache.newInstance(actorSystem);

        final var mockReceiver = Mockito.mock(ActorRef.class);
        underTest.cacheSignalResponseReceiver(command, mockReceiver);

        final var cacheEntryFuture = underTest.get(correlationId.toString());

        assertThat(cacheEntryFuture).succeedsWithin(expiry)
                .isEqualTo(Optional.of(new ResponseReceiverCache.ResponseReceiverCacheEntry(mockReceiver, AUTHORIZATION_CONTEXT)));
    }

    @Test
    public void getPreviouslyPutEntryAfterExpiryReturnsEmptyOptional() throws ExecutionException, InterruptedException {
        final var expiry = Duration.ofSeconds(1L);
        final var command = Mockito.mock(Command.class);
        final var correlationId = testNameCorrelationId.getCorrelationId();
        Mockito.when(command.getDittoHeaders())
                .thenReturn(getDittoHeadersWithCorrelationIdAndTimeout(correlationId, expiry));
        final var underTest = ResponseReceiverCache.newInstance(actorSystem);

        underTest.cacheSignalResponseReceiver(command, Mockito.mock(ActorRef.class));

        final var cacheEntryFuture = Awaitility.await("get expired cache entry")
                .pollDelay(expiry.plusMillis(250L))
                .until(() -> underTest.get(correlationId), CompletableFuture::isDone);

        assertThat(cacheEntryFuture.get()).isEmpty();
    }

    @Test
    public void getEntryFromEmptyCacheReturnsEmptyOptional()
            throws ExecutionException, InterruptedException, TimeoutException {

        final var underTest = ResponseReceiverCache.newInstance(actorSystem);

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
                .map(_expiry -> new ResponseReceiverCache.ResponseReceiverCacheEntry(
                        Mockito.mock(ActorRef.class), AUTHORIZATION_CONTEXT))
                .collect(Collectors.toList());

        final var underTest = ResponseReceiverCache.newInstance(actorSystem);
        IntStream.range(0, expirySequence.size())
                .forEach(index -> underTest.cacheSignalResponseReceiver(commands.get(index), responseReceivers.get(index).sender()));

        Awaitility.await()
                .pollDelay(shortExpiry.plusMillis(100L))
                .untilAsserted(() -> {
                    final var cacheEntryFutures = correlationIds.stream()
                            .map(underTest::get)
                            .collect(Collectors.toList());

                    IntStream.range(0, cacheEntryFutures.size())
                            .forEach(index -> {
                                final Optional<ResponseReceiverCache.ResponseReceiverCacheEntry> expected;
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

        return DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .timeout(timeout).build();
    }

}
