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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.services.connectivity.messaging.PublishTarget;


/**
 * A Kafka target.
 */
@Immutable
final class KafkaPublishTarget implements PublishTarget {

    private final String topic;

    static KafkaPublishTarget of(final String topic) {
        return new KafkaPublishTarget(topic);
    }

    private KafkaPublishTarget(final String topic) {
        this.topic = ConditionChecker.argumentNotEmpty(topic, "topic");
    }

    String getTopic() {
        return topic;
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
