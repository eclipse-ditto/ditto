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
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_NAMESPACE;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_REVISION;

import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
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
    private static final Index WILDCARD = IndexFactory.newInstance("v_wildcard", List.of("$**"), false)
            .withWildcardProjection(new BsonDocument()
                    .append("t", new BsonInt32(1))
                    .append("f", new BsonInt32(1)));

    /**
     * Index for queries without effective filters to be executed as scans over all visible things.
     */
    private static final Index GLOBAL_READ =
            IndexFactory.newInstance("global_read", List.of(FIELD_GLOBAL_READ, FIELD_ID), false);

    /**
     * Index for dispatching policy events.
     */
    private static final Index POLICY =
            IndexFactory.newInstance("policyId", List.of(FIELD_POLICY_ID, FIELD_POLICY_REVISION), false);

    /**
     * Index for namespace.
     */
    private static final Index NAMESPACE =
            IndexFactory.newInstance("_namespace", List.of(FIELD_NAMESPACE, FIELD_ID), false);

    /**
     * Index for namespace purging.
     */
    private static final Index DELETE_AT = IndexFactory.newExpirationIndex(FIELD_DELETE_AT, FIELD_DELETE_AT, 0L);

    /**
     * Gets all defined indices.
     *
     * @return the indices
     */
    public static List<Index> all() {
        return List.of(NAMESPACE, GLOBAL_READ, WILDCARD, POLICY, DELETE_AT);
    }

}
