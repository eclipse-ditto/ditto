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
package org.eclipse.ditto.things.service.persistence.actors;

import static org.eclipse.ditto.things.api.ThingsMessagingConstants.THINGS_PERSISTENCE_STREAMING_ACTOR_NAME;

import java.util.function.BiFunction;
import java.util.regex.Pattern;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.internal.utils.persistence.mongo.SnapshotStreamingActor;
import org.eclipse.ditto.things.model.ThingId;

import akka.actor.ActorRef;
import akka.actor.Props;


/**
 * Creates an actor which streams information about persisted things.
 */
public final class ThingsPersistenceStreamingActorCreator {

    /**
     * The name of the streaming actor.
     */
    public static final String STREAMING_ACTOR_NAME = THINGS_PERSISTENCE_STREAMING_ACTOR_NAME;

    private static final Pattern PERSISTENCE_ID_PATTERN = Pattern.compile(ThingPersistenceActor.PERSISTENCE_ID_PREFIX);

    private ThingsPersistenceStreamingActorCreator() {
        throw new AssertionError();
    }

    /**
     * Create an actor that streams from the snapshot store and the event journal.
     *
     * @param actorCreator function to create a named actor with.
     * @return a reference of the created actor.
     */
    public static ActorRef startPersistenceStreamingActor(final BiFunction<String, Props, ActorRef> actorCreator) {
        final var props = SnapshotStreamingActor.props(ThingsPersistenceStreamingActorCreator::pid2EntityId,
                ThingsPersistenceStreamingActorCreator::entityId2Pid);

        return actorCreator.apply(STREAMING_ACTOR_NAME, props);
    }

    private static ThingId pid2EntityId(final String pid) {
        final String id = PERSISTENCE_ID_PATTERN.matcher(pid).replaceFirst("");
        return ThingId.of(id);
    }

    private static String entityId2Pid(final EntityId entityId) {
        return ThingPersistenceActor.PERSISTENCE_ID_PREFIX + entityId;
    }

}
