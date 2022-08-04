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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;

import akka.actor.Props;

/**
 * Factory for creating {@link KafkaPublisherActor}s.
 */
public interface KafkaPublisherActorFactory {

    /**
     * Get the name that should be used for the actor that is created with {@code props}.
     *
     * @return the name of the actor.
     */
    String getActorName();

    /**
     * Get the props of the publisher actor that should be created.
     *
     * @param connection the connection.
     * @param producerFactory a factory to create a kafka SendProducer.
     * @param dryRun if the publisher actor should be started in dry-run mode.
     * @param connectivityStatusResolver connectivity status resolver to resolve occurred exceptions to a connectivity
     * status.
     * @param connectivityConfig the config of the connectivity service with potential overwrites.
     * @return the {@code Props} to create the publisher actor.
     */
    Props props(Connection connection,
            SendProducerFactory producerFactory,
            boolean dryRun,
            ConnectivityStatusResolver connectivityStatusResolver,
            ConnectivityConfig connectivityConfig);

}
