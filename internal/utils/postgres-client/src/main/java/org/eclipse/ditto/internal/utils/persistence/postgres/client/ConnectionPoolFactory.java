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
package org.eclipse.ditto.internal.utils.persistence.postgres.client;

import java.time.Duration;
import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultSslConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PoolerMode;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring.R2dbcMetricsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.postgresql.client.SSLMode;
import io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * Builds the R2DBC {@link ConnectionPool} for the PostgreSQL backend from a {@link PostgresConfig}.
 * <p>
 * Applies the SSL boot guard and the connection-pooling-safe defaults: {@code fetch-size=0} (no server-side
 * portals/cursors) and {@code prepared-statement-cache-queries=0} (unnamed statements), which keep the backend safe
 * behind PgBouncer in any pool mode. The configured {@code pooler-mode} does not shape the pool: here it drives a
 * startup cross-check log line and never blocks boot; the search backend gates its path-index maintenance on it
 * (transaction mode needs a direct URI to hold a session).
 * </p>
 * <p>
 * <strong>Read path with {@code fetch-size=0}:</strong> r2dbc-postgresql sends one fetch-all {@code Execute} per
 * statement and still reads the rows demand-driven (it requests backend messages only while the subscriber has demand,
 * and reactor-netty stops reading the socket otherwise), so client memory stays bounded; but the server produces the
 * whole result in one go and blocks on the unread socket while the consumer is slow, holding the statement and its
 * snapshot until {@code statement_timeout}. The long, genuinely-unbounded streaming read-journal queries therefore bind
 * a positive per-statement {@code Statement.fetchSize(...)}
 * (see {@code PostgresPersistenceOperations.STREAMING_FETCH_SIZE}) to open a server-side portal, so the server
 * produces the rows fetch by fetch on demand; the write path keeps {@code fetch-size=0} to stay safe under PgBouncer
 * transaction pooling.
 * </p>
 */
