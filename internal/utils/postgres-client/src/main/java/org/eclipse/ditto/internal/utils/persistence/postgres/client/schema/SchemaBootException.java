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
 * Thrown to <strong>refuse to boot</strong> when the PostgreSQL schema does not match what this code expects — either a
 * {@code schema_version} checksum mismatch (a code-vs-stored downgrade or drift) or a live-catalog divergence
 * (a pre-existing table whose PK/columns differ from the canonical DDL, which {@code CREATE TABLE IF NOT EXISTS}
 * silently keeps).
 */
public final class SchemaBootException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SchemaBootException(final String message) {
        super(message);
    }

}
