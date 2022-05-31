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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.ReconnectDelay;

/**
 * Message that triggers reconnection of the consumer client after a particular delay.
 */
@Immutable
public final class ReconnectConsumerClient {

    private final ReconnectDelay reconnectDelay;

    private ReconnectConsumerClient(final ReconnectDelay reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    /**
     * Returns an instance of {@code ReconnectConsumerClient} with the specified {@code ReconnectDelay} argument.
     *
     * @param reconnectDelay the delay how long to wait before reconnecting a consumer client for redelivery.
     * @return the instance.
     * @throws NullPointerException if {@code reconnectDelay} is {@code null}.
     */
    static ReconnectConsumerClient of(final ReconnectDelay reconnectDelay) {
        return new ReconnectConsumerClient(ConditionChecker.checkNotNull(reconnectDelay, "reconnectDelay"));
    }

    /**
     * Returns the delay how long to wait before reconnecting a consumer client for redelivery.
     *
     * @return the delay for reconnecting the consumer client.
     */
    public ReconnectDelay getReconnectDelay() {
        return reconnectDelay;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (ReconnectConsumerClient) o;
        return Objects.equals(reconnectDelay, that.reconnectDelay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reconnectDelay);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "reconnectDelay=" + reconnectDelay +
                "]";
    }

}
