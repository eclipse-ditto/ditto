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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.service.messaging.PublishTarget;


/**
 * An MQTT target.
 */
@Immutable
public final class MqttPublishTarget implements PublishTarget {

    private final String topic;
    private final int qos;

    public static MqttPublishTarget of(final String topic, final int qos) {
        return new MqttPublishTarget(topic, qos);
    }

    private MqttPublishTarget(final String topic, final int qos) {
        this.topic = ConditionChecker.argumentNotEmpty(topic, "topic");
        this.qos = qos;
    }

    public String getTopic() {
        return topic;
    }

    public int getQos() {
        return qos;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MqttPublishTarget that = (MqttPublishTarget) o;
        return Objects.equals(topic, that.topic) && qos == that.qos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, qos);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "topic=" + topic +
                ", qos=" + qos +
                "]";
    }

}
