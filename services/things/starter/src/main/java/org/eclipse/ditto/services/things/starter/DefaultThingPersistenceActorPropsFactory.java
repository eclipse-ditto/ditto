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
package org.eclipse.ditto.services.things.starter;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActorPropsFactory;
import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.Props;

/**
 * Factory for creating Props of {@link ThingPersistenceActor}.
 */
@Immutable
final class DefaultThingPersistenceActorPropsFactory implements ThingPersistenceActorPropsFactory {

    private DefaultThingPersistenceActorPropsFactory() {}

    /**
     * Returns an instance of {@code ThingPersistenceActorPropsFactory}.
     *
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static DefaultThingPersistenceActorPropsFactory getInstance() {
        return new DefaultThingPersistenceActorPropsFactory();
    }

    @Override
    public Props props(final ThingId thingId, final DistributedPub<ThingEvent> distributedPub) {
        argumentNotEmpty(thingId);
        return ThingPersistenceActor.props(thingId, distributedPub);
    }
}
