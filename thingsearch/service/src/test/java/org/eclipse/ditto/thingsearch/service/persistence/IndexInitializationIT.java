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
package org.eclipse.ditto.thingsearch.service.persistence;

import org.eclipse.ditto.internal.utils.persistence.mongo.assertions.MongoIndexAssertions;
import org.junit.Test;

/**
 * Checks the initialization of Mongo indices.
 */
public final class IndexInitializationIT extends AbstractThingSearchPersistenceITBase {

    @Test
    public void indicesAreCorrectlyInitialized() {
        MongoIndexAssertions.assertIndices(getClient().getDefaultDatabase(),
                PersistenceConstants.THINGS_COLLECTION_NAME, getMaterializer(), Indices.all());
    }

}
