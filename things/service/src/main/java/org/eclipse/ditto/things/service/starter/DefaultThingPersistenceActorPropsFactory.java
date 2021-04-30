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
package org.eclipse.ditto.things.service.starter;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceActorPropsFactory;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Factory for creating Props of {@link org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceActor}.
 */
@Immutable
final class DefaultThingPersistenceActorPropsFactory implements ThingPersistenceActorPropsFactory {

    private final ActorRef pubSubMediator;

    private DefaultThingPersistenceActorPropsFactory(final ActorRef pubSubMediator) {
        this.pubSubMediator = pubSubMediator;
    }

    /**
     * Returns an instance of {@code ThingPersistenceActorPropsFactory}.
     *
     * @param pubSubMediator the Akka pub-sub mediator with which to
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static DefaultThingPersistenceActorPropsFactory of(final ActorRef pubSubMediator) {
        return new DefaultThingPersistenceActorPropsFactory(pubSubMediator);
    }

    @Override
    public Props props(final ThingId thingId, final DistributedPub<ThingEvent<?>> distributedPub) {
        argumentNotEmpty(thingId);
        return ThingPersistenceActor.props(thingId, distributedPub, pubSubMediator);
    }
}
