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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.Connection;

import akka.actor.Props;

/**
 * Default implementation, providing a {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaPublisherActor}.
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
    public Props props(final Connection connection, final KafkaConnectionFactory factory, final boolean dryRun) {
        return KafkaPublisherActor.props(connection, factory, dryRun);
    }

}
