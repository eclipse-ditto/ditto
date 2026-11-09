/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex;

import java.util.Arrays;
import java.util.Optional;

/**
 * The system indexes of {@code search_flat}: part of the search schema (since search schema version 3), but not of its
 * checksummed DDL. A plain {@code CREATE INDEX} in the bootstrap transaction would block writes for the whole build on
 * a populated table, so {@link PathIndexMaintenance} builds them with {@code CREATE INDEX CONCURRENTLY} after every
 * successful bootstrap and repairs INVALID leftovers. They are maintained whether or not per-path indexes are
 * configured, and never dropped except to repair an INVALID one.
 */
public enum SystemSearchIndex {

    /**
     * One entry per path occurrence that holds more than one value in a thing (an array, or a repeated path): the
     * flattener numbers the rows of one path {@code ord = 0, 1, 2, …}, so {@code ord = 1} exists exactly then. It serves
     * the multi-valued branch of a range leg of the selective-leg CTE: two range conditions on one path may be met by
     * different array elements (MongoDB semantics), so a range leg must include every thing with a second value there.
     */
    MULTI_VALUED("sf_multi", "(wpath, thing_id) WHERE ord = 1");

    private static final String TABLE = "search_flat";

    private final String indexName;
    private final String definition;

    SystemSearchIndex(final String indexName, final String definition) {
        this.indexName = indexName;
        this.definition = definition;
    }

    /**
     * @return the index name.
     */
    public String indexName() {
        return indexName;
    }

    /**
     * @return the {@code CREATE INDEX CONCURRENTLY IF NOT EXISTS} statement the maintenance runs.
     */
    public String createConcurrentlyStatement() {
        return "CREATE INDEX CONCURRENTLY IF NOT EXISTS " + indexName + " ON " + TABLE + " " + definition;
    }

    /**
     * @return the same index as a plain, transactional {@code CREATE INDEX IF NOT EXISTS}: for an empty or offline
     * table only (tests); the service never runs it.
     */
    public String createStatement() {
        return "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + TABLE + " " + definition;
    }

    /**
     * @return the {@code DROP INDEX CONCURRENTLY IF EXISTS} statement (repair of an INVALID index).
     */
    public String dropConcurrentlyStatement() {
        return "DROP INDEX CONCURRENTLY IF EXISTS " + indexName;
    }

    /**
     * @param indexName an index name.
     * @return the system index of that name, if any.
     */
    public static Optional<SystemSearchIndex> byIndexName(final String indexName) {
        return Arrays.stream(values()).filter(index -> index.indexName.equals(indexName)).findFirst();
    }

}
