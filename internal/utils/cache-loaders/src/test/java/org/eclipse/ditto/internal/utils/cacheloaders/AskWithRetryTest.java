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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig.AskWithRetryConfigValue.ASK_TIMEOUT;
import static org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_MAX;
import static org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_MIN;
import static org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_RANDOM_FACTOR;
import static org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig.AskWithRetryConfigValue.FIXED_DELAY;
import static org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig.AskWithRetryConfigValue.RETRY_ATTEMPTS;
import static org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig.AskWithRetryConfigValue.RETRY_STRATEGY;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.commands.CommandHeaderInvalidException;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.RetryStrategy;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Provides unit tests for {@link org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry} "pattern".
 */
public final class AskWithRetryTest {

    private static final String ASK_MESSAGE = "asking..";
    private static final String ASK_MESSAGE_SUCCESS_RESPONSE = "..answering";

    private static final Integer DEFAULT_RETRY_ATTEMPTS = 3;
    private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration DEFAULT_OFFSET = Duration.ofMillis(500);
    private static final Duration DEFAULT_QUIET_PERIOD = DEFAULT_ASK_TIMEOUT.minus(DEFAULT_OFFSET);
    private static final Duration DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD = DEFAULT_OFFSET.multipliedBy(2);
    private static final Duration DEFAULT_RETRY_DELAY = DEFAULT_ASK_TIMEOUT.plus(DEFAULT_OFFSET);

    private static ActorSystem system;

    @BeforeClass
    public static void setUpClass() {
        system = ActorSystem.create("test", ConfigFactory.load("test"));
    }

    @AfterClass
    public static void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void ensureRetryStrategyOffDoesNotRetry() {
        final Map<String, Object> configMap = Map.of(
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.OFF.name()
        );
        ensureDoesNotRetryOnSuccess(configMap);
    }

    @Test
    public void ensureRetryStrategyOffDoesNotRetryOnSentDittoRuntimeException() {
        final Map<String, Object> configMap = Map.of(
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.OFF.name()
        );
        ensureDoesNotRetryOnSentDittoRuntimeException(configMap);
    }

