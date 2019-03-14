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
package org.eclipse.ditto.services.thingsearch.persistence;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETE_AT;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GLOBAL_READ;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GRANTED_PATH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PATH_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_PATH_VALUE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_REVISION;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.services.utils.persistence.mongo.indices.Index;
import org.eclipse.ditto.services.utils.persistence.mongo.indices.IndexFactory;

/**
 * Defines the MongoDB indices used by the things-search persistence.
 */
public final class Indices {

    private Indices() {
        throw new AssertionError();
    }

    /**
     * Index for queries with effective filters.
     */
    private static final Index KEY_VALUE = IndexFactory.newInstance("key-value",
            Arrays.asList(FIELD_GRANTED_PATH, FIELD_PATH_KEY, FIELD_PATH_VALUE), false);

    /**
     * Index for queries without effective filters to be executed as scans over all visible things.
     */
    private static final Index GLOBAL_READ = IndexFactory.newInstance("global-read",
            Collections.singletonList(FIELD_GLOBAL_READ), false);

    /**
     * Index for dispatching policy events.
     */
    private static final Index POLICY = IndexFactory.newInstance("policyId",
            Arrays.asList(FIELD_POLICY_ID, FIELD_POLICY_REVISION), false);

    private static final Index DUMMY_INDEX_DELETE_AT = IndexFactory.newInstance(FIELD_DELETE_AT,
            Collections.singletonList(FIELD_DELETE_AT), false);

    /**
     * Index for namespace.
     */
    private static final Index NAMESPACE = IndexFactory.newInstance("namespace",
            Collections.singletonList(FIELD_NAMESPACE), false);

    /**
     * Gets all defined indices.
     *
     * @return the indices
     */
    public static List<Index> all() {
        return Collections.unmodifiableList(
                Arrays.asList(KEY_VALUE, GLOBAL_READ, POLICY, NAMESPACE, DUMMY_INDEX_DELETE_AT));
    }

}
