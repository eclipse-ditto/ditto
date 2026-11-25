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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.parity;

import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchUpdaterFlow;

/**
 * One side of the parity matrix: a fully wired search backend (real production write flow, real read persistence,
 * real query builder factory) against a real containerized database. The parity engine treats both sides through
 * this seam only, so every comparison exercises identical neutral inputs (criteria, options, auth subject lists).
 */
interface ParityBackend {

    /** Human-readable backend name used in assertion messages ("mongo" / "postgres"). */
    String name();

    /** The backend's real {@link QueryBuilderFactory} (Mongo: MongoQueryBuilderFactory, PG: PostgresQueryBuilderFactory). */
    QueryBuilderFactory queryBuilderFactory();

    /** The backend's real read persistence. */
    ThingsSearchPersistence searchPersistence();

    /** The backend's real production write flow (the same flow the updater stream runs in the service). */
    SearchUpdaterFlow updaterFlow();

    /** Release all client resources (does not stop the container). */
    void close();
}
