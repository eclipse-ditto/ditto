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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Bootstraps a component's PostgreSQL schema programmatically, driven by a {@link PostgresSchemaDescriptor}.
 * <p>
 * The manager is backend-agnostic: it owns everything shared across every Postgres-backed component — the
 * {@code schema_version} bookkeeping table ({@link #SCHEMA_VERSION_DDL}) and the cluster-wide first-boot advisory lock
 * ({@link #ADVISORY_LOCK_KEY}) — and applies them for whichever descriptor it is given. The descriptor supplies only its
 * own DDL statements, table contracts, component name and version.
 * </p>
 * <p>
 * On the bootstrap's connection, before the transaction begins, {@code SHOW server_version_num} checks the server
 * version: a server older than PostgreSQL 16 (the oldest tested version) gets a WARN naming the real requirements
 * ({@link PostgresServerVersion}). The check never refuses a boot and never fails the bootstrap; when the version cannot
 * be read it also only logs a WARN. It runs outside the transaction, so even a failing {@code SHOW} cannot abort it.
 * </p>
 * <p>
 * The whole bootstrap is a <strong>single transaction</strong>:
 * </p>
 * <ol>
 *     <li>{@code pg_advisory_xact_lock(<constant>)} — a transaction-scoped advisory lock (NOT session
 *     {@code pg_advisory_lock}, which is unsafe over a pooled connection because the lock could be acquired and
 *     released on different physical connections); auto-released on commit. The key is SHARED across every component so
 *     concurrent first-boot DDL against one fresh database is serialised.</li>
 *     <li>the shared {@code schema_version} table ({@link #SCHEMA_VERSION_DDL}) plus, only while it is missing, the
 *     manager-owned {@code min_compatible_version} column ({@link #SCHEMA_VERSION_COMPATIBILITY_DDL}).</li>
 *     <li><strong>read the component's {@code schema_version} row and decide</strong>
 *     ({@link SchemaVerifier#verifyStoredVersion}) before any component DDL runs: apply, run against a newer compatible
 *     schema without touching it, or refuse to boot (same-version checksum drift, or a newer incompatible schema).</li>
 *     <li>{@link SchemaVersionDecision#APPLY} only: all of the descriptor's {@code CREATE TABLE}/{@code CREATE
 *     INDEX}/{@code ALTER TABLE … autovacuum} statements (each idempotent) — <strong>unless</strong> the stored row has
 *     this code's version and checksum and every table and index the DDL creates exists
 *     ({@link #namedRelations(PostgresSchemaDescriptor)}, looked up with {@code to_regclass}, no table lock): then
 *     {@link SchemaVersionDecision#UP_TO_DATE}, no DDL. When the DDL runs, it runs after
 *     {@link #SET_DDL_LOCK_TIMEOUT}.</li>
 *     <li><strong>live-catalog verification</strong> (all decisions) — {@code pg_get_constraintdef} on every PK +
 *     {@code information_schema.columns} per table; refuse-to-boot on any divergence (because
 *     {@code CREATE TABLE IF NOT EXISTS} silently keeps a pre-existing divergent table). Extra columns and extra or
 *     missing indexes do not fail it, so it holds against a newer compatible schema too.</li>
 *     <li>{@link SchemaVersionDecision#APPLY} and {@link SchemaVersionDecision#UP_TO_DATE}: upsert of the row (version,
 *     checksum, {@code min_compatible_version}). A {@link SchemaVersionDecision#COMPATIBLE_NEWER} boot never writes the
 *     row, so an older pod can't flip the schema or the version back.</li>
 *     <li>commit — the advisory xact lock auto-releases.</li>
 * </ol>
 * <p>
 * There is deliberately no {@code SELECT indisvalid} rebuild step: invalid indexes only arise from {@code CREATE INDEX
 * CONCURRENTLY}, which is never used here and cannot run in a transaction; plain {@code CREATE INDEX} rolls back
 * cleanly. The bootstrap blocks the calling (startup) thread — it is a one-time step run before any actor starts. A
 * manager created with a {@link PostgresConfig.BootstrapConfig} retries it while PostgreSQL is not reachable yet
 * ({@link #bootstrap(Mono, Mono)}); the runtime self-heal is never retried.
 * </p>
 * <h2>Same-version boots</h2>
 * <p>
 * A start whose stored row is current and whose tables and indexes all exist takes no lock on the component's tables,
 * so it never waits for (nor deadlocks) a concurrent index build such as a {@code CREATE INDEX CONCURRENTLY}. The
 * consequence: a manually dropped table or index is re-created at the next start, but a manually reset reloption is
 * not re-applied until the next version bump or a self-heal that runs the DDL.
 * </p>
 * <h2>Checksum semantics</h2>
 * The {@code schema_version} checksum is computed over the <em>effective</em> DDL — the shared {@code schema_version}
 * table statement followed by the descriptor's own statements ({@link #effectiveDdl(PostgresSchemaDescriptor)}), i.e.
 * exactly the DDL the bootstrap executes — so the checksum can never drift away from what is actually run. The one
 * deliberate exception is the manager-owned {@link #SCHEMA_VERSION_COMPATIBILITY_DDL}: it is bookkeeping for the
 * {@code schema_version} table itself and is kept <strong>out</strong> of the checksum, so adding it moved no deployed
 * component's checksum.
 */
public final class PostgresSchemaManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSchemaManager.class);

    /**
     * The constant advisory-lock key. Arbitrary but fixed; serialises concurrent first-boot DDL across the cluster.
     * ("DITTO" in a mnemonic encoding.) SHARED across every component so a single fresh database's first-boot DDL is
     * serialised regardless of which component bootstraps first.
     */
    static final long ADVISORY_LOCK_KEY = 0x0D17_70_5C_4E_4AL;

    /**
     * The single, shared {@code schema_version(component, version, checksum)} table DDL, owned by the manager and
     * prepended to every descriptor's own statements. Each component owns one row (keyed by {@code component}) that
     * backs the refuse-to-boot checksum/version guard.
     */
    public static final String SCHEMA_VERSION_DDL = "CREATE TABLE IF NOT EXISTS schema_version ("
            + "component TEXT PRIMARY KEY, "
            + "version INT NOT NULL, "
            + "checksum TEXT NOT NULL"
            + ")";

    /**
     * Adds the {@code min_compatible_version} column to the shared {@code schema_version} table (rolling upgrades, see
     * {@link PostgresSchemaDescriptor#minCompatibleVersion()}). Executed by the manager right after
     * {@link #SCHEMA_VERSION_DDL} in both the boot and the self-heal transaction, but deliberately <strong>not</strong>
     * part of {@link #effectiveDdl(PostgresSchemaDescriptor)}: {@link #SCHEMA_VERSION_DDL} is part of every component's
     * pinned checksum, so changing it (or adding to the checksummed list) would make every deployed database refuse to
     * boot with checksum drift. Rows written before the column existed read as {@code NULL}, which counts as
     * {@code min_compatible_version = version}.
     * <p>
     * Issued <strong>only while the column is missing</strong> (probed in {@code pg_attribute} under the same advisory
     * lock): PostgreSQL checks table ownership and takes an {@code ACCESS EXCLUSIVE} lock before it evaluates
     * {@code IF NOT EXISTS}, so an unconditional statement would make every boot and heal need ownership of
     * {@code schema_version}. The one-time addition itself needs ownership (the DDL role that created the table).
     * </p>
     */
    public static final String SCHEMA_VERSION_COMPATIBILITY_DDL =
            "ALTER TABLE schema_version ADD COLUMN IF NOT EXISTS min_compatible_version INT";

    /**
     * Whether {@code schema_version} (resolved through the {@code search_path}, exactly like the unqualified name in
     * {@link #SCHEMA_VERSION_COMPATIBILITY_DDL}) already has the {@code min_compatible_version} column. Reads
     * {@code pg_attribute}, which needs no privilege on the table.
     */
    private static final String COMPATIBILITY_COLUMN_EXISTS_SQL = "SELECT EXISTS (SELECT 1 FROM pg_attribute "
            + "WHERE attrelid = to_regclass('schema_version') AND attname = 'min_compatible_version' "
            + "AND NOT attisdropped)";

    /**
     * How long each statement of a component's DDL may wait for a lock: below PostgreSQL's default
     * {@code deadlock_timeout} (1 s), so a boot that meets a running {@code CREATE INDEX CONCURRENTLY} gives up before
     * the deadlock detector can abort that build, and writers queued behind the boot's lock request wait at most this
     * long. A boot that times out fails like any failed boot (the transaction is rolled back, nothing changed).
     */
    static final String DDL_LOCK_TIMEOUT = "500ms";

    /**
     * Issued right before the component DDL, inside the bootstrap (or heal) transaction and after the advisory lock.
     * {@code SET LOCAL} ends with the transaction and, unlike a startup parameter, also holds behind a
     * transaction-mode PgBouncer.
     */
    static final String SET_DDL_LOCK_TIMEOUT = "SET LOCAL lock_timeout = '" + DDL_LOCK_TIMEOUT + "'";

    /**
     * The tables and indexes of {@code $1} that do not exist in {@code current_schema()} — the schema
     * {@code CREATE TABLE/INDEX IF NOT EXISTS} and {@link #verifyCatalog} use. {@code to_regclass} reads the catalog
     * only: no lock on any of the relations.
     */
    static final String MISSING_RELATIONS_SQL = "SELECT relation FROM unnest($1::text[]) AS named(relation) "
            + "WHERE to_regclass(format('%I.%I', current_schema(), relation)) IS NULL";

    /** SQLSTATE {@code lock_not_available}: raised when {@link #DDL_LOCK_TIMEOUT} expires. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private static final Duration DEFAULT_BOOTSTRAP_TIMEOUT = Duration.ofSeconds(60L);

    private final ConnectionFactory ddlConnectionFactory;
    private final PostgresSchemaDescriptor descriptor;
    private final Duration bootstrapTimeout;
    @Nullable private final PostgresConfig.BootstrapConfig bootstrapConfig;

    private PostgresSchemaManager(final ConnectionFactory ddlConnectionFactory,
            final PostgresSchemaDescriptor descriptor, final Duration bootstrapTimeout,
            @Nullable final PostgresConfig.BootstrapConfig bootstrapConfig) {
        this.ddlConnectionFactory = ddlConnectionFactory;
        this.descriptor = validate(Objects.requireNonNull(descriptor, "descriptor"));
        this.bootstrapTimeout = bootstrapTimeout;
        this.bootstrapConfig = bootstrapConfig;
    }

    private static PostgresSchemaDescriptor validate(final PostgresSchemaDescriptor descriptor) {
        final int minCompatibleVersion = descriptor.minCompatibleVersion();
        if (minCompatibleVersion < 1 || minCompatibleVersion > descriptor.version()) {
            throw new IllegalArgumentException("Schema descriptor for component '" + descriptor.component()
                    + "' declares minCompatibleVersion " + minCompatibleVersion + ", which must lie within [1, version "
                    + descriptor.version() + "].");
        }
        return descriptor;
    }

    /**
     * Creates a schema manager that runs the given descriptor's DDL through the given (DDL-role) connection factory.
     *
     * @param ddlConnectionFactory a connection factory authenticated as the schema-owning DDL role.
     * @param descriptor the component schema to bootstrap.
     * @return the manager.
     */
    public static PostgresSchemaManager of(final ConnectionFactory ddlConnectionFactory,
            final PostgresSchemaDescriptor descriptor) {
        return new PostgresSchemaManager(ddlConnectionFactory, descriptor, DEFAULT_BOOTSTRAP_TIMEOUT, null);
    }

    /**
     * Creates a schema manager with an explicit bootstrap timeout (for tests).
     *
     * @param ddlConnectionFactory a connection factory authenticated as the schema-owning DDL role.
     * @param descriptor the component schema to bootstrap.
     * @param bootstrapTimeout how long to wait for the bootstrap transaction to complete.
     * @return the manager.
     */
    public static PostgresSchemaManager of(final ConnectionFactory ddlConnectionFactory,
            final PostgresSchemaDescriptor descriptor, final Duration bootstrapTimeout) {
        return new PostgresSchemaManager(ddlConnectionFactory, descriptor, bootstrapTimeout, null);
    }

    /**
     * Creates a schema manager whose bootstrap waits for a PostgreSQL that is not reachable yet, as configured
     * ({@code ditto.postgresql.bootstrap}): every attempt is bounded by the attempt timeout, failures that mean "not
     * reachable or not ready (yet)" are retried with backoff within the retry budget, and every other failure ends the
     * bootstrap at once. The production entry point is
     * {@code org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSchemaBootstrapper}.
     *
     * @param ddlConnectionFactory a connection factory authenticated as the schema-owning DDL role.
     * @param descriptor the component schema to bootstrap.
     * @param bootstrapConfig the attempt timeout and retry settings.
     * @return the manager.
     */
    public static PostgresSchemaManager of(final ConnectionFactory ddlConnectionFactory,
            final PostgresSchemaDescriptor descriptor, final PostgresConfig.BootstrapConfig bootstrapConfig) {
        Objects.requireNonNull(bootstrapConfig, "bootstrapConfig");
        return new PostgresSchemaManager(ddlConnectionFactory, descriptor, bootstrapConfig.getAttemptTimeout(),
                bootstrapConfig);
    }

    /**
     * The effective DDL executed by (and checksummed for) a component: the shared {@code schema_version} table followed
     * by the descriptor's own statements. This is exactly the pre-extraction statement list (the {@code schema_version}
     * statement was historically element 0 of each descriptor's own list), so the persisted checksum is preserved
     * across the postgres-client extraction. {@link #SCHEMA_VERSION_COMPATIBILITY_DDL} is deliberately not part of it.
     *
     * @param descriptor the component schema.
     * @return the ordered effective DDL statements.
     */
    public static List<String> effectiveDdl(final PostgresSchemaDescriptor descriptor) {
        final List<String> statements = new ArrayList<>();
        statements.add(SCHEMA_VERSION_DDL);
        statements.addAll(descriptor.ddlStatements());
        return List.copyOf(statements);
    }

    /**
     * @param descriptor the component schema.
     * @return the {@code schema_version} checksum for the component (SHA-256 over the effective DDL).
     */
    public static String checksum(final PostgresSchemaDescriptor descriptor) {
        return SchemaChecksum.compute(effectiveDdl(descriptor));
    }

    /**
     * The tables and indexes the descriptor's own DDL creates, by name, in statement order — what the same-version
     * fast path looks up before it skips the component DDL ({@link SchemaVersionDecision#UP_TO_DATE}). Empty when a
     * statement creates something whose name cannot be read; every boot then runs the DDL.
     *
     * @param descriptor the component schema.
     * @return the relation names, or an empty {@code Optional}.
     */
    public static Optional<List<String>> namedRelations(final PostgresSchemaDescriptor descriptor) {
        return SchemaDdlRelations.of(descriptor.ddlStatements());
    }

    /**
     * Runs the bootstrap synchronously, blocking until commit or failure. A manager created without a
     * {@link PostgresConfig.BootstrapConfig} makes exactly one attempt bounded by its bootstrap timeout (unchanged);
     * one created with it runs {@link #bootstrap(Mono, Mono)} without precondition and abort signal.
     *
     * @throws SchemaBootException on a same-version checksum mismatch, a newer stored version this code is not
     * compatible with, or a live-catalog divergence (refuse to boot).
     */
    public void bootstrap() {
        if (bootstrapConfig != null) {
            bootstrap(Mono.empty(), Mono.never());
        } else {
            logOutcome(bootstrapWithOutcome(new AtomicBoolean()).block(bootstrapTimeout));
        }
    }

    /**
     * Runs the bootstrap synchronously with the configured retry (see
     * {@link #of(ConnectionFactory, PostgresSchemaDescriptor, PostgresConfig.BootstrapConfig)}). The unit that is
     * retried is the whole single-transaction bootstrap on a fresh connection, preceded by {@code precondition}; a
     * failed attempt has rolled back completely, so the next attempt reads the {@code schema_version} row and decides
     * again.
     *
     * @param precondition subscribed at the start of every attempt, before the bootstrap's connection is opened and
     * inside the attempt timeout, e.g. a check of database prerequisites; its failures are classified like the
     * bootstrap's own. It must be lazy, since it is re-subscribed for every attempt.
     * @param abortSignal completing it ends the wait at once, cancelling an attempt in flight.
     * @throws IllegalStateException if the manager was created without a {@link PostgresConfig.BootstrapConfig}.
     * @throws SchemaBootException on a refusal to boot; never retried.
     * @throws SchemaBootstrapUnavailableException when PostgreSQL stayed unreachable for the whole retry budget.
     * @throws SchemaBootstrapTimeoutException when an attempt did not finish within the attempt timeout.
     * @throws SchemaBootstrapAbortedException when {@code abortSignal} completed first.
     */
    public void bootstrap(final Mono<Void> precondition, final Mono<Void> abortSignal) {
        if (bootstrapConfig == null) {
            throw new IllegalStateException("The PostgreSQL schema manager of component '" + descriptor.component()
                    + "' has no bootstrap configuration; create it with a BootstrapConfig to bootstrap with retries.");
        }
        final AtomicBoolean serverVersionChecked = new AtomicBoolean();
        logOutcome(SchemaBootstrapRetry.of(descriptor.component(), bootstrapConfig)
                .run(() -> precondition.then(Mono.defer(() -> bootstrapWithOutcome(serverVersionChecked))),
                        abortSignal)
                .block());
    }

    private void logOutcome(@Nullable final BootstrapOutcome outcome) {
        if (outcome != null && outcome.decision() == SchemaVersionDecision.COMPATIBLE_NEWER) {
            LOGGER.info("PostgreSQL schema bootstrap complete for component '{}': running against the newer, "
                            + "compatible stored schema version {} (code version {}); no DDL run, schema_version row "
                            + "unchanged.",
                    descriptor.component(), outcome.storedVersion(), descriptor.version());
        } else if (outcome != null && outcome.decision() == SchemaVersionDecision.UP_TO_DATE) {
            LOGGER.info("PostgreSQL schema bootstrap complete for component '{}' (version {}, checksum {}): every "
                            + "table and index of the schema exists, so no DDL was run.",
                    descriptor.component(), descriptor.version(), checksum(descriptor));
        } else {
            LOGGER.info("PostgreSQL schema bootstrap complete for component '{}' (version {}, checksum {}).",
                    descriptor.component(), descriptor.version(), checksum(descriptor));
        }
    }

    /**
     * @return the bootstrap as a reactive pipeline (single transaction, released on every terminal path).
     */
    public Mono<Void> bootstrapReactive() {
        return bootstrapWithOutcome(new AtomicBoolean()).then();
    }

    private Mono<BootstrapOutcome> bootstrapWithOutcome(final AtomicBoolean serverVersionChecked) {
        return Mono.usingWhen(
                Mono.from(ddlConnectionFactory.create()),
                connection -> bootstrapWithin(connection, serverVersionChecked),
                Connection::close);
    }

    /**
     * The <strong>heal</strong> sibling of {@link #bootstrapReactive()}, used by the runtime schema self-heal
     * when a statement hits a missing table ({@code 42P01}). It runs the exact same idempotent DDL
     * inside one transaction, guarded by the <em>same</em> {@link #ADVISORY_LOCK_KEY} advisory xact lock as the boot
     * bootstrap (so a hot heal serialises against a concurrent first-boot bootstrap cluster-wide), but deliberately
     * <strong>omits</strong> the live-catalog verification and the {@code schema_version} row upsert.
     * <p>
     * Those two steps are the refuse-to-boot guards that throw {@link SchemaBootException} on any catalog/checksum
     * divergence — correct at boot, but wrong for a hot heal path: a single missing table must not trigger a
     * full-catalog verify that could fail for an unrelated reason. The DDL is idempotent, so re-running it when only
     * some tables are missing is safe.
     * </p>
     * <p>
     * Like the boot bootstrap, the heal first reads the component's {@code schema_version} row and applies the "newer
     * stored version" rule ({@link SchemaVerifier#verifyStoredVersionForHeal}): when a peer running newer code has
     * already advanced the schema, this code's DDL must not run against it (it could re-create what the newer version
     * dropped). A newer <em>compatible</em> schema makes the heal a no-op (the caller's single retry then decides); a
     * newer <em>incompatible</em> one fails the heal with a {@link SchemaBootException}, so the original missing-table
     * error surfaces.
     * </p>
     * <p>
     * The heal follows the boot's same-version fast path: when the stored row has this code's version and checksum and
     * every table and index of the DDL exists, it runs no DDL ({@link SchemaVersionDecision#UP_TO_DATE}); when it runs
     * DDL, it does so after {@link #SET_DDL_LOCK_TIMEOUT}.
     * </p>
     *
     * @return the heal as a reactive pipeline (single transaction, connection released on every terminal
     * path), emitting {@link SchemaVersionDecision#APPLY} when the DDL ran, and
     * {@link SchemaVersionDecision#UP_TO_DATE} or {@link SchemaVersionDecision#COMPATIBLE_NEWER} when it deliberately
     * did not.
     */
    public Mono<SchemaVersionDecision> ensureTablesReactive() {
        return Mono.usingWhen(
                Mono.from(ddlConnectionFactory.create()),
                this::ensureWithin,
                Connection::close);
    }

    Mono<SchemaVersionDecision> ensureWithin(final Connection connection) {
        return Mono.from(connection.beginTransaction())
                // Same advisory xact lock as the boot bootstrap -> a hot heal serialises against a concurrent first-boot
                // DDL cluster-wide; auto-released on commit.
                .then(executeUpdate(connection, "SELECT pg_advisory_xact_lock($1)", ADVISORY_LOCK_KEY))
                // The shared schema_version table (+ compatibility column), then the row decides whether this code's
                // DDL may run at all. No catalog verification / row upsert (see Javadoc).
                .then(ensureSchemaVersionTable(connection))
                .then(readStoredVersion(connection))
                .flatMap(stored -> {
                    final SchemaVersionDecision versionDecision = SchemaVerifier.verifyStoredVersionForHeal(
                            descriptor.component(), descriptor.version(), stored.version(),
                            stored.minCompatibleVersion());
                    if (versionDecision == SchemaVersionDecision.COMPATIBLE_NEWER) {
                        logCompatibleNewer("self-heal", stored);
                        return Mono.just(versionDecision);
                    }
                    return decideComponentDdl(connection, "self-heal", versionDecision, stored, checksum(descriptor))
                            .flatMap(decision -> {
                                if (decision == SchemaVersionDecision.UP_TO_DATE) {
                                    LOGGER.info("PostgreSQL schema self-heal for component '{}': every table and "
                                            + "index of the schema exists; running no DDL.", descriptor.component());
                                    return Mono.just(decision);
                                }
                                return runComponentDdl(connection, "self-heal").thenReturn(decision);
                            });
                })
                .flatMap(decision -> Mono.from(connection.commitTransaction()).thenReturn(decision))
                .onErrorResume(error -> Mono.from(connection.rollbackTransaction())
                        .then(Mono.error(error)));
    }

    private Mono<BootstrapOutcome> bootstrapWithin(final Connection connection,
            final AtomicBoolean serverVersionChecked) {
        final String expectedChecksum = checksum(descriptor);
        // 0) server version: WARN below PostgreSQL 16, once per bootstrap call even when attempts are retried; never
        //    fails, and runs before the transaction so that even a failing SHOW cannot abort it.
        final Mono<Void> serverVersionCheck = serverVersionChecked.compareAndSet(false, true)
                ? checkServerVersion(connection, descriptor.component())
                : Mono.empty();
        return serverVersionCheck
                .then(Mono.defer(() -> Mono.from(connection.beginTransaction())))
                // 1) transaction-scoped advisory lock — serialises concurrent first-boot DDL across the cluster.
                .then(executeUpdate(connection, "SELECT pg_advisory_xact_lock($1)", ADVISORY_LOCK_KEY))
                // 2) the shared schema_version table + the manager-owned compatibility column.
                .then(ensureSchemaVersionTable(connection))
                // 3) read the row and decide BEFORE any component DDL runs (refuses on drift / incompatible newer).
                .then(readStoredVersion(connection))
                .flatMap(stored -> {
                    final SchemaVersionDecision versionDecision = SchemaVerifier.verifyStoredVersion(
                            descriptor.component(), expectedChecksum, descriptor.version(), stored.checksum(),
                            stored.version(), stored.minCompatibleVersion());
                    if (versionDecision == SchemaVersionDecision.COMPATIBLE_NEWER) {
                        logCompatibleNewer("boot", stored);
                        // No DDL, no row write: verify only the catalog facts this code needs.
                        return verifyCatalog(connection)
                                .thenReturn(new BootstrapOutcome(versionDecision, stored.version()));
                    }
                    // 4) same version + checksum and every table/index present: no DDL, no table lock; otherwise the
                    //    component's DDL under the DDL lock timeout.
                    return decideComponentDdl(connection, "boot", versionDecision, stored, expectedChecksum)
                            .flatMap(decision -> (decision == SchemaVersionDecision.UP_TO_DATE
                                    ? Mono.<Void>empty()
                                    : runComponentDdl(connection, "boot"))
                                    // 5) live-catalog verification inside the same txn (both paths).
                                    .then(verifyCatalog(connection))
                                    // 6) insert / advance the row (unchanged values on the up-to-date path).
                                    .then(upsertRow(connection, expectedChecksum))
                                    .thenReturn(new BootstrapOutcome(decision, stored.version())));
                })
                // 7) commit -> advisory xact lock auto-releases.
                .flatMap(outcome -> Mono.from(connection.commitTransaction()).thenReturn(outcome))
                .onErrorResume(error -> Mono.from(connection.rollbackTransaction())
                        .then(Mono.error(error)));
    }

    /**
     * Reads {@code server_version_num} on the given connection and logs a WARN when the server is older than
     * PostgreSQL 16 ({@link PostgresServerVersion#warning(String, int)}), or when the version cannot be read. Never
     * fails: every error is logged and dropped, so the check can never refuse a boot.
     *
     * @param connection the bootstrap's connection, outside any transaction.
     * @param component the component whose schema is bootstrapped, named in the WARN.
     * @return completes empty once the version was checked.
     */
    static Mono<Void> checkServerVersion(final Connection connection, final String component) {
        return Flux.defer(() -> Flux.from(
                        connection.createStatement(PostgresServerVersion.SHOW_SERVER_VERSION_NUM).execute()))
                .flatMap(result -> result.map((row, meta) -> Objects.toString(row.get(0, String.class), "")))
                .next()
                .defaultIfEmpty("")
                .doOnNext(value -> {
                    final OptionalInt versionNum = PostgresServerVersion.parse(value);
                    if (versionNum.isEmpty()) {
                        LOGGER.warn("PostgreSQL server version could not be determined for component '{}': <{}> "
                                        + "returned <{}>. Ditto is tested with PostgreSQL 16, 17 and 18.", component,
                                PostgresServerVersion.SHOW_SERVER_VERSION_NUM, value);
                    } else {
                        PostgresServerVersion.warning(component, versionNum.getAsInt())
                                .ifPresentOrElse(LOGGER::warn,
                                        () -> LOGGER.debug("PostgreSQL server version {} (server_version_num {}) "
                                                        + "for component '{}'.",
                                                PostgresServerVersion.display(versionNum.getAsInt()),
                                                versionNum.getAsInt(), component));
                    }
                })
                .then()
                .onErrorResume(error -> {
                    LOGGER.warn("PostgreSQL server version could not be determined for component '{}': <{}> failed: "
                                    + "{}. Ditto is tested with PostgreSQL 16, 17 and 18.", component,
                            PostgresServerVersion.SHOW_SERVER_VERSION_NUM, error.toString());
                    return Mono.empty();
                });
    }

    /**
     * {@link #SCHEMA_VERSION_DDL} (element 0 of the checksummed effective DDL) followed, only while the column is
     * missing, by the manager-owned, non-checksummed {@link #SCHEMA_VERSION_COMPATIBILITY_DDL}. On
     * {@link SchemaVersionDecision#APPLY}, together with {@link #runComponentDdl(Connection, String)}, the checksummed
     * DDL executed is still exactly {@link #effectiveDdl(PostgresSchemaDescriptor)}, in order.
     */
    private static Mono<Void> ensureSchemaVersionTable(final Connection connection) {
        return executeUpdate(connection, SCHEMA_VERSION_DDL)
                .then(Mono.defer(() -> compatibilityColumnExists(connection)))
                .flatMap(exists -> exists
                        ? Mono.<Void>empty()
                        : executeUpdate(connection, SCHEMA_VERSION_COMPATIBILITY_DDL));
    }

    private static Mono<Boolean> compatibilityColumnExists(final Connection connection) {
        return Flux.from(connection.createStatement(COMPATIBILITY_COLUMN_EXISTS_SQL).execute())
                .flatMap(result -> result.map((row, meta) -> Boolean.TRUE.equals(row.get(0, Boolean.class))))
                .next()
                .defaultIfEmpty(false);
    }

    /**
     * The same-version fast path: probes the catalog only when the stored row is exactly this code's schema (see
     * {@link SchemaVerifier#decideComponentDdl}).
     */
    private Mono<SchemaVersionDecision> decideComponentDdl(final Connection connection, final String phase,
            final SchemaVersionDecision versionDecision, final StoredSchemaVersion stored,
            final String expectedChecksum) {
        final boolean storedRowIsCurrent = SchemaVerifier.storedRowIsCurrent(descriptor.version(), expectedChecksum,
                stored.version(), stored.checksum());
        if (versionDecision != SchemaVersionDecision.APPLY || !storedRowIsCurrent) {
            return Mono.just(versionDecision);
        }
        final Optional<List<String>> relations = namedRelations(descriptor);
        if (relations.isEmpty()) {
            LOGGER.info("PostgreSQL schema {} for component '{}': its DDL creates a relation whose name cannot be "
                    + "read, so the DDL runs although the stored schema is current.", phase, descriptor.component());
            return Mono.just(SchemaVerifier.decideComponentDdl(versionDecision, true, null));
        }
        return missingRelations(connection, relations.get())
                .map(missing -> {
                    if (!missing.isEmpty()) {
                        LOGGER.info("PostgreSQL schema {} for component '{}': missing {}; running the component DDL.",
                                phase, descriptor.component(), missing);
                    }
                    return SchemaVerifier.decideComponentDdl(versionDecision, true, missing);
                });
    }

    private static Mono<Set<String>> missingRelations(final Connection connection, final List<String> names) {
        if (names.isEmpty()) {
            return Mono.just(Set.of());
        }
        return Flux.from(connection.createStatement(MISSING_RELATIONS_SQL)
                        .bind(0, names.toArray(String[]::new))
                        .execute())
                .flatMap(result -> result.map((row, meta) -> row.get(0, String.class)))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .map(Collections::unmodifiableSet);
    }

    /**
     * The descriptor's own statements, i.e. the effective DDL minus the already executed {@link #SCHEMA_VERSION_DDL},
     * after {@link #SET_DDL_LOCK_TIMEOUT}: each statement waits at most {@link #DDL_LOCK_TIMEOUT} for a lock.
     */
    private Mono<Void> runComponentDdl(final Connection connection, final String phase) {
        return executeUpdate(connection, SET_DDL_LOCK_TIMEOUT)
                .thenMany(Flux.fromIterable(descriptor.ddlStatements())
                        .concatMap(ddl -> executeUpdate(connection, ddl)))
                .then()
                .doOnError(PostgresSchemaManager::isLockNotAvailable, error -> LOGGER.warn("PostgreSQL schema {} "
                                + "for component '{}' gave up after waiting {} for a table lock (lock_timeout); "
                                + "nothing was changed. Another session holds a conflicting lock on one of the "
                                + "component's tables, e.g. a running CREATE INDEX CONCURRENTLY or DROP INDEX "
                                + "CONCURRENTLY, a long transaction or an autovacuum.", phase, descriptor.component(),
                        DDL_LOCK_TIMEOUT));
    }

    private static boolean isLockNotAvailable(final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof R2dbcException r2dbcException
                    && LOCK_NOT_AVAILABLE.equals(r2dbcException.getSqlState())) {
                return true;
            }
        }
        return false;
    }

    private Mono<StoredSchemaVersion> readStoredVersion(final Connection connection) {
        return Flux.defer(() -> Flux.from(connection.createStatement(
                                        "SELECT version, checksum, min_compatible_version FROM schema_version "
                                                + "WHERE component = $1")
                                .bind(0, descriptor.component())
                                .execute()))
                .flatMap(result -> result.map((Row row, io.r2dbc.spi.RowMetadata meta) ->
                        new StoredSchemaVersion(row.get("version", Integer.class),
                                row.get("checksum", String.class),
                                row.get("min_compatible_version", Integer.class))))
                .next()
                .defaultIfEmpty(StoredSchemaVersion.ABSENT);
    }

    private void logCompatibleNewer(final String phase, final StoredSchemaVersion stored) {
        LOGGER.info("PostgreSQL schema {} for component '{}': stored schema version {} is newer than this code's "
                        + "version {} and declares min_compatible_version {} (<= {}), so it is compatible. Running no "
                        + "DDL and leaving the schema_version row unchanged (rolling upgrade in progress).",
                phase, descriptor.component(), stored.version(), descriptor.version(), stored.minCompatibleVersion(),
                descriptor.version());
    }

    private Mono<Void> verifyCatalog(final Connection connection) {
        return Mono.fromRunnable(() -> { })
                .thenMany(Flux.fromIterable(descriptor.tableContracts())
                        .concatMap(contract -> verifyTable(connection, contract)))
                .then();
    }

    private Mono<Void> verifyTable(final Connection connection, final TableContract contract) {
        final Mono<String> pkDef = Flux.from(connection.createStatement(
                        "SELECT pg_get_constraintdef(c.oid) AS def "
                                + "FROM pg_constraint c "
                                + "JOIN pg_class t ON c.conrelid = t.oid "
                                + "JOIN pg_namespace n ON t.relnamespace = n.oid "
                                + "WHERE t.relname = $1 AND n.nspname = current_schema() AND c.contype = 'p'")
                        .bind(0, contract.tableName())
                        .execute())
                .flatMap(result -> result.map((row, meta) -> row.get("def", String.class)))
                .next()
                .defaultIfEmpty("");

        // Read data_type AND collation_name in one pass: data_type alone cannot reveal a missing COLLATE "C" (it always
        // reports "text"), so the verifier additionally checks collation_name on the columns that require it.
        final Mono<List<ColumnInfo>> columnInfos = Flux.from(connection.createStatement(
                        "SELECT column_name, data_type, collation_name FROM information_schema.columns "
                                + "WHERE table_name = $1 AND table_schema = current_schema()")
                        .bind(0, contract.tableName())
                        .execute())
                .flatMap(result -> result.map((row, meta) -> new ColumnInfo(
                        row.get("column_name", String.class),
                        row.get("data_type", String.class),
                        row.get("collation_name", String.class))))
                .collectList();

        return Mono.zip(pkDef, columnInfos)
                .doOnNext(tuple -> {
                    final Map<String, String> types = new HashMap<>();
                    final Map<String, String> collations = new HashMap<>();
                    for (final ColumnInfo info : tuple.getT2()) {
                        types.put(info.columnName(), info.dataType());
                        // collation_name is NULL for columns without an explicit collation; the verifier treats a
                        // null/absent collation as drift for the columns that must be COLLATE "C".
                        collations.put(info.columnName(), info.collationName());
                    }
                    SchemaVerifier.verifyTable(contract,
                            tuple.getT1().isEmpty() ? null : tuple.getT1(), types, collations);
                })
                .then();
    }

    /**
     * Inserts the component's row or advances it to this code's version, checksum and {@code min_compatible_version}.
     * Only reached on {@link SchemaVersionDecision#APPLY} and {@link SchemaVersionDecision#UP_TO_DATE} (unchanged
     * values), i.e. never when the stored version is newer.
     */
    private Mono<Void> upsertRow(final Connection connection, final String checksum) {
        return executeUpdate(connection,
                "INSERT INTO schema_version (component, version, checksum, min_compatible_version) "
                        + "VALUES ($1, $2, $3, $4) "
                        + "ON CONFLICT (component) DO UPDATE SET version = excluded.version, "
                        + "checksum = excluded.checksum, min_compatible_version = excluded.min_compatible_version",
                descriptor.component(), descriptor.version(), checksum, descriptor.minCompatibleVersion());
    }

    /**
     * The {@code schema_version} row for the bootstrapped component, or {@link #ABSENT} on first boot (no row yet).
     * {@code minCompatibleVersion} is {@code null} for a row written before the column existed.
     */
    private record StoredSchemaVersion(@Nullable Integer version, @Nullable String checksum,
            @Nullable Integer minCompatibleVersion) {
        static final StoredSchemaVersion ABSENT = new StoredSchemaVersion(null, null, null);
    }

    /** What a boot decided, plus the stored version it decided on ({@code null} on first boot), for the final log. */
    private record BootstrapOutcome(SchemaVersionDecision decision, @Nullable Integer storedVersion) {}

    private static Mono<Void> executeUpdate(final Connection connection, final String sql, final Object... binds) {
        final io.r2dbc.spi.Statement statement = connection.createStatement(sql);
        for (int i = 0; i < binds.length; i++) {
            statement.bind(i, binds[i]);
        }
        return Flux.from(statement.execute())
                .flatMap(Result::getRowsUpdated)
                .then();
    }

    /** Exposed for tests: the ordered effective DDL + the catalog contracts that the bootstrap executes/verifies. */
    public Map<String, Object> describe() {
        final Map<String, Object> description = new LinkedHashMap<>();
        description.put("statements", effectiveDdl(descriptor));
        description.put("contracts", descriptor.tableContracts());
        description.put("checksum", checksum(descriptor));
        return description;
    }

    /**
     * A single row of {@code information_schema.columns} for a verified table. {@code collationName} is {@code null}
     * when the column declares no explicit collation (it then uses the database default).
     */
    private record ColumnInfo(String columnName, String dataType, @Nullable String collationName) {}

}
