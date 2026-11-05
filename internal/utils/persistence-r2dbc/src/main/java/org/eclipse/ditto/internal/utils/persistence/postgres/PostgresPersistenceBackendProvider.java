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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.api.BackendFamily;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsCollaborators;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsFactory;
import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.NoOpCloseable;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.SnapshotStreamingActor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresHealthChecker;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSchemaBootstrapper;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresTableNames;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * PostgreSQL-backed implementation of {@link PersistenceBackendProvider}.
 * <p>
 * It yields the Ditto Postgres Pekko plugin IDs ({@code ditto-postgres-*}) for each entity type and wraps a shared,
 * lazily-materialised {@link PostgresReadJournal}.
 * </p>
 * <h2>Explicit plugin-ID resolution — NO {@code pluralize()} heuristic</h2>
 * Unlike the Mongo provider's old {@code thing → things} guess (which returned the wrong default for {@code connection}),
 * this provider requires an <strong>explicit</strong> HOCON mapping per entity:
 * <pre>
 * ditto.extensions.persistence-backend-provider = {
 *   extension-class = "org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceBackendProvider"
 *   extension-config {
 *     plugin-ids {
 *       thing.journal       = "ditto-postgres-things-journal"
 *       thing.snapshot      = "ditto-postgres-things-snapshots"
 *       connection.journal  = "ditto-postgres-connections-journal"
 *       connection.snapshot = "ditto-postgres-connections-snapshots"
 *       # ...
 *     }
 *   }
 * }
 * </pre>
 * A missing {@code plugin-ids.<entity>.{journal|snapshot}} mapping is a {@link DittoConfigError} — fail clearly rather
 * than silently fabricate a wrong plugin ID. Plugin-ID lookups are pure config reads, so they are safe to fire during a
 * persistent actor's {@code Eventsourced} super-construction (the trait-init constraint that forbids constructor-injected
 * plugin-ID fields applies to the persistent actor, not to this provider).
 * <h2>Synchronized-lazy read journal</h2>
 * {@link #getReadJournal()} is {@code synchronized}-lazy, mirroring {@code MongoPersistenceBackendProvider} (NOT
 * {@code Suppliers.memoize}). The read journal is heavy — it builds a {@link DittoPostgresClient} connection pool — and
 * is only needed by Cleanup / PersistencePingActor / historical queries, never during actor super-construction, so it
 * must not be built eagerly in the constructor (that breaks test setups that use the in-memory persistence plugin and
 * never touch Postgres). {@code synchronized} (not CAS) avoids a loser-thread connection-pool leak under a race.
 */
