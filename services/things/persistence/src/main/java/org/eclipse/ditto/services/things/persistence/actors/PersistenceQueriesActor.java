/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.things.persistence.actors;

import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.utils.persistence.mongo.AbstractPersistenceStreamingActor;

import akka.actor.Props;
import akka.contrib.persistence.mongodb.DittoJavaDslMongoReadJournal;
import akka.contrib.persistence.mongodb.DittoMongoReadJournal;
import akka.japi.Creator;
import akka.persistence.query.PersistenceQuery;


/**
 * Actor which executes special persistence queries on the things event store.
 */
public final class PersistenceQueriesActor extends AbstractPersistenceStreamingActor<ThingTag> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "persistenceQueries";

    private final DittoJavaDslMongoReadJournal readJournal = PersistenceQuery.get(getContext().getSystem())
            .getReadJournalFor(DittoJavaDslMongoReadJournal.class, DittoMongoReadJournal.Identifier());

    private PersistenceQueriesActor(final int streamingCacheSize) {
        super(streamingCacheSize);
    }

    /**
     * Creates Akka configuration object Props for this PersistenceQueriesActor.
     *
     * @param streamingCacheSize the size of the streaming cache.
     * @return the Akka configuration Props object.
     */
    public static Props props(final int streamingCacheSize) {
        return Props.create(PersistenceQueriesActor.class, new Creator<PersistenceQueriesActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public PersistenceQueriesActor create() throws Exception {
                return new PersistenceQueriesActor(streamingCacheSize);
            }
        });
    }

    @Override
    protected DittoJavaDslMongoReadJournal getJournal() {
        return readJournal;
    }

    @Override
    protected ThingTag createElement(final String pid, final long sequenceNumber) {
        return ThingTag.of(pid.replaceFirst(ThingPersistenceActor.PERSISTENCE_ID_PREFIX, ""), sequenceNumber);
    }
}
