/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Objects;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.services.connectivity.messaging.PublishTarget;

/**
 * An MQTT target.
 */
class MqttTarget implements PublishTarget {

    private final String topic;

    static MqttTarget of(final String topic) {
        return new MqttTarget(topic);
    }

    private MqttTarget(final String topic) {
        this.topic = ConditionChecker.argumentNotEmpty(topic, "topic");
    }

    static MqttTarget fromTargetAddress(final String targetAddress) {
        return new MqttTarget(targetAddress);
    }

    String getTopic() {
        return topic;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final MqttTarget that = (MqttTarget) o;
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
