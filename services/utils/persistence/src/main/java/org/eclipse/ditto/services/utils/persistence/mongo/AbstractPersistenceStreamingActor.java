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

import org.eclipse.ditto.services.models.streaming.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.utils.akka.streaming.AbstractStreamingActor;
import org.eclipse.ditto.services.utils.akkapersistence.mongoaddons.DittoJavaDslMongoReadJournal;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Actor that streams information about persisted entities modified in a time window in the past.
 */
public abstract class AbstractPersistenceStreamingActor<T>
        extends AbstractStreamingActor<SudoStreamModifiedEntities, T> {

    private final int streamingCacheSize;

    /**
     * Constructor.
     *
     * @param streamingCacheSize the size of the streaming cache
     */
    protected AbstractPersistenceStreamingActor(final int streamingCacheSize) {
        this.streamingCacheSize = streamingCacheSize;
    }

    /**
     * Returns the journal to query for entities modified in a time window in the past.
     *
     * @return The journal.
     */
    protected abstract DittoJavaDslMongoReadJournal getJournal();

    /**
     * Transforms persistence ID and sequence number into an object to stream to the recipient actor.
     *
     * @param pid The persistence ID.
     * @param sequenceNumber The latest sequence number.
     * @return Element to stream to the recipient actor.
     */
    protected abstract T createElement(final String pid, final long sequenceNumber);

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
        // create a separate cache per stream (don't use member variable!)
        final ComparableCache<String, Long> cache = new ComparableCache<>(streamingCacheSize);
        return getJournal().sequenceNumbersOfPidsByInterval(command.getStart(), command.getEnd())
                .log("unfiltered-streaming", log)
                // avoid unnecessary streaming of old sequence numbers
                .filter(pidWithSeqNr -> cache.updateIfNewOrGreater(pidWithSeqNr.persistenceId(),
                        pidWithSeqNr.sequenceNr()))
                .map(pidWithSeqNr -> createElement(pidWithSeqNr.persistenceId(), pidWithSeqNr.sequenceNr()))
                .log("filtered-streaming", log);
    }

}