public final class ConnectionPoolFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionPoolFactory.class);

    /**
     * The R2DBC pool name. Distinct + role-bearing ({@value}) so the single shared per-service pool is identifiable in
     * pool-internal logging; its gauges carry the matching {@code pool=shared} discriminating tag (round-2 finding H-9).
     */
    static final String POOL_NAME = "ditto-postgres-pool-shared";

    private ConnectionPoolFactory() {
        throw new AssertionError();
    }

    public static ConnectionPool createConnectionPool(final PostgresConfig config) {
        final DefaultSslConfig sslConfig = config.getSslConfig() instanceof DefaultSslConfig defaultSslConfig
                ? defaultSslConfig
                : null;
        if (sslConfig != null) {
            // Refuse to boot on an unsafe verify-(ca|full)-without-root-cert combo.
            sslConfig.validateBootGuard();
        }

        logPoolerModeCrossCheck(config);
        logAcquireBudgetCrossCheck(config);

        final ConnectionFactory driverFactory = ConnectionFactories.get(buildOptions(config));
        // Wrap the driver factory with r2dbc-proxy so command timers + acquire-latency feed the neutral
        // ditto_persistence_* metric family. The proxy sits BELOW the pool, so afterCreateOnConnectionFactory observes
        // the actual physical connection creation latency.
        final ConnectionFactory connectionFactory = withMetricsProxy(driverFactory);
        final ConnectionPoolConfiguration poolConfiguration = buildPoolConfiguration(connectionFactory, config);
        return new ConnectionPool(poolConfiguration);
    }

    private static ConnectionFactory withMetricsProxy(final ConnectionFactory delegate) {
        return ProxyConnectionFactory.builder(delegate)
                .listener(R2dbcMetricsListener.kamon())
                .build();
    }

    static ConnectionFactoryOptions buildOptions(final PostgresConfig config) {
        // The runtime (DML) role: layer the runtime username/password.
        final ConnectionFactoryOptions.Builder builder = baseOptions(config);
        config.getUsername().ifPresent(user -> builder.option(ConnectionFactoryOptions.USER, user));
        config.getPassword().ifPresent(pwd -> builder.option(ConnectionFactoryOptions.PASSWORD, pwd));
        return builder.build();
    }

    /**
     * Builds the {@link ConnectionFactoryOptions} for the one-shot schema-bootstrap (DDL) connection, layering the
     * {@code ddl-credentials} role on top of the same URL + SSL + pooling-safe options as the runtime pool.
     * <p>
     * Per {@code ditto-postgres-persistence.conf} ({@code pool.ddl-credentials}), an empty DDL role means "reuse the runtime
     * role": when {@link PostgresConfig.DdlCredentialsConfig#isConfigured()} is {@code false} the runtime
     * username/password are layered instead, so the bootstrap still authenticates. When it IS configured, the DDL
     * username/password win — that is what makes {@code POSTGRES_DDL_USER}/{@code POSTGRES_DDL_PASSWORD} actually do
     * something (the least-privilege schema-owner split). This is the sole production consumer of
     * {@link PostgresConfig.PoolConfig#getDdlCredentials()}.
     * </p>
     *
     * @param config the backend configuration.
     * @return the DDL connection-factory options.
     */
    static ConnectionFactoryOptions buildDdlOptions(final PostgresConfig config) {
        final ConnectionFactoryOptions.Builder builder = baseOptions(config);
        final PostgresConfig.DdlCredentialsConfig ddlCredentials = config.getPoolConfig().getDdlCredentials();
        if (ddlCredentials.isConfigured()) {
            ddlCredentials.getUsername().ifPresent(user -> builder.option(ConnectionFactoryOptions.USER, user));
            ddlCredentials.getPassword().ifPresent(pwd -> builder.option(ConnectionFactoryOptions.PASSWORD, pwd));
        } else {
            // Empty DDL role -> reuse the runtime role for the bootstrap (per the conf comment).
            config.getUsername().ifPresent(user -> builder.option(ConnectionFactoryOptions.USER, user));
            config.getPassword().ifPresent(pwd -> builder.option(ConnectionFactoryOptions.PASSWORD, pwd));
        }
        return builder.build();
    }

    /**
     * Builds a one-shot (single-connection) {@link ConnectionFactory} authenticated as the DDL role, used by the schema
     * bootstrap. It is NOT pooled: the bootstrap opens exactly one connection, runs all DDL in a single transaction and
     * closes it, so a pool would only add an idle DDL-privileged connection for the lifetime of the service.
     *
     * @param config the backend configuration.
     * @return a DDL-role connection factory.
     */
    public static ConnectionFactory createDdlConnectionFactory(final PostgresConfig config) {
        final DefaultSslConfig sslConfig = config.getSslConfig() instanceof DefaultSslConfig defaultSslConfig
                ? defaultSslConfig
                : null;
        if (sslConfig != null) {
            sslConfig.validateBootGuard();
        }
        return ConnectionFactories.get(buildDdlOptions(config));
    }

    private static ConnectionFactoryOptions.Builder baseOptions(final PostgresConfig config) {
        final PostgresConfig.PoolConfig poolConfig = config.getPoolConfig();
        final PostgresConfig.SslConfig sslConfig = config.getSslConfig();

        // Parse the r2dbc URL first, then layer explicit overrides so config keys win over URL query params.
        // NOTE: credentials are intentionally NOT layered here. baseOptions() is the shared (URL + SSL + pooling-safe)
        // foundation; the runtime (DML) role is layered by buildOptions() and the schema-bootstrap (DDL) role by
        // buildDdlOptions(), so each caller picks its own credentials (H-1 DDL credential-layering).
        final ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions.parse(config.getUri()).mutate();

        // fetch-size=0 -> no server-side portal/cursor; keeps transaction pooling safe. The driver still reads the
        // rows demand-driven (client memory stays bounded), but the server produces the whole result at once and
        // blocks on the unread socket under a slow consumer. Unbounded streaming read-journal statements override
        // this per statement with a positive Statement.fetchSize(...) — see
        // PostgresPersistenceOperations.STREAMING_FETCH_SIZE.
        builder.option(PostgresqlConnectionFactoryProvider.FETCH_SIZE, poolConfig.getFetchSize());
        // prepared-statement-cache-queries=0 -> unnamed statements; safe on any PgBouncer version / pool mode.
        builder.option(PostgresqlConnectionFactoryProvider.PREPARED_STATEMENT_CACHE_QUERIES,
                poolConfig.getPreparedStatementCacheQueries());

        // Connect timeout: bound how long a borrow may block on a dead/unreachable host before failing fast. Bound
        // directly via the r2dbc SPI option (Duration-typed). Interacts with pool.max-acquire-time: a borrow that has
        // to open a fresh physical connection can wait up to connect-timeout for the TCP/TLS handshake WITHIN the
        // max-acquire-time budget — keep connect-timeout < max-acquire-time so the acquire surfaces the connect failure
        // rather than a generic acquire timeout (checked at startup by logAcquireBudgetCrossCheck).
        builder.option(ConnectionFactoryOptions.CONNECT_TIMEOUT, config.getConnectTimeout());

        // Bind statement_timeout as an r2dbc "options" STARTUP parameter (a GUC name->value Map) rather than a plain
        // per-session "SET", which under transaction pooling would only reach the one server connection that ran it.
        // NOTE: r2dbc-postgresql's OPTIONS is a Map<String,String> of GUC name->value (e.g.
        // {"statement_timeout":"60000"}), NOT a libpq "-c statement_timeout=..." string. Value is milliseconds (the
        // unit-less GUC default unit). statement-timeout=0 sends no startup parameter at all, so the server-side
        // default of the connecting role applies (60s for the runtime role from
        // deployment/postgres/provisioning/01-roles-and-grants.sql); it does not mean "no timeout".
        //
        // PgBouncer caveat (see statement-timeout in ditto-postgres-client.conf): each OPTIONS entry is sent as a
        // startup parameter of its own. PgBouncer (in any pool mode) refuses it unless it is listed in
        // track_extra_parameters (then applied to each server connection it hands out) or ignore_startup_parameters
        // (then dropped; what is enforced is the connecting role's statement_timeout default, if any: the
        // provisioning script sets one for the runtime role only, none for the DDL role).
        // Build the r2dbc-postgresql OPTIONS startup-GUC map ONCE, MERGING with any startup GUCs the operator supplied
        // via the URI's options= query parameter (e.g. search_path) — a bare Map.of(...) would REPLACE the parsed
        // OPTIONS value wholesale. Both statement_timeout and the plancache guard (plan_cache_mode) are layered on top.
        final Map<String, String> options = parseUriStartupOptions(config.getUri());

        final Duration statementTimeout = config.getStatementTimeout();
        // statement-timeout=0: send nothing, the connecting role's server-side default applies (see above).
        if (statementTimeout != null && !statementTimeout.isZero() && !statementTimeout.isNegative()) {
            options.put("statement_timeout", Long.toString(statementTimeout.toMillis()));
        }

        // Plancache generic-plan-flip guard (search plan §3.5, bench-results doc §8.2 — HARD Phase-D requirement).
        // plan_cache_mode is bound as a startup GUC (rather than a per-session SET); on a direct connection it applies
        // to every connection this pool opens for the lifetime of the connection. Behind PgBouncer it has the same
        // caveat as statement_timeout above: refused unless listed in track_extra_parameters (then applied) or
        // ignore_startup_parameters (then dropped, so the guard is off). Off by default; the search profile opts in
        // via ditto.postgresql.force-custom-plan=true (see PostgresConfig#isForceCustomPlan).
        if (config.isForceCustomPlan()) {
            options.put("plan_cache_mode", "force_custom_plan");
        }

        if (!options.isEmpty()) {
            builder.option(PostgresqlConnectionFactoryProvider.OPTIONS, options);
        }

        builder.option(PostgresqlConnectionFactoryProvider.SSL_MODE, SSLMode.fromValue(sslConfig.getMode()));
        // Only set cert/key paths when non-empty: r2dbc's requireExistingFilePath() throws on empty/missing.
        sslConfig.getRootCert()
                .ifPresent(v -> builder.option(PostgresqlConnectionFactoryProvider.SSL_ROOT_CERT, v));
        sslConfig.getCert()
                .ifPresent(v -> builder.option(PostgresqlConnectionFactoryProvider.SSL_CERT, v));
        sslConfig.getKey()
                .ifPresent(v -> builder.option(PostgresqlConnectionFactoryProvider.SSL_KEY, v));
        sslConfig.getKeyPassword()
                .ifPresent(v -> builder.option(PostgresqlConnectionFactoryProvider.SSL_PASSWORD, v));

        return builder;
    }

    /**
     * Parses the {@code options=} startup-GUC query parameter of the r2dbc URI into a mutable, insertion-ordered map, so
     * operator-supplied GUCs (e.g. {@code search_path}) are preserved when {@code statement_timeout} / {@code
     * plan_cache_mode} are layered on. Returns an empty (mutable) map when the URI carries no {@code options=}.
     */
    private static Map<String, String> parseUriStartupOptions(final String uri) {
        final Map<String, String> options = new java.util.LinkedHashMap<>();
        final Object parsedOptions = ConnectionFactoryOptions.parse(uri)
                .getValue(PostgresqlConnectionFactoryProvider.OPTIONS);
        if (parsedOptions instanceof Map<?, ?> parsedMap) {
            parsedMap.forEach((k, v) -> options.put(String.valueOf(k), String.valueOf(v)));
        } else if (parsedOptions instanceof String parsedString) {
            // URL form arrives as "a=b;c=d" before the provider converts it.
            for (final String pair : parsedString.split(";")) {
                final int eq = pair.indexOf('=');
                if (eq > 0) {
                    options.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }
        return options;
    }

    static ConnectionPoolConfiguration buildPoolConfiguration(final ConnectionFactory connectionFactory,
            final PostgresConfig config) {
        final PostgresConfig.PoolConfig poolConfig = config.getPoolConfig();
        final int pendingAcquireMaxSize = poolConfig.getPendingAcquireMaxSize();
        final ConnectionPoolConfiguration.Builder builder = ConnectionPoolConfiguration.builder(connectionFactory)
                // Distinct, role-bearing pool name: with the shared-pool topology a service builds exactly one pool
                // (shared across journal+snapshot+read-journal), tagged pool=shared on its gauges (round-2 finding H-9).
                .name(POOL_NAME)
                .initialSize(poolConfig.getInitialSize())
                .maxSize(poolConfig.getMaxSize())
                // Overload: the acquire budget must fit INSIDE the caller's deadline. A borrow that times out fails
                // the write Future, which stops the persistent actor (not backpressure). But the caller -- the
                // entity supervisor's 5s local-ask.timeout -- has given up before that, so a borrow that waits
                // longer and then succeeds commits a write the caller was told had failed (e.g. an orphan Policy on
                // CreateThing). A longer budget therefore never helps; it only widens that window. Hence:
                //  - max-acquire-time (4s) below the caller deadline, and acquire-retry 0 so it IS the effective
                //    budget (r2dbc-pool's default acquireRetry=1 re-runs the whole acquire with a fresh timeout);
                //  - a bounded pending-acquire queue (default 2 x max-size): r2dbc-pool has no option for it, only
                //    this PoolBuilder customizer. Borrowers beyond the bound fail immediately with
                //    PoolAcquirePendingLimitException instead of queueing behind a deadline nobody waits for.
                // Sustained overload is a capacity problem (max-size, PgBouncer, upstream throttling), not a reason to
                // raise these values. See ditto-postgres-client.conf.
                .maxAcquireTime(poolConfig.getMaxAcquireTime())
                .acquireRetry(poolConfig.getAcquireRetry())
                .customizer(poolBuilder -> poolBuilder.maxPendingAcquire(pendingAcquireMaxSize));

        applyIfPositive(poolConfig.getMaxIdleTime(), builder::maxIdleTime);
        applyIfPositive(poolConfig.getMaxLifeTime(), builder::maxLifeTime);

        return builder.build();
    }

    private static void applyIfPositive(final Duration duration,
            final java.util.function.Consumer<Duration> setter) {
        if (duration != null && !duration.isZero() && !duration.isNegative()) {
            setter.accept(duration);
        }
    }

    /**
     * Logs the effective pool-overload settings once at startup (INFO) and WARNs about the combinations an env override
     * can silently break: {@code acquire-retry > 0} (it multiplies the effective acquire budget),
     * {@code connect-timeout >= max-acquire-time} (a failed connect would then surface as a generic acquire timeout)
     * and an unbounded pending-acquire queue. Never blocks boot.
     */
    static void logAcquireBudgetCrossCheck(final PostgresConfig config) {
        final PostgresConfig.PoolConfig poolConfig = config.getPoolConfig();
        final Duration maxAcquireTime = poolConfig.getMaxAcquireTime();
        final Duration effectiveAcquireBudget = maxAcquireTime.multipliedBy(1L + poolConfig.getAcquireRetry());
        final int pendingAcquireMaxSize = poolConfig.getPendingAcquireMaxSize();
        final Duration connectTimeout = config.getConnectTimeout();

        LOGGER.info("PostgreSQL pool overload settings: max-size={}, pending-acquire-max-size={}, " +
                        "max-acquire-time={}ms, acquire-retry={} -> effective acquire budget={}ms, " +
                        "connect-timeout={}ms",
                poolConfig.getMaxSize(),
                pendingAcquireMaxSize == PostgresConfig.PoolConfig.UNBOUNDED_PENDING_ACQUIRE
                        ? "unbounded"
                        : pendingAcquireMaxSize,
                maxAcquireTime.toMillis(), poolConfig.getAcquireRetry(), effectiveAcquireBudget.toMillis(),
                connectTimeout.toMillis());

        if (poolConfig.getAcquireRetry() > 0) {
            LOGGER.warn("pool.acquire-retry={} re-runs the whole acquire with a fresh max-acquire-time, so a starved " +
                            "borrow waits up to effective acquire budget={}ms instead of {}ms. Keep the effective " +
                            "budget below the callers' 5s deadline (the things/policies supervisor " +
                            "local-ask.timeout), or a write can commit after its caller was told it failed. Set " +
                            "POSTGRES_POOL_ACQUIRE_RETRY=0.",
                    poolConfig.getAcquireRetry(), effectiveAcquireBudget.toMillis(), maxAcquireTime.toMillis());
        }
        if (connectTimeout.compareTo(maxAcquireTime) >= 0) {
            LOGGER.warn("connect-timeout={}ms is not below pool.max-acquire-time={}ms: a borrow that has to open a " +
                            "connection to an unreachable host fails with a generic acquire timeout instead of the " +
                            "real connect error. Lower connect-timeout (POSTGRES_CONNECT_TIMEOUT) together with " +
                            "max-acquire-time.",
                    connectTimeout.toMillis(), maxAcquireTime.toMillis());
        }
        if (pendingAcquireMaxSize == PostgresConfig.PoolConfig.UNBOUNDED_PENDING_ACQUIRE) {
            LOGGER.warn("pool.pending-acquire-max-size=-1: the pending-acquire queue is unbounded, so an overload " +
                    "queues borrowers whose callers have already given up instead of failing them fast. Unset " +
                    "POSTGRES_POOL_PENDING_ACQUIRE_MAX_SIZE to get the default bound of 2 x max-size.");
        }
    }

    static void logPoolerModeCrossCheck(final PostgresConfig config) {
        final PoolerMode poolerMode = config.getPoolerMode();
        final int cacheQueries = config.getPoolConfig().getPreparedStatementCacheQueries();

        // Any populated named-statement cache under a non-SESSION pooler is risky:
        //  - TRANSACTION: actively unsafe NOW — server connections are multiplexed between transactions, so named
        //    prepared statements break unless PgBouncer >= 1.21 with max_prepared_statements > 0.
        //  - DIRECT (or unset): safe TODAY, but it is a latent foot-gun — a future flip to TRANSACTION pooling would
        //    silently break this prepared-statement cache.
        // SESSION pins one server connection per client, so a named-statement cache is and stays safe.
        if (cacheQueries > 0 && poolerMode != PoolerMode.SESSION) {
            if (poolerMode == PoolerMode.TRANSACTION) {
                LOGGER.warn("pooler-mode=transaction with prepared-statement-cache-queries={} (!=0): named prepared " +
                        "statements are unsafe under PgBouncer transaction pooling unless PgBouncer >= 1.21 with " +
                        "max_prepared_statements > 0.", cacheQueries);
            } else {
                LOGGER.warn("pooler-mode={} with prepared-statement-cache-queries={} (!=0): this named " +
                        "prepared-statement cache is safe under the current pooling mode, but a future flip to " +
                        "TRANSACTION pooling would break this cache (named statements are unsafe under PgBouncer " +
                        "transaction pooling unless PgBouncer >= 1.21 with max_prepared_statements > 0). Either keep " +
                        "prepared-statement-cache-queries=0 or do not switch to transaction pooling.",
                        poolerMode.getConfigValue(), cacheQueries);
            }
        } else if (poolerMode == PoolerMode.TRANSACTION) {
            LOGGER.info("pooler-mode=transaction with prepared-statement-cache-queries=0: unnamed statements are " +
                    "safe under PgBouncer transaction pooling on any version.");
        }
    }

}
