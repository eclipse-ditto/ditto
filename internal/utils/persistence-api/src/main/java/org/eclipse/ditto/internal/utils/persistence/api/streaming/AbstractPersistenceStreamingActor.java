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
package org.eclipse.ditto.internal.utils.persistence.api.streaming;

import static java.util.Objects.requireNonNull;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.internal.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.internal.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.internal.models.streaming.SudoStreamPids;
import org.eclipse.ditto.internal.utils.pekko.streaming.AbstractStreamingActor;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Abstract implementation of an Actor that streams information about persisted entities modified in a time window in
 * the past.
 * <p>
 * The actor is backend-neutral: it depends only on the {@link DittoReadJournal} abstraction for PID retrieval and on an
 * injected {@link Closeable} resource that it closes on {@code postStop}. Each persistence backend supplies the concrete
 * read journal and the resource to close (MongoDB passes its client; PostgreSQL passes a no-op because its connection
 * pool is owned elsewhere).
 *
 * @param <T> type of the elements.
 */
@AllValuesAreNonnullByDefault
public abstract class AbstractPersistenceStreamingActor<T extends EntityIdWithRevision<?>>
        extends AbstractStreamingActor<SudoStreamPids, T> {

    private final Function<PidWithSeqNr, T> entityMapper;
    private final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper;

    private final DittoReadJournal readJournal;
    private final Closeable resourceToClose;

    /**
     * Constructor.
     *
     * @param entityMapper the mapper used to map {@link PidWithSeqNr} to {@code T}. The resulting entity will be
     * streamed to the recipient actor.
     * @param entityUnmapper the mapper used to map elements back to PidWithSeqNr for stream resumption.
     * @param readJournal the backend's read journal.
     * @param resourceToClose the backend resource to close when the actor stops (a no-op {@code Closeable} when the
     * backend owns its resources elsewhere).
     */
    protected AbstractPersistenceStreamingActor(final Function<PidWithSeqNr, T> entityMapper,
            final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper,
            final DittoReadJournal readJournal,
            final Closeable resourceToClose) {
        this.entityMapper = requireNonNull(entityMapper);
        this.entityUnmapper = entityUnmapper;
        this.readJournal = requireNonNull(readJournal);
        this.resourceToClose = requireNonNull(resourceToClose);
    }

    @Override
    public void postStop() throws Exception {
        resourceToClose.close();
        super.postStop();
    }

    /**
     * Get the class of the elements.
     *
     * @return the class of the elements.
     */
    protected abstract Class<T> getElementClass();

    @Override
    protected final Class<SudoStreamPids> getCommandClass() {
        return SudoStreamPids.class;
    }

    @Override
    protected int getBurst(final SudoStreamPids command) {
        return command.getBurst();
    }

    @Override
    protected Duration getInitialTimeout(final SudoStreamPids command) {
        return Duration.ofMillis(command.getTimeoutMillis());
    }

    @Override
    protected Duration getIdleTimeout(final SudoStreamPids command) {
        return Duration.ofMillis(command.getTimeoutMillis());
    }

    @Override
    protected Object batchMessages(final List<T> elements) {
        return BatchedEntityIdWithRevisions.of(getElementClass(), elements);
    }

    @Override
    protected final Source<T, NotUsed> createSource(final SudoStreamPids command) {
        log.info("Starting stream for <{}>", command);
        final var maxIdleTime = Duration.ofMillis(command.getTimeoutMillis());
        final int batchSize = command.getBurst() * 5;
        final Source<String, NotUsed> pidSource;
        if (command.hasNonEmptyLowerBound()) {
            // resume from lower bound
            final var pidWithSeqNr = entityUnmapper.apply(command.getLowerBound());
            pidSource =
                    readJournal.getJournalPidsAbove(pidWithSeqNr.getPersistenceId(), batchSize, materializer);
        } else {
            // no lower bound; read from event journals with restart-source
            pidSource = readJournal.getJournalPids(batchSize, maxIdleTime, materializer);
        }

        return pidSource.map(pid -> mapEntity(new PidWithSeqNr(pid, 0L))).log("pid-streaming", log);
    }

    private T mapEntity(final PidWithSeqNr pidWithSeqNr) {
        return entityMapper.apply(pidWithSeqNr);
    }
}
