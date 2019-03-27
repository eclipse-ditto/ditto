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

import java.util.List;

import org.eclipse.ditto.model.connectivity.Target;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Default implementation, providing a {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaPublisherActor}.
 */
public final class DefaultKafkaPublisherActorFactory implements KafkaPublisherActorFactory {

    private static DefaultKafkaPublisherActorFactory instance;

    private static final long serialVersionUID = 5200344820825330940L;

    private DefaultKafkaPublisherActorFactory() {
        // intentionally empty
    }

    /**
     * Get an instance of the publisher actor factory.
     * @return an instance of the publisher actor factory.
     */
    public static DefaultKafkaPublisherActorFactory getInstance() {
        if (null == instance) {
            instance = new DefaultKafkaPublisherActorFactory();
        }
        return instance;
    }

    @Override
    public String name() {
        return KafkaPublisherActor.ACTOR_NAME;
    }

    @Override
    public Props props(final String connectionId, final List<Target> targets,
            final KafkaConnectionFactory factory, final ActorRef kafkaClientActor,
            final boolean dryRun) {
        return KafkaPublisherActor.props(connectionId, targets, factory, kafkaClientActor, dryRun);
    }

}
