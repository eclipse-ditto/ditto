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

import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource.MongoEventSourceSettings;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource.MongoNamespacePersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.operations.AbstractPersistenceOperationsActor;
import org.eclipse.ditto.internal.utils.persistence.operations.NamespacePersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.operations.PersistenceOperationsConfig;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Ops for the event-sourcing persistence of things.
 */
@AllValuesAreNonnullByDefault
public final class ThingPersistenceOperationsActor extends AbstractPersistenceOperationsActor {

    public static final String ACTOR_NAME = "thingOps";

    private ThingPersistenceOperationsActor(final ActorRef pubSubMediator,
            final NamespacePersistenceOperations namespaceOps,
            final MongoClientWrapper mongoClientWrapper,
            final PersistenceOperationsConfig persistenceOperationsConfig) {

        super(pubSubMediator,
                ThingConstants.ENTITY_TYPE,
                namespaceOps,
                null,
                persistenceOperationsConfig,
                mongoClientWrapper);
    }

    /**
     * Create Props of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param mongoDbConfig the MongoDB configuration settings.
     * @param config Configuration with info about event journal, snapshot store and database.
     * @param persistenceOperationsConfig the persistence operations config.
     * @return a Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final MongoDbConfig mongoDbConfig,
            final Config config,
            final PersistenceOperationsConfig persistenceOperationsConfig) {

        return Props.create(ThingPersistenceOperationsActor.class, () -> {
            final MongoEventSourceSettings eventSourceSettings =
                    MongoEventSourceSettings.fromConfig(config, ThingPersistenceActor.PERSISTENCE_ID_PREFIX,
                            true, ThingPersistenceActor.JOURNAL_PLUGIN_ID,
                            ThingPersistenceActor.SNAPSHOT_PLUGIN_ID);

            final MongoClientWrapper mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
            final MongoDatabase db = mongoClient.getDefaultDatabase();

            final NamespacePersistenceOperations namespaceOps =
                    MongoNamespacePersistenceOperations.of(db, eventSourceSettings);

            return new ThingPersistenceOperationsActor(pubSubMediator, namespaceOps, mongoClient,
                    persistenceOperationsConfig);
        });
    }

    @Override
    public String getActorName() {
        return ACTOR_NAME;
    }

}
