/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttSubscribeException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;

import akka.NotUsed;

/**
 * Represents failed subscribing for at least one MQTT topic via one particular
 * Subscribe message ({@link GenericMqttSubscribe}).
 */
final class SubscribeFailure extends SubscribeResult {

    private final MqttSubscribeException mqttSubscribeException;

    private SubscribeFailure(final Source connectionSource, final MqttSubscribeException mqttSubscribeException) {
        super(connectionSource);
        this.mqttSubscribeException = mqttSubscribeException;
    }

    /**
     * Returns a new instance of {@code SubscribeFailure} for the specified arguments.
     *
     * @param connectionSource the connection source which is associated with the returned subscribe failure.
     * @param mqttSubscribeException the error that caused subscribing to fail.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static SubscribeFailure newInstance(final Source connectionSource,
            final MqttSubscribeException mqttSubscribeException) {

        return new SubscribeFailure(connectionSource,
                ConditionChecker.checkNotNull(mqttSubscribeException, "mqttSubscribeException"));
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    /**
     * Throws always an IllegalStateException.
     */
    @Override
    public akka.stream.javadsl.Source<GenericMqttPublish, NotUsed> getMqttPublishSourceOrThrow() {
        throw new IllegalStateException("Failure cannot provide a MQTT Publish Source.");
    }

    @Override
    public MqttSubscribeException getErrorOrThrow() {
        return mqttSubscribeException;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final var that = (SubscribeFailure) o;
        return Objects.equals(mqttSubscribeException, that.mqttSubscribeException);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mqttSubscribeException);
    }

}
