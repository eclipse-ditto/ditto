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

/**
 * Thrown when a coordinated shutdown ended the schema bootstrap's wait for PostgreSQL.
 */
public final class SchemaBootstrapAbortedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SchemaBootstrapAbortedException(final String component) {
        super("PostgreSQL schema bootstrap of component '" + component
                + "' was aborted because the service is shutting down.");
    }

}
