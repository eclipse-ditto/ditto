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
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.services.connectivity.messaging.PublishTarget;

/**
 * A RabbitMQ target has an exchange and an optional routing key.
 */
class RabbitMQTarget implements PublishTarget {

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
     * @return the exchange part of the target, {@link Optional#EMPTY} otherwise.
     */
    private static Optional<String> getExchangeFromTarget(final String target) {
        return Optional.of(target.split("/"))
                .filter(segments -> segments.length > 0)
                .map(segments -> segments[0])
                .filter(exchange -> !exchange.isEmpty());
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
