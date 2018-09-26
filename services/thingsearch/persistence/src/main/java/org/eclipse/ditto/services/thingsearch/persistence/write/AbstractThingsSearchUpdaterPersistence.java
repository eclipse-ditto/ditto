/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.write;

import org.eclipse.ditto.model.things.Thing;

import akka.NotUsed;
import akka.event.LoggingAdapter;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * Abstract concepts used for Updater Persistence.
 */
public abstract class AbstractThingsSearchUpdaterPersistence implements ThingsSearchUpdaterPersistence {

    /**
     * The logger.
     */
    protected final LoggingAdapter log;

    /**
     * Default contructor.
     *
     * @param loggingAdapter the logger to use for logging.
     */
    public AbstractThingsSearchUpdaterPersistence(final LoggingAdapter loggingAdapter) {
        log = loggingAdapter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Source<Boolean, NotUsed> insertOrUpdate(final Thing thing, final long revision, final long
            policyRevision) {
        // enforce the restrictions on the data
        final Thing toSave = IndexLengthRestrictionEnforcer.enforceRestrictions(log, thing);
        return save(toSave, revision, policyRevision)
                .recoverWithRetries(1, errorRecovery(getThingId(toSave)));
    }

    /**
     * Inserts or updates a passed in {@link Thing}.
     *
     * @param thing the thing to insert or update.
     * @param revision the revision to perform the upsert operation with.
     * @param policyRevision the revision of the policy to also persist.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    protected abstract Source<Boolean, NotUsed> save(final Thing thing, final long revision, final long policyRevision);

    /**
     * Called to recover from error with retries.
     *
     * @param thingId the thing for which an update was issued.
     * @return a partial function that will recover from the error
     */
    protected abstract PartialFunction<Throwable, Source<Boolean, NotUsed>> errorRecovery(final String
            thingId);

    /**
     * Get the id of the thing.
     *
     * @param thing The thing.
     * @return The id or an {@link IllegalArgumentException} if no id is present.
     */
    protected final String getThingId(final Thing thing) {
        return thing.getId().orElseThrow(() -> new IllegalArgumentException("The thing has no ID!"));
    }

}
