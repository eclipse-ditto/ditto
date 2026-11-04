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
package org.eclipse.ditto.internal.utils.persistence.api;

/**
 * The persistence backend family a {@link PersistenceBackendProvider} belongs to.
 * <p>
 * This is the backend-NEUTRAL family indicator the boot-time self-check ({@link PersistenceBackendSelfCheck}) compares
 * against the journal/snapshot plugin {@code class} entries and the read-journal class the effective config resolves. It
 * is intentionally a plain enum carrying no backend type: each provider declares its family <em>plus</em> the expected
 * plugin/read-journal class-name STRINGS (see {@link PersistenceBackendProvider#expectedPluginClassNames()} /
 * {@link PersistenceBackendProvider#expectedReadJournalClassName()}), so the persistence-api module never imports a
 * Mongo or Postgres class to express which backend is active.
 *
 * @since 4.0.0
 */
public enum BackendFamily {

    /** The MongoDB backend (the bundled default). */
    MONGODB,

    /** The PostgreSQL backend. */
    POSTGRESQL
}
