/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.things.persistence.actors;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.snapshotting.DittoThingSnapshotter;

import akka.persistence.PersistentActor;
import scala.Function1;
import scala.collection.immutable.Seq;
import scala.runtime.BoxedUnit;

/**
 * Interface for {@link ThingPersistenceActor}. Mainly used in strategy groups such as {@link DittoThingSnapshotter} so
 * that they can be tested with Mockito.
 */
public interface ThingPersistenceActorInterface extends PersistentActor {

    /**
     * @return The state of the {@code ThingPersistenceActor} as a Thing.
     */
    @Nonnull
    Thing getThing();

    /**
     * @return The ID of the {@code ThingPersistenceActor}.
     */
    @Nonnull String getThingId();

    /**
     * @return whether the Thing is in state "deleted".
     */
    boolean isThingDeleted();

    @Override
    default <A> void deferAsync(final A event, final Function1<A, BoxedUnit> handler) {
        internalDeferAsync(event, handler);
    }

    @Override
    default <A> void persist(final A event, final Function1<A, BoxedUnit> handler) {
        internalPersist(event, handler);
    }

    @Override
    default <A> void persistAsync(final A event, final Function1<A, BoxedUnit> handler) {
        internalPersistAsync(event, handler);
    }

    @Override
    default <A> void persistAll(final Seq<A> events, final Function1<A, BoxedUnit> handler) {
        internalPersistAll(events, handler);
    }

    @Override
    default <A> void persistAllAsync(final Seq<A> events, final Function1<A, BoxedUnit> handler) {
        internalPersistAllAsync(events, handler);
    }
}
