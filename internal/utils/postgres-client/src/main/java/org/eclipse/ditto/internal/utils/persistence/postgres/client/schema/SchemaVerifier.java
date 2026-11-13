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
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Pure, I/O-free refuse-to-boot decision logic for the schema bootstrap. Kept separate from {@code PostgresSchemaManager}
 * so the two guards can be unit-tested without a live PostgreSQL:
 * <ol>
 *     <li><strong>version guard</strong> — the stored {@code schema_version} row (version, checksum,
 *     {@code min_compatible_version}) decides, <em>before</em> any component DDL runs, whether this code applies its
 *     DDL, runs against a newer but compatible schema without touching it, or refuses (same-version checksum drift, or
 *     a newer schema this code is not compatible with). See {@link #verifyStoredVersion} and
 *     {@link #verifyStoredVersionForHeal}.</li>
 *     <li>the <strong>same-version fast path</strong> — {@link #decideComponentDdl}: a current row whose DDL relations
 *     all exist runs no DDL.</li>
 *     <li><strong>live-catalog guard</strong> — each table's actual primary-key definition, column types, and the
 *     {@code COLLATE "C"} collation of its persistence-id columns (read from the catalog) must match the canonical
 *     contract, because {@code CREATE TABLE IF NOT EXISTS} silently keeps a divergent pre-existing table that the
 *     checksum alone cannot catch on first boot. Collation is checked separately from {@code data_type} because
 *     {@code information_schema.columns.data_type} reports {@code text} regardless of collation.</li>
 * </ol>
 * Every method throws {@link SchemaBootException} with a precise diff on divergence.
 */
public final class SchemaVerifier {

    /**
     * The byte-ordered collation every persistence-id column must declare so that {@code col > $1 ORDER BY col} cursor
     * pagination and primary-key ordering are stable across locales. PostgreSQL reports it as
     * {@code information_schema.columns.collation_name = 'C'}. Shared by every Postgres-backed component.
     */
    static final String PID_COLLATION = "C";

    private SchemaVerifier() {
        throw new AssertionError();
    }

    /**
     * The boot-time decision over the stored {@code schema_version} row, taken under the advisory lock
     * <strong>before</strong> any component DDL runs:
     * <table>
     *   <caption>Boot decision matrix</caption>
     *   <tr><th>Stored row</th><th>Decision</th></tr>
     *   <tr><td>none (first boot)</td><td>{@link SchemaVersionDecision#APPLY}</td></tr>
     *   <tr><td>{@code version < code}</td><td>{@link SchemaVersionDecision#APPLY} — upgrade: the idempotent DDL runs
     *   and the caller advances the row (including this code's {@code min_compatible_version})</td></tr>
     *   <tr><td>{@code version == code}</td><td>{@link SchemaVersionDecision#APPLY} if the checksums match; refuse on a
     *   mismatch (drift: manual DDL edits or a code/DB mismatch at the same version)</td></tr>
     *   <tr><td>{@code version > code} and {@code min_compatible_version <= code}</td>
     *   <td>{@link SchemaVersionDecision#COMPATIBLE_NEWER} — the checksum of the newer version is not
     *   compared</td></tr>
     *   <tr><td>{@code version > code} and {@code min_compatible_version > code}</td><td>refuse (downgrade against a
     *   schema this code is not compatible with)</td></tr>
     * </table>
     * A {@code NULL} {@code min_compatible_version} (a row written before the column existed) counts as
     * {@code min_compatible_version = version}, i.e. a newer legacy row is never compatible.
     *
     * @param component the logical component identifier (for the diagnostic message).
     * @param expectedChecksum the current code checksum (computed over the effective DDL for the component).
     * @param currentVersion the current code schema version for the component.
     * @param storedChecksum the checksum read from {@code schema_version}, or {@code null} on first boot (no row yet).
     * @param storedVersion the version read from {@code schema_version}, or {@code null} on first boot (no row yet).
     * @param storedMinCompatibleVersion the {@code min_compatible_version} read from {@code schema_version}, or
     * {@code null} when there is no row or the row predates the column.
     * @return {@link SchemaVersionDecision#APPLY} or {@link SchemaVersionDecision#COMPATIBLE_NEWER}.
     * @throws SchemaBootException on a same-version checksum mismatch (drift) or a newer stored version whose
     * {@code min_compatible_version} exceeds the current code version.
     */
    public static SchemaVersionDecision verifyStoredVersion(final String component, final String expectedChecksum,
            final int currentVersion, @Nullable final String storedChecksum, @Nullable final Integer storedVersion,
            @Nullable final Integer storedMinCompatibleVersion) {
        if (storedChecksum == null || storedVersion == null) {
            return SchemaVersionDecision.APPLY;
        }
        if (storedVersion > currentVersion) {
            return decideNewer("Refusing to boot", component, currentVersion, storedVersion,
                    storedMinCompatibleVersion);
        }
        if (storedVersion == currentVersion && !storedChecksum.equals(expectedChecksum)) {
            throw new SchemaBootException(
                    "Refusing to boot: schema_version checksum mismatch for component '" + component
                            + "' at version " + currentVersion + ". Stored=" + storedChecksum
                            + " expected=" + expectedChecksum
                            + ". This indicates schema drift (manual DDL edits or a code/DB mismatch at the "
                            + "same schema version). See deployment/postgres/README.md#schema-upgrades.");
        }
        // storedVersion < currentVersion: upgrade in flight; storedVersion == currentVersion: checksums match.
        return SchemaVersionDecision.APPLY;
    }

    /**
     * The self-heal decision over the stored {@code schema_version} row. Only the "newer stored version" rule of
     * {@link #verifyStoredVersion} applies (unlike a boot, a same-version checksum mismatch is not refused): with no
     * row, an older or the same version this returns {@link SchemaVersionDecision#APPLY}, and
     * {@link #decideComponentDdl(SchemaVersionDecision, boolean, Set)} then skips the DDL only for a current row (same
     * version and checksum) with every table and index present; with a newer version the heal runs no DDL — a no-op
     * when the newer schema is compatible, a failure when it isn't.
     *
     * @param component the logical component identifier (for the diagnostic message).
     * @param currentVersion the current code schema version for the component.
     * @param storedVersion the version read from {@code schema_version}, or {@code null} when there is no row.
     * @param storedMinCompatibleVersion the {@code min_compatible_version} read from {@code schema_version}, or
     * {@code null} when there is no row or the row predates the column.
     * @return {@link SchemaVersionDecision#APPLY} or {@link SchemaVersionDecision#COMPATIBLE_NEWER}.
     * @throws SchemaBootException on a newer stored version whose {@code min_compatible_version} exceeds the current
     * code version.
     */
    public static SchemaVersionDecision verifyStoredVersionForHeal(final String component, final int currentVersion,
            @Nullable final Integer storedVersion, @Nullable final Integer storedMinCompatibleVersion) {
        if (storedVersion != null && storedVersion > currentVersion) {
            return decideNewer("Refusing PostgreSQL schema self-heal", component, currentVersion, storedVersion,
                    storedMinCompatibleVersion);
        }
        return SchemaVersionDecision.APPLY;
    }

    /**
     * Whether the stored {@code schema_version} row describes exactly this code's schema: the same version and the same
     * checksum. Only then may a boot or self-heal skip the component DDL
     * ({@link #decideComponentDdl(SchemaVersionDecision, boolean, Set)}).
     *
     * @param currentVersion the current code schema version for the component.
     * @param expectedChecksum the current code checksum.
     * @param storedVersion the stored version, or {@code null} when there is no row.
     * @param storedChecksum the stored checksum, or {@code null} when there is no row.
     * @return {@code true} if both match.
     */
    public static boolean storedRowIsCurrent(final int currentVersion, final String expectedChecksum,
            @Nullable final Integer storedVersion, @Nullable final String storedChecksum) {
        return storedVersion != null && storedVersion == currentVersion && expectedChecksum.equals(storedChecksum);
    }

    /**
     * The same-version fast path, decided after {@link #verifyStoredVersion} (boot) or
     * {@link #verifyStoredVersionForHeal} (self-heal) returned:
     * <table>
     *   <caption>Component DDL decision</caption>
     *   <tr><th>Version decision</th><th>Stored row current</th><th>Missing relations</th><th>Result</th></tr>
     *   <tr><td>{@link SchemaVersionDecision#APPLY}</td><td>yes</td><td>none</td>
     *   <td>{@link SchemaVersionDecision#UP_TO_DATE} — no DDL</td></tr>
     *   <tr><td>{@link SchemaVersionDecision#APPLY}</td><td>yes</td><td>some, or unknown ({@code null})</td>
     *   <td>{@link SchemaVersionDecision#APPLY}</td></tr>
     *   <tr><td>{@link SchemaVersionDecision#APPLY}</td><td>no (first boot, upgrade, drifted heal)</td>
     *   <td>any</td><td>{@link SchemaVersionDecision#APPLY}</td></tr>
     *   <tr><td>{@link SchemaVersionDecision#COMPATIBLE_NEWER}</td><td>any</td><td>any</td>
     *   <td>{@link SchemaVersionDecision#COMPATIBLE_NEWER} (unchanged)</td></tr>
     * </table>
     *
     * @param versionDecision what the stored version allows.
     * @param storedRowIsCurrent {@link #storedRowIsCurrent(int, String, Integer, String)}.
     * @param missingRelations the tables and indexes of the component's DDL that do not exist, or {@code null} when
     * they are unknown (the DDL could not be named, or nothing was probed).
     * @return the decision.
     */
    public static SchemaVersionDecision decideComponentDdl(final SchemaVersionDecision versionDecision,
            final boolean storedRowIsCurrent, @Nullable final Set<String> missingRelations) {
        if (versionDecision == SchemaVersionDecision.APPLY && storedRowIsCurrent && missingRelations != null
                && missingRelations.isEmpty()) {
            return SchemaVersionDecision.UP_TO_DATE;
        }
        return versionDecision;
    }

    private static SchemaVersionDecision decideNewer(final String refusal, final String component,
            final int currentVersion, final int storedVersion, @Nullable final Integer storedMinCompatibleVersion) {
        // A row written before min_compatible_version existed is compatible with nothing older than itself.
        final int minCompatibleVersion =
                storedMinCompatibleVersion != null ? storedMinCompatibleVersion : storedVersion;
        if (minCompatibleVersion <= currentVersion) {
            return SchemaVersionDecision.COMPATIBLE_NEWER;
        }
        throw new SchemaBootException(refusal + ": stored schema version " + storedVersion
                + " (min_compatible_version " + minCompatibleVersion + ") for component '" + component
                + "' is NEWER than this code's version " + currentVersion
                + " and not compatible with it — schema downgrade detected. Upgrade this service "
                + "or restore the matching database. See deployment/postgres/README.md#schema-upgrades.");
    }

    /**
     * Verifies the live catalog of a single table against its canonical contract: primary-key definition and column
     * data types. Collation is <strong>not</strong> checked by this overload — use
     * {@link #verifyTable(TableContract, String, Map, Map)} to additionally catch collation drift on the
     * {@code COLLATE "C"} persistence-id columns.
     *
     * @param contract the expected PK definition + column types.
     * @param actualPrimaryKeyDef the {@code pg_get_constraintdef} of the table's PK, or {@code null} if absent.
     * @param actualColumns the actual {@code column name → data_type} map read from {@code information_schema.columns}.
     * @throws SchemaBootException on any PK or column divergence.
     */
    public static void verifyTable(final TableContract contract,
            @Nullable final String actualPrimaryKeyDef,
            final Map<String, String> actualColumns) {
        verifyPrimaryKeyAndColumns(contract, actualPrimaryKeyDef, actualColumns);
    }

    /**
     * Verifies the live catalog of a single table against its canonical contract: primary-key definition, column data
     * types, and the byte-ordered collation of the persistence-id columns.
     * <p>
     * The collation check exists because {@code information_schema.columns.data_type} reports {@code text} regardless
     * of collation, so a pre-existing table whose {@code pid} column silently lacks {@code COLLATE "C"} would pass the
     * data-type check yet break {@code pid > $1 ORDER BY pid} cursor pagination and primary-key ordering. A column with
     * no explicit collation reports {@code collation_name = NULL} (it inherits the database default), so for any column
     * the contract marks as collated, a {@code null}/absent collation is itself a drift failure.
     * </p>
     *
     * @param contract the expected PK definition, column types, and collated columns.
     * @param actualPrimaryKeyDef the {@code pg_get_constraintdef} of the table's PK, or {@code null} if absent.
     * @param actualColumns the actual {@code column name → data_type} map read from {@code information_schema.columns}.
     * @param actualCollations the actual {@code column name → collation_name} map read from
     * {@code information_schema.columns} (a {@code null} value / absent entry means the column has no explicit
     * collation and uses the database default).
     * @throws SchemaBootException on any PK, column, or collation divergence.
     */
    public static void verifyTable(final TableContract contract,
            @Nullable final String actualPrimaryKeyDef,
            final Map<String, String> actualColumns,
            final Map<String, String> actualCollations) {

        verifyPrimaryKeyAndColumns(contract, actualPrimaryKeyDef, actualColumns);

        for (final String collatedColumn : contract.collatedColumns()) {
            final String actualCollation = actualCollations.get(collatedColumn);
            if (actualCollation == null || !actualCollation.equals(PID_COLLATION)) {
                throw new SchemaBootException(
                        "Refusing to boot: table '" + contract.tableName() + "' column '" + collatedColumn
                                + "' has collation_name '" + actualCollation + "' but expected '"
                                + PID_COLLATION + "'. A pre-existing table whose persistence-id column "
                                + "lacks COLLATE \"" + PID_COLLATION + "\" breaks pid cursor pagination "
                                + "and primary-key ordering; a NULL collation means the column uses the database "
                                + "default rather than the required byte-ordered collation.");
            }
        }
    }

    /**
     * Verifies all canonical table contracts (PK + data types only; no collation check).
     *
     * @param contracts the canonical contracts ({ PostgresSchemaDescriptor#tableContracts()}).
     * @param actualPrimaryKeyDefs table name → actual PK definition (null entry / missing key = absent).
     * @param actualColumnsByTable table name → (column name → data_type).
     * @throws SchemaBootException on the first divergence.
     */
    public static void verifyAllTables(final List<TableContract> contracts,
            final Map<String, String> actualPrimaryKeyDefs,
            final Map<String, Map<String, String>> actualColumnsByTable) {
        for (final TableContract contract : contracts) {
            verifyTable(contract, actualPrimaryKeyDefs.get(contract.tableName()),
                    actualColumnsByTable.getOrDefault(contract.tableName(), Map.of()));
        }
    }

    /**
     * Verifies all canonical table contracts including the persistence-id collation.
     *
     * @param contracts the canonical contracts ({ PostgresSchemaDescriptor#tableContracts()}).
     * @param actualPrimaryKeyDefs table name → actual PK definition (null entry / missing key = absent).
     * @param actualColumnsByTable table name → (column name → data_type).
     * @param actualCollationsByTable table name → (column name → collation_name); a missing table or column means no
     * explicit collation.
     * @throws SchemaBootException on the first divergence.
     */
    public static void verifyAllTables(final List<TableContract> contracts,
            final Map<String, String> actualPrimaryKeyDefs,
            final Map<String, Map<String, String>> actualColumnsByTable,
            final Map<String, Map<String, String>> actualCollationsByTable) {
        for (final TableContract contract : contracts) {
            verifyTable(contract, actualPrimaryKeyDefs.get(contract.tableName()),
                    actualColumnsByTable.getOrDefault(contract.tableName(), Map.of()),
                    actualCollationsByTable.getOrDefault(contract.tableName(), Map.of()));
        }
    }

    private static void verifyPrimaryKeyAndColumns(final TableContract contract,
            @Nullable final String actualPrimaryKeyDef,
            final Map<String, String> actualColumns) {

        if (actualPrimaryKeyDef == null || !normalizePk(actualPrimaryKeyDef).equals(normalizePk(contract.primaryKeyDef()))) {
            throw new SchemaBootException(
                    "Refusing to boot: table '" + contract.tableName() + "' has a divergent primary key. Expected '"
                            + contract.primaryKeyDef() + "' but catalog reports '" + actualPrimaryKeyDef
                            + "'. CREATE TABLE IF NOT EXISTS silently kept a pre-existing divergent table.");
        }

        for (final Map.Entry<String, String> expected : contract.columns().entrySet()) {
            final String actualType = actualColumns.get(expected.getKey());
            if (actualType == null) {
                throw new SchemaBootException(
                        "Refusing to boot: table '" + contract.tableName() + "' is missing expected column '"
                                + expected.getKey() + "'.");
            }
            if (!actualType.equalsIgnoreCase(expected.getValue())) {
                throw new SchemaBootException(
                        "Refusing to boot: table '" + contract.tableName() + "' column '" + expected.getKey()
                                + "' has data_type '" + actualType + "' but expected '" + expected.getValue()
                                + "'.");
            }
        }
    }

    private static String normalizePk(final String pk) {
        // pg_get_constraintdef returns e.g. "PRIMARY KEY (pid, sn)"; normalise whitespace + case for comparison.
        return pk.replaceAll("\\s+", " ").trim().toUpperCase();
    }

}
