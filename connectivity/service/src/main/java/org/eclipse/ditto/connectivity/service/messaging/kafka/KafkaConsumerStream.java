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

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import akka.Done;

/**
 * A start- and stoppable kafka consumer stream.
 */
interface KafkaConsumerStream {

    /**
     * Starts the consumer stream.
     *
     * @return a completion stage that completes when the stream is stopped (gracefully or exceptionally).
     */
    CompletionStage<Done> start();

    /**
     * Stops the consumer stream gracefully.
     */
    void stop();

    /**
     * Checks based on the Kafka headers {@code "creation-time"} and {@code "ttl"} (time to live) whether the processed
     * record should be treated as expired message (and no longer processed as a result) or not.
     *
     * @param consumerRecord the Kafka record to check the headers for expiry in.
     * @return whether the record/message is expired or not.
     */
    static boolean isNotExpired(final ConsumerRecord<String, String> consumerRecord) {
        final Headers headers = consumerRecord.headers();
        final long now = Instant.now().toEpochMilli();
        try {
            final Optional<Long> creationTimeOptional = Optional.ofNullable(headers.lastHeader("creation-time"))
                    .map(Header::value)
                    .map(String::new)
                    .map(Long::parseLong);
            final Optional<Long> ttlOptional = Optional.ofNullable(headers.lastHeader("ttl"))
                    .map(Header::value)
                    .map(String::new)
                    .map(Long::parseLong);
            if (creationTimeOptional.isPresent() && ttlOptional.isPresent()) {
                return now - creationTimeOptional.get() >= ttlOptional.get();
            }
            return true;
        } catch (final Exception e) {
            // Errors during reading/parsing headers should not cause the message to be dropped.
            return true;
        }
    }

}
