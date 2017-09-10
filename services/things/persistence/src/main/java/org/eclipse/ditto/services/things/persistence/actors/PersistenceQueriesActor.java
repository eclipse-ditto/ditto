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

import java.util.ArrayList;

import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveModifiedThingTags;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveModifiedThingTagsResponse;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.contrib.persistence.mongodb.DittoJavaDslMongoReadJournal;
import akka.contrib.persistence.mongodb.DittoMongoReadJournal;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.query.PersistenceQuery;
import akka.stream.ActorMaterializer;


/**
 * Actor which executes special persistence queries on the things event store.
 */
public final class PersistenceQueriesActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "persistenceQueries";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    
    private final ActorMaterializer materializer;
    private final DittoJavaDslMongoReadJournal readJournal;

    private PersistenceQueriesActor() {
        materializer = ActorMaterializer.create(getContext());
        readJournal = PersistenceQuery.get(getContext().getSystem())
                .getReadJournalFor(DittoJavaDslMongoReadJournal.class, DittoMongoReadJournal.Identifier());
    }

    /**
     * Creates Akka configuration object Props for this PersistenceQueriesActor.
     *
     * @return the Akka configuration Props object.
     */
    public static Props props() {
        return Props.create(PersistenceQueriesActor.class, new Creator<PersistenceQueriesActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public PersistenceQueriesActor create() throws Exception {
                return new PersistenceQueriesActor();
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(SudoRetrieveModifiedThingTags.class, command -> {
                    log.debug("Got 'SudoRetrieveModifiedThingTags' message");
                    final ActorRef sender = getSender();
                    readJournal.sequenceNumbersOfPidsByDuration(command.getTimespan(), command.getOffset())
                            .runFold(new ArrayList<ThingTag>(), (list, pidWithSeqNr) -> {
                                list.add(ThingTag
                                        .of(pidWithSeqNr.persistenceId()
                                                        .replaceFirst(ThingPersistenceActor.PERSISTENCE_ID_PREFIX, ""),
                                                pidWithSeqNr.sequenceNr()));
                                return list;
                            }, materializer).thenAccept(
                            list -> sender.tell(
                                    SudoRetrieveModifiedThingTagsResponse.of(list, command.getDittoHeaders()), null));
                })
                .matchAny(m -> log.warning("Got unknown message, expected a 'Command': {}", m))
                .build();
    }
}
