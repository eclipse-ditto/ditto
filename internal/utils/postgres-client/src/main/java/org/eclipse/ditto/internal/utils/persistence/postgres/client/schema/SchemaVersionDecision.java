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
 * What the schema bootstrap (or self-heal) may do after comparing the stored {@code schema_version} row with this
 * code's {@link PostgresSchemaDescriptor}. Decided by {@link SchemaVerifier} <em>before</em> any component DDL runs;
 * the refuse cases are not values but a {@link SchemaBootException}.
 */
public enum SchemaVersionDecision {

    /**
     * No stored row yet, an older stored version, or the same version with something missing: run this code's DDL. At
     * boot the catalog is then verified and the row is inserted or advanced to this code's version, checksum and
     * {@code min_compatible_version}.
     */
    APPLY,

    /**
     * The stored row has exactly this code's version and checksum, and every table and index this code's DDL creates
     * exists ({@link PostgresSchemaManager#namedRelations(PostgresSchemaDescriptor)}): run <strong>no</strong> DDL, so
     * the boot takes no lock on the component's tables. At boot the catalog is still verified and the row is upserted
     * with unchanged values; a self-heal does nothing (the caller's single retry then decides). Statements that change
     * existing objects (reloptions, {@code DROP INDEX IF EXISTS}) are therefore not re-applied by such a boot.
     */
    UP_TO_DATE,

    /**
     * The stored version is newer than this code's, and its {@code min_compatible_version} is at most this code's
     * version (a rolling upgrade is in flight and the newer schema is declared backward compatible): run
     * <strong>no</strong> DDL and write <strong>no</strong> row. At boot only the catalog facts this code needs are
     * verified.
     */
    COMPATIBLE_NEWER

}
