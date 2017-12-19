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
package org.eclipse.ditto.services.utils.persistence.mongo;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.models.streaming.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.utils.akka.streaming.AbstractStreamingActor;
import org.eclipse.ditto.services.utils.akkapersistence.mongoaddons.DittoJavaDslMongoReadJournal;
import org.eclipse.ditto.services.utils.akkapersistence.mongoaddons.DittoMongoReadJournal;
import org.eclipse.ditto.services.utils.akkapersistence.mongoaddons.PidWithSeqNr;

import akka.NotUsed;
import akka.persistence.query.PersistenceQuery;
import akka.stream.javadsl.Source;

/**
 * Abstract implementation of an Actor that streams information about persisted entities modified in a time window in
 * the past.
 */
public abstract class AbstractPersistenceStreamingActor<T extends EntityIdWithRevision>
        extends AbstractStreamingActor<SudoStreamModifiedEntities, T> {

    private final int streamingCacheSize;
    private final Function<PidWithSeqNr, T> entityMapper;
    private final DittoJavaDslMongoReadJournal readJournal;

    /**
     * Constructor.
     *
     * @param streamingCacheSize the size of the streaming cache.
     * @param entityMapper the mapper used to map {@link PidWithSeqNr} to {@code T}. The resulting entity will be
     * streamed to the recipient actor.
     * @param readJournal the journal to query for entities modified in a time window in the past. If {@code null},
     * the read journal will be retrieved from the actor context.
     */
    protected AbstractPersistenceStreamingActor(final int streamingCacheSize,
            final Function<PidWithSeqNr, T> entityMapper, @Nullable final DittoJavaDslMongoReadJournal readJournal) {
        this.streamingCacheSize = streamingCacheSize;
        this.entityMapper = requireNonNull(entityMapper);
        this.readJournal = readJournal != null ? readJournal : PersistenceQuery.get(getContext().getSystem())
                .getReadJournalFor(DittoJavaDslMongoReadJournal.class, DittoMongoReadJournal.Identifier());
    }

    @Override
    protected final Class<SudoStreamModifiedEntities> getCommandClass() {
        return SudoStreamModifiedEntities.class;
    }

    @Override
    protected int getRate(final SudoStreamModifiedEntities command) {
        return command.getRate();
    }

    @Override
    protected final Source<T, NotUsed> createSource(final SudoStreamModifiedEntities command) {
        final String actorName = getSelf().path().name();
        final String unfilteredStreamingLogName = actorName + "unfiltered-streaming";
        final String filteredStreamingLogName = actorName + "filtered-streaming";

        // create a separate cache per stream (don't use member variable!)
        final ComparableCache<String, Long> cache = new ComparableCache<>(streamingCacheSize);
        return readJournal.sequenceNumbersOfPidsByInterval(command.getStart(), command.getEnd())
                .log(unfilteredStreamingLogName, log)
                // avoid unnecessary streaming of old sequence numbers
                .filter(pidWithSeqNr -> cache.updateIfNewOrGreater(pidWithSeqNr.persistenceId(),
                        pidWithSeqNr.sequenceNr()))
                .map(this::mapEntity)
                .log(filteredStreamingLogName, log);
    }

    private T mapEntity(final PidWithSeqNr pidWithSeqNr) {
        return entityMapper.apply(pidWithSeqNr) ;
    }
}
