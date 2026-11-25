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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Whether a {@link SystemSearchIndex} is present and valid ({@code pg_index.indisvalid}) on {@code search_flat}, as
 * last seen by this instance. The read path stages range legs only while {@link SystemSearchIndex#MULTI_VALUED} is
 * valid: without it the multi-valued branch of a range leg reads every row of its path. Only one instance builds the
 * index, so every instance checks the catalog itself: once at the start, after this instance's maintenance run, and
 * every {@link #DEFAULT_INTERVAL}. A failed check keeps the last state. An index that is missing, still being built or
 * INVALID counts as not valid.
 */
@ThreadSafe
public final class SystemIndexProbe implements BooleanSupplier {

    /** How often the service re-checks the catalog. */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(60L);

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemIndexProbe.class);

    private static final String VALID_SQL = "SELECT EXISTS (SELECT 1 FROM pg_index i "
            + "JOIN pg_class c ON c.oid = i.indexrelid "
            + "JOIN pg_class t ON t.oid = i.indrelid "
            + "JOIN pg_namespace n ON n.oid = t.relnamespace "
            + "WHERE n.nspname = current_schema() AND t.relname = 'search_flat' AND c.relname = $1 "
            + "AND i.indisvalid) AS valid";

    private final SystemSearchIndex index;
    private final AtomicBoolean valid = new AtomicBoolean(false);
    private final AtomicBoolean checked = new AtomicBoolean(false);

    private SystemIndexProbe(final SystemSearchIndex index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    /**
     * @param index the system index to watch.
     * @return a probe that reports "not valid" until its first successful check.
     */
    public static SystemIndexProbe of(final SystemSearchIndex index) {
        return new SystemIndexProbe(index);
    }

    @Override
    public boolean getAsBoolean() {
        return valid.get();
    }

    /**
     * Checks the catalog once.
     *
     * @param connectionFactory where to run the catalog query (the runtime pool is fine).
     * @return the state after the check; on failure the previous state.
     */
    public Mono<Boolean> refresh(final ConnectionFactory connectionFactory) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Flux.from(connection.createStatement(VALID_SQL)
                                        .bind("$1", index.indexName())
                                        .execute())
                                .flatMap(result -> result.map((row, meta) ->
                                        Boolean.TRUE.equals(row.get("valid", Boolean.class))))
                                .next()
                                .defaultIfEmpty(false),
                        Connection::close)
                .doOnNext(this::update)
                .onErrorResume(error -> {
                    LOGGER.debug("Checking search index {} failed, keeping the last state ({}): {}",
                            index.indexName(), valid.get(), error.getMessage());
                    return Mono.just(valid.get());
                });
    }

    /**
     * Checks now and then every {@code interval} until the returned {@link Disposable} is disposed.
     *
     * @param connectionFactory where to run the catalog query.
     * @param interval the check interval.
     * @return the running poll.
     */
    public Disposable poll(final ConnectionFactory connectionFactory, final Duration interval) {
        return Flux.interval(Duration.ZERO, interval)
                .onBackpressureDrop()
                .concatMap(tick -> refresh(connectionFactory), 1)
                .subscribe();
    }

    private void update(final boolean nowValid) {
        final boolean before = valid.getAndSet(nowValid);
        final boolean first = checked.compareAndSet(false, true);
        if (nowValid && (first || !before)) {
            LOGGER.info("Search index {} is valid: two-sided numeric range filters may start from the range.",
                    index.indexName());
        } else if (!nowValid && (first || before)) {
            LOGGER.info("Search index {} is missing, being built or INVALID: two-sided numeric range filters use the "
                    + "plan without it until it is valid.", index.indexName());
        }
    }

}
