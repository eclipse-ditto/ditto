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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.config;

import java.time.Duration;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of the PostgreSQL (R2DBC) persistence backend.
 * <p>
 * Lives under the HOCON namespace {@code ditto.postgresql.{uri, pooler-mode, pool.*, ssl.*, bootstrap.*}}. Defaults:
 * {@code pool.max-size=25} (connections per service instance; the sum over all replicas of all PostgreSQL-backed
 * services, the extra pods of a rolling update included, must fit {@code max_connections}, see
 * {@code ditto-postgres-client.conf}; confirm under load),
 * {@code fetch-size=0}, {@code prepared-statement-cache-queries=0} and {@code ssl.mode=verify-full}. The pool-overload
 * settings deliberately do NOT mirror Mongo's 30 s wait: {@code pool.max-acquire-time=4s} with
 * {@code pool.acquire-retry=0}, {@code connect-timeout=2s} and a pending-acquire queue bounded at
 * {@code 2 x pool.max-size}, so a borrow fails before its caller's 5 s deadline instead of committing after it (see
 * {@code ditto-postgres-client.conf}).
 * </p>
 */
@Immutable
public interface PostgresConfig {

    /**
     * The R2DBC connection URL, e.g. {@code r2dbc:postgresql://host:5432/ditto}. The runtime role is DML-only.
     *
     * @return the configured URI.
     */
    String getUri();

    /**
     * @return the optional runtime DML username (may be embedded in the URI instead).
     */
    Optional<String> getUsername();

    /**
     * @return the optional runtime DML password (may be embedded in the URI instead).
     */
    Optional<String> getPassword();

    /**
     * The connection-pooler topology. Does not shape the pool: it drives a soft startup WARN and gates the search
     * path-index maintenance ({@code transaction} needs a direct URI to hold a session); never a boot block.
     *
     * @return the configured pooler mode.
     */
    PoolerMode getPoolerMode();

    /**
     * The TCP connect timeout for establishing a physical connection (r2dbc SPI {@code CONNECT_TIMEOUT}). Bounds how
     * long a borrow may block on a dead/unreachable host before failing fast. Must stay below
     * {@link PoolConfig#getMaxAcquireTime()} so a failed connect surfaces as itself rather than as an acquire timeout.
     *
     * @return the configured connect timeout.
     */
    Duration getConnectTimeout();

    /**
     * The server-side {@code statement_timeout}, sent as a connection startup parameter (via the r2dbc-postgresql
     * {@code options} map). Behind PgBouncer it only takes effect if PgBouncer lists {@code statement_timeout} in
     * {@code track_extra_parameters}; see {@code ditto-postgres-client.conf}. {@code Duration.ZERO} sends no startup
     * parameter, so the server-side default of the connecting role applies (60s for the runtime role from the
     * provisioning script); it does not disable the timeout.
     *
     * @return the configured statement timeout.
     */
    Duration getStatementTimeout();

    /**
     * Whether the runtime schema self-heal is enabled. When {@code true} and a runtime SQL statement of the persistence
     * backend hits a missing table (PostgreSQL SQLSTATE {@code 42P01}), the backend re-runs the schema check and, where
     * needed, the schema's idempotent DDL, and retries the statement once, mirroring MongoDB's
     * implicit-collection-create-on-first-use behaviour so a dropped/recreated database self-heals instead of failing
     * every persistence call. The search backend's statements are not heal-wrapped. Default {@code true}.
     * <p>
     * This <strong>masks data loss</strong> (a wiped database silently self-heals to empty tables) — the same trade-off
     * MongoDB already has. A heal that ran DDL logs a WARN and increments {@code ditto_persistence_schema_self_heal}; a
     * heal that ran no DDL or failed is not counted. Set {@code false} to fail loud instead: the original
     * {@code 42P01} error then surfaces unchanged.
     * </p>
     *
     * @return whether schema self-heal is enabled.
     */
    boolean isSchemaSelfHealEnabled();

