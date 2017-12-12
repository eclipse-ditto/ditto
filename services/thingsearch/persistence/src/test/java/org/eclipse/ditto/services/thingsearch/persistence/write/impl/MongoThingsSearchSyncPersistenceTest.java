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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceTestBase;
import org.junit.Test;

/**
 * Tests {@link MongoThingsSearchSyncPersistence}.
 */
public final class MongoThingsSearchSyncPersistenceTest extends AbstractThingSearchPersistenceTestBase {

    /**
     * Test that calling init multiple times does not throw an exception.
     */
    @Test
    public void multipleInitDoesNotThrow() {
        // (Note that init should already been called in parent class, nevertheless we do it twice here)
        syncPersistence.init();
        syncPersistence.init();
    }

    /**
     * Checks that the fallback is returned when the timestamp has not yet been persisted.
     */
    @Test
    public void retrieveFallbackForLastSuccessfulSyncTimestamp() {
        final Instant fallbackTs = Instant.now();

        final Instant actualTs = syncPersistence.retrieveLastSuccessfulStreamEnd(fallbackTs);
        assertThat(actualTs).isEqualTo(fallbackTs);
    }

    /**
     * Checks updating and retrieving the timestamp afterwards.
     */
    @Test
    public void updateAndRetrieveLastSuccessfulSyncTimestamp() {
        final Instant ts = Instant.now();


        runBlocking(syncPersistence.updateLastSuccessfulStreamEnd(ts));

        final Instant fallbackTs = ts.minusSeconds(1000);
        final Instant persistedTs = syncPersistence.retrieveLastSuccessfulStreamEnd(fallbackTs);
        assertThat(persistedTs).isEqualTo(ts);
    }
}

