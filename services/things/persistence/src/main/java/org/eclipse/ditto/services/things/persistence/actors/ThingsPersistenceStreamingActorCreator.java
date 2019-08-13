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
package org.eclipse.ditto.services.things.persistence.actors;

import java.util.regex.Pattern;

import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.utils.persistence.mongo.DefaultPersistenceStreamingActor;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.PidWithSeqNr;

import akka.actor.Props;


/**
 * Creates an actor which streams information about persisted things.
 */
public final class ThingsPersistenceStreamingActorCreator {

    /**
     * The name of the created Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "persistenceStreamingActor";

    private static final Pattern PERSISTENCE_ID_PATTERN = Pattern.compile(ThingPersistenceActor.PERSISTENCE_ID_PREFIX);

    private ThingsPersistenceStreamingActorCreator() {
        throw new AssertionError();
    }

    /**
     * Creates Akka configuration object Props for this PersistenceQueriesActor.
     *
     * @param streamingCacheSize the size of the streaming cache.
     * @return the Akka configuration Props object.
     */
    public static Props props(final int streamingCacheSize) {
        return DefaultPersistenceStreamingActor.props(ThingTag.class,
                streamingCacheSize,
                ThingsPersistenceStreamingActorCreator::createElement,
                ThingsPersistenceStreamingActorCreator::createPidWithSeqNr);
    }

    private static ThingTag createElement(final PidWithSeqNr pidWithSeqNr) {
        final String id = PERSISTENCE_ID_PATTERN.matcher(pidWithSeqNr.getPersistenceId()).replaceFirst("");
        final ThingId thingId = ThingId.of(id);
        return ThingTag.of(thingId, pidWithSeqNr.getSequenceNr());
    }

    private static PidWithSeqNr createPidWithSeqNr(final EntityIdWithRevision connectionTag) {
        return new PidWithSeqNr(ThingPersistenceActor.PERSISTENCE_ID_PREFIX + connectionTag.getEntityId(),
                connectionTag.getRevision());
    }

}
