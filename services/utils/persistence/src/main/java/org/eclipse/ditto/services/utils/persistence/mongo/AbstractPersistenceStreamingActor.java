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
package org.eclipse.ditto.services.utils.persistence.mongo;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.models.streaming.StartStreamRequest;
import org.eclipse.ditto.services.models.streaming.StartStreamRequestVisitor;
import org.eclipse.ditto.services.models.streaming.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.models.streaming.SudoStreamPids;
import org.eclipse.ditto.services.utils.akka.streaming.AbstractStreamingActor;
import org.eclipse.ditto.services.utils.cache.ComparableCache;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.PidWithSeqNr;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Abstract implementation of an Actor that streams information about persisted entities modified in a time window in
 * the past.
 *
 * @param <T> type of the elements.
 */
@AllValuesAreNonnullByDefault
public abstract class AbstractPersistenceStreamingActor<T extends EntityIdWithRevision>
        extends AbstractStreamingActor<StartStreamRequest, T>
        implements StartStreamRequestVisitor<Source<T, NotUsed>> {

    private final int streamingCacheSize;
    private final Function<PidWithSeqNr, T> entityMapper;

    private final DittoMongoClient mongoClient;
    private final MongoReadJournal readJournal;

    /**
     * Constructor.
     *
     * @param streamingCacheSize the size of the streaming cache.
     * @param entityMapper the mapper used to map {@link PidWithSeqNr} to {@code T}. The resulting entity will be
     * streamed to the recipient actor.
     */
    protected AbstractPersistenceStreamingActor(final int streamingCacheSize,
            final Function<PidWithSeqNr, T> entityMapper) {
        this.streamingCacheSize = streamingCacheSize;
        this.entityMapper = requireNonNull(entityMapper);

        final Config config = getContext().getSystem().settings().config();
        final MongoDbConfig mongoDbConfig =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(config));
        mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
        readJournal = MongoReadJournal.newInstance(config, mongoClient);
    }

    /**
     * Constructor for tests.
     *
     * @param streamingCacheSize the size of the streaming cache.
     * @param entityMapper the mapper used to map {@link PidWithSeqNr} to {@code T}. The resulting entity will be
     * streamed to the recipient actor.
     * @param readJournal the ReadJournal to use instead of creating one in the non-test constructor.
     */
    protected AbstractPersistenceStreamingActor(final int streamingCacheSize,
            final Function<PidWithSeqNr, T> entityMapper, final MongoReadJournal readJournal) {
        this.streamingCacheSize = streamingCacheSize;
        this.entityMapper = requireNonNull(entityMapper);

        final Config config = getContext().getSystem().settings().config();
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
    protected final Class<StartStreamRequest> getCommandClass() {
        return StartStreamRequest.class;
    }

    @Override
    protected int getBurst(final StartStreamRequest command) {
        return command.getBurst();
    }

    @Override
    protected Duration getInitialTimeout(final StartStreamRequest command) {
        return Duration.ofMillis(command.getTimeoutMillis());
    }

    @Override
    protected Duration getIdleTimeout(final StartStreamRequest command) {
        return Duration.ofMillis(command.getTimeoutMillis());
    }

    @Override
    protected Object batchMessages(final List<T> elements) {
        return BatchedEntityIdWithRevisions.of(getElementClass(), elements);
    }

    @Override
    protected final Source<T, NotUsed> createSource(final StartStreamRequest command) {
        return command.accept(this);
    }

    @Override
    public Source<T, NotUsed> visit(final SudoStreamModifiedEntities command) {
        final String actorName = getSelf().path().name();
        final String unfilteredStreamingLogName = actorName + "unfiltered-streaming";
        final String filteredStreamingLogName = actorName + "filtered-streaming";

        // create a separate cache per stream (don't use member variable!)
        final ComparableCache<String, Long> cache = new ComparableCache<>(streamingCacheSize);
        return readJournal.getPidWithSeqNrsByInterval(command.getStart(), command.getEnd())
                .log(unfilteredStreamingLogName, log)
                // avoid unnecessary streaming of old sequence numbers
                .filter(pidWithSeqNr ->
                        cache.updateIfNewOrGreater(pidWithSeqNr.getPersistenceId(), pidWithSeqNr.getSequenceNr()))
                .map(this::mapEntity)
                .log(filteredStreamingLogName, log);
    }

    @Override
    public Source<T, NotUsed> visit(final SudoStreamPids command) {
        final Duration maxIdleTime = Duration.ofMillis(command.getTimeoutMillis());
        final int batchSize = command.getBurst() * 5;

        return readJournal.getJournalPids(batchSize, maxIdleTime, materializer)
                .map(pid -> mapEntity(new PidWithSeqNr(pid, 0L)))
                .log("pid-streaming", log);
    }

    private T mapEntity(final PidWithSeqNr pidWithSeqNr) {
        return entityMapper.apply(pidWithSeqNr);
    }
}
