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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.models.connectivity.ConnectionTag;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.utils.persistence.mongo.DefaultPersistenceStreamingActor;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.PidWithSeqNr;

import akka.actor.Props;


/**
 * Creates an actor which streams information about persisted connections.
 */
public final class ConnectionPersistenceStreamingActorCreator {

    /**
     * The name of the created Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "persistenceStreamingActor";

    private ConnectionPersistenceStreamingActorCreator() {
        throw new AssertionError();
    }

    /**
     * Creates Akka configuration object Props for this PersistenceQueriesActor.
     *
     * @param streamingCacheSize the size of the streaming cache.
     * @return the Akka configuration Props object.
     */
    public static Props props(final int streamingCacheSize) {
        return DefaultPersistenceStreamingActor.props(ConnectionTag.class, streamingCacheSize,
                ConnectionPersistenceStreamingActorCreator::createElement,
                ConnectionPersistenceStreamingActorCreator::createPidWithSeqNr);
    }

    private static ConnectionTag createElement(final PidWithSeqNr pidWithSeqNr) {
        final String id = pidWithSeqNr.getPersistenceId()
                .replaceFirst(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX, "");
        return ConnectionTag.of(ConnectionId.of(id), pidWithSeqNr.getSequenceNr());
    }

    private static PidWithSeqNr createPidWithSeqNr(final EntityIdWithRevision connectionTag) {
        return new PidWithSeqNr(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + connectionTag.getEntityId(),
                connectionTag.getRevision());
    }
}
