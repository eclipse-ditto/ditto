/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.services.connectivity.messaging.PublishTarget;


/**
 * A Kafka target.
 */
@Immutable
final class KafkaPublishTarget implements PublishTarget {

    private final String topic;
    private final String key;
    private final Integer partition;

    static KafkaPublishTarget of(final String topic) {
        // TODO: extract key/partition from the possible formats:
        // topic/<key string>
        // topic#<partition 1..99_999>
        // topic
        return new KafkaPublishTarget(topic, null, null);
    }

    private KafkaPublishTarget(final String topic, @Nullable final String key, @Nullable final Integer partition) {
        this.topic = ConditionChecker.argumentNotEmpty(topic, "topic");
        this.key = key;
        this.partition = partition;
}

    String getTopic() {
        return topic;
    }

    String getKey() {
        return key;
    }

    Integer getPartition() {
        return partition;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KafkaPublishTarget that = (KafkaPublishTarget) o;
        return Objects.equals(topic, that.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "topic=" + topic +
                "]";
    }
}
