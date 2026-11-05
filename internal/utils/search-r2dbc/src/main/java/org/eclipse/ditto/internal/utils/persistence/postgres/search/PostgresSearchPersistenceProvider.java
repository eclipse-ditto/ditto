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
package org.eclipse.ditto.internal.utils.persistence.postgres.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.pekko.streaming.TimestampPersistence;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresHealthChecker;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSchemaBootstrapper;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootException;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.DefaultPostgresSearchPathIndexConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.PathIndexMaintenance;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.PostgresSearchPathIndexConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.SystemIndexProbe;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.SystemSearchIndex;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper.DefaultPostgresDeleteAtReaperConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper.PostgresDeleteAtReaper;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper.PostgresDeleteAtReaperActor;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper.PostgresDeleteAtReaperConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresQueryBuilderFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresSortClause;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.SearchQueryAssembler;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.SelectiveLegs;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.read.PostgresThingsAggregationPersistence;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.read.PostgresThingsSearchPersistence;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresSearchUpdaterFlow;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresThingsSearchUpdaterPersistence;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresTimestampPersistence;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.thingsearch.persistence.api.SearchPersistenceProvider;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsAggregationPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchUpdaterFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcException;
import reactor.core.Disposable;

/**
 * PostgreSQL-backed implementation of {@link SearchPersistenceProvider} — the OPT-IN things-search backend, selected by
 * a deployment that includes {@code ditto-postgres-search.conf} (which points
 * {@code ditto.extensions.search-persistence-provider} at this class). MongoDB stays the bundled default.
 * <p>
 * Constructed reflectively by {@code DittoExtensionPoint} with {@code (ActorSystem, Config)} — exactly like
 * {@code MongoSearchPersistenceProvider}. The constructor is intentionally lightweight: it opens NO PostgreSQL
 * connection; it only reads the actor-system config to warn about Mongo-only operational knobs that have no effect on
 * PostgreSQL (plan §3.6). The DDL connection is opened lazily inside {@link #bootstrapSchema()}.
 * </p>
 * <p>
 * <strong>Status: feature-complete (Phase F).</strong> {@link #bootstrapSchema()} landed in Phase B; the write path
 * ({@link #createUpdaterFlow()}, {@link #createUpdaterPersistence()}, {@link #createBackgroundSyncBookmarkPersistence()})
 * in Phase C; the read path ({@link #createSearchPersistence()}, {@link #queryBuilderFactory(LimitsConfig)},
 * {@link #renderForDiagnostics(Query, List)}) in Phase D; {@link #createAggregationPersistence()} in Phase E; and
 * {@link #healthCheckProps()} in Phase F, which closes out every SPI method — none throws
 * {@link UnsupportedOperationException} any more.
 * </p>
 *
 * @since 4.0.0
 */
