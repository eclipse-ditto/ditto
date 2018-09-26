/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.starter;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Factory for creating Props of {@link ThingPersistenceActor}.
 */
@Immutable
final class ThingPersistenceActorPropsFactory implements Function<String, Props> {

    private final ActorRef pubSubMediator;
    private final ThingSnapshotter.Create thingSnapshotterCreate;

    private ThingPersistenceActorPropsFactory(final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        this.pubSubMediator = checkNotNull(pubSubMediator);
        this.thingSnapshotterCreate = checkNotNull(thingSnapshotterCreate);
    }

    /**
     * Returns an instance of {@code ThingPersistenceActorPropsFactory}.
     *
     * @param pubSubMediator ActorRef of the distributed pub-sub-mediator.
     * @param thingSnapshotterCreate functional interface for the constructor of snapshotter classes.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ThingPersistenceActorPropsFactory getInstance(final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        return new ThingPersistenceActorPropsFactory(pubSubMediator, thingSnapshotterCreate);
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
    public Props apply(final String thingId) {
        argumentNotEmpty(thingId, "thing ID");

        return ThingPersistenceActor.props(thingId, pubSubMediator, thingSnapshotterCreate);
    }

}
