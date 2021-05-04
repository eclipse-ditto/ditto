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

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_DELETE_AT;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_GLOBAL_READ;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_GRANTED_PATH;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_NAMESPACE;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_PATH_KEY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_PATH_VALUE;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_REVISION;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.mongo.indices.Index;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.IndexFactory;

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
            Arrays.asList(FIELD_GRANTED_PATH, FIELD_PATH_KEY, FIELD_PATH_VALUE, FIELD_ID), false);

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

    private static final Index DELETE_AT = IndexFactory.newExpirationIndex(FIELD_DELETE_AT, FIELD_DELETE_AT, 0L);

    /**
     * Index for namespace.
     */
    private static final Index NAMESPACE = IndexFactory.newInstance("namespace",
            Arrays.asList(FIELD_NAMESPACE, FIELD_ID), false);

    /**
     * Gets all defined indices.
     *
     * @return the indices
     */
    public static List<Index> all() {
        return Collections.unmodifiableList(
                Arrays.asList(KEY_VALUE, GLOBAL_READ, POLICY, NAMESPACE, DELETE_AT));
    }

}
