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

import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import akka.Done;

/**
 * Responsible to wrap a {@link KafkaConsumerStream} and restart it on demand.
 */
final class RestartableKafkaConsumerStream implements KafkaConsumerStream {

    private final KafkaConsumerStream kafkaConsumerStream;
    private final Supplier<KafkaConsumerStream> consumerStreamStarter;

    RestartableKafkaConsumerStream(final Supplier<KafkaConsumerStream> consumerStreamStarter) {
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
                .thenApply(done -> new RestartableKafkaConsumerStream(consumerStreamStarter));
    }

}
