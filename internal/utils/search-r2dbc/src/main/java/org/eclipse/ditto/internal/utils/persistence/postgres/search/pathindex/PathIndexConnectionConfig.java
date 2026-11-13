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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PoolerMode;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;

/**
 * The {@link PostgresConfig} of the connection that maintains the path indexes: the runtime config with the direct
 * URI (when configured) instead of {@code ditto.postgresql.uri}, no {@code statement_timeout} startup option and no
 * {@code plan_cache_mode} override. Credentials, SSL and connect timeout are the runtime ones; the connection is opened
 * with the DDL role like the schema bootstrap.
 * <p>
 * The maintenance needs one server session for its whole run (a session advisory lock, {@code SET statement_timeout},
 * {@code CREATE INDEX CONCURRENTLY}), which a transaction-mode pooler does not provide:
 * {@link #canHoldASession(PostgresConfig, Optional)}.
 * </p>
 */
@Immutable
final class PathIndexConnectionConfig implements PostgresConfig {

    private final PostgresConfig delegate;
    private final String uri;

    private PathIndexConnectionConfig(final PostgresConfig delegate, final String uri) {
        this.delegate = delegate;
        this.uri = uri;
    }

    /**
     * @param runtime the runtime client config ({@code ditto.postgresql}).
     * @param directUri the configured direct URI, if any.
     * @return the config of the index-maintenance connection.
     */
    static PostgresConfig of(final PostgresConfig runtime, final Optional<String> directUri) {
        Objects.requireNonNull(runtime, "runtime");
        return new PathIndexConnectionConfig(runtime, directUri.orElseGet(runtime::getUri));
    }

    /**
     * @param runtime the runtime client config.
     * @param directUri the configured direct URI, if any.
     * @return {@code false} if the runtime URI goes through a transaction-mode pooler (declared by
     * {@code ditto.postgresql.pooler-mode = transaction}) and no direct URI is configured: consecutive statements may
     * then run on different server connections.
     */
    static boolean canHoldASession(final PostgresConfig runtime, final Optional<String> directUri) {
        return directUri.isPresent() || runtime.getPoolerMode() != PoolerMode.TRANSACTION;
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public Optional<String> getUsername() {
        return delegate.getUsername();
    }

    @Override
    public Optional<String> getPassword() {
        return delegate.getPassword();
    }

    @Override
    public PoolerMode getPoolerMode() {
        return delegate.getPoolerMode();
    }

    @Override
    public Duration getConnectTimeout() {
        return delegate.getConnectTimeout();
    }

    /**
     * @return zero: an index build runs for minutes, so the connection gets no {@code statement_timeout} startup
     * option (the maintenance additionally runs {@code SET statement_timeout = 0} against role/database defaults).
     */
    @Override
    public Duration getStatementTimeout() {
        return Duration.ZERO;
    }

    @Override
    public boolean isSchemaSelfHealEnabled() {
        return delegate.isSchemaSelfHealEnabled();
    }

    /**
     * @return {@code false}: the maintenance connection runs no search queries.
     */
    @Override
    public boolean isForceCustomPlan() {
        return false;
    }

    @Override
    public PoolConfig getPoolConfig() {
        return delegate.getPoolConfig();
    }

    @Override
    public SslConfig getSslConfig() {
        return delegate.getSslConfig();
    }

    @Override
    public BootstrapConfig getBootstrapConfig() {
        return delegate.getBootstrapConfig();
    }

}