public final class PostgresPersistenceBackendProvider implements PersistenceBackendProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresPersistenceBackendProvider.class);

    private static final String PLUGIN_IDS_PATH = "plugin-ids";
    private static final String JOURNAL_KEY = "journal";
    private static final String SNAPSHOT_KEY = "snapshot";

    /**
     * The fully-qualified {@code class =} values of the Postgres journal/snapshot Pekko persistence plugins, as shipped
     * in {@code ditto-postgres-persistence.conf} ({@code ditto-postgres-*.class}). The boot-time
     * {@code PersistenceBackendSelfCheck} asserts every resolved journal/snapshot plugin class is one of these.
     */
    private static final Set<String> EXPECTED_PLUGIN_CLASS_NAMES = Set.of(
            PostgresJournal.class.getName(),
            PostgresSnapshotStore.class.getName());

    /** The class name of this provider's read journal, asserted against by the boot-time self-check. */
    private static final String EXPECTED_READ_JOURNAL_CLASS_NAME = PostgresReadJournal.class.getName();

    /**
     * The {@code extension-config} key naming the entity prefix the shared read journal binds to (one of
     * {@link PostgresTableNames}'s known entities). A Ditto service persists a single entity type per actor system
     * (things, policies, connections, wot), so the read journal targets exactly one table set — exactly as
     * {@code MongoReadJournal} binds to one journal collection per service.
     * <p>
     * This key is <strong>required</strong> when the Postgres profile is active: there is NO silent default (a
     * fabricated {@code "things"} default would have pointed a policies/connectivity service's read journal at the
     * {@code things_*} tables, returning the wrong rows). Each consuming service sets it explicitly to its own entity
     * ({@code things.conf="things"}, {@code policies.conf="policies"}, {@code connectivity.conf="connections"}). A
     * missing value is a {@link DittoConfigError}.
     */
    private static final String READ_JOURNAL_ENTITY_PATH = "read-journal.entity";

    private final ActorSystem actorSystem;
    private final Config extensionConfig;

    @Nullable
    private DittoReadJournal readJournal;
    @Nullable
    private DittoPostgresClient client;
    @Nullable
    private PersistenceOperationsFactory operationsFactory;

    /**
     * Constructor invoked reflectively by {@code PekkoClassLoader} during extension loading.
     *
     * @param actorSystem the actor system the extension is loaded into.
     * @param extensionConfig the {@code extension-config} subtree of the extension declaration.
     */
    public PostgresPersistenceBackendProvider(final ActorSystem actorSystem, final Config extensionConfig) {
        this.actorSystem = Objects.requireNonNull(actorSystem, "actorSystem");
        this.extensionConfig = Objects.requireNonNull(extensionConfig, "extensionConfig");
        // Layered-extension self-check (§6 D2): the thin persistence jar requires the base client jar alongside it, and
        // both must be the same Ditto release. Delegated to the verification-clean helper so the missing-base case
        // yields an actionable error rather than a bare NoClassDefFoundError on a base type used elsewhere here.
        PostgresPersistenceExtensionSelfCheck.verify();
    }

    @Override
    public String getJournalPluginId(final String entityType) {
        return requireMapping(entityType, JOURNAL_KEY);
    }

    @Override
    public String getSnapshotPluginId(final String entityType) {
        return requireMapping(entityType, SNAPSHOT_KEY);
    }

    @Override
    public synchronized DittoReadJournal getReadJournal() {
        if (readJournal == null) {
            // Resolve + validate the entity prefix BEFORE touching the (heavy) pool, so a misconfigured entity fails
            // clearly without materialising the shared client.
            final String entity = resolveReadJournalEntity();
            // Share the SINGLE per-actor-system pool with the journal + snapshot-store plugins (one pool per service,
            // not 3 × max-size). The extension owns the client lifecycle and registers it with CoordinatedShutdown once.
            final DittoPostgresClient sharedClient = PostgresClientExtension.get(actorSystem).getClient();
            client = sharedClient;
            // Pool-occupancy gauge polling is owned by PostgresClientExtension (started when the single shared pool is
            // created), NOT here: a write-heavy service may never call getReadJournal(), so starting the poller on this
            // lazy path left the write path's pool saturation invisible (round-2 finding H-9).
            final PostgresPersistenceOperations operations = PostgresPersistenceOperations.of(sharedClient, entity);
            readJournal = PostgresReadJournal.of(operations);
        }
        return readJournal;
    }

    @Override
    public BackendFamily backendFamily() {
        return BackendFamily.POSTGRESQL;
    }

    @Override
    public Set<String> expectedPluginClassNames() {
        return EXPECTED_PLUGIN_CLASS_NAMES;
    }

    @Override
    public String expectedReadJournalClassName() {
        return EXPECTED_READ_JOURNAL_CLASS_NAME;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a {@link PostgresPersistencePluginConfig} backed by this provider (so the per-entity journal/snapshot IDs
     * stay single-sourced through {@link #getJournalPluginId}/{@link #getSnapshotPluginId}), exposing the Postgres
     * profile's per-service auto-start IDs and the {@code connection-remember} remember-store IDs that connectivity's
     * shard region persists through. Task E2, mirroring B1.
     */
    @Override
    public PersistencePluginConfig pluginConfig() {
        return PostgresPersistencePluginConfig.of(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to a {@link PostgresPersistenceOperationsFactory}, which builds the per-entity namespace/entity purge
     * collaborators (REAL SQL DELETE primitives) around the single shared pool owned by {@link PostgresClientExtension}.
     * The factory holds no client (it resolves the shared pool per call), so it is created once and reused; the bundle's
     * {@code closeable} is a no-op because the pool is owned by the extension, not the ops actor. Task E2, mirroring B2.
     */
    @Override
    public PersistenceOperationsCollaborators operations(final String entityType) {
        return getOperationsFactory().operations(entityType);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@link PostgresHealthChecker#props()} — a real {@code SELECT 1} health actor on the shared pool, the
     * no-arg {@link Props} factory a service's {@code RootActor} passes to {@code DefaultHealthCheckingActorFactory}
     * exactly as it passes {@code MongoHealthChecker.props()} today. Task E2, mirroring B3.
     */
    @Override
    public Props healthCheck() {
        return PostgresHealthChecker.props();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wires the backend-neutral {@link SnapshotStreamingActor} (persistence-api, B4) with the shared
     * {@link PostgresReadJournal} from {@link #getReadJournal()} and a {@link NoOpCloseable}: PostgreSQL owns no client
     * the streaming actor must close (the pool is owned by {@link PostgresClientExtension}), so the actor's
     * {@code postStop} close is a no-op — the legitimate empty-close the plan calls out. Task E2, mirroring B4.
     */
    @Override
    public Props streaming(final String entityType,
            final Function<String, EntityId> pid2EntityId,
            final Function<EntityId, String> entityId2Pid) {
        // The shared read journal binds to ONE table set (read-journal.entity). Streaming a DIFFERENT
        // entity type would silently read the wrong tables — fail loudly instead (interface honesty:
        // the entityType parameter is otherwise unused).
        final String boundEntity = resolveReadJournalEntity();
        final String requestedEntity = actorSystem.settings().config()
                .getString(getJournalPluginId(entityType) + ".entity");
        if (!boundEntity.equals(requestedEntity)) {
            throw new DittoConfigError("streaming(" + entityType + ") requested entity table set <"
                    + requestedEntity + "> but this service's read journal is bound to <" + boundEntity
                    + "> (extension-config read-journal.entity). A per-entity streaming read journal is "
                    + "not supported yet — bind the service's read journal to the requested entity.");
        }
        return SnapshotStreamingActor.props(pid2EntityId, entityId2Pid, getReadJournal(), NoOpCloseable.getInstance());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the singleton {@link PostgresSnapshotCodec} — the JSONB envelope (encode→JSONB text, decode←JSONB
     * shapes). Task E2, mirroring B5.
     */
    @Override
    public SnapshotCodec snapshotCodec() {
        return PostgresSnapshotCodec.INSTANCE;
    }

    private synchronized PersistenceOperationsFactory getOperationsFactory() {
        if (operationsFactory == null) {
            operationsFactory = PostgresPersistenceOperationsFactory.of(actorSystem);
        }
        return operationsFactory;
    }

    /**
     * Bootstraps the PostgreSQL persistence schema through {@link PostgresSchemaBootstrapper}, the production entry
     * point for {@link PostgresSchemaManager} shared with the search backend.
     * <p>
     * The bootstrapper builds a one-shot DDL-role connection factory from {@code ditto.postgresql.pool.ddl-credentials}
     * (falling back to the runtime credentials when the DDL role is empty), runs the single-transaction bootstrap
     * (CREATE … IF NOT EXISTS + live-catalog verification + {@code schema_version} guard) and disposes the factory, so
     * no DDL-privileged connection lingers. While PostgreSQL is not reachable yet it retries for up to
     * {@code ditto.postgresql.bootstrap.retry.max-duration}; any other failure is not retried.
     * </p>
     * <p>
     * This must be called once, before the persistent actors start writing journals, so a fresh database has its tables
     * created (rather than the first journal write hitting {@code relation does not exist}). Because the bootstrap
     * verifies the live catalog and refuses to boot on a checksum/PK divergence, a successful return is itself the
     * guarantee that the required tables exist; a failure throws and, with
     * {@code ditto.postgresql.bootstrap.exit-on-failure}, also stops the service.
     * </p>
     *
     * @throws org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootException on a checksum
     * mismatch, an incompatible newer schema or a live-catalog divergence.
     * @throws org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapUnavailableException
     * when PostgreSQL stayed unreachable for the whole retry budget.
     * @throws org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapTimeoutException when
     * an attempt did not finish within the attempt timeout.
     * @throws org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapAbortedException when
     * the service shuts down during the wait.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if the SSL configuration is unsafe.
     */
    @Override
    public void bootstrapSchema() {
        final PostgresConfig config = loadPostgresConfig();
        final boolean ddlRoleConfigured = config.getPoolConfig().getDdlCredentials().isConfigured();
        LOGGER.info("Bootstrapping PostgreSQL persistence schema (ddl-credentials {}).",
                ddlRoleConfigured ? "configured -> dedicated DDL role" : "empty -> reusing runtime role");
        PostgresSchemaBootstrapper.bootstrap(actorSystem, config, PostgresSchema.descriptor());
        // Register the persistence schema with the shared client's runtime self-heal: a statement hitting a missing
        // table (42P01) then recreates exactly this descriptor's tables and retries. Registration is here, after a
        // successful bootstrap and before the plugins issue runtime statements.
        PostgresClientExtension.get(actorSystem).getClient().registerSchemaDescriptor(PostgresSchema.descriptor());
    }

    private String requireMapping(final String entityType, final String key) {
        final String path = PLUGIN_IDS_PATH + "." + Objects.requireNonNull(entityType, "entityType") + "." + key;
        if (!extensionConfig.hasPath(path)) {
            throw new DittoConfigError(String.format(
                    "Missing required Postgres plugin-id mapping <ditto.extensions.persistence-backend-provider."
                            + "extension-config.%s>. Every entity type that goes through the provider must declare an "
                            + "explicit %s plugin ID (e.g. \"ditto-postgres-%ss-%s\"); there is no pluralize() "
                            + "fallback.", path, key, entityType, key.equals(JOURNAL_KEY) ? "journal" : "snapshots"));
        }
        return extensionConfig.getString(path);
    }

    private PostgresConfig loadPostgresConfig() {
        // The backend block lives under ditto.postgresql; the actor-system config is the ditto-scoped root.
        return DefaultPostgresConfig.of(actorSystem.settings().config().getConfig("ditto"));
    }

    String resolveReadJournalEntity() {
        if (!extensionConfig.hasPath(READ_JOURNAL_ENTITY_PATH)) {
            throw new DittoConfigError(
                    "ditto.extensions.persistence-backend-provider.extension-config.read-journal.entity is required "
                            + "when the Postgres profile is active. Set it to this service's entity prefix "
                            + "(things|policies|connections|wot) — there is no silent \"things\" default. Each service "
                            + "config sets it explicitly (things.conf=\"things\", policies.conf=\"policies\", "
                            + "connectivity.conf=\"connections\").");
        }
        final String entity = extensionConfig.getString(READ_JOURNAL_ENTITY_PATH);
        // Fail clearly on an unknown entity rather than at first query.
        final String tableSet = PostgresTableNames.of(entity).entityPrefix();
        if (!PostgresSchema.ENTITY_TABLE_SETS.contains(tableSet)) {
            // the remember-entities table set holds Pekko envelopes under /sharding/... ids, never Ditto events
            throw new DittoConfigError("read-journal.entity <" + entity + "> is a table set but no entity: the read "
                    + "journal (persistence ping, cleanup, streaming) binds to an entity's tables only "
                    + "(things|policies|connections|wot).");
        }
        return entity;
    }

    /**
     * @return the configured plugin-id entity keys for which a mapping is present (for diagnostics/tests).
     */
    Set<String> configuredEntities() {
        if (!extensionConfig.hasPath(PLUGIN_IDS_PATH)) {
            return Set.of();
        }
        return Set.copyOf(extensionConfig.getConfig(PLUGIN_IDS_PATH).root().keySet());
    }

    /**
     * @return the client backing this provider's read journal once {@link #getReadJournal()} has materialised it,
     * {@code null} otherwise. Package-private to let tests assert the read journal shares the single per-actor-system
     * pool (H-8) rather than building its own.
     */
    @Nullable
    DittoPostgresClient resolvedReadJournalClient() {
        return client;
    }

}
