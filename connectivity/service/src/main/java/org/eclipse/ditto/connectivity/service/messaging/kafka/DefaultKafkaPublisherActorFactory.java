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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.KafkaProducerConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;

import akka.actor.Props;

/**
 * Default implementation, providing a {@link KafkaPublisherActor}.
 */
@Immutable
public final class DefaultKafkaPublisherActorFactory implements KafkaPublisherActorFactory {

    @Nullable private static DefaultKafkaPublisherActorFactory instance;

    private static final long serialVersionUID = 5200344820825330940L;

    private DefaultKafkaPublisherActorFactory() {
        super();
    }

    /**
     * Gets an instance of the publisher actor factory.
     *
     * @return the instance.
     */
    public static DefaultKafkaPublisherActorFactory getInstance() {
        DefaultKafkaPublisherActorFactory result = instance;
        if (null == result) {
            result = new DefaultKafkaPublisherActorFactory();
            instance = result;
        }
        return result;
    }

    @Override
    public String getActorName() {
        return KafkaPublisherActor.ACTOR_NAME;
    }

    @Override
    public Props props(final Connection connection,
            final KafkaProducerConfig config,
            final SendProducerFactory sendProducerFactory,
            final boolean dryRun,
            final String clientId,
            final ConnectivityStatusResolver connectivityStatusResolver) {
        return KafkaPublisherActor.props(connection, config, sendProducerFactory, dryRun, clientId,
                connectivityStatusResolver);
    }

}
