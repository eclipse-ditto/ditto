/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors;

import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.persistence.mongo.namespace.AbstractEventSourceNamespaceOpsActor;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Namespace operations for the event-sourcing persistence of things.
 */
@AllValuesAreNonnullByDefault
public final class ThingNamespaceOpsActor extends AbstractEventSourceNamespaceOpsActor {

    public static final String ACTOR_NAME = "thingNamespaceOps";

    private ThingNamespaceOpsActor(final ActorRef pubSubMediator, final MongoDatabase db, final Config config) {
        super(pubSubMediator, db, config);
    }

    /**
     * Create Props of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param config Configuration with info about event journal, snapshot store, suffix-builder and database.
     * @return a Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final Config config) {
        final MongoDatabase db = MongoClientWrapper.newInstance(config).getDatabase();
        return Props.create(ThingNamespaceOpsActor.class, () -> new ThingNamespaceOpsActor(pubSubMediator, db, config));
    }

    @Override
    protected String persistenceIdPrefix() {
        return ThingPersistenceActor.PERSISTENCE_ID_PREFIX;
    }

    @Override
    protected String journalPluginId() {
        return ThingPersistenceActor.JOURNAL_PLUGIN_ID;
    }

    @Override
    protected String snapshotPluginId() {
        return ThingPersistenceActor.SNAPSHOT_PLUGIN_ID;
    }

    @Override
    protected String resourceType() {
        return ThingCommand.RESOURCE_TYPE;
    }
}
