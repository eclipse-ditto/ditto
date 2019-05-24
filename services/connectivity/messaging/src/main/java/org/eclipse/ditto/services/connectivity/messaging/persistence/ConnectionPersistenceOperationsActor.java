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

import java.util.Collections;

import org.eclipse.ditto.services.connectivity.messaging.ConnectionActor;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.AbstractPersistenceOperationsActor;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.EntityPersistenceOperations;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.PersistenceOperationsConfiguration;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoEntitiesPersistenceOperations;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoEventSourceSettings;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;

import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Ops for the event-sourcing persistence of things.
 */
public final class ConnectionPersistenceOperationsActor extends AbstractPersistenceOperationsActor {

    public static final String ACTOR_NAME = "connectionOps";

    private ConnectionPersistenceOperationsActor(final ActorRef pubSubMediator,
            final EntityPersistenceOperations entitiesOps,
            final MongoClientWrapper mongoClientWrapper,
            final PersistenceOperationsConfiguration persistenceOperationsConfiguration) {

        super(
                pubSubMediator,
                ConnectivityCommand.RESOURCE_TYPE,
                null,
                entitiesOps,
                Collections.singleton(mongoClientWrapper),
                persistenceOperationsConfiguration
        );
    }

    /**
     * Create Props of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param config Configuration with info about event journal, snapshot store, suffix-builder and database.
     * @return a Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final Config config) {
        return Props.create(ConnectionPersistenceOperationsActor.class, () -> {
            final MongoEventSourceSettings eventSourceSettings =
                    MongoEventSourceSettings.fromConfig(config, ConnectionActor.PERSISTENCE_ID_PREFIX, false,
                            ConnectionActor.JOURNAL_PLUGIN_ID, ConnectionActor.SNAPSHOT_PLUGIN_ID);

            final MongoClientWrapper mongoClient = MongoClientWrapper.newInstance(config);
            final MongoDatabase db = mongoClient.getDefaultDatabase();

            final EntityPersistenceOperations entitiesOps = MongoEntitiesPersistenceOperations.of(db, eventSourceSettings);
            final PersistenceOperationsConfiguration persistenceOperationsConfiguration =
                    PersistenceOperationsConfiguration.fromConfig(config);

            return new ConnectionPersistenceOperationsActor(
                    pubSubMediator,
                    entitiesOps,
                    mongoClient,
                    persistenceOperationsConfiguration
            );
        });
    }

}
