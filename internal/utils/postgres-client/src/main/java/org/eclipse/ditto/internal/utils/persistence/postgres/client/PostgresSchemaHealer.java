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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring.PostgresMetrics;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaVersionDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Closeable;
import io.r2dbc.spi.ConnectionFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * On-demand PostgreSQL schema self-heal collaborator.
 * <p>
 * When a runtime statement hits a missing table (SQLSTATE {@code 42P01}), the runtime execution paths call
 * {@link #heal()} to recreate the schema (idempotent {@code CREATE … IF NOT EXISTS}) before retrying the statement once,
 * giving the Postgres backend MongoDB's implicit-collection-create-on-first-use property. A dropped/recreated database
 * therefore self-heals instead of failing every persistence call. A heal never runs this code's DDL against a newer
 * stored schema version (rolling upgrade): see {@link PostgresSchemaManager#ensureTablesReactive()}.
 * </p>
 * <p>
 * Healing needs DDL privilege, which the DML-only runtime pool does not have, so the heal runs through a separate,
 * DDL-role {@link ConnectionFactory} built via {@link ConnectionPoolFactory#createDdlConnectionFactory(PostgresConfig)}
 * (which falls back to the runtime role when {@code ddl-credentials} are empty — exactly like boot bootstrap). That
 * factory is built lazily and <strong>cached</strong> (reused across heals), but each heal still opens one short-lived
 * DDL connection — there is no lingering idle privileged connection and no DDL pool.
 * </p>
 * <p>
 * {@link #heal()} has <strong>single-flight</strong> semantics: concurrent callers (heals fire from many dispatcher
 * threads) share one in-flight heal {@link Mono}; the shared reference is reset on terminal (success or failure) so a
 * later miss can heal again. A single heal recreates <em>all</em> entity tables at once, so one coalescing key is
 * sufficient.
 * </p>
 * <p>
 * Each heal that recreates a component's schema emits a WARN log + increments {@link PostgresMetrics#SELF_HEAL_COUNT}
 * (a heal that runs no DDL because the stored schema version is newer and compatible, or because every table and
 * index of the schema exists, is neither logged as a recreation nor counted): the heal <strong>masks data loss</strong>
 * (a wiped database silently self-heals to empty tables), the same trade-off MongoDB already has. Operators wanting
 * fail-loud set {@code ditto.postgresql.schema.self-heal = false} ({@link #isEnabled()} then {@code false}, and the
 * original error surfaces unchanged).
 * </p>
 */
@ThreadSafe
public final class PostgresSchemaHealer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSchemaHealer.class);

    private final boolean enabled;
    private final Supplier<ConnectionFactory> ddlConnectionFactorySupplier;
    private final Counter healCounter;

    /**
     * The component schemas a heal recreates, registered by each component's bootstrap (persistence and/or search —
     * the {@link PostgresSchemaManager} is descriptor-parameterized, so the healer cannot know them statically without
     * a reverse dependency onto the component modules). Registration happens strictly before runtime statements flow
     * (bootstrap precedes traffic), so a firing heal always sees the service's descriptors.
     */
    private final Set<PostgresSchemaDescriptor> descriptors = new CopyOnWriteArraySet<>();

    /** The lazily-built, cached DDL-role factory (reused across heals). Built under {@code this} monitor. */
    private final AtomicReference<ConnectionFactory> ddlConnectionFactory = new AtomicReference<>();
    /** The in-flight heal, shared by concurrent callers and cleared on terminal (single-flight). */
    private final AtomicReference<Mono<Void>> inFlightHeal = new AtomicReference<>();

    private PostgresSchemaHealer(final boolean enabled,
            final Supplier<ConnectionFactory> ddlConnectionFactorySupplier) {
        this.enabled = enabled;
        this.ddlConnectionFactorySupplier = ddlConnectionFactorySupplier;
        healCounter = DittoMetrics.counter(PostgresMetrics.SELF_HEAL_COUNT)
                .tag(PostgresMetrics.TAG_ENGINE, PostgresMetrics.ENGINE_POSTGRES);
    }

    /**
     * Builds a healer from the backend configuration. The DDL-role connection factory is built lazily on the first heal
     * (never eagerly), so constructing a healer opens no connection.
     *
     * @param config the backend configuration; {@link PostgresConfig#isSchemaSelfHealEnabled()} decides whether healing
     * is active.
     * @return the healer.
     */
    public static PostgresSchemaHealer of(final PostgresConfig config) {
        return new PostgresSchemaHealer(config.isSchemaSelfHealEnabled(),
                () -> ConnectionPoolFactory.createDdlConnectionFactory(config));
    }

    /**
     * Test seam: builds a healer over an explicit DDL-factory supplier (e.g. a counting stub), bypassing
     * {@link ConnectionPoolFactory}.
     *
     * @param enabled whether healing is active.
     * @param ddlConnectionFactorySupplier the supplier of the DDL-role connection factory (invoked at most once —
     * the result is cached).
     * @return the healer.
     */
    static PostgresSchemaHealer forFactory(final boolean enabled,
            final Supplier<ConnectionFactory> ddlConnectionFactorySupplier) {
        return new PostgresSchemaHealer(enabled, ddlConnectionFactorySupplier);
    }

    /**
     * @return whether schema self-heal is enabled (from configuration).
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Registers a component schema this healer recreates on {@link #heal()}. Called by each component's schema
     * bootstrap (idempotent — descriptors are singletons, re-registration is a no-op); a heal runs the schema check
     * and, where needed, the idempotent DDL of every registered descriptor.
     *
     * @param descriptor the component schema descriptor.
     */
    public void registerDescriptor(final PostgresSchemaDescriptor descriptor) {
        descriptors.add(Objects.requireNonNull(descriptor, "descriptor"));
    }

    /**
     * Recreates the schema (idempotent DDL), coalescing concurrent callers onto a single in-flight heal.
     *
     * @return a {@link Mono} completing when the schema has been (re)created; {@link Mono#error(Throwable)} carrying an
     * {@link IllegalStateException} when healing is disabled (callers guard on {@link #isEnabled()} and surface the
     * original error instead).
     */
    public Mono<Void> heal() {
        if (!enabled) {
            return Mono.error(new IllegalStateException("PostgreSQL schema self-heal is disabled."));
        }
        // updateAndGet at subscription time: the first caller installs a shared, cached heal Mono; concurrent callers
        // that arrive while it is in flight get the same instance and share its single DDL run. The lambda may run more
        // than once under CAS contention, but only the stored value is ever subscribed (the throwaways are cold), so no
        // extra DDL runs.
        return Mono.defer(() -> inFlightHeal.updateAndGet(existing -> existing != null ? existing : buildSharedHeal()));
    }

    private Mono<Void> buildSharedHeal() {
        // .cache() so all concurrent subscribers share ONE upstream subscription (one DDL run); doFinally clears the
        // shared reference on terminal so a later miss can heal again. A later heal can only install its Mono AFTER this
        // doFinally has cleared the reference (updateAndGet installs only when the reference is null), so the plain
        // set(null) can never clobber a successor.
        return Mono.defer(this::doHeal)
                // Log a heal FAILURE once (doHeal WARN-logs a heal only after it recreated a schema). The callers
                // surface the original missing-table error on a heal failure, so without this the reason a heal could
                // not run (e.g. 42501 insufficient_privilege when the role lacks CREATE) would otherwise be invisible.
                .doOnError(error -> LOGGER.warn("PostgreSQL schema self-heal failed; the original missing-table error "
                        + "will surface unchanged.", error))
                .doFinally(signalType -> inFlightHeal.set(null))
                .cache();
    }

    private Mono<Void> doHeal() {
        final List<PostgresSchemaDescriptor> toHeal = List.copyOf(descriptors);
        if (toHeal.isEmpty()) {
            // No component bootstrap registered a schema (must not happen in a booted service: bootstrap precedes
            // runtime statements). Fail the heal so the caller surfaces the original missing-table error unchanged.
            return Mono.error(new IllegalStateException(
                    "PostgreSQL schema self-heal fired before any schema descriptor was registered; cannot heal."));
        }
        final ConnectionFactory ddlFactory = getOrCreateDdlConnectionFactory();
        return Flux.fromIterable(toHeal)
                .concatMap(descriptor -> PostgresSchemaManager.of(ddlFactory, descriptor).ensureTablesReactive()
                        .doOnNext(decision -> reportHeal(descriptor, decision)))
                .then();
    }

    private void reportHeal(final PostgresSchemaDescriptor descriptor, final SchemaVersionDecision decision) {
        if (decision == SchemaVersionDecision.APPLY) {
            LOGGER.warn("Missing PostgreSQL table detected; recreated the schema of component '{}' (self-heal). This "
                    + "masks a wiped/absent database — set ditto.postgresql.schema.self-heal=false to fail loud "
                    + "instead.", descriptor.component());
            incrementHealCounter();
        }
        // COMPATIBLE_NEWER / UP_TO_DATE: no DDL ran; PostgresSchemaManager logged why at INFO.
    }

    private void incrementHealCounter() {
        try {
            healCounter.increment();
        } catch (final RuntimeException e) {
            // Metrics must never break the heal: swallow any recorder/registry failure. Logged at DEBUG to avoid spam.
            LOGGER.debug("Failed to record the schema self-heal metric: {}", e.getMessage(), e);
        }
    }

    private ConnectionFactory getOrCreateDdlConnectionFactory() {
        ConnectionFactory factory = ddlConnectionFactory.get();
        if (factory == null) {
            synchronized (this) {
                factory = ddlConnectionFactory.get();
                if (factory == null) {
                    factory = ddlConnectionFactorySupplier.get();
                    ddlConnectionFactory.set(factory);
                }
            }
        }
        return factory;
    }

    /**
     * Closes the cached DDL connection factory, if one was built. Called when the owning {@link DittoPostgresClient} is
     * disposed (so no separate CoordinatedShutdown task is required). Never throws.
     */
    public void dispose() {
        final ConnectionFactory factory = ddlConnectionFactory.getAndSet(null);
        if (factory instanceof Closeable closeable) {
            try {
                Mono.from(closeable.close()).onErrorResume(error -> {
                    LOGGER.warn("Failed to close the schema self-heal DDL connection factory.", error);
                    return Mono.empty();
                }).block();
            } catch (final RuntimeException e) {
                LOGGER.warn("Failed to close the schema self-heal DDL connection factory.", e);
            }
        }
    }

}
