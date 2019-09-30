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

import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoEntitiesPersistenceOperations;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoEventSourceSettings;
import org.eclipse.ditto.services.utils.persistence.operations.AbstractPersistenceOperationsActor;
import org.eclipse.ditto.services.utils.persistence.operations.EntityPersistenceOperations;
import org.eclipse.ditto.services.utils.persistence.operations.PersistenceOperationsConfig;
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
            final PersistenceOperationsConfig persistenceOperationsConfig) {

        super(pubSubMediator,
                ConnectivityCommand.RESOURCE_TYPE,
                null,
                entitiesOps,
                persistenceOperationsConfig,
                mongoClientWrapper);
    }

    /**
     * Create Props of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param mongoDbConfig the MongoDB configuration settings.
     * @param config configuration with info about event journal, snapshot store, suffix-builder and database.
     * @param persistenceOperationsConfig the persistence operations configuration settings.
     * @return a Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final MongoDbConfig mongoDbConfig,
            final Config config,
            final PersistenceOperationsConfig persistenceOperationsConfig) {

        return Props.create(ConnectionPersistenceOperationsActor.class, () -> {
            final MongoEventSourceSettings eventSourceSettings =
                    MongoEventSourceSettings.fromConfig(config, ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX, false,
                            ConnectionPersistenceActor.JOURNAL_PLUGIN_ID, ConnectionPersistenceActor.SNAPSHOT_PLUGIN_ID);

            final MongoClientWrapper mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
            final MongoDatabase db = mongoClient.getDefaultDatabase();

            final EntityPersistenceOperations entitiesOps =
                    MongoEntitiesPersistenceOperations.of(db, eventSourceSettings);

            return new ConnectionPersistenceOperationsActor(pubSubMediator, entitiesOps, mongoClient,
                    persistenceOperationsConfig);
        });
    }

}
