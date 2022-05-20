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
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;

import akka.NotUsed;

/**
 * Represents the result of subscribing a client with a Subscribe message ({@link GenericMqttSubscribe}).
 */
public abstract class SubscribeResult {

    private final Source connectionSource;

    /**
     * Constructs a {@code SubscribeResult}.
     *
     * @param connectionSource the source which is associated with this subscribe result.
     * @throws NullPointerException if {@code connectionSource} is {@code null}.
     */
    protected SubscribeResult(final Source connectionSource) {
        this.connectionSource = ConditionChecker.checkNotNull(connectionSource, "connectionSource");
    }

    /**
     * Indicates whether this subscribe result represents a success.
     *
     * @return {@code true} if this subscribe result is a success, {@code false} else.
     * @see #isFailure()
     */
    public abstract boolean isSuccess();

    /**
     * Indicates whether this subscribe result represents a failure.
     *
     * @return {@code true} if this subscribe result is a failure, {@code false} else.
     * @see #isSuccess()
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Returns the connection source which is associated with this subscribe result.
     *
     * @return the associated connection source.
     */
    public Source getConnectionSource() {
        return connectionSource;
    }

    /**
     * Returns the stream of received MQTT Publish messages for subscribed topics if this result is a success.
     *
     * @return the stream of received MQTT Publish messages for subscribed topics.
     * @throws IllegalStateException if this result is a failure.
     * @see #isSuccess()
     */
    public abstract akka.stream.javadsl.Source<GenericMqttPublish, NotUsed> getMqttPublishSourceOrThrow();

    /**
     * Returns the error that caused subscribing to fail if this result is a failure.
     *
     * @return the error that caused subscribing to fail.
     * @throws IllegalStateException if this result is a success.
     * @see #isFailure()
     */
    public abstract MqttSubscribeException getErrorOrThrow();

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (SubscribeResult) o;
        return Objects.equals(connectionSource, that.connectionSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionSource);
    }

}

