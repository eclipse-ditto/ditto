/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.persistence.actors;

import java.util.Collections;

import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.AbstractOpsActor;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoNamespaceOps;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoOpsSelectionProvider;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.NamespaceOps;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Ops for the event-sourcing persistence of things.
 */
@AllValuesAreNonnullByDefault
public final class ThingOpsActor extends AbstractOpsActor {

    public static final String ACTOR_NAME = "thingOps";

    private ThingOpsActor(final ActorRef pubSubMediator, final NamespaceOps namespaceOps,
            final MongoClientWrapper mongoClientWrapper) {

        super(pubSubMediator, ThingCommand.RESOURCE_TYPE, namespaceOps, null,
                Collections.singleton(mongoClientWrapper));
    }

    /**
     * Create Props of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param config Configuration with info about event journal, snapshot store, suffix-builder and database.
     * @return a Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final Config config) {
        return Props.create(ThingOpsActor.class, () -> {
            final MongoOpsSelectionProvider selectionProvider =
                    MongoOpsSelectionProvider.of(ThingPersistenceActor.PERSISTENCE_ID_PREFIX, true,
                            config, ThingPersistenceActor.JOURNAL_PLUGIN_ID,
                            ThingPersistenceActor.SNAPSHOT_PLUGIN_ID);

            final MongoClientWrapper mongoClient = MongoClientWrapper.newInstance(config);
            final MongoDatabase db = mongoClient.getDefaultDatabase();

            final NamespaceOps namespaceOps = MongoNamespaceOps.of(db, selectionProvider);

            return new ThingOpsActor(pubSubMediator, namespaceOps, mongoClient);
        });
    }

}
