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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOff;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;

import akka.Done;

/**
 * Responsible to wrap a {@link KafkaConsumerStream} and restart it on demand.
 */
final class RestartableKafkaConsumerStream implements KafkaConsumerStream {

    private final ExponentialBackOff backOff;
    private final KafkaConsumerStream kafkaConsumerStream;
    private final Supplier<KafkaConsumerStream> consumerStreamStarter;

    RestartableKafkaConsumerStream(final Supplier<KafkaConsumerStream> consumerStreamStarter,
            final ExponentialBackOffConfig backOffConfig) {

        backOff = ExponentialBackOff.initial(backOffConfig);
        this.consumerStreamStarter = consumerStreamStarter;
        kafkaConsumerStream = consumerStreamStarter.get();
    }

    private RestartableKafkaConsumerStream(final Supplier<KafkaConsumerStream> consumerStreamStarter,
            final ExponentialBackOff backOff) {

        this.backOff = backOff;
        this.consumerStreamStarter = consumerStreamStarter;
        kafkaConsumerStream = consumerStreamStarter.get();
    }

    @Override
    public CompletionStage<Done> whenComplete(final BiConsumer<? super Done, ? super Throwable> handleCompletion) {
        return kafkaConsumerStream.whenComplete(handleCompletion);
    }

    @Override
    public CompletionStage<Done> stop() {
        return kafkaConsumerStream.stop();
    }

    @Override
    public void reportMetrics() {
        kafkaConsumerStream.reportMetrics();
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
                    final CompletableFuture<ExponentialBackOff> delayFuture = new CompletableFuture<>();
                    final ExponentialBackOff calculatedBackOff = this.backOff.calculateNextBackOff();
                    final Duration restartDelay = calculatedBackOff.getRestartDelay();
                    delayFuture.completeOnTimeout(calculatedBackOff, restartDelay.toMillis(), TimeUnit.MILLISECONDS);
                    return delayFuture;
                })
                .thenApply(nextBackoff -> new RestartableKafkaConsumerStream(consumerStreamStarter, nextBackoff));
    }

}
