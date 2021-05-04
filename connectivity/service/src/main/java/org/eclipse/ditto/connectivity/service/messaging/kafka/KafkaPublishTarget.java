/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.internals.Topic;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.messaging.PublishTarget;

/**
 * A Kafka target to which messages can be published.
 */
@Immutable
final class KafkaPublishTarget implements PublishTarget {

    static final String KEY_SEPARATOR = "/";
    static final String PARTITION_SEPARATOR = "#";

    private final String topic;
    private final String key;
    private final Integer partition;

    private KafkaPublishTarget(final String topic, @Nullable final String key, @Nullable final Integer partition) {
        this.topic = topic;
        this.key = key;
        this.partition = partition;
    }

    static KafkaPublishTarget fromTargetAddress(final String targetAddress) {
        if (containsKey(targetAddress)) {
            return fromTargetAddressWithKey(targetAddress);
        } else if (containsPartition(targetAddress)) {
            return fromTargetAddressWithPartition(targetAddress);
        }

        return fromTargetAddressWithOnlyTopic(targetAddress);
    }

    static boolean containsKey(final String targetAddress) {
        final int index = targetAddress.indexOf(KEY_SEPARATOR);
        return index > 0 && index < targetAddress.length();
    }

    private static KafkaPublishTarget fromTargetAddressWithKey(final String targetAddress) {
        final String[] split = targetAddress.split(KEY_SEPARATOR, 2);
        final String topic = validateTopic(split[0]);
        final String key = validateKey(split[1]);
        return new KafkaPublishTarget(topic, key, null);
    }

    @Nullable
    private static String validateKey(final String key) {
        return key.isEmpty() ? null : key;
    }

    static boolean containsPartition(final String targetAddress) {
        final int index = targetAddress.indexOf(PARTITION_SEPARATOR);
        return index > 0 && index < targetAddress.length();
    }

    private static KafkaPublishTarget fromTargetAddressWithPartition(final String targetAddress) {
        final String[] split = targetAddress.split(PARTITION_SEPARATOR, 2);
        final String topic = validateTopic(split[0]);
        final Integer partition = validatePartition(split[1]);
        return new KafkaPublishTarget(topic, null, partition);
    }

    @Nullable
    private static Integer validatePartition(final String partitionString) {
        if (partitionString.isEmpty()) {
            return null;
        }
        return parsePartitionOrThrow(partitionString);
    }

    private static KafkaPublishTarget fromTargetAddressWithOnlyTopic(final String targetAddress) {
        validateTopic(targetAddress);
        return new KafkaPublishTarget(targetAddress, null, null);
    }

    private static String validateTopic(final String topic) {
        try {
            Topic.validate(topic);
            return topic;
        } catch (final InvalidTopicException e) {
            throw ConnectionConfigurationInvalidException.newBuilder(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private static Integer parsePartitionOrThrow(final String partitionString) {
        try {
            return Integer.parseInt(partitionString);
        } catch (final NumberFormatException e) {
            final String message = MessageFormat.format("Can not parse partition number from {0}", partitionString);
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .cause(e)
                    .build();
        }
    }

    String getTopic() {
        return topic;
    }

    Optional<String> getKey() {
        return Optional.ofNullable(key);
    }

    Optional<Integer> getPartition() {
        return Optional.ofNullable(partition);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final KafkaPublishTarget that = (KafkaPublishTarget) o;
        return Objects.equals(topic, that.topic) &&
                Objects.equals(key, that.key) &&
                Objects.equals(partition, that.partition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, key, partition);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "topic=" + topic +
                ", key=" + key +
                ", partition=" + partition +
                "]";
    }

}
