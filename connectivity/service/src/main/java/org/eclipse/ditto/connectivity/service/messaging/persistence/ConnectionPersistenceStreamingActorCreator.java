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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import org.eclipse.ditto.connectivity.api.ConnectionTag;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.DefaultPersistenceStreamingActor;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.NoOpCloseable;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.PidWithSeqNr;

import org.apache.pekko.actor.Props;


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
     * Creates Pekko configuration object Props for this PersistenceQueriesActor.
     * <p>
     * The backend-neutral {@link DefaultPersistenceStreamingActor} is wired with the active backend's shared read
     * journal; it owns no backend resource, so it is given a {@link NoOpCloseable}.
     *
     * @param readJournal the active backend's read journal.
     * @return the Pekko configuration Props object.
     */
    public static Props props(final DittoReadJournal readJournal) {
        return DefaultPersistenceStreamingActor.props(ConnectionTag.class,
                ConnectionPersistenceStreamingActorCreator::createElement,
                ConnectionPersistenceStreamingActorCreator::createPidWithSeqNr,
                readJournal,
                NoOpCloseable.getInstance());
    }

    private static ConnectionTag createElement(final PidWithSeqNr pidWithSeqNr) {
        final String id = pidWithSeqNr.getPersistenceId()
                .replaceFirst(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX, "");
        return ConnectionTag.of(ConnectionId.of(id), pidWithSeqNr.getSequenceNr());
    }

    private static PidWithSeqNr createPidWithSeqNr(final EntityIdWithRevision<?> connectionTag) {
        return new PidWithSeqNr(ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + connectionTag.getEntityId(),
                connectionTag.getRevision());
    }
}
