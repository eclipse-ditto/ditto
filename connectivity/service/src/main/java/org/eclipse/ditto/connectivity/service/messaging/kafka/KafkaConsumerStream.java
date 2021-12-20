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

import javax.annotation.concurrent.Immutable;

import akka.Done;

/**
 * A start- and stoppable kafka consumer stream.
 */
@Immutable
interface KafkaConsumerStream {

    /**
     * Allows registering a handler for stream completion.
     *
     * @param handleCompletion the handler
     * @return the chained completion stage. Completes when the Stream completes and the handler finished.
     */
    CompletionStage<Done> whenComplete(BiConsumer<? super Done, ? super Throwable> handleCompletion);

    /**
     * Stops the consumer stream gracefully.
     *
     * @return A completion stage that completes when the stream is stopped.
     */
    CompletionStage<Done> stop();

    /**
     * Triggers report of metrics to DittoMetrics.
     */
    void reportMetrics();

}
