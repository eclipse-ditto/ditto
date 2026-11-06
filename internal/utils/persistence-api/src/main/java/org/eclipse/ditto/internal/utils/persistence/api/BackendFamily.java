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
 * A plain enum carrying no backend type, so shared code can branch on the active backend (connectivity's encryption
 * migration refuses on PostgreSQL, for example) without importing a MongoDB or PostgreSQL class. Each provider declares
 * its family through {@link PersistenceBackendProvider#backendFamily()}.
 *
 * @since 4.0.0
 */
public enum BackendFamily {

    /** The MongoDB backend (the bundled default). */
    MONGODB,

    /** The PostgreSQL backend. */
    POSTGRESQL
}
