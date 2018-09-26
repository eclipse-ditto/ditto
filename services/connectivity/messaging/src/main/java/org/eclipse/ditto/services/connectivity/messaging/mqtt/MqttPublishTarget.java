/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Objects;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.services.connectivity.messaging.PublishTarget;

/**
 * An MQTT target.
 */
class MqttPublishTarget implements PublishTarget {

    private final String topic;

    static MqttPublishTarget of(final String topic) {
        return new MqttPublishTarget(topic);
    }

    private MqttPublishTarget(final String topic) {
        this.topic = ConditionChecker.argumentNotEmpty(topic, "topic");
    }

    static MqttPublishTarget fromTargetAddress(final String targetAddress) {
        return new MqttPublishTarget(targetAddress);
    }

    String getTopic() {
        return topic;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final MqttPublishTarget that = (MqttPublishTarget) o;
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
