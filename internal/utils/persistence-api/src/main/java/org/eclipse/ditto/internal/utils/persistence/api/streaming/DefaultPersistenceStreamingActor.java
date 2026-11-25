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

import java.io.Closeable;
import java.util.function.Function;

import org.apache.pekko.actor.Props;
import org.eclipse.ditto.internal.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

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
            final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper,
            final DittoReadJournal readJournal,
            final Closeable resourceToClose) {

        super(entityMapper, entityUnmapper, readJournal, resourceToClose);
        this.elementClass = elementClass;
    }

    /**
     * Creates Pekko configuration object Props for this PersistenceStreamingActor.
     * <p>
     * The caller supplies the backend's {@link DittoReadJournal} and the {@link Closeable} resource the actor closes on
     * stop.
     *
     * @param <T> type of messages to stream.
     * @param elementClass class of the elements.
     * @param entityMapper the mapper used to map {@link PidWithSeqNr} to {@code T}. The resulting entity will be
     * streamed to the recipient actor.
     * @param entityUnmapper the inverse of {@code entityMapper}.
     * @param readJournal the backend's read journal.
     * @param resourceToClose the backend resource to close when the actor stops (a no-op {@code Closeable} when the
     * backend owns its resources elsewhere).
     * @return the Pekko configuration Props object.
     */
    public static <T extends EntityIdWithRevision<?>> Props props(final Class<T> elementClass,
            final Function<PidWithSeqNr, T> entityMapper,
            final Function<EntityIdWithRevision<?>, PidWithSeqNr> entityUnmapper,
            final DittoReadJournal readJournal,
            final Closeable resourceToClose) {

        return Props.create(DefaultPersistenceStreamingActor.class, elementClass, entityMapper, entityUnmapper,
                readJournal, resourceToClose);
    }

    @Override
    protected Class<T> getElementClass() {
        return elementClass;
    }

}
