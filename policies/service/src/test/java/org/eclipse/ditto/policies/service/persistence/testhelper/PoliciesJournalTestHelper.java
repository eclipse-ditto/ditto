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
package org.eclipse.ditto.policies.service.persistence.testhelper;

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

import org.bson.BsonDocument;
import org.eclipse.ditto.policies.model.PolicyId;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.inmemory.query.javadsl.InMemoryReadJournal;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.persistence.query.PersistenceQuery;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

/**
 * Helper class which provides functionality for testing with Pekko persistence journal for the policies services.
 * Requires pekko-persistence-inmemory (by com.github.alstanchev).
 *
 * @param <J> the domain specific datatype stored in the journal
 */
public final class PoliciesJournalTestHelper<J> {

    private static final int WAIT_TIMEOUT = 3;
    private final Function<PolicyId, String> domainIdToPersistenceId;
    private final BiFunction<BsonDocument, Long, J> journalEntryToDomainObject;
    private final InMemoryReadJournal readJournal;
    private final ActorSystem actorSystem;

    /**
     * Constructor.
     *
     * @param actorSystem the actor system to be used to find the persistence extension
     * @param journalEntryToDomainObject a {@link java.util.function.BiFunction} providing the journal entry and its sequence number and
     * expecting a domain object
     * @param domainIdToPersistenceId a {@link java.util.function.Function} providing the domain ID and expecting the matching
     * persistence ID
     */
    public PoliciesJournalTestHelper(final ActorSystem actorSystem,
            final BiFunction<BsonDocument, Long, J> journalEntryToDomainObject, final Function<PolicyId, String>
            domainIdToPersistenceId) {
        this.journalEntryToDomainObject = requireNonNull(journalEntryToDomainObject);
        this.domainIdToPersistenceId = requireNonNull(domainIdToPersistenceId);
        this.actorSystem = actorSystem;

        readJournal = PersistenceQuery.get(actorSystem).
                getReadJournalFor(InMemoryReadJournal.class, InMemoryReadJournal.Identifier());
    }

    /**
     * Gets all events for the given domain ID.
     *
     * @param domainId the domain ID
     * @return the events
     */
    public List<J> getAllEvents(final PolicyId domainId) {
        final String persistenceId = domainIdToPersistenceId.apply(domainId);
        final List<EventEnvelope> eventEnvelopes = getAllEventEnvelopes(persistenceId);
        return Collections.unmodifiableList(
                eventEnvelopes.stream().map(this::convertEventEnvelopeToDomainObject).collect(Collectors.toList()));
    }

    private List<EventEnvelope> getAllEventEnvelopes(final String persistenceId) {
        return runBlockingWithReturn(readJournal.currentEventsByPersistenceId(persistenceId, 0, Long.MAX_VALUE));
    }

    private J convertEventEnvelopeToDomainObject(final EventEnvelope eventEnvelope) {
        final BsonDocument event = (BsonDocument) eventEnvelope.event();
        return journalEntryToDomainObject.apply(event, eventEnvelope.sequenceNr());
    }

    private <T> List<T> runBlockingWithReturn(final Source<T, NotUsed> publisher) {
        final CompletionStage<List<T>> done = publisher.runWith(Sink.seq(), actorSystem);
        try {
            return done.toCompletableFuture().get(WAIT_TIMEOUT, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

}
