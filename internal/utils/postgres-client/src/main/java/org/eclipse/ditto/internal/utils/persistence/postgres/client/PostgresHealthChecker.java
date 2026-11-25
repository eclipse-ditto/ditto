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

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.internal.utils.health.StatusInfo;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;

/**
 * PostgreSQL health-check actor — the Postgres mirror of {@code MongoHealthChecker}. It surfaces backend
 * connectivity into Ditto's health subsystem by running a real {@code SELECT 1} against the single shared connection
 * pool owned by {@link PostgresClientExtension} and reporting {@link StatusInfo.Status#UP} on a {@code 1} result,
 * {@link StatusInfo.Status#DOWN} (with the error detail) otherwise.
 * <p>
 * Unlike {@code MongoHealthChecker} — which builds its OWN small Mongo client and closes it on {@code postStop} — this
 * health checker reuses the shared {@link DittoPostgresClient} pool resolved from {@link PostgresClientExtension}. That
 * pool is owned and lifecycle-managed by the extension (drained once via {@code CoordinatedShutdown}), so this actor
 * neither builds nor closes a client: {@code SELECT 1} is a trivial, read-only probe whose per-query connection is
 * acquired from and released back to the shared pool on every terminal path (see {@link DittoPostgresClient#executeSql}).
 *
 * @since 4.0.0
 */
public final class PostgresHealthChecker extends AbstractHealthCheckingActor {

    private final DittoPostgresClient client;
    private final Materializer materializer;

    @SuppressWarnings("unused") // instantiated reflectively via Props
    private PostgresHealthChecker() {
        // Resolve the single shared pool the journal / snapshot-store / read-journal plugins already use. Resolving it
        // here (rather than building a dedicated health pool) keeps a Postgres service to ONE pool and means the health
        // probe reports on the very pool the write/read paths depend on.
        this.client = PostgresClientExtension.get(getContext().getSystem()).getClient();
        this.materializer = Materializer.createMaterializer(this::getContext);
    }

    /**
     * @return the {@link Props} of the PostgreSQL health-check actor. No-arg, matching the no-arg
     * {@code MongoHealthChecker.props()} that the per-service {@code RootActor}s pass to
     * {@code DefaultHealthCheckingActorFactory}.
     */
    public static Props props() {
        return Props.create(PostgresHealthChecker.class);
    }

    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .match(CurrentPostgresStatus.class, this::applyPostgresStatus)
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        runSelectOne().thenAccept(errorOpt -> {
            final CurrentPostgresStatus status;
            if (errorOpt.isPresent()) {
                final Throwable error = errorOpt.get();
                status = new CurrentPostgresStatus(false,
                        error.getClass().getCanonicalName() + ": " + error.getMessage());
                log.error(error, "PostgreSQL health check failed: {}", error.getMessage());
            } else {
                status = new CurrentPostgresStatus(true, null);
            }
            getSelf().tell(status, ActorRef.noSender());
        });
    }

    private CompletionStage<Optional<Throwable>> runSelectOne() {
        // SELECT 1 — a real round-trip to PostgreSQL via the shared pool; one row carrying the literal 1.
        return client.executeSql("SELECT 1 AS one", null, (row, meta) -> row.get("one", Integer.class))
                .runWith(Sink.head(), materializer)
                .handle((result, error) -> {
                    if (error != null) {
                        return Optional.of(error);
                    } else if (result == null || result != 1) {
                        return Optional.of(
                                new IllegalStateException("Expected SELECT 1 to return 1 but got: " + result));
                    } else {
                        return Optional.<Throwable>empty();
                    }
                });
    }

    private void applyPostgresStatus(final CurrentPostgresStatus status) {
        final StatusInfo persistenceStatus = StatusInfo.fromStatus(
                status.alive() ? StatusInfo.Status.UP : StatusInfo.Status.DOWN,
                status.description());
        updateHealth(persistenceStatus);
    }

    /**
     * The internal status message the {@code SELECT 1} probe pipes back to this actor (the Postgres counterpart of
     * {@code CurrentMongoStatus}).
     *
     * @param alive whether the probe succeeded.
     * @param description the failure detail when {@code alive} is {@code false}, otherwise {@code null}.
     */
    private record CurrentPostgresStatus(boolean alive, @Nullable String description) {}

}
