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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring;

import java.util.Locale;
import java.util.Set;

/**
 * The new, engine-neutral R2DBC/PostgreSQL observability metric family.
 * <p>
 * This deliberately does <em>not</em> mirror the Mongo {@code KamonCommandListener} family (which uses a
 * {@code _mongodb} name suffix + {@code command_name}/{@code cluster_id} tags and lives only in thingsearch). There is
 * no {@code engine=} tag on the Mongo side and {@code persistence-api} has zero metric constants, so there is nothing
 * engine-neutral to be "parity" with. The Mongo path is left entirely untouched and no constants are shared — these
 * constants are defined locally here.
 * </p>
 * <ul>
 *     <li>{@value #COMMAND_DURATION} — command timer, tagged {@value #TAG_ENGINE}={@value #ENGINE_POSTGRES},
 *     {@value #TAG_OP_KIND} (the neutral op-kind value domain), {@value #TAG_STATUS} ({@value #STATUS_SUCCESS} /
 *     {@value #STATUS_ERROR}). Recorded on success <em>and</em> error.</li>
 *     <li>{@value #ACQUIRE_DURATION} — connection-acquire latency histogram ({@code PoolMetrics} exposes no acquire
 *     wait-time, so this comes from r2dbc-proxy create-connection hooks).</li>
 *     <li>{@value #POOL_ACQUIRED}/{@value #POOL_ALLOCATED}/{@value #POOL_IDLE}/{@value #POOL_PENDING} — pool gauges
 *     polled from {@link io.r2dbc.pool.ConnectionPool#getMetrics()}.</li>
 * </ul>
 */
public final class PostgresMetrics {

    /** Command duration timer metric name. */
    public static final String COMMAND_DURATION = "ditto_persistence_command_duration";

    /** Connection-acquire latency histogram metric name. */
    public static final String ACQUIRE_DURATION = "ditto_persistence_acquire_duration";

    /** Pool gauge metric names. */
    public static final String POOL_ACQUIRED = "ditto_persistence_pool_acquired";
    public static final String POOL_ALLOCATED = "ditto_persistence_pool_allocated";
    public static final String POOL_IDLE = "ditto_persistence_pool_idle";
    public static final String POOL_PENDING = "ditto_persistence_pool_pending";

    /**
     * Schema self-heal counter metric name — incremented once per component schema whose on-demand heal ran DDL (a
     * runtime statement hit a missing table {@code 42P01}, so the schema was recreated and the statement retried); a
     * heal that ran no DDL or failed is not counted. Tagged {@value #TAG_ENGINE}={@value #ENGINE_POSTGRES}. The WARN
     * log companion of this counter flags the masks-data-loss trade-off; a non-zero rate means a table was dropped or
     * a database wiped/recreated under a running service.
     */
    public static final String SELF_HEAL_COUNT = "ditto_persistence_schema_self_heal";

    /** Tag keys. */
    public static final String TAG_ENGINE = "engine";
    public static final String TAG_OP_KIND = "op_kind";
    public static final String TAG_STATUS = "status";

    /**
     * Discriminating tag key for the pool gauges. Its value is the pool's <em>role</em> (a fixed, low-cardinality
     * member of {@link #POOL_ROLE_DOMAIN}, never a {@code pid}), so that more than one pool in a single JVM cannot
     * collapse onto the same Kamon instrument (the gauge {@code set} is last-writer-wins). With the shared-pool
     * topology a service publishes exactly one role ({@value #ROLE_SHARED}); the per-role values exist so a future
     * split-pool topology stays observable without changing the metric name.
     */
    public static final String TAG_POOL = "pool";

    /** The single {@value #TAG_ENGINE} tag value for this family. */
    public static final String ENGINE_POSTGRES = "postgres";

    /**
     * {@value #TAG_POOL} values — the pool's role. A Ditto service shares one pool across its journal, snapshot-store
     * and read-journal plugins (so it polls under {@value #ROLE_SHARED}); the journal/snapshot/read-journal values
     * keep the gauges distinctly tagged should the pools ever be split again.
     */
    public static final String ROLE_SHARED = "shared";
    public static final String ROLE_JOURNAL = "journal";
    public static final String ROLE_SNAPSHOT = "snapshot";
    public static final String ROLE_READ_JOURNAL = "readjournal";

    /** The complete, closed {@value #TAG_POOL} value domain. */
    public static final Set<String> POOL_ROLE_DOMAIN =
            Set.of(ROLE_SHARED, ROLE_JOURNAL, ROLE_SNAPSHOT, ROLE_READ_JOURNAL);

    /** {@value #TAG_STATUS} values. */
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";

    /** {@value #TAG_OP_KIND} values — the neutral, engine-independent op-kind domain. */
    public static final String OP_SELECT = "select";
    public static final String OP_INSERT = "insert";
    public static final String OP_UPDATE = "update";
    public static final String OP_DELETE = "delete";
    public static final String OP_DDL = "ddl";
    public static final String OP_OTHER = "other";

    /** The complete, closed {@value #TAG_OP_KIND} value domain (asserted by the contract test). */
    public static final Set<String> OP_KIND_DOMAIN =
            Set.of(OP_SELECT, OP_INSERT, OP_UPDATE, OP_DELETE, OP_DDL, OP_OTHER);

    /** The complete {@value #TAG_STATUS} value domain. */
    public static final Set<String> STATUS_DOMAIN = Set.of(STATUS_SUCCESS, STATUS_ERROR);

    private PostgresMetrics() {
        throw new AssertionError();
    }

    /**
     * Classifies a SQL statement into the neutral {@value #TAG_OP_KIND} domain by its leading keyword. Keeps tag
     * cardinality bounded (no table names, no full query text) — exactly the values in {@link #OP_KIND_DOMAIN}.
     *
     * @param sql the SQL text (may be {@code null}).
     * @return one of {@link #OP_KIND_DOMAIN}.
     */
    public static String classifyOpKind(final String sql) {
        if (sql == null || sql.isBlank()) {
            return OP_OTHER;
        }
        final String trimmed = sql.stripLeading().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("select") || trimmed.startsWith("with")) {
            return OP_SELECT;
        }
        if (trimmed.startsWith("insert")) {
            return OP_INSERT;
        }
        if (trimmed.startsWith("update")) {
            return OP_UPDATE;
        }
        if (trimmed.startsWith("delete")) {
            return OP_DELETE;
        }
        if (trimmed.startsWith("create") || trimmed.startsWith("alter") || trimmed.startsWith("drop")) {
            return OP_DDL;
        }
        return OP_OTHER;
    }

}
