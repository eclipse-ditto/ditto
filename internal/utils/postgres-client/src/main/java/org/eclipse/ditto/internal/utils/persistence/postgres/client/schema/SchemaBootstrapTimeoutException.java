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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.schema;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Thrown when one schema-bootstrap attempt did not finish within {@code ditto.postgresql.bootstrap.attempt-timeout}.
 * Not retried: the database answered but the bootstrap is stuck.
 */
public final class SchemaBootstrapTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SchemaBootstrapTimeoutException(final String component, final Duration attemptTimeout,
            final TimeoutException cause) {
        super("PostgreSQL schema bootstrap of component '" + component + "' did not finish within "
                + "ditto.postgresql.bootstrap.attempt-timeout (" + SchemaBootstrapRetry.seconds(attemptTimeout)
                + ", POSTGRES_BOOTSTRAP_ATTEMPT_TIMEOUT); not retried. Usually another session holds the schema "
                + "advisory lock (" + advisoryLockQuery() + ") or a conflicting lock on one of the component's tables; "
                + "behind PgBouncer a PostgreSQL that is down looks the same.", cause);
    }

    /**
     * @return a query that lists the sessions holding or waiting for the schema advisory lock (a bigint key: classid =
     * its high, objid = its low 32 bits, objsubid = 1).
     */
    static String advisoryLockQuery() {
        return "SELECT pid, granted FROM pg_locks WHERE locktype = 'advisory' AND classid = "
                + (PostgresSchemaManager.ADVISORY_LOCK_KEY >>> 32) + " AND objid = "
                + (PostgresSchemaManager.ADVISORY_LOCK_KEY & 0xFFFF_FFFFL) + " AND objsubid = 1";
    }

}
