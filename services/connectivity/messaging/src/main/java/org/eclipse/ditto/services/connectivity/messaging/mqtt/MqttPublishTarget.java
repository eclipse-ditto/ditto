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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.services.connectivity.messaging.PublishTarget;


/**
 * An MQTT target.
 */
@Immutable
public final class MqttPublishTarget implements PublishTarget {

    private final String topic;

    public static MqttPublishTarget of(final String topic) {
        return new MqttPublishTarget(topic);
    }

    private MqttPublishTarget(final String topic) {
        this.topic = ConditionChecker.argumentNotEmpty(topic, "topic");
    }

    public String getTopic() {
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
