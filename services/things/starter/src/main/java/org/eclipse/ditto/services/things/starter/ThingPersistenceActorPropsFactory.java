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
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Factory for creating Props of {@link ThingPersistenceActor}.
 */
@Immutable
final class ThingPersistenceActorPropsFactory implements Function<ThingId, Props> {

    private final ActorRef pubSubMediator;

    private ThingPersistenceActorPropsFactory(final ActorRef pubSubMediator) {

        this.pubSubMediator = checkNotNull(pubSubMediator);
    }

    /**
     * Returns an instance of {@code ThingPersistenceActorPropsFactory}.
     *
     * @param pubSubMediator ActorRef of the distributed pub-sub-mediator.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ThingPersistenceActorPropsFactory getInstance(final ActorRef pubSubMediator) {

        return new ThingPersistenceActorPropsFactory(pubSubMediator);
    }

    /**
     * Returns the Props of the {@link ThingPersistenceActor} which manages a thing with the specified ID.
     *
     * @param thingId identifier of the thing the ThingPersistenceActor manages.
     * @return the Props.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} is empty.
     */
    @Override
    public Props apply(final ThingId thingId) {
        argumentNotEmpty(thingId, "thing ID");

        return ThingPersistenceActor.props(thingId, pubSubMediator);
    }

}
