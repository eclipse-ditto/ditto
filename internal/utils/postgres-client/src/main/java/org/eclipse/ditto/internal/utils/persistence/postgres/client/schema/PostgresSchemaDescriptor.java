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

import java.util.List;

/**
 * The backend-agnostic description of one component's PostgreSQL schema, consumed by the generic
 * {@link PostgresSchemaManager}. Each Postgres-backed component (the event-sourcing persistence backend, the search
 * backend, …) supplies its OWN descriptor; the manager owns everything that is shared across all of them — the
 * {@code schema_version} bookkeeping table ({@link PostgresSchemaManager#SCHEMA_VERSION_DDL}) and the cluster-wide
 * first-boot advisory lock — and applies them for every component.
 * <p>
 * A descriptor therefore declares ONLY its component-specific parts:
 * </p>
 * <ul>
 *     <li>{@link #component()} — the logical identifier stored in {@code schema_version.component} (the checksum guard
 *     is scoped per component).</li>
 *     <li>{@link #version()} — bumped on any DDL change, per the schema-evolution policy.</li>
 *     <li>{@link #minCompatibleVersion()} — the oldest code version that can still run against this version's schema
 *     (rolling upgrades, see below).</li>
 *     <li>{@link #ddlStatements()} — the ordered, idempotent ({@code IF NOT EXISTS}) DDL for this component's own
 *     tables/indexes, <strong>excluding</strong> the shared {@code schema_version} table (the manager prepends that
 *     once). Order is stable so the checksum is deterministic.</li>
 *     <li>{@link #tableContracts()} — the live-catalog verification contracts for this component's tables.</li>
 * </ul>
 * <p>
 * The manager computes the {@code schema_version} checksum over the shared {@code schema_version} DDL followed by this
 * descriptor's {@link #ddlStatements()}, i.e. over the exact statement set the bootstrap executes — so the checksum can
 * never drift away from what is actually run.
 * </p>
 * <h2>Schema bumps and {@code minCompatibleVersion}</h2>
 * <p>
 * During a rolling upgrade the first upgraded pod advances the component's {@code schema_version} row, while pods of
 * the previous release keep running and may restart. Such an older pod boots against the newer schema only if the
 * stored {@code min_compatible_version} is at most its own {@link #version()}; it then runs no DDL and leaves the row
 * alone (see {@link SchemaVersionDecision#COMPATIBLE_NEWER}). Every bump of {@link #version()} therefore declares its
 * {@link #minCompatibleVersion()}:
 * </p>
 * <ul>
 *     <li><strong>Backward-compatible change</strong> (older code keeps working against the new schema — e.g. an index
 *     drop, reloptions such as autovacuum settings, a new index): keep the <em>previous</em>
 *     {@code minCompatibleVersion}.</li>
 *     <li><strong>Change older code can't run against</strong> (e.g. a column rename or type change, a new
 *     {@code NOT NULL} column without a default, a dropped column or table older code still uses): set it to the
 *     <em>new</em> version. Older pods that restart mid-roll then refuse to boot until they are upgraded.</li>
 * </ul>
 * <p>
 * Both versions run side by side during the roll, so a "compatible" change must also let the newer code cope with
 * rows the older code writes. {@code minCompatibleVersion} is row metadata, not DDL: it is not part of the checksum.
 * </p>
 */
public interface PostgresSchemaDescriptor {

    /**
     * @return the logical component identifier stored in {@code schema_version.component}.
     */
    String component();

    /**
     * @return the current code schema version for this component.
     */
    int version();

    /**
     * The oldest code schema version that can still run against this version's schema, stored as
     * {@code schema_version.min_compatible_version}. Must lie within {@code [1, version()]}; see the class Javadoc for
     * how each bump chooses it.
     *
     * @return the minimum compatible code schema version for this component's current schema.
     */
    int minCompatibleVersion();

    /**
     * @return the ordered list of this component's own DDL statements (CREATE TABLE / CREATE INDEX / ALTER TABLE …),
     * each {@code IF NOT EXISTS}, <strong>excluding</strong> the shared {@code schema_version} table (owned by the
     * manager). Order is stable so the checksum is deterministic. A statement that creates a table or index must have
     * the form {@code CREATE TABLE IF NOT EXISTS <name> (…} or {@code CREATE INDEX IF NOT EXISTS <name> ON …} with a
     * plain lower-case name: a same-version boot skips the DDL only if every relation named so exists
     * ({@link PostgresSchemaManager#namedRelations(PostgresSchemaDescriptor)}), and any other creating statement makes
     * every boot run the DDL. Statements that change existing objects ({@code ALTER TABLE … SET (…)},
     * {@code DROP INDEX IF EXISTS}) are applied only by boots that run the DDL (first boot, a version bump, a missing
     * relation) — ship such a change with a version bump.
     */
    List<String> ddlStatements();

    /**
     * @return the live-catalog verification contracts for this component's tables (never the shared
     * {@code schema_version} table).
     */
    List<TableContract> tableContracts();

}
