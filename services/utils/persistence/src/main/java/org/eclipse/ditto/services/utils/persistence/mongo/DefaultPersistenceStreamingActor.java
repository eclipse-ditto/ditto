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
import org.eclipse.ditto.services.utils.akkapersistence.mongoaddons.DittoJavaDslMongoReadJournal;
import org.eclipse.ditto.services.utils.akkapersistence.mongoaddons.PidWithSeqNr;

import akka.actor.Props;
import akka.japi.Creator;


/**
 * Configurable default implementation of {@link AbstractPersistenceStreamingActor}.
 */
public final class DefaultPersistenceStreamingActor<T extends EntityIdWithRevision>
        extends AbstractPersistenceStreamingActor<T> {

    private DefaultPersistenceStreamingActor(final int streamingCacheSize, final Function<PidWithSeqNr, T> entityMapper) {
        this(streamingCacheSize, entityMapper, null);
    }

    private DefaultPersistenceStreamingActor(final int streamingCacheSize,
            final Function<PidWithSeqNr, T> entityMapper, final DittoJavaDslMongoReadJournal readJournal) {
        super(streamingCacheSize, entityMapper, readJournal);
    }

    /**
     * Creates Akka configuration object Props for this PersistenceStreamingActor.
     *
     * @param streamingCacheSize the size of the streaming cache.
     * @param entityMapper the mapper used to map {@link PidWithSeqNr} to {@code T}. The resulting entity will be
     * streamed to the recipient actor.
     *
     * @return the Akka configuration Props object.
     */
    public static <T extends EntityIdWithRevision> Props props(final int streamingCacheSize,
            final Function<PidWithSeqNr, T> entityMapper) {

        return Props.create(DefaultPersistenceStreamingActor.class, new Creator<DefaultPersistenceStreamingActor<T>>() {
            private static final long serialVersionUID = 1L;

            @Override
            public DefaultPersistenceStreamingActor<T> create() {
                return new DefaultPersistenceStreamingActor<>(streamingCacheSize, entityMapper);
            }
        });
    }

    static <T extends EntityIdWithRevision> Props props(final int streamingCacheSize,
            final Function<PidWithSeqNr, T> entityMapper, final DittoJavaDslMongoReadJournal readJournal) {
        return Props.create(DefaultPersistenceStreamingActor.class, new Creator<DefaultPersistenceStreamingActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public DefaultPersistenceStreamingActor create() {
                return new DefaultPersistenceStreamingActor<>(streamingCacheSize, entityMapper, readJournal);
            }
        });
    }

}