    @Test
    public void ensureRetryStrategyOffDoesNotRetryOnAskTimeout() {
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.OFF.name()
        );
        new TestKit(system) {{
            final CompletionStage<Object> retryStage = buildRetryStage(getRef(), configMap);

            expectMsg(ASK_MESSAGE);
            expectNoMessage(DEFAULT_RETRY_DELAY);

            assertThat(retryStage)
                    .failsWithin(DEFAULT_ASK_TIMEOUT)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(AskException.class);
        }};
    }

    @Test
    public void ensureRetryStrategyNoDelayDoesNotRetryOnSuccess() {
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.NO_DELAY.name()
        );
        ensureDoesNotRetryOnSuccess(configMap);
    }

    @Test
    public void ensureRetryStrategyNoDelayDoesNotRetryOnSentDittoRuntimeException() {
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.NO_DELAY.name(),
                RETRY_ATTEMPTS.getConfigPath(), 5
        );
        ensureDoesNotRetryOnSentDittoRuntimeException(configMap);
    }

    @Test
    public void ensureRetryStrategyNoDelayDoesRetryOnAskTimeoutUntilSuccess() {
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.NO_DELAY.name(),
                RETRY_ATTEMPTS.getConfigPath(), DEFAULT_RETRY_ATTEMPTS
        );
        new TestKit(system) {{
            final CompletionStage<Object> retryStage = buildRetryStage(getRef(), configMap);

            expectMsg(ASK_MESSAGE);
            expectNoMessage(DEFAULT_QUIET_PERIOD);
            for (int i = 0; i < DEFAULT_RETRY_ATTEMPTS; i++) {
                expectMsg(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD, ASK_MESSAGE);
                if (i == DEFAULT_RETRY_ATTEMPTS -
                        2) { // Succeed on penultimate attempt to make sure it's not retried again
                    reply(ASK_MESSAGE_SUCCESS_RESPONSE);
                    break;
                }
                expectNoMessage(DEFAULT_QUIET_PERIOD);
            }
            expectNoMessage(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD);

            assertThat(retryStage)
                    .succeedsWithin(DEFAULT_ASK_TIMEOUT)
                    .isEqualTo(ASK_MESSAGE_SUCCESS_RESPONSE);
        }};
    }

    @Test
    public void ensureRetryStrategyNoDelayDoesRetryOnAskTimeoutUntilFailed() {
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.NO_DELAY.name(),
                RETRY_ATTEMPTS.getConfigPath(), DEFAULT_RETRY_ATTEMPTS
        );
        new TestKit(system) {{
            final CompletionStage<Object> retryStage = buildRetryStage(getRef(), configMap);

            expectMsg(ASK_MESSAGE);
            expectNoMessage(DEFAULT_QUIET_PERIOD);
            for (int i = 0; i < DEFAULT_RETRY_ATTEMPTS; i++) {
                expectMsg(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD, ASK_MESSAGE);
                expectNoMessage(DEFAULT_QUIET_PERIOD);
            }
            expectNoMessage(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD);

            assertThat(retryStage)
                    .failsWithin(DEFAULT_ASK_TIMEOUT)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(AskException.class);
        }};
    }

    @Test
    public void ensureRetryStrategyFixedDelayDoesNotRetryOnSuccess() {
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.FIXED_DELAY.name()
        );
        ensureDoesNotRetryOnSuccess(configMap);
    }

    @Test
    public void ensureRetryStrategyFixedDelayDoesNotRetryOnSentDittoRuntimeException() {
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.FIXED_DELAY.name(),
                RETRY_ATTEMPTS.getConfigPath(), 5
        );
        ensureDoesNotRetryOnSentDittoRuntimeException(configMap);
    }

    @Test
    public void ensureRetryStrategyFixedDelayDoesRetryOnAskTimeoutUntilSuccess() {
        final Duration fixedDelay = Duration.ofSeconds(1);
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.FIXED_DELAY.name(),
                RETRY_ATTEMPTS.getConfigPath(), DEFAULT_RETRY_ATTEMPTS,
                FIXED_DELAY.getConfigPath(), fixedDelay
        );
        new TestKit(system) {{
            final CompletionStage<Object> retryStage = buildRetryStage(getRef(), configMap);

            expectMsg(ASK_MESSAGE);
            expectNoMessage(DEFAULT_QUIET_PERIOD);
            for (int i = 0; i < DEFAULT_RETRY_ATTEMPTS; i++) {
                expectMsg(fixedDelay.plus(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD), ASK_MESSAGE);
                if (i == DEFAULT_RETRY_ATTEMPTS - 2) { // Succeed on penultimate attempt to make sure it's not retried
                    reply(ASK_MESSAGE_SUCCESS_RESPONSE);
                    break;
                }
                expectNoMessage(DEFAULT_QUIET_PERIOD);
            }
            expectNoMessage(fixedDelay.plus(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD));

            assertThat(retryStage)
                    .succeedsWithin(DEFAULT_RETRY_DELAY)
                    .isEqualTo(ASK_MESSAGE_SUCCESS_RESPONSE);
        }};
    }

    @Test
    public void ensureRetryStrategyFixedDelayDoesRetryOnAskTimeoutUntilFailed() {
        final Duration fixedDelay = Duration.ofSeconds(1);
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.FIXED_DELAY.name(),
                RETRY_ATTEMPTS.getConfigPath(), DEFAULT_RETRY_ATTEMPTS,
                FIXED_DELAY.getConfigPath(), fixedDelay
        );
        new TestKit(system) {{
            final CompletionStage<Object> retryStage = buildRetryStage(getRef(), configMap);

            expectMsg(ASK_MESSAGE);
            expectNoMessage(DEFAULT_QUIET_PERIOD);
            for (int i = 0; i < DEFAULT_RETRY_ATTEMPTS; i++) {
                expectMsg(fixedDelay.plus(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD), ASK_MESSAGE);
                expectNoMessage(DEFAULT_QUIET_PERIOD);
            }
            expectNoMessage(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD);

            assertThat(retryStage)
                    .failsWithin(DEFAULT_ASK_TIMEOUT)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(AskException.class);
        }};
    }

    @Test
    public void ensureRetryStrategyBackoffDelayDoesNotRetryOnSuccess() {
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.BACKOFF_DELAY.name()
        );
        ensureDoesNotRetryOnSuccess(configMap);
    }

    @Test
    public void ensureRetryStrategyBackoffDelayDoesNotRetryOnSentDittoRuntimeException() {
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.BACKOFF_DELAY.name(),
                RETRY_ATTEMPTS.getConfigPath(), 5
        );
        ensureDoesNotRetryOnSentDittoRuntimeException(configMap);
    }

    @Test
    public void ensureRetryStrategyBackoffDelayDoesRetryOnAskTimeoutUntilSuccess() {
        final Duration minDelay = Duration.ofMillis(100);
        final Duration maxDelay = Duration.ofSeconds(1);
        final double randomFactor = 0.1;
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.BACKOFF_DELAY.name(),
                RETRY_ATTEMPTS.getConfigPath(), DEFAULT_RETRY_ATTEMPTS,
                BACKOFF_DELAY_MIN.getConfigPath(), minDelay,
                BACKOFF_DELAY_MAX.getConfigPath(), maxDelay,
                BACKOFF_DELAY_RANDOM_FACTOR.getConfigPath(), randomFactor
        );
        new TestKit(system) {{
            final CompletionStage<Object> retryStage = buildRetryStage(getRef(), configMap);

            expectMsg(ASK_MESSAGE);
            expectNoMessage(DEFAULT_QUIET_PERIOD);
            for (int i = 0; i < DEFAULT_RETRY_ATTEMPTS; i++) {
                expectMsg(maxDelay.plus(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD), ASK_MESSAGE);
                if (i == DEFAULT_RETRY_ATTEMPTS - 2) { // Succeed on penultimate attempt to make sure it's not retried
                    reply(ASK_MESSAGE_SUCCESS_RESPONSE);
                    break;
                }
                expectNoMessage(DEFAULT_QUIET_PERIOD);
            }
            expectNoMessage(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD);

            assertThat(retryStage)
                    .succeedsWithin(DEFAULT_ASK_TIMEOUT)
                    .isEqualTo(ASK_MESSAGE_SUCCESS_RESPONSE);
        }};
    }

    @Test
    public void ensureRetryStrategyBackoffDelayDoesRetryOnAskTimeoutUntilFailed() {
        final Duration minDelay = Duration.ofMillis(100);
        final Duration maxDelay = Duration.ofSeconds(1);
        final double randomFactor = 0.1;
        final Map<String, Object> configMap = Map.of(
                ASK_TIMEOUT.getConfigPath(), DEFAULT_ASK_TIMEOUT,
                RETRY_STRATEGY.getConfigPath(), RetryStrategy.BACKOFF_DELAY.name(),
                RETRY_ATTEMPTS.getConfigPath(), DEFAULT_RETRY_ATTEMPTS,
                BACKOFF_DELAY_MIN.getConfigPath(), minDelay,
                BACKOFF_DELAY_MAX.getConfigPath(), maxDelay,
                BACKOFF_DELAY_RANDOM_FACTOR.getConfigPath(), randomFactor
        );
        new TestKit(system) {{
            final CompletionStage<Object> retryStage = buildRetryStage(getRef(), configMap);

            expectMsg(ASK_MESSAGE);
            expectNoMessage(DEFAULT_QUIET_PERIOD);
            for (int i = 0; i < DEFAULT_RETRY_ATTEMPTS; i++) {
                expectMsg(maxDelay.plus(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD), ASK_MESSAGE);
                expectNoMessage(DEFAULT_QUIET_PERIOD);
            }
            expectNoMessage(maxDelay.plus(DEFAULT_RETRY_DELAY_AFTER_QUIET_PERIOD));

            assertThat(retryStage)
                    .failsWithin(DEFAULT_ASK_TIMEOUT)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(AskException.class);
        }};
    }

    private static void ensureDoesNotRetryOnSuccess(final Map<String, Object> configMap) {
        new TestKit(system) {{
            final CompletionStage<Object> retryStage = buildRetryStage(getRef(), configMap);

            expectMsg(ASK_MESSAGE);
            reply(ASK_MESSAGE_SUCCESS_RESPONSE);
            expectNoMessage(DEFAULT_RETRY_DELAY);

            assertThat(retryStage)
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo(ASK_MESSAGE_SUCCESS_RESPONSE);
        }};
    }

    private static void ensureDoesNotRetryOnSentDittoRuntimeException(final Map<String, Object> configMap) {
        new TestKit(system) {{
            final CompletionStage<Object> retryStage = buildRetryStage(getRef(), configMap);

            expectMsg(ASK_MESSAGE);
            reply(CommandHeaderInvalidException.newBuilder("just-for-testing").build());
            expectNoMessage(DEFAULT_RETRY_DELAY);

            assertThat(retryStage)
                    .failsWithin(DEFAULT_RETRY_DELAY)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(CommandHeaderInvalidException.class);
        }};
    }

    private static CompletionStage<Object> buildRetryStage(final ActorRef ref, final Map<String, Object> configMap) {
        return AskWithRetry.askWithRetry(ref, ASK_MESSAGE,
                buildConfig(configMap), system.getScheduler(), system.getDispatcher(),
                response -> {
                    if (response instanceof DittoRuntimeException) {
                        throw (DittoRuntimeException) response;
                    } else {
                        return response;
                    }
                });
    }

    private static AskWithRetryConfig buildConfig(final Map<String, Object> configMap) {
        final Config config = ConfigFactory.parseMap(configMap);
        return DefaultAskWithRetryConfig.of(ConfigFactory.empty()
                .withValue("test", config.root()), "test");
    }

}
