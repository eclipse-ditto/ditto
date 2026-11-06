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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Closeable;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Runs a component's PostgreSQL schema bootstrap at service start: the production entry point of
 * {@link PostgresSchemaManager}, shared by the persistence and the search backend.
 * <p>
 * It builds the one-shot DDL connection factory ({@link ConnectionPoolFactory#createDdlConnectionFactory}) and disposes
 * it afterwards, and runs the bootstrap with the retry configured under {@code ditto.postgresql.bootstrap}: while
 * PostgreSQL is not reachable yet, the calling (root-actor constructor) thread waits for at most
 * {@code retry.max-duration}. A coordinated shutdown (e.g. SIGTERM) ends the wait at once, through a cancellable task
 * in phase {@code before-service-unbind}. When the bootstrap finally fails for any other reason and
 * {@code exit-on-failure} is set, it starts a coordinated shutdown with {@link BootstrapFailed}, so that the service
 * stops (exit code 1 where {@code exit-jvm} is on) and its orchestrator restarts it. Every failure is rethrown
 * unchanged. {@link #stopServiceOnBootFailure} applies the same rule to the other PostgreSQL boot check, the
 * persistence provider's plugin-wiring check.
 * </p>
 */
public final class PostgresSchemaBootstrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresSchemaBootstrapper.class);

    private static final String ABORT_TASK_NAME_PREFIX = "postgres-schema-bootstrap-abort-";

    private PostgresSchemaBootstrapper() {
        throw new AssertionError();
    }

    /**
     * Bootstraps the given component schema.
     *
     * @param system the service's actor system (coordinated shutdown).
     * @param config the PostgreSQL configuration.
     * @param descriptor the component schema.
     * @throws RuntimeException the bootstrap's failure, unchanged (see
     * {@link PostgresSchemaManager#bootstrap(Mono, Mono)}).
     * @throws DittoConfigError if the DDL connection factory's configuration is unsafe (SSL boot guard).
     */
    public static void bootstrap(final ActorSystem system, final PostgresConfig config,
            final PostgresSchemaDescriptor descriptor) {
        bootstrap(system, config, descriptor, ddlConnectionFactory -> Mono.empty());
    }

    /**
     * Bootstraps the given component schema after a precondition that runs at the start of every attempt.
     *
     * @param system the service's actor system (coordinated shutdown).
     * @param config the PostgreSQL configuration.
     * @param descriptor the component schema.
     * @param precondition given the DDL connection factory, a lazy check subscribed at the start of every attempt
     * (e.g. database prerequisites); it fails with a {@code SchemaBootException} to refuse the database.
     */
    public static void bootstrap(final ActorSystem system, final PostgresConfig config,
            final PostgresSchemaDescriptor descriptor, final Function<ConnectionFactory, Mono<Void>> precondition) {
        bootstrap(system, config, descriptor, precondition, ConnectionPoolFactory::createDdlConnectionFactory);
    }

    static void bootstrap(final ActorSystem system, final PostgresConfig config,
            final PostgresSchemaDescriptor descriptor, final Function<ConnectionFactory, Mono<Void>> precondition,
            final Function<PostgresConfig, ConnectionFactory> ddlConnectionFactoryCreator) {
        final PostgresConfig.BootstrapConfig bootstrapConfig = config.getBootstrapConfig();
        final CoordinatedShutdown coordinatedShutdown = CoordinatedShutdown.get(system);
        final Sinks.Empty<Void> abort = Sinks.empty();
        final Cancellable abortTask = coordinatedShutdown.addCancellableTask(
                CoordinatedShutdown.PhaseBeforeServiceUnbind(),
                ABORT_TASK_NAME_PREFIX + descriptor.component(),
                () -> {
                    abort.tryEmitEmpty();
                    return CompletableFuture.completedFuture(Done.done());
                });
        if (coordinatedShutdown.getShutdownReason().isPresent()) {
            // A task added after the shutdown started may never run.
            abort.tryEmitEmpty();
        }
        ConnectionFactory ddlConnectionFactory = null;
        try {
            ddlConnectionFactory = ddlConnectionFactoryCreator.apply(config);
            PostgresSchemaManager.of(ddlConnectionFactory, descriptor, bootstrapConfig)
                    .bootstrap(precondition.apply(ddlConnectionFactory), abort.asMono());
        } catch (final RuntimeException | DittoConfigError e) {
            // DittoConfigError (SSL boot guard) extends Error, not RuntimeException: a configuration error is a final
            // failure too. The abort task may still fire while the shutdown starts; nothing listens any more.
            onFailure(coordinatedShutdown, bootstrapConfig, descriptor.component(), e);
            throw e;
        } finally {
            abortTask.cancel();
            disposeQuietly(ddlConnectionFactory);
        }
    }

    private static void onFailure(final CoordinatedShutdown coordinatedShutdown,
            final PostgresConfig.BootstrapConfig bootstrapConfig, final String component, final Throwable failure) {
        if (failure instanceof SchemaBootstrapAbortedException) {
            LOGGER.info("PostgreSQL schema bootstrap of component '{}' aborted: the service is shutting down.",
                    component);
        } else {
            stopServiceOnBootFailure(coordinatedShutdown, bootstrapConfig, component, "schema bootstrap");
        }
    }

    /**
     * Applies the {@code exit-on-failure} rule to a failed PostgreSQL boot step other than the schema bootstrap: when
     * {@code ditto.postgresql.bootstrap.exit-on-failure} is set, starts a coordinated shutdown with
     * {@link BootstrapFailed} (exit code 1 where {@code exit-jvm} is on), so that the orchestrator restarts the service
     * and a corrected configuration is picked up; otherwise the JVM keeps running without its root actor. The caller
     * rethrows its failure either way.
     *
     * @param system the service's actor system.
     * @param bootstrapConfig the bootstrap configuration carrying {@code exit-on-failure}.
     * @param component the component whose boot failed, as in {@link BootstrapFailed#getComponent()}.
     * @param step the failed boot step for the log, e.g. {@code "plugin wiring check"}.
     */
    public static void stopServiceOnBootFailure(final ActorSystem system,
            final PostgresConfig.BootstrapConfig bootstrapConfig, final String component, final String step) {
        stopServiceOnBootFailure(CoordinatedShutdown.get(system), bootstrapConfig, component, step);
    }

    private static void stopServiceOnBootFailure(final CoordinatedShutdown coordinatedShutdown,
            final PostgresConfig.BootstrapConfig bootstrapConfig, final String component, final String step) {
        if (bootstrapConfig.isExitOnFailure()) {
            LOGGER.warn("PostgreSQL {} of component '{}' failed: stopping the service (coordinated shutdown) so that "
                    + "it is restarted. Set ditto.postgresql.bootstrap.exit-on-failure = false "
                    + "(POSTGRES_BOOTSTRAP_EXIT_ON_FAILURE) to keep it running without its root actor.",
                    step, component);
            coordinatedShutdown.run(new BootstrapFailed(component));
        }
    }

    private static void disposeQuietly(@Nullable final ConnectionFactory ddlConnectionFactory) {
        // The DDL factory is a one-shot, non-pooled factory; r2dbc factories implement Closeable.close() which returns
        // a Publisher. Subscribe so it actually closes, but never let a close failure mask a bootstrap outcome.
        if (ddlConnectionFactory instanceof Closeable closeable) {
            try {
                Mono.from(closeable.close()).onErrorResume(error -> {
                    LOGGER.warn("Failed to close the DDL connection factory after schema bootstrap.", error);
                    return Mono.empty();
                }).block();
            } catch (final RuntimeException e) {
                LOGGER.warn("Failed to close the DDL connection factory after schema bootstrap.", e);
            }
        }
    }

    /**
     * The coordinated-shutdown reason after a failed schema bootstrap or plugin-wiring check. Its class name is the key
     * of the {@code pekko.coordinated-shutdown.reason-overrides} entry in {@code ditto-postgres-client.conf} (exit
     * code 1).
     */
    @Immutable
    public static final class BootstrapFailed implements CoordinatedShutdown.Reason {

        private final String component;

        BootstrapFailed(final String component) {
            this.component = Objects.requireNonNull(component, "component");
        }

        /**
         * @return the component whose boot step failed.
         */
        public String getComponent() {
            return component;
        }

        @Override
        public boolean equals(final Object o) {
            return this == o || o instanceof BootstrapFailed that && component.equals(that.component);
        }

        @Override
        public int hashCode() {
            return component.hashCode();
        }

        @Override
        public String toString() {
            return "PostgresBootFailed[component=" + component + "]";
        }

    }

}
