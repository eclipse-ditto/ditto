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

import static java.util.Optional.ofNullable;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Defines headers that are extracted from a consumed {@code ConsumerRecord} and made available to payload and/or
 * header mappings.
 */
enum KafkaHeader implements Function<ConsumerRecord<String, ByteBuffer>, Optional<String>> {

    /**
     * The topic the record is received from.
     */
    KAFKA_TOPIC("kafka.topic", ConsumerRecord::topic),
    /**
     * The timestamp of the received record.
     */
    KAFKA_TIMESTAMP("kafka.timestamp", kRecord -> Long.toString(kRecord.timestamp())),
    /**
     * The key of the received record (or null if not specified).
     */
    KAFKA_KEY("kafka.key", ConsumerRecord::key);

    private final String name;
    private final Function<ConsumerRecord<String, ByteBuffer>, String> extractor;

    /**
     * @param name the header name to be used in source header mappings
     */
    KafkaHeader(final String name,
            final Function<ConsumerRecord<String, ByteBuffer>, String> extractor) {
        this.name = name;
        this.extractor = extractor;
    }

    /**
     * @return the header name
     */
    public String getName() {
        return name;
    }

    @Override
    public Optional<String> apply(final ConsumerRecord<String, ByteBuffer> consumerRecord) {
        return ofNullable(extractor.apply(consumerRecord));
    }
}
