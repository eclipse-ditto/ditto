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
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_FEATURE_POLICY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_GLOBAL_READ;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_NAMESPACE;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_REVISION;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REFERENCED_POLICIES;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REVISION;

import java.util.List;
import java.util.stream.Stream;

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
     * <p>
     * All fields not included in the wildcard index are enumerated.
     * Policy objects are excluded on thing and feature levels to prevent MongoDB from choosing an inefficient query
     * plan according to the nested clauses for authorization.
     * It is not possible to list the included fields because the feature array 'f' is the parent of the excluded
     * feature policy field 'f.p'.
     */
    private static final Index WILDCARD = IndexFactory.newInstance("v_wildcard", List.of("$**"), false)
            .withWildcardProjection(Stream.of(FIELD_ID, FIELD_NAMESPACE, FIELD_GLOBAL_READ, FIELD_REVISION,
                            FIELD_POLICY_ID, FIELD_POLICY_REVISION, FIELD_POLICY, FIELD_FEATURE_POLICY)
                    .reduce(new BsonDocument(),
                            (doc, field) -> doc.append(field, new BsonInt32(0)),
                            (doc1, doc2) -> {
                                doc2.forEach(doc1::append);
                                return doc1;
                            })
            );

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
     * Index for dispatching policy events.
     */
    private static final Index REFERENCED_POLICIES =
            IndexFactory.newInstance("referencedPolicies", List.of(FIELD_REFERENCED_POLICIES), false);

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
        return List.of(NAMESPACE, GLOBAL_READ, WILDCARD, POLICY, REFERENCED_POLICIES, DELETE_AT);
    }

}
