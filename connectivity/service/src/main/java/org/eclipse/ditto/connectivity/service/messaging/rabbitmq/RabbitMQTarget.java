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
package org.eclipse.ditto.connectivity.service.messaging.rabbitmq;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.messaging.PublishTarget;

/**
 * A RabbitMQ target has an exchange and an optional routing key.
 */
final class RabbitMQTarget implements PublishTarget {

    private final String exchange;
    @Nullable private final String routingKey;

    static RabbitMQTarget of(final String exchange, final String routingKey) {
        return new RabbitMQTarget(exchange, routingKey);
    }

    private RabbitMQTarget(final String exchange, @Nullable final String routingKey) {
        this.exchange = ConditionChecker.checkNotNull(exchange, "exchange");
        this.routingKey = routingKey;
    }

    static RabbitMQTarget fromTargetAddress(final String targetAddress) {
        final Supplier<DittoRuntimeException> exceptionSupplier =
                () -> ConnectionConfigurationInvalidException.newBuilder(
                        "The target address '" + targetAddress + "' must be specified " +
                                "in the format 'exchange/routingKey'.")
                        .build();
        final String exchange = getExchangeFromTarget(targetAddress).orElseThrow(exceptionSupplier);
        final String routingKey = getRoutingKeyFromTarget(targetAddress).orElseThrow(exceptionSupplier);
        return new RabbitMQTarget(exchange, routingKey);
    }

    /**
     * For RabbitMQ connections the target can have the following format: [exchange]/[routingKey].
     *
     * @param target the configured target
     * @return the exchange part of the target, empty Optional otherwise.
     */
    private static Optional<String> getExchangeFromTarget(final String target) {
        return Optional.of(target.split("/"))
                .filter(segments -> segments.length > 0)
                .map(segments -> segments[0]);
    }

    /**
     * For RabbitMQ connections the target can have the following format: [exchange]/[routingKey].
     *
     * @param target the configured target
     * @return the optional routing key part
     */
    private static Optional<String> getRoutingKeyFromTarget(final String target) {
        return Optional.of(target.split("/"))
                .filter(segments -> segments.length == 2)
                .map(segments -> segments[1])
                .filter(routingKey -> !routingKey.isEmpty());
    }

    String getExchange() {
        return exchange;
    }

    @Nullable
    String getRoutingKey() {
        return routingKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RabbitMQTarget that = (RabbitMQTarget) o;
        return Objects.equals(exchange, that.exchange) &&
                Objects.equals(routingKey, that.routingKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exchange, routingKey);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "exchange=" + exchange +
                ", routingKey=" + routingKey +
                "]";
    }
}
