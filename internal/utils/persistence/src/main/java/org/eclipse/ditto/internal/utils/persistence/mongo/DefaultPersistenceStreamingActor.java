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

import java.util.function.Function;

import org.eclipse.ditto.internal.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.PidWithSeqNr;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import akka.actor.Props;

/**
 * Configurable default implementation of {@link AbstractPersistenceStreamingActor}.
 */
@AllValuesAreNonnullByDefault
public final class DefaultPersistenceStreamingActor<T extends EntityIdWithRevision<?>>
        extends AbstractPersistenceStreamingActor<T> {

    private final Class<T> elementClass;

    @SuppressWarnings("unused")
    private DefaultPersistenceStreamingActor(final Class<T> elementClass,
            final Function<PidWithSeqNr, T> entityMapper,
            final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper) {

        super(entityMapper, entityUnmapper);
        this.elementClass = elementClass;
    }

    @SuppressWarnings("unused")
    private DefaultPersistenceStreamingActor(final Class<T> elementClass,
            final Function<PidWithSeqNr, T> entityMapper,
            final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper,
            final MongoReadJournal readJournal) {

        super(entityMapper, entityUnmapper, readJournal);
        this.elementClass = elementClass;
    }

    /**
     * Creates Akka configuration object Props for this PersistenceStreamingActor.
     *
     * @param <T> type of messages to stream.
     * @param elementClass class of the elements.
     * @param entityMapper the mapper used to map
     * {@link org.eclipse.ditto.internal.utils.persistence.mongo.streaming.PidWithSeqNr} to {@code T}.
     * The resulting entity will be streamed to the recipient actor.
     * @param entityUnmapper the inverse of {@code entityMapper}.
     * @return the Akka configuration Props object.
     */
    public static <T extends EntityIdWithRevision<?>> Props props(final Class<T> elementClass,
            final Function<PidWithSeqNr, T> entityMapper,
            final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper) {

        return Props.create(DefaultPersistenceStreamingActor.class, elementClass, entityMapper, entityUnmapper);
    }

    static <T extends EntityIdWithRevision<?>> Props propsForTests(final Class<T> elementClass,
            final Function<PidWithSeqNr, T> entityMapper,
            final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper,
            final MongoReadJournal readJournal) {

        return Props.create(DefaultPersistenceStreamingActor.class, elementClass, entityMapper, entityUnmapper,
                readJournal);
    }

    @Override
    protected Class<T> getElementClass() {
        return elementClass;
    }

}