    /**
     * Whether to force {@code plan_cache_mode=force_custom_plan} on every connection this pool opens (bound as an
     * r2dbc-postgresql {@code options} startup parameter, like {@code statement_timeout}; behind PgBouncer it must be
     * listed in {@code track_extra_parameters}, see {@code ditto-postgres-client.conf}).
     * <p>
     * This is the <strong>plancache generic-plan-flip guard</strong> (a HARD Phase-D requirement of the PostgreSQL
     * things-search backend — see the search plan §3.5 and bench-results doc §8.2). r2dbc-postgresql, like pgjdbc,
     * reuses named prepared statements; Task 0.2b measured PostgreSQL's plancache flipping a {@code wpath}-parameterized
     * search query to a catastrophic generic plan once improved statistics made that generic plan look artificially
     * cheap — <strong>55&nbsp;s per execution vs. 0.4&nbsp;s custom-planned</strong>, a cliff triggered purely by a
     * statistics change that was otherwise a pure improvement. Forcing custom planning removes the generic-plan branch
     * of the plancache entirely, so the translator's parameterized queries are always re-planned against the actual
     * bind values.
     * <p>
     * The alternative — inlining {@code wpath} literals through a whitelist so the queries are never parameterized on
     * {@code wpath} — is <strong>rejected</strong>: it enlarges the SQL-injection surface (user-derived path text would
     * reach SQL text) and bloats the plancache with one entry per distinct wpath. Off by default (the persistence
     * services' PK-keyed statements do not need it and would only pay extra planning cost); the search profile opts in.
     *
     * @return {@code true} to bind {@code plan_cache_mode=force_custom_plan} on this pool's connections; default
     * {@code false}.
     */
    default boolean isForceCustomPlan() {
        return false;
    }

    /**
     * @return the connection-pool settings.
     */
    PoolConfig getPoolConfig();

    /**
     * @return the TLS / SSL settings.
     */
    SslConfig getSslConfig();

    /**
     * @return the settings of the schema bootstrap that runs once at service start
     * ({@code ditto.postgresql.bootstrap}).
     */
    BootstrapConfig getBootstrapConfig();

    /**
     * Settings of the R2DBC connection pool.
     */
    @Immutable
    interface PoolConfig {

        /**
         * The {@link #getPendingAcquireMaxSize()} value that leaves the pending-acquire queue unbounded (reactor-pool
         * semantics). Discouraged: an unbounded queue only holds borrowers whose callers have already given up.
         */
        int UNBOUNDED_PENDING_ACQUIRE = -1;

        /**
         * @return the initial number of connections created when the pool is warmed up.
         */
        int getInitialSize();

        /**
         * @return the maximum number of connections held by the pool.
         */
        int getMaxSize();

        /**
         * The maximum time to wait for a connection to be acquired from the pool before failing. With
         * {@link #getAcquireRetry()} {@code = 0} this is the effective acquire budget; it must stay below the caller's
         * deadline (the entity supervisor's 5 s {@code local-ask.timeout}).
         *
         * @return the maximum acquire wait.
         */
        Duration getMaxAcquireTime();

        /**
         * How often r2dbc-pool re-subscribes a failed acquire, each time with a fresh {@link #getMaxAcquireTime()}
         * budget. The effective acquire budget is therefore {@code (1 + acquire-retry) x max-acquire-time}. Default
         * {@code 0}; the library default of {@code 1} would silently double the budget.
         *
         * @return the number of acquire retries, never negative.
         */
        int getAcquireRetry();

        /**
         * The maximum number of borrowers allowed to wait for a connection once all {@link #getMaxSize()} connections
         * are in use. Borrowers beyond it fail immediately with {@code reactor.pool.PoolAcquirePendingLimitException}.
         * Derived as {@code 2 x max-size} when {@code pool.pending-acquire-max-size} is not configured; {@code 0}
         * means no queue at all; {@link #UNBOUNDED_PENDING_ACQUIRE} means unbounded.
         *
         * @return the effective pending-acquire bound, or {@link #UNBOUNDED_PENDING_ACQUIRE}.
         */
        int getPendingAcquireMaxSize();

        /**
         * @return the maximum time a pooled connection may stay idle before being evicted ({@code Duration.ZERO} = no
         * limit).
         */
        Duration getMaxIdleTime();

        /**
         * @return the maximum lifetime of a pooled connection ({@code Duration.ZERO} = no limit).
         */
        Duration getMaxLifeTime();

        /**
         * The server-side fetch size. {@code 0} disables server-side portals/cursors, keeping the backend safe under
         * PgBouncer transaction pooling.
         *
         * @return the fetch size.
         */
        int getFetchSize();