public final class PostgresSearchPersistenceProvider implements SearchPersistenceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSearchPersistenceProvider.class);

    /** The Mongo-only operational knobs that are silently ignored on PostgreSQL (plan §3.6); each is WARNed if set. */
    private static final String SEARCH_CONFIG_PATH = "ditto.search";
    private static final String MONGO_HINTS_BY_NAMESPACE = "mongo-hints-by-namespace";
    private static final String MONGO_COUNT_HINT_INDEX_NAME = "mongo-count-hint-index-name";
    private static final String INDEX_INITIALIZATION_CUSTOM_INDEXES = "index-initialization.custom-indexes";
    private static final String INDEX_HINT_KEY = "index-hint";

    /**
     * The actor-system config subtree holding the search updater persistence config — the same location
     * {@code DefaultSearchPersistenceConfig} reads, from which the fan-out {@code ThrottlingConfig} is resolved
     * neutrally (its Mongo type is not used, only the neutral {@code base/service} {@link ThrottlingConfig}).
     */
    private static final String UPDATER_PERSISTENCE_CONFIG_PATH = "ditto.search.updater.persistence";

    /** The neutral simple-field-mappings config path (RQL field name → internal name), read for the metric filter. */
    private static final String SIMPLE_FIELD_MAPPINGS_PATH = "ditto.search.simple-field-mappings";

    /**
     * The built-in {@code simple-field-mappings} defaults — a verbatim copy of {@code SearchConfigValue
     * .SIMPLE_FIELD_MAPPINGS}'s default map, applied when the config subtree is absent (a minimal Postgres-only config)
     * so operator-metric filters resolve identically to Mongo.
     */
    private static final Map<String, String> DEFAULT_SIMPLE_FIELD_MAPPINGS = Map.of(
            "thingId", "_id",
            "namespace", "_namespace",
            "policyId", "/policyId",
            "_revision", "/_revision",
            "_modified", "/_modified",
            "_created", "/_created",
            "definition", "/definition",
            "_metadata", "/_metadata");

    private final ActorSystem actorSystem;

    /**
     * Guards the {@code delete_at} reaper actor start: {@code createUpdaterPersistence()} is — per the Phase F final
     * decision, see {@link #startDeleteAtReaperOnce(DittoPostgresClient)}'s javadoc — the provider's ONE, permanent
     * signal that the write path is actually being materialized, and it may in principle be called more than once on
     * the same (per-actor-system-cached, see {@code SearchPersistenceProvider.get}) provider instance; this flag makes
     * the actor start idempotent so a second call never races {@link ActorSystem#actorOf} into an
     * {@code InvalidActorNameException} against an already-running reaper.
     */
    private final AtomicBoolean deleteAtReaperStarted = new AtomicBoolean(false);

    @Nullable private volatile CompletableFuture<PathIndexMaintenance.Outcome> pathIndexMaintenanceOutcome;

    /**
     * The gate of the range legs of the selective-leg CTE: whether this instance sees the system index {@code sf_multi}
     * valid. Started after a successful bootstrap; until then it reports "not valid" (point legs only).
     */
    private final SystemIndexProbe multiValuedIndex = SystemIndexProbe.of(SystemSearchIndex.MULTI_VALUED);

    /**
     * Constructor invoked reflectively by {@code PekkoClassLoader} during extension loading.
     *
     * @param actorSystem the actor system the extension is loaded into.
     * @param extensionConfig the {@code extension-config} subtree of the extension declaration (unused; this provider
     * reads its configuration from the actor-system config, matching {@code MongoSearchPersistenceProvider}).
     */
    public PostgresSearchPersistenceProvider(final ActorSystem actorSystem, final Config extensionConfig) {
        this.actorSystem = Objects.requireNonNull(actorSystem, "actorSystem");
        Objects.requireNonNull(extensionConfig, "extensionConfig");
        // Layered-extension self-check (§6 D2): the thin search jar requires the base client jar alongside it, and both
        // must be the same Ditto release. Delegated to the verification-clean helper so the missing-base case yields an
        // actionable error rather than a bare NoClassDefFoundError on a base type used elsewhere here.
        PostgresSearchExtensionSelfCheck.verify();
        warnIgnoredMongoKnobs(actorSystem.settings().config());
    }

    @Override
    public void bootstrapSchema() {
        // Synchronous (SPI contract): waits a bounded time while PostgreSQL is not reachable yet, throws on anything
        // else, so the service never serves traffic against an unusable index.
        final PostgresConfig config = postgresConfig();
        // Validated before any database work: an invalid per-path index entry fails the start with a config error.
        final PostgresSearchPathIndexConfig pathIndexConfig = DefaultPostgresSearchPathIndexConfig.of(
                actorSystem.settings().config().getConfig("ditto"), simpleFieldMappings());
        try {
            // Every bootstrap attempt first refuses a database the search schema cannot work correctly on (encoding,
            // LC_CTYPE, the "C.utf8" collation) — at every start, also when the schema is up to date.
            PostgresSchemaBootstrapper.bootstrap(actorSystem, config, PostgresSearchSchema.descriptor(),
                    PostgresSearchDatabasePrerequisites::verify);
        } catch (final SchemaBootException e) {
            // A refusal is already actionable; its text mentions pg_trgm, so it must not be re-classified below.
            throw e;
        } catch (final RuntimeException e) {
            final TrgmFailure trgmFailure = classifyTrgmFailure(e);
            if (trgmFailure != TrgmFailure.NONE) {
                throw new IllegalStateException(trgmFailure.getMessage(), e);
            }
            throw e;
        }
        // Register the search schema with the shared client's runtime self-heal. Note: the search read/updater paths
        // open their connections from the raw pool (getConnectionPool()), so no search statement is heal-wrapped; a
        // missing search table is re-created only by the schema check of the next start. Registration is here, after
        // a successful bootstrap.
        PostgresClientExtension.get(actorSystem).getClient()
                .registerSchemaDescriptor(PostgresSearchSchema.descriptor());
        final ConnectionFactory pool = PostgresClientExtension.get(actorSystem).getClient().getConnectionPool();
        startPathIndexMaintenance(config, pathIndexConfig, pool);
        final Disposable probe = multiValuedIndex.poll(pool, SystemIndexProbe.DEFAULT_INTERVAL);
        actorSystem.registerOnTermination(probe::dispose);
    }

    /**
     * Starts the per-path and system index maintenance in the background — only reached after a successful bootstrap,
     * so a refused bootstrap never builds an index. It runs on its own non-pooled DDL connection (never a connection of
     * the runtime pool) and does not delay the start: an index build takes minutes on a large {@code search_flat}, and
     * until an index is valid the planner keeps using the table-wide one (and, for the system index {@code sf_multi},
     * range filters keep the plan without it). Failures are logged; the service keeps running (Mongo parity: custom
     * search indexes are initialized asynchronously, failures logged).
     */
    private void startPathIndexMaintenance(final PostgresConfig config,
            final PostgresSearchPathIndexConfig pathIndexConfig, final ConnectionFactory pool) {
        final CompletableFuture<PathIndexMaintenance.Outcome> outcome = new CompletableFuture<>();
        pathIndexMaintenanceOutcome = outcome;
        try {
            final Disposable run = PathIndexMaintenance.of(config, pathIndexConfig).run()
                    .subscribe(result -> {
                        outcome.complete(result);
                        // this instance may just have built sf_multi: switch range legs on without waiting a poll
                        multiValuedIndex.refresh(pool).subscribe();
                    }, error -> {
                        LOGGER.error("Search path index maintenance failed: {}", error.getMessage(), error);
                        outcome.completeExceptionally(error);
                    });
            actorSystem.registerOnTermination(run::dispose);
        } catch (final RuntimeException e) {
            LOGGER.error("Search path index maintenance could not be started: {}", e.getMessage(), e);
            outcome.completeExceptionally(e);
        }
    }

    /**
     * @return the outcome of the path-index maintenance started by the last {@link #bootstrapSchema()}, or
     * {@code null} if none was started. Package private for tests.
     */
    @Nullable
    CompletableFuture<PathIndexMaintenance.Outcome> pathIndexMaintenanceOutcome() {
        return pathIndexMaintenanceOutcome;
    }

    /**
     * @return the probe of the system index sf_multi. Package private for tests.
     */
    SystemIndexProbe multiValuedIndexProbe() {
        return multiValuedIndex;
    }

    @Override
    public ThingsSearchPersistence createSearchPersistence() {
        // The full read path (count/find/namespace/recover + background-sync metadata source) over the single shared
        // connection pool. The pool carries plan_cache_mode=force_custom_plan (ditto-postgres-search.conf) so the
        // wpath-parameterized read statements never latch onto a generic plan (plan §3.5 / bench-results §8.2). Range
        // legs follow this instance's view of the system index sf_multi, read per statement.
        return PostgresThingsSearchPersistence.of(PostgresClientExtension.get(actorSystem).getClient(),
                multiValuedIndex);
    }

    @Override
    public ThingsSearchUpdaterPersistence createUpdaterPersistence() {
        // Policy fan-out + namespace purge over the single shared connection pool (plan §3.4/§3.6). The fan-out stream
        // is throttled with the same ThrottlingConfig the Mongo impl consumes, resolved neutrally from the actor-system
        // config below.
        final DittoPostgresClient client = PostgresClientExtension.get(actorSystem).getClient();
        startDeleteAtReaperOnce(client);
        return PostgresThingsSearchUpdaterPersistence.of(client, updaterThrottlingConfig());
    }

    /**
     * Starts the {@code delete_at} reaper actor, exactly once per provider instance (and therefore, since the
     * provider is cached one-per-{@link ActorSystem}, at most once per JVM).
     * <p>
     * <strong>Phase F final decision: {@code createUpdaterPersistence()} stays the hook.</strong> Task C4 flagged this
     * call site for a Phase F revisit once the provider's full lifecycle/health wiring existed to compare it against.
     * Phase F now supplies both remaining candidates — {@link #createUpdaterFlow()} and {@link #healthCheckProps()} —
     * and neither displaces this hook:
     * <ul>
     *     <li>{@code createUpdaterFlow()} is called by {@code SearchUpdaterRootActor} BEFORE
     *     {@code createUpdaterPersistence()} ({@code thingsearch/service/.../updater/actors/SearchUpdaterRootActor.java:96}
     *     then {@code :109}), so it fires strictly earlier — but the search-updater flow is also constructed on
     *     Mongo-parity write paths that are exercised in isolation by tests without ever standing up the full
     *     updater-persistence/namespace-purge machinery the reaper protects (namespace purge, precisely, is what
     *     writes the {@code delete_at} marker in the first place — {@code PostgresThingsSearchUpdaterPersistence}).
     *     Tying the reaper to the write FLOW rather than to the updater PERSISTENCE would start it in strictly more
     *     situations than necessary, including some that never mark a row for deletion.
     *     <li>{@code healthCheckProps()} is called by {@code SearchRootActor} AFTER the {@code SearchUpdaterRootActor}
     *     (and therefore after {@code createUpdaterPersistence()}) has already been started
     *     ({@code SearchRootActor.java:73-77}: {@code startChildActor(SearchUpdaterRootActor...)} precedes
     *     {@code initializeHealthCheckActor(...)}). Health-check wiring is also the wrong PLACE for an operational
     *     side effect like starting a background actor: {@code healthCheckProps()} must stay a pure "give me your
     *     health-check {@link Props}" query — a caller resolving health check {@link Props} for introspection/testing
     *     must never accidentally start a reaper as a side effect.
     * </ul>
     * {@code createUpdaterPersistence()} therefore remains — now FINALLY, not provisionally — the ONE trigger: it is
     * the earliest point at which "this JVM is genuinely running the Postgres search-updater write path" (the write
     * path that marks rows for the reaper to collect) is known true, called by production wiring exactly once at real
     * service startup ({@code SearchUpdaterRootActor.java:109}). Pinned by
     * {@code PostgresSearchPersistenceProviderTest.createUpdaterPersistenceStartsTheReaperExactlyOnceRegardlessOfSurroundingCallOrder}
     * (interleaves every other factory method around it) and
     * {@code ...otherFactoryMethodsNeverStartTheReaperWithoutCreateUpdaterPersistence} (proves the negative: none of
     * the other methods — including {@code createUpdaterFlow()} — start it on their own).
     * </p>
     *
     * @param client the shared PostgreSQL client the reaper's connections are drawn from.
     */
    private void startDeleteAtReaperOnce(final DittoPostgresClient client) {
        if (deleteAtReaperStarted.compareAndSet(false, true)) {
            final PostgresDeleteAtReaperConfig reaperConfig =
                    DefaultPostgresDeleteAtReaperConfig.of(actorSystem.settings().config().getConfig("ditto"));
            final PostgresDeleteAtReaper reaper = PostgresDeleteAtReaper.of(client, reaperConfig);
            actorSystem.actorOf(PostgresDeleteAtReaperActor.props(reaper, reaperConfig),
                    PostgresDeleteAtReaperActor.ACTOR_NAME);
            LOGGER.info("Started the PostgreSQL search delete-at reaper (interval={}, batchSize={}, "
                            + "maxBatchesPerTick={}).", reaperConfig.getInterval(), reaperConfig.getBatchSize(),
                    reaperConfig.getMaxBatchesPerTick());
        }
    }

    @Override
    public ThingsAggregationPersistence createAggregationPersistence() {
        // Operator "custom aggregation metrics" ($match+$group) over the single shared connection pool (plan §3.5). The
        // metric filter's RQL field names are resolved with the neutral simple-field-mappings (read below), so this
        // provider passes a plain Map — the persistence takes no Mongo-coupled service config.
        return PostgresThingsAggregationPersistence.of(PostgresClientExtension.get(actorSystem).getClient(),
                simpleFieldMappings());
    }

    @Override
    public SearchUpdaterFlow createUpdaterFlow() {
        // Per-thing transactional write engine over the single shared connection pool (one per service, resolved via
        // PostgresClientExtension). Unconditional doc-row upsert + v1.5 anti-join flat maintenance (plan §3.4).
        return PostgresSearchUpdaterFlow.of(PostgresClientExtension.get(actorSystem).getClient());
    }

    @Override
    public TimestampPersistence createBackgroundSyncBookmarkPersistence() {
        // The background-sync bookmark, kept as a single row in search_sync (the MongoTimestampPersistence replacement).
        return PostgresTimestampPersistence.of(PostgresClientExtension.get(actorSystem).getClient());
    }

    @Override
    public Props healthCheckProps() {
        // Drop-in reuse of the shared postgres-client SELECT-1 prober (plan §3.6): SearchHealthCheckingActorFactory
        // consumes this Props exactly like MongoHealthChecker.props() — a no-arg-constructible AbstractHealthCheckingActor
        // Props it stores verbatim under the "persistence" label inside a CompositeCachingHealthCheckingActor
        // (thingsearch/service SearchHealthCheckingActorFactory:62). PostgresHealthChecker already satisfies that shape
        // (no-arg Props, StatusInfo UP/DOWN via the RetrieveHealth/triggerHealthRetrieval protocol) and resolves the SAME
        // shared pool this provider's other factory methods use (PostgresClientExtension.get(actorSystem).getClient()),
        // so it needs no search-specific adaptation. Identical to how PostgresPersistenceBackendProvider#healthCheckProps()
        // reuses it for the event-sourcing backend (persistence-r2dbc's own drop-in precedent).
        return PostgresHealthChecker.props();
    }

    @Override
    public QueryBuilderFactory queryBuilderFactory(final LimitsConfig limitsConfig) {
        // Backend-neutral criteria→Query builder: default _id ASC, thing-id truncation, LimitsConfig page bounds (D3).
        return new PostgresQueryBuilderFactory(limitsConfig);
    }

    @Override
    public String renderForDiagnostics(final Query query, @Nullable final List<String> authorizationSubjectIds) {
        // The slow-query log form: the translated read SQL with $n placeholders shown (never inlined values), plus the
        // ordered bind values as a trailing comment. A null subjects list renders the sudo (no-auth) form, matching how
        // the caller passes authorization (MongoThingsSearchPersistence.explain uses the same sudo/non-sudo split).
        final PostgresSortClause sortClause = PostgresSortClause.of(query.getSortOptions());
        final RenderedSql rendered = SearchQueryAssembler.renderForDiagnostics(query.getCriteria(),
                authorizationSubjectIds, sortClause, SelectiveLegs.of(multiValuedIndex.getAsBoolean()));
        return rendered.sql() + System.lineSeparator() + "-- binds: " + rendered.bindValues();
    }

    private PostgresConfig postgresConfig() {
        // Effective namespace ditto.postgresql.* — DefaultPostgresConfig.CONFIG_PATH ("postgresql") relative to "ditto".
        return DefaultPostgresConfig.of(actorSystem.settings().config().getConfig("ditto"));
    }

    /**
     * Resolves {@code ditto.search.simple-field-mappings} (the RQL field-name → internal-name mappings the operator
     * aggregation metric filter is parsed with) from the neutral actor-system config, overlaying any configured entries
     * onto the built-in defaults (the same defaults {@code SearchConfigValue.SIMPLE_FIELD_MAPPINGS} ships). Read here so
     * {@link PostgresThingsAggregationPersistence} takes a plain {@code Map}, not the Mongo-coupled {@code SearchConfig}.
     */
    private Map<String, String> simpleFieldMappings() {
        final Map<String, String> mappings = new LinkedHashMap<>(DEFAULT_SIMPLE_FIELD_MAPPINGS);
        final Config root = actorSystem.settings().config();
        if (root.hasPath(SIMPLE_FIELD_MAPPINGS_PATH)) {
            final Config configured = root.getConfig(SIMPLE_FIELD_MAPPINGS_PATH);
            for (final Map.Entry<String, ConfigValue> entry : configured.root().entrySet()) {
                if (entry.getValue().valueType() == ConfigValueType.STRING) {
                    mappings.put(entry.getKey(), (String) entry.getValue().unwrapped());
                }
            }
        }
        return Map.copyOf(mappings);
    }

    /**
     * Resolves the fan-out {@link ThrottlingConfig} exactly as {@code DefaultSearchPersistenceConfig} does — passing the
     * {@code ditto.search.updater.persistence} subtree to {@link ThrottlingConfig#of(Config)} (which reads its own
     * {@code throttling} sub-block, falling back to the neutral defaults when absent). The Postgres provider takes no
     * Mongo-coupled service config, so it reads this neutral {@code base/service} config directly. When the search
     * config subtree is absent (a minimal Postgres-only config) the neutral defaults apply — identical to Mongo.
     */
    private ThrottlingConfig updaterThrottlingConfig() {
        final Config root = actorSystem.settings().config();
        final Config persistence = root.hasPath(UPDATER_PERSISTENCE_CONFIG_PATH)
                ? root.getConfig(UPDATER_PERSISTENCE_CONFIG_PATH)
                : ConfigFactory.empty();
        return ThrottlingConfig.of(persistence);
    }

    /**
     * Detects and WARN-logs each Mongo-only operational knob that is set in the config but has NO effect on the
     * PostgreSQL search backend (plan §3.6): {@code mongo-hints-by-namespace}, {@code mongo-count-hint-index-name},
     * {@code index-initialization.custom-indexes}, and any per-metric {@code index-hint}.
     *
     * @param config the actor-system (root) config.
     */
    private static void warnIgnoredMongoKnobs(final Config config) {
        for (final String knob : detectIgnoredMongoKnobs(config)) {
            LOGGER.warn("Ignoring Mongo-only search configuration '{}': it has no effect on the PostgreSQL search "
                    + "backend (the PostgreSQL query planner chooses indexes; there is no per-query index hint, and "
                    + "custom Mongo indexes have no direct PostgreSQL analog — per-path PostgreSQL search indexes "
                    + "are configured under ditto.postgresql.search.path-indexes).", knob);
        }
    }

    /**
     * @param config the actor-system (root) config.
     * @return the list of set Mongo-only knob paths that are ignored on PostgreSQL (empty if none are set). Package
     * private for direct unit testing (config-flag assertion, no log capture).
     */
    static List<String> detectIgnoredMongoKnobs(final Config config) {
        final List<String> ignored = new ArrayList<>();
        if (!config.hasPath(SEARCH_CONFIG_PATH)) {
            return ignored;
        }
        final Config search = config.getConfig(SEARCH_CONFIG_PATH);
        if (isSet(search, MONGO_HINTS_BY_NAMESPACE)) {
            ignored.add(SEARCH_CONFIG_PATH + "." + MONGO_HINTS_BY_NAMESPACE);
        }
        if (isSet(search, MONGO_COUNT_HINT_INDEX_NAME)) {
            ignored.add(SEARCH_CONFIG_PATH + "." + MONGO_COUNT_HINT_INDEX_NAME);
        }
        if (isSet(search, INDEX_INITIALIZATION_CUSTOM_INDEXES)) {
            ignored.add(SEARCH_CONFIG_PATH + "." + INDEX_INITIALIZATION_CUSTOM_INDEXES);
        }
        // per-metric index-hint may appear anywhere under operator-metrics.custom-metrics.<name>.index-hint — scan.
        collectIndexHints(search.root(), SEARCH_CONFIG_PATH, ignored);
        return ignored;
    }

    private static void collectIndexHints(final ConfigObject object, final String prefix, final List<String> out) {
        for (final Map.Entry<String, ConfigValue> entry : object.entrySet()) {
            final String path = prefix + "." + entry.getKey();
            if (INDEX_HINT_KEY.equals(entry.getKey()) && isSetValue(entry.getValue())) {
                out.add(path);
            } else if (entry.getValue() instanceof ConfigObject nested) {
                collectIndexHints(nested, path, out);
            }
        }
    }

    private static boolean isSet(final Config config, final String path) {
        return config.hasPath(path) && isSetValue(config.getValue(path));
    }

    private static boolean isSetValue(final ConfigValue value) {
        final ConfigValueType type = value.valueType();
        return switch (type) {
            case NULL -> false;
            case STRING -> !((String) value.unwrapped()).isBlank();
            case OBJECT -> !((ConfigObject) value).isEmpty();
            case LIST -> !((List<?>) value.unwrapped()).isEmpty();
            default -> true;
        };
    }

    /**
     * The two ways a deployment can misprovision the {@code pg_trgm} extension the search schema needs, each with the
     * actionable boot error that names its remedy.
     */
    enum TrgmFailure {
        /** The bootstrap failure has nothing to do with the extension. */
        NONE(""),
        /**
         * The DDL role may not create the extension: a trusted extension needs {@code CREATE} on the database, which
         * schema ownership does not give (SQLSTATE 42501).
         */
        NOT_PERMITTED("PostgreSQL search schema bootstrap failed: the DDL role could not create the required "
                + "'pg_trgm' extension. pg_trgm backs the sf_trgm trigram index used by like()/ilike() search "
                + "predicates. It is a trusted extension since PostgreSQL 13, so a non-superuser creates it once it "
                + "has CREATE on the database: as the database owner run GRANT CREATE ON DATABASE <database> TO "
                + "<ddl role> "
                + "(deployment/postgres/provisioning/01-roles-and-grants.sql does this); or have a superuser "
                + "pre-install it once, in the schema the Ditto roles use: "
                + "CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA ditto;"),
        /**
         * The extension exists, but in a schema that is not on the DDL role's {@code search_path}, so the trigram index
         * build cannot resolve {@code gin_trgm_ops} (SQLSTATE 42704). The usual cause is a superuser's plain
         * {@code CREATE EXTENSION}, which installs into {@code public} while the Ditto roles' {@code search_path} is
         * {@code ditto}.
         */
        NOT_ON_SEARCH_PATH("PostgreSQL search schema bootstrap failed: the 'pg_trgm' extension is installed, but in a "
                + "schema that is not on the DDL role's search_path, so the operator class gin_trgm_ops of the sf_trgm "
                + "trigram index (like()/ilike() search predicates) cannot be resolved. A plain CREATE EXTENSION as a "
                + "superuser installs into 'public'; the Ditto roles' search_path is 'ditto'. Move it: "
                + "ALTER EXTENSION pg_trgm SET SCHEMA ditto; (as a superuser or the extension's owner), or add its "
                + "schema to the roles' search_path.");

        private final String message;

        TrgmFailure(final String message) {
            this.message = message;
        }

        String getMessage() {
            return message;
        }
    }

    /**
     * Classifies a bootstrap failure by walking its cause chain.
     *
     * @param throwable a bootstrap failure.
     * @return {@link TrgmFailure#NOT_ON_SEARCH_PATH} for SQLSTATE 42704 ({@code undefined_object}) on
     * {@code gin_trgm_ops}; {@link TrgmFailure#NOT_PERMITTED} for SQLSTATE 42501 ({@code insufficient_privilege}) or a
     * message referencing extension creation / pg_trgm / permission-denied-to-create-extension;
     * {@link TrgmFailure#NONE} otherwise.
     */
    static TrgmFailure classifyTrgmFailure(final Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            final String sqlState = t instanceof R2dbcException r2dbc ? r2dbc.getSqlState() : null;
            final String message = t.getMessage();
            final String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
            if (lower.contains("gin_trgm_ops") && ("42704".equals(sqlState) || lower.contains("does not exist"))) {
                return TrgmFailure.NOT_ON_SEARCH_PATH;
            }
            if ("42501".equals(sqlState)
                    || lower.contains("pg_trgm")
                    || lower.contains("permission denied to create extension")
                    || (lower.contains("create extension") && lower.contains("permission"))) {
                return TrgmFailure.NOT_PERMITTED;
            }
        }
        return TrgmFailure.NONE;
    }

}
