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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;

import akka.Done;

/**
 * Responsible to wrap a {@link KafkaConsumerStream} and restart it on demand.
 */
final class RestartableKafkaConsumerStream implements KafkaConsumerStream {

    private final Backoff backoff;
    private final KafkaConsumerStream kafkaConsumerStream;
    private final Supplier<KafkaConsumerStream> consumerStreamStarter;

    RestartableKafkaConsumerStream(final Supplier<KafkaConsumerStream> consumerStreamStarter,
            final ExponentialBackOffConfig backOffConfig) {

        this.backoff = new Backoff(backOffConfig);
        this.consumerStreamStarter = consumerStreamStarter;
        this.kafkaConsumerStream = consumerStreamStarter.get();
    }

    private RestartableKafkaConsumerStream(final Supplier<KafkaConsumerStream> consumerStreamStarter,
            final Backoff backoff) {

        this.backoff = backoff;
        this.consumerStreamStarter = consumerStreamStarter;
        this.kafkaConsumerStream = consumerStreamStarter.get();
    }

    @Override
    public CompletionStage<Done> whenComplete(final BiConsumer<? super Done, ? super Throwable> handleCompletion) {
        return kafkaConsumerStream.whenComplete(handleCompletion);
    }

    @Override
    public CompletionStage<Done> stop() {
        return kafkaConsumerStream.stop();
    }

    /**
     * Stops the current stream and starts a new one which will be returned by the CompletionStage.
     *
     * @return The new instance of the kafka consumer stream.
     */
    CompletionStage<RestartableKafkaConsumerStream> restart() {
        return kafkaConsumerStream.stop()
                //Ignore errors from last stream to ensure a new stream is started
                .exceptionally(error -> Done.getInstance())
                .thenCompose(done -> {
                    final CompletableFuture<Backoff> delayFuture = new CompletableFuture<>();
                    final Duration restartDelay = backoff.getRestartDelay();
                    final Backoff nextBackoff = this.backoff.calculateNextBackoff();
                    delayFuture.completeOnTimeout(nextBackoff, restartDelay.toMillis(), TimeUnit.MILLISECONDS);
                    return delayFuture;
                })
                .thenApply(nextBackoff -> new RestartableKafkaConsumerStream(consumerStreamStarter, nextBackoff));
    }

    private static final class Backoff {

        private final Instant lastRestart = Instant.now();
        private final ExponentialBackOffConfig exponentialBackOffConfig;
        private final Duration restartDelay;
        private final Duration resetToMinThreshold;

        private Backoff(final ExponentialBackOffConfig exponentialBackOffConfig) {
            resetToMinThreshold = exponentialBackOffConfig.getMax().multipliedBy(2);
            this.exponentialBackOffConfig = exponentialBackOffConfig;
            this.restartDelay = exponentialBackOffConfig.getMin();
        }

        private Backoff(final ExponentialBackOffConfig exponentialBackOffConfig, final Duration restartDelay) {
            resetToMinThreshold = exponentialBackOffConfig.getMax().multipliedBy(2);
            this.exponentialBackOffConfig = exponentialBackOffConfig;
            this.restartDelay = restartDelay;
        }

        public Backoff calculateNextBackoff() {
            return new Backoff(exponentialBackOffConfig, calculateRestartDelay());
        }

        public Duration getRestartDelay() {
            return restartDelay;
        }

        private Duration calculateRestartDelay() {
            final Duration minBackOff = exponentialBackOffConfig.getMin();
            final Duration maxBackOff = exponentialBackOffConfig.getMax();
            final Instant now = Instant.now();
            final Duration sinceLastError = Duration.between(lastRestart, now);
            if (resetToMinThreshold.compareTo(sinceLastError) <= 0) {
                // no restart if time since last error exceed backoff threshold; reset to minBackOff.
                return minBackOff;
            } else {
                // increase delay.
                final double randomFactor = exponentialBackOffConfig.getRandomFactor();
                return calculateNextBackOffDuration(minBackOff, restartDelay, maxBackOff, randomFactor);
            }
        }

        private static Duration calculateNextBackOffDuration(final Duration minBackOff, final Duration restartDelay,
                final Duration maxBackOff, final double randomFactor) {
            final Duration nextBackoff = restartDelay.plus(randomize(restartDelay, randomFactor));
            return boundDuration(minBackOff, nextBackoff, maxBackOff);
        }

        /**
         * Return a random duration between the base duration and {@code (1 + randomFactor)} times the base duration.
         *
         * @param base the base duration.
         * @param randomFactor the random factor.
         * @return the random duration.
         */
        private static Duration randomize(final Duration base, final double randomFactor) {
            final double multiplier = 1.0 + ThreadLocalRandom.current().nextDouble() * randomFactor;
            return Duration.ofMillis((long) (base.toMillis() * multiplier));
        }

        private static Duration boundDuration(final Duration min, final Duration duration, final Duration max) {
            if (duration.minus(min).isNegative()) {
                return min;
            } else if (max.minus(duration).isNegative()) {
                return max;
            } else {
                return duration;
            }
        }

    }

}
