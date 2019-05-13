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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import java.util.Collections;

import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.AbstractPersistenceOperationsActor;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.EntityPersistenceOperations;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.NamespacePersistenceOperations;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoEntitiesPersistenceOperations;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoEventSourceSettings;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoNamespacePersistenceOperations;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Ops for the event-sourcing persistence of policies.
 */
@AllValuesAreNonnullByDefault
public final class PolicyPersistenceOperationsActor extends AbstractPersistenceOperationsActor {

    public static final String ACTOR_NAME = "policyOps";

    private PolicyPersistenceOperationsActor(final ActorRef pubSubMediator, final NamespacePersistenceOperations namespaceOps,
            final EntityPersistenceOperations entitiesOps, final MongoClientWrapper mongoClient) {
        super(pubSubMediator, PolicyCommand.RESOURCE_TYPE, namespaceOps, entitiesOps,
                Collections.singleton(mongoClient));
    }

    /**
     * Create Props of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param config Configuration with info about event journal, snapshot store, suffix-builder and database.
     * @return a Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final Config config) {
        return Props.create(PolicyPersistenceOperationsActor.class, () -> {
            final MongoEventSourceSettings eventSourceSettings =
                    MongoEventSourceSettings.fromConfig(config, PolicyPersistenceActor.PERSISTENCE_ID_PREFIX, true,
                            PolicyPersistenceActor.JOURNAL_PLUGIN_ID, PolicyPersistenceActor.SNAPSHOT_PLUGIN_ID);

            final MongoClientWrapper mongoClient = MongoClientWrapper.newInstance(config);
            final MongoDatabase db = mongoClient.getDefaultDatabase();

            final NamespacePersistenceOperations namespaceOps = MongoNamespacePersistenceOperations.of(db, eventSourceSettings);
            final EntityPersistenceOperations entitiesOps = MongoEntitiesPersistenceOperations.of(db, eventSourceSettings);

            return new PolicyPersistenceOperationsActor(pubSubMediator, namespaceOps, entitiesOps, mongoClient);
        });
    }

}