        /**
         * The number of prepared statements cached by r2dbc-postgresql. {@code 0} uses unnamed statements, which is safe
         * on any PgBouncer version / pool mode.
         *
         * @return the prepared-statement-cache size.
         */
        int getPreparedStatementCacheQueries();

        /**
         * @return the DDL-runner credentials, used only at schema bootstrap (empty if not configured).
         */
        DdlCredentialsConfig getDdlCredentials();

        /**
         * An enumeration of the known config path / default value pairs of {@code PoolConfig}.
         */
        enum PoolConfigValue implements KnownConfigValue {

            INITIAL_SIZE("initial-size", 0),

            MAX_SIZE("max-size", 25),

            MAX_ACQUIRE_TIME("max-acquire-time", Duration.ofSeconds(4L)),

            ACQUIRE_RETRY("acquire-retry", 0),

            /**
             * No literal default: when unset, {@link #getPendingAcquireMaxSize()} is derived as 2 x max-size.
             */
            PENDING_ACQUIRE_MAX_SIZE("pending-acquire-max-size", null),

            MAX_IDLE_TIME("max-idle-time", Duration.ZERO),

            MAX_LIFE_TIME("max-life-time", Duration.ZERO),

            FETCH_SIZE("fetch-size", 0),

            PREPARED_STATEMENT_CACHE_QUERIES("prepared-statement-cache-queries", 0);

            private final String path;
            private final Object defaultValue;

            PoolConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

    }

    /**
     * The separate DDL-runner role (owns the schema; used by the schema bootstrap, the runtime self-heal and the
     * per-path search index builds). Empty when not configured.
     */
    @Immutable
    interface DdlCredentialsConfig {

        /**
         * @return whether both username and password are configured.
         */
        boolean isConfigured();

        /**
         * @return the DDL username, if configured.
         */
        Optional<String> getUsername();

        /**
         * @return the DDL password, if configured.
         */
        Optional<String> getPassword();

        /**
         * An enumeration of the known config path / default value pairs of {@code DdlCredentialsConfig}.
         */
        enum DdlCredentialsConfigValue implements KnownConfigValue {

            USERNAME("username", ""),

            PASSWORD("password", "");

            private final String path;
            private final Object defaultValue;

            DdlCredentialsConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

    }

    /**
     * TLS / SSL settings, mapped onto r2dbc-postgresql {@code sslMode}/{@code sslRootCert}/{@code sslCert}/
     * {@code sslKey}/{@code sslPassword} options.
     */
    @Immutable
    interface SslConfig {

        /**
         * The SSL mode: {@code disable|allow|prefer|require|verify-ca|verify-full}. Default {@code verify-full}.
         *
         * @return the configured SSL mode value.
         */
        String getMode();

        /**
         * @return the PEM CA bundle path the server cert must chain to ({@code sslRootCert}), if configured.
         */
        Optional<String> getRootCert();

        /**
         * @return the client cert PEM path for mTLS ({@code sslCert}), if configured.
         */
        Optional<String> getCert();

        /**
         * @return the client PKCS#8 key path for mTLS ({@code sslKey}), if configured.
         */
        Optional<String> getKey();

        /**
         * @return the password protecting the client key ({@code sslPassword}), if configured.
         */
        Optional<String> getKeyPassword();

        /**
         * The boot guard: when {@code false}, the client REFUSES to boot if {@code mode} is
         * {@code verify-ca}/{@code verify-full} and {@code root-cert} is unset — converting an otherwise silent
         * first-connect TLS handshake failure into a loud boot error.
         *
         * @return whether falling back to the JVM default truststore is permitted.
         */
        boolean isAllowSystemTruststore();

        /**
         * An enumeration of the known config path / default value pairs of {@code SslConfig}.
         */
        enum SslConfigValue implements KnownConfigValue {

            MODE("mode", "verify-full"),

            ROOT_CERT("root-cert", ""),

            CERT("cert", ""),

            KEY("key", ""),

            KEY_PASSWORD("key-password", ""),

            ALLOW_SYSTEM_TRUSTSTORE("allow-system-truststore", false);

            private final String path;
            private final Object defaultValue;

            SslConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

    }

