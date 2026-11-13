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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.write;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.pekko.streaming.TimestampPersistence;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * PostgreSQL implementation of {@link TimestampPersistence} backing the background-sync bookmark — the Postgres
 * replacement for {@code MongoTimestampPersistence} (plan §3.4 "Bookmark via {@code search_sync} upsert").
 * <p>
 * Where the Mongo impl keeps the single latest timestamp in a capped, {@code maxDocuments(1)} collection (each write
 * inserts a fresh document; reads take {@code sort(_id desc).limit(1)}), this impl keeps ONE row in {@code search_sync}
 * keyed by {@link #BOOKMARK_ID}: a write is an idempotent {@code INSERT … ON CONFLICT (id) DO UPDATE} (last write wins),
 * a read is a point {@code SELECT} on the primary key. The observable behavior is identical — after
 * {@code setTaggedTimestamp(t, tag)}, {@code getTaggedTimestamp()} returns {@code Optional.of(Pair(t, tag))} (with a
 * {@code null} tag preserved as a {@code null} second element, exactly like Mongo's {@code Pair.create(timestamp, tag)}),
 * and an unwritten bookmark reads back {@code Optional.empty()}.
 * </p>
 * <p>
 * <strong>Initialization guarantee:</strong> the {@code search_sync (id, ts, tag)} table is created by the provider's
 * synchronous {@code bootstrapSchema()} (SPI contract: it throws rather than return without the schema) before any
 * read/write is served — the same guarantee Mongo's {@code initializedInstance} gives by creating the capped
 * collection. This persistence therefore performs NO per-instance initialization; it only reads/writes the
 * already-provisioned row.
 * </p>
 *
 * @since 4.0.0
 */
@ThreadSafe
public final class PostgresTimestampPersistence implements TimestampPersistence {

    /** The single {@code search_sync} row id holding the background-sync bookmark. */
    static final String BOOKMARK_ID = "backgroundSync";

    /** Idempotent single-row upsert of the bookmark. Binds: {@code $1} id, {@code $2} ts, {@code $3} tag (nullable). */
    static final String UPSERT_SQL =
            "INSERT INTO search_sync (id, ts, tag) VALUES ($1, $2, $3) "
                    + "ON CONFLICT (id) DO UPDATE SET ts = EXCLUDED.ts, tag = EXCLUDED.tag";

    /** Point read of the bookmark row. Binds: {@code $1} id. */
    static final String SELECT_SQL = "SELECT ts, tag FROM search_sync WHERE id = $1";

    private final ConnectionFactory connectionFactory;

    private PostgresTimestampPersistence(final ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * @param client the shared PostgreSQL client (one connection pool per service, via {@code PostgresClientExtension}).
     * @return the timestamp persistence.
     */
    public static PostgresTimestampPersistence of(final DittoPostgresClient client) {
        return new PostgresTimestampPersistence(Objects.requireNonNull(client, "client").getConnectionPool());
    }

    /**
     * @param connectionFactory a connection factory (a pool, or a plain testkit factory) whose connections back the
     * bookmark upsert and read. Public so a cross-package IT (the search-sync IT lives in the read package) can build
     * this persistence directly over a testkit connection factory.
     * @return the timestamp persistence.
     */
    public static PostgresTimestampPersistence forConnectionFactory(final ConnectionFactory connectionFactory) {
        return new PostgresTimestampPersistence(Objects.requireNonNull(connectionFactory, "connectionFactory"));
    }

    @Override
    public Source<NotUsed, NotUsed> setTimestamp(final Instant timestamp) {
        return setTaggedTimestamp(timestamp, null).map(done -> NotUsed.getInstance());
    }

    @Override
    public Source<Done, NotUsed> setTaggedTimestamp(final Instant timestamp, @Nullable final String tag) {
        final Mono<Done> result = Flux.usingWhen(
                        Mono.from(connectionFactory.create()),
                        connection -> {
                            final Statement statement = connection.createStatement(UPSERT_SQL);
                            statement.bind(0, BOOKMARK_ID);
                            statement.bind(1, timestamp);
                            if (tag != null) {
                                statement.bind(2, tag);
                            } else {
                                statement.bindNull(2, String.class);
                            }
                            return Flux.from(statement.execute()).flatMap(Result::getRowsUpdated);
                        },
                        Connection::close)
                .then(Mono.just(Done.done()));
        return Source.fromPublisher(result);
    }

    @Override
    public Source<Optional<Instant>, NotUsed> getTimestampAsync() {
        return getTaggedTimestamp().map(optional -> optional.map(Pair::first));
    }

    @Override
    public Source<Optional<Pair<Instant, String>>, NotUsed> getTaggedTimestamp() {
        final Mono<Optional<Pair<Instant, String>>> result = Flux.usingWhen(
                        Mono.from(connectionFactory.create()),
                        connection -> {
                            final Statement statement = connection.createStatement(SELECT_SQL);
                            statement.bind(0, BOOKMARK_ID);
                            return Flux.from(statement.execute())
                                    .flatMap(res -> res.map((row, meta) -> Pair.create(
                                            row.get("ts", Instant.class),
                                            row.get("tag", String.class))));
                        },
                        Connection::close)
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        return Source.fromPublisher(result);
    }

}
