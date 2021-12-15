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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.internal.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.internal.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.internal.models.streaming.SudoStreamPids;
import org.eclipse.ditto.internal.utils.akka.streaming.AbstractStreamingActor;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.PidWithSeqNr;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Abstract implementation of an Actor that streams information about persisted entities modified in a time window in
 * the past.
 *
 * @param <T> type of the elements.
 */
@AllValuesAreNonnullByDefault
public abstract class AbstractPersistenceStreamingActor<T extends EntityIdWithRevision<?>>
        extends AbstractStreamingActor<SudoStreamPids, T> {

    private final Function<PidWithSeqNr, T> entityMapper;
    private final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper;

    private final DittoMongoClient mongoClient;
    private final MongoReadJournal readJournal;

    /**
     * Constructor.
     *
     * @param entityMapper the mapper used to map {@link org.eclipse.ditto.internal.utils.persistence.mongo.streaming.PidWithSeqNr}
     * to {@code T}. The resulting entity will be streamed to the recipient actor.
     * @param entityUnmapper the mapper used to map elements back to PidWithSeqNr for stream resumption.
     */
    protected AbstractPersistenceStreamingActor(final Function<PidWithSeqNr, T> entityMapper,
            final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper) {
        this.entityMapper = requireNonNull(entityMapper);
        this.entityUnmapper = entityUnmapper;

        final var config = getContext().getSystem().settings().config();
        final MongoDbConfig mongoDbConfig =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(config));
        mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
        readJournal = MongoReadJournal.newInstance(config, mongoClient, getContext().getSystem());
    }

    /**
     * Constructor for tests.
     *
     * @param entityMapper the mapper used to map {@link org.eclipse.ditto.internal.utils.persistence.mongo.streaming.PidWithSeqNr}
     * to {@code T}. The resulting entity will be streamed to the recipient actor.
     * @param entityUnmapper the mapper used to map elements back to PidWithSeqNr for stream resumption.
     * @param readJournal the ReadJournal to use instead of creating one in the non-test constructor.
     */
    protected AbstractPersistenceStreamingActor(final Function<PidWithSeqNr, T> entityMapper,
            final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper,
            final MongoReadJournal readJournal) {
        this.entityMapper = requireNonNull(entityMapper);
        this.entityUnmapper = entityUnmapper;

        final var config = getContext().getSystem().settings().config();
        final MongoDbConfig mongoDbConfig =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(config));
        mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
        this.readJournal = readJournal;
    }

    @Override
    public void postStop() throws Exception {
        mongoClient.close();
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
