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

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publish.GenericMqttPublish;

import akka.NotUsed;

/**
 * Represents the result of subscribing a client with a Subscribe message ({@link GenericMqttSubscribe}).
 */
public interface SubscribeResult {

    /**
     * Indicates whether this subscribe result represents a success.
     *
     * @return {@code true} if this subscribe result is a success, {@code false} else.
     * @see #isFailure()
     */
    boolean isSuccess();

    /**
     * Indicates whether this subscribe result represents a failure.
     *
     * @return {@code true} if this subscribe result is a failure, {@code false} else.
     * @see #isSuccess()
     */
    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Returns the stream of received MQTT Publish messages for subscribed topics if this result is a success.
     *
     * @return the stream of received MQTT Publish messages for subscribed topics.
     * @throws IllegalStateException if this result is a failure.
     * @see #isSuccess()
     */
    akka.stream.javadsl.Source<GenericMqttPublish, NotUsed> getMqttPublishSourceOrThrow();

    /**
     * Returns the error that caused subscribing to fail if this result is a failure.
     *
     * @return the error that caused subscribing to fail.
     * @throws IllegalStateException if this result is a success.
     * @see #isFailure()
     */
    MqttSubscribeException getErrorOrThrow();

}

