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
package org.eclipse.ditto.model.amqpbridge;

/**
 * A mutable builder for a {@link AmqpConnection} with a fluent API.
 */
public interface AmqpConnectionBuilder {

    /**
     * Enable/disable failover for the {@link AmqpConnection}.
     *
     * @param failoverEnabled if failover is enabled for this connection (default {@code true})
     * @return this builder to allow method chaining.
     */
    ImmutableAmqpConnectionBuilder failoverEnabled(boolean failoverEnabled);

    /**
     * Enable/disable validtion of certificates for the {@link AmqpConnection}.
     *
     * @param validateCertificate if server certificates are validated (default {@code true})
     * @return this builder to allow method chaining.
     */
    ImmutableAmqpConnectionBuilder validateCertificate(boolean validateCertificate);

    /**
     * Set the throttling rate for the {@link AmqpConnection}.
     *
     * @param throttle the throttling rate per second (default {@code 0}, disabled)
     * @return this builder to allow method chaining.
     */
    ImmutableAmqpConnectionBuilder throttle(int throttle);

    /**
     * Set the consumer count for the {@link AmqpConnection}.
     *
     * @param consumerCount the number of consumers that will be started in the cluster (default {@code 1})
     * @return this builder to allow method chaining.
     */
    ImmutableAmqpConnectionBuilder consumerCount(int consumerCount);

    /**
     * Set the command processor pool size for the {@link AmqpConnection}.
     *
     * @param processorPoolSize number of command processor actors that will be used at max (default {@code 5})
     * @return this builder to allow method chaining.
     */
    ImmutableAmqpConnectionBuilder processorPoolSize(int processorPoolSize);

    /**
     * Builds a new {@link AmqpConnection}.
     *
     * @return the new {@link AmqpConnection}
     */
    AmqpConnection build();
}