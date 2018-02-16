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
package org.eclipse.ditto.services.thingsearch.persistence;

import org.eclipse.ditto.services.utils.persistence.mongo.assertions.MongoIndexAssertions;
import org.junit.Test;

/**
 * Checks the initialization of Mongo indices.
 */
public class IndexInitializationIT extends AbstractThingSearchPersistenceITBase {

    @Test
    public void indicesAreCorrectlyInitialized() {
        MongoIndexAssertions.assertIndices(getClient().getDatabase(),
                PersistenceConstants.THINGS_COLLECTION_NAME, getMaterializer(), Indices.Things.all());
    }
}
