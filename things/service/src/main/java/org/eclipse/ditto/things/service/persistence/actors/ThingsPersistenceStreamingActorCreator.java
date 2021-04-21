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

import java.util.function.BiFunction;
import java.util.regex.Pattern;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.things.api.ThingTag;
import org.eclipse.ditto.services.utils.persistence.mongo.DefaultPersistenceStreamingActor;
import org.eclipse.ditto.services.utils.persistence.mongo.SnapshotStreamingActor;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.PidWithSeqNr;

import akka.actor.ActorRef;
import akka.actor.Props;


/**
 * Creates an actor which streams information about persisted things.
 */
public final class ThingsPersistenceStreamingActorCreator {

    /**
     * The name of the event streaming actor. Must agree with
     * {@link org.eclipse.ditto.things.api.ThingsMessagingConstants#THINGS_STREAM_PROVIDER_ACTOR_PATH}.
     */
    public static final String EVENT_STREAMING_ACTOR_NAME = "persistenceStreamingActor";

    /**
     * The name of the snapshot streaming actor. Must agree with
     * {@link org.eclipse.ditto.things.api.ThingsMessagingConstants#THINGS_SNAPSHOT_STREAMING_ACTOR_PATH}.
     */
    public static final String SNAPSHOT_STREAMING_ACTOR_NAME = "snapshotStreamingActor";

    private static final Pattern PERSISTENCE_ID_PATTERN = Pattern.compile(ThingPersistenceActor.PERSISTENCE_ID_PREFIX);

    private ThingsPersistenceStreamingActorCreator() {
        throw new AssertionError();
    }

    /**
     * Create an actor for streaming from the event journal.
     *
     * @param streamingCacheSize the size of the streaming cache.
     * @param actorCreator function to create a named actor with.
     * @return a reference of the created actor.
     */
    public static ActorRef startEventStreamingActor(final int streamingCacheSize,
            final BiFunction<String, Props, ActorRef> actorCreator) {
        final Props props = DefaultPersistenceStreamingActor.props(ThingTag.class,
                ThingsPersistenceStreamingActorCreator::createElement,
                ThingsPersistenceStreamingActorCreator::createPidWithSeqNr);
        return actorCreator.apply(EVENT_STREAMING_ACTOR_NAME, props);
    }

    /**
     * Create an actor that streams from the snapshot store.
     *
     * @param actorCreator function to create a named actor with.
     * @return a reference of the created actor.
     */
    public static ActorRef startSnapshotStreamingActor(final BiFunction<String, Props, ActorRef> actorCreator) {
        final Props props = SnapshotStreamingActor.props(ThingsPersistenceStreamingActorCreator::pid2EntityId,
                ThingsPersistenceStreamingActorCreator::entityId2Pid);
        return actorCreator.apply(SNAPSHOT_STREAMING_ACTOR_NAME, props);
    }

    private static ThingTag createElement(final PidWithSeqNr pidWithSeqNr) {
        return ThingTag.of(pid2EntityId(pidWithSeqNr.getPersistenceId()), pidWithSeqNr.getSequenceNr());
    }

    private static PidWithSeqNr createPidWithSeqNr(final EntityIdWithRevision<?> thingTag) {
        return new PidWithSeqNr(entityId2Pid(thingTag.getEntityId()), thingTag.getRevision());
    }

    private static ThingId pid2EntityId(final String pid) {
        final String id = PERSISTENCE_ID_PATTERN.matcher(pid).replaceFirst("");
        return ThingId.of(id);
    }

    private static String entityId2Pid(final EntityId entityId) {
        return ThingPersistenceActor.PERSISTENCE_ID_PREFIX + entityId;
    }

}
