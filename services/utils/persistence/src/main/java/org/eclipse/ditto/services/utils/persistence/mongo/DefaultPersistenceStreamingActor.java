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

import java.util.function.Function;

import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.PidWithSeqNr;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.typesafe.config.Config;

import akka.actor.Props;


/**
 * Configurable default implementation of {@link AbstractPersistenceStreamingActor}.
 */
@AllValuesAreNonnullByDefault
public final class DefaultPersistenceStreamingActor<T extends EntityIdWithRevision>
        extends AbstractPersistenceStreamingActor<T> {

    private final Class<T> elementClass;
    private final MongoClientWrapper mongoClientWrapper;

    DefaultPersistenceStreamingActor(final Class<T> elementClass,
            final int streamingCacheSize,
            final Function<PidWithSeqNr, T> entityMapper,
            final MongoReadJournal readJournal,
            final MongoClientWrapper mongoClientWrapper) {

        super(streamingCacheSize, entityMapper, readJournal);
        this.elementClass = elementClass;
        this.mongoClientWrapper = mongoClientWrapper;
    }

    /**
     * Creates Akka configuration object Props for this PersistenceStreamingActor.
     *
     * @param <T> type of messages to stream.
     * @param elementClass class of the elements.
     * @param config the configuration of the akka system.
     * @param streamingCacheSize the size of the streaming cache.
     * @param entityMapper the mapper used to map {@link PidWithSeqNr} to {@code T}. The resulting entity will be
     * streamed to the recipient actor.
     * @return the Akka configuration Props object.
     */
    public static <T extends EntityIdWithRevision> Props props(final Class<T> elementClass,
            final Config config,
            final int streamingCacheSize,
            final Function<PidWithSeqNr, T> entityMapper) {

        return Props.create(DefaultPersistenceStreamingActor.class, () -> {
            final MongoClientWrapper mongoClient = MongoClientWrapper.newInstance(config);
            final MongoReadJournal readJournal = MongoReadJournal.newInstance(config, mongoClient);
            return new DefaultPersistenceStreamingActor<>(elementClass,
                    streamingCacheSize, entityMapper, readJournal, mongoClient);
        });
    }

    @Override
    public void postStop() throws Exception {
        mongoClientWrapper.close();
        super.postStop();
    }

    @Override
    protected Class<T> getElementClass() {
        return elementClass;
    }
}