    /**
     * Settings of the schema bootstrap a PostgreSQL-backed service runs once at start: how long one attempt may take,
     * whether and how long the start waits for a PostgreSQL that is not reachable yet, and whether a finally failed
     * bootstrap stops the service.
     */
    @Immutable
    interface BootstrapConfig {

        /**
         * How long one bootstrap attempt (connect, any precondition, the schema check/DDL and the catalog verification
         * under the advisory lock) may take. An attempt that runs out of it is not retried: PostgreSQL accepted the
         * connection, but the bootstrap is stuck.
         *
         * @return the attempt timeout, positive.
         */
        Duration getAttemptTimeout();

        /**
         * Whether a finally failed bootstrap (a failure that is not retried, exhausted retries, an attempt timeout or
         * an invalid configuration) stops the service through Pekko's coordinated shutdown, so that its orchestrator
         * restarts it. A wait ended by a shutdown that is already in progress never does.
         *
         * @return whether a failed bootstrap stops the service.
         */
        boolean isExitOnFailure();

        /**
         * @return the settings of the wait for a PostgreSQL that is not reachable yet.
         */
        RetryConfig getRetryConfig();

        /**
         * The wait for a PostgreSQL that is not reachable yet: exponential backoff from {@link #getMinBackoff()} to
         * {@link #getMaxBackoff()} with ±20 % jitter, for at most {@link #getMaxDuration()} after the first attempt.
         */
        @Immutable
        interface RetryConfig {

            /**
             * @return whether unreachable-PostgreSQL failures are retried at all ({@code false}: one attempt).
             */
            boolean isEnabled();

            /**
             * @return the total wait from the first attempt; the last attempt starts no later than this.
             * {@code Duration.ZERO} means no limit.
             */
            Duration getMaxDuration();

            /**
             * @return the delay after the first failed attempt, positive.
             */
            Duration getMinBackoff();

            /**
             * @return the largest delay between two attempts (before jitter), not shorter than
             * {@link #getMinBackoff()}.
             */
            Duration getMaxBackoff();

            /**
             * An enumeration of the known config path / default value pairs of {@code RetryConfig}.
             */
            enum RetryConfigValue implements KnownConfigValue {

                ENABLED("enabled", true),

                MAX_DURATION("max-duration", Duration.ofSeconds(60L)),

                MIN_BACKOFF("min-backoff", Duration.ofSeconds(1L)),

                MAX_BACKOFF("max-backoff", Duration.ofSeconds(10L));

                private final String path;
                private final Object defaultValue;

                RetryConfigValue(final String thePath, final Object theDefaultValue) {
                    path = thePath;
                    defaultValue = theDefaultValue;
                }

                @Override
                public String getConfigPath() {
                    return path;
                }

                @Override
                public Object getDefaultValue() {
                    return defaultValue;
                }

            }

        }

        /**
         * An enumeration of the known config path / default value pairs of {@code BootstrapConfig}.
         */
        enum BootstrapConfigValue implements KnownConfigValue {

            ATTEMPT_TIMEOUT("attempt-timeout", Duration.ofSeconds(60L)),

            EXIT_ON_FAILURE("exit-on-failure", true);

            private final String path;
            private final Object defaultValue;

            BootstrapConfigValue(final String thePath, final Object theDefaultValue) {
                path = thePath;
                defaultValue = theDefaultValue;
            }

            @Override
            public String getConfigPath() {
                return path;
            }

            @Override
            public Object getDefaultValue() {
                return defaultValue;
            }

        }

    }

    /**
     * An enumeration of the top-level known config path / default value pairs of {@code PostgresConfig}.
     */
    enum PostgresConfigValue implements KnownConfigValue {

        URI("uri", "r2dbc:postgresql://localhost:5432/ditto"),

        USERNAME("username", ""),

        PASSWORD("password", ""),

        POOLER_MODE("pooler-mode", PoolerMode.DIRECT.getConfigValue()),

        CONNECT_TIMEOUT("connect-timeout", Duration.ofSeconds(2L)),

        STATEMENT_TIMEOUT("statement-timeout", Duration.ofSeconds(60L)),

        SCHEMA_SELF_HEAL("schema.self-heal", true),

        FORCE_CUSTOM_PLAN("force-custom-plan", false);

        private final String path;
        private final Object defaultValue;

        PostgresConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

}
