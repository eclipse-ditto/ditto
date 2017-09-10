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
package org.eclipse.ditto.services.things.persistence.testhelper;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mongodb.DBObject;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.persistence.inmemory.query.javadsl.InMemoryReadJournal;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.PersistenceQuery;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Helper class which provides functionality for testing with Akka persistence journal for the things services.
 * Requires akka-persistence-inmemory (by com.github.dnvriend).
 *
 * @param <J> the domain specific datatype stored in the journal
 */
public final class ThingsJournalTestHelper<J> {

    private static final int WAIT_TIMEOUT = 3;
    private final Function<String, String> domainIdToPersistenceId;
    private final BiFunction<DBObject, Long, J> journalEntryToDomainObject;
    private final ActorMaterializer mat;
    private final InMemoryReadJournal readJournal;

    /**
     * Constructor.
     *
     * @param actorSystem the actor system to be used to find the persistence extension
     * @param journalEntryToDomainObject a {@link BiFunction} providing the journal entry and its sequence number and
     * expecting a domain object
     * @param domainIdToPersistenceId a {@link Function} providing the domain ID and expecting the matching
     * persistence ID
     */
    public ThingsJournalTestHelper(final ActorSystem actorSystem,
            final BiFunction<DBObject, Long, J> journalEntryToDomainObject, final Function<String, String>
            domainIdToPersistenceId) {
        this.journalEntryToDomainObject = requireNonNull(journalEntryToDomainObject);
        this.domainIdToPersistenceId = requireNonNull(domainIdToPersistenceId);
        mat = ActorMaterializer.create(actorSystem);

        readJournal = PersistenceQuery.get(actorSystem).
                getReadJournalFor(InMemoryReadJournal.class, InMemoryReadJournal.Identifier());
    }

    /**
     * Gets all events for the given domain ID.
     *
     * @param domainId the domain ID
     * @return the events
     */
    public List<J> getAllEvents(final String domainId) {
        final String persistenceId = domainIdToPersistenceId.apply(domainId);
        final List<EventEnvelope> eventEnvelopes = getAllEventEnvelopes(persistenceId);
        return Collections.unmodifiableList(
                eventEnvelopes.stream().map(this::convertEventEnvelopeToDomainObject).collect(Collectors.toList()));
    }

    private List<EventEnvelope> getAllEventEnvelopes(final String persistenceId) {
        return runBlockingWithReturn(readJournal.currentEventsByPersistenceId(persistenceId, 0, Long.MAX_VALUE));
    }

    private J convertEventEnvelopeToDomainObject(final EventEnvelope eventEnvelope) {
        final DBObject event = (DBObject) eventEnvelope.event();
        return journalEntryToDomainObject.apply(event, eventEnvelope.sequenceNr());
    }

    private <T> List<T> runBlockingWithReturn(final Source<T, NotUsed> publisher) {
        final CompletionStage<List<T>> done = publisher.runWith(Sink.seq(), mat);
        try {
            return done.toCompletableFuture().get(WAIT_TIMEOUT, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

}
