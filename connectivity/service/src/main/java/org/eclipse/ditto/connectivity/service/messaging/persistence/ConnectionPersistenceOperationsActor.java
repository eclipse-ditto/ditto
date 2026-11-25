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

import java.io.Closeable;

import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsCollaborators;
import org.eclipse.ditto.internal.utils.persistence.api.operations.EntityPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.operations.AbstractPersistenceOperationsActor;
import org.eclipse.ditto.internal.utils.persistence.operations.PersistenceOperationsConfig;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;

/**
 * Ops for the event-sourcing persistence of things.
 */
public final class ConnectionPersistenceOperationsActor extends AbstractPersistenceOperationsActor {

    public static final String ACTOR_NAME = "connectionOps";

    private ConnectionPersistenceOperationsActor(final ActorRef pubSubMediator,
            final EntityPersistenceOperations entitiesOps,
            final Closeable toCloseWhenStopped,
            final PersistenceOperationsConfig persistenceOperationsConfig) {

        super(pubSubMediator,
                ConnectivityConstants.ENTITY_TYPE,
                null,
                entitiesOps,
                persistenceOperationsConfig,
                toCloseWhenStopped);
    }

    /**
     * Create Props of this actor.
     *
     * @param pubSubMediator Pekko pub-sub mediator.
     * @param provider the active persistence-backend provider yielding the backend-neutral ops collaborators.
     * @param persistenceOperationsConfig the persistence operations configuration settings.
     * @return a Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final PersistenceBackendProvider provider,
            final PersistenceOperationsConfig persistenceOperationsConfig) {

        return Props.create(ConnectionPersistenceOperationsActor.class, () -> {
            // Build the backend client + collaborators lazily, at actor instantiation time (one client per actor).
            final PersistenceOperationsCollaborators collaborators =
                    provider.operations(ConnectivityConstants.ENTITY_TYPE.toString());

            return new ConnectionPersistenceOperationsActor(pubSubMediator, collaborators.entitiesOps(),
                    collaborators.closeable(), persistenceOperationsConfig);
        });
    }

    @Override
    public String getActorName() {
        return ACTOR_NAME;
    }

}
