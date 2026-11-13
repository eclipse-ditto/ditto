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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.junit.rules.ExternalResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * A JUnit {@link ExternalResource} wrapping a Testcontainers PostgreSQL container (PG 16 by default), mirroring the
 * shape of {@code MongoDbResource} so the PostgreSQL parity-matrix ITs share one container-lifecycle wrapper.
 * <p>
 * The image of a resource created without an explicit one is chosen by the system property
 * {@value #IMAGE_PROPERTY}, e.g. {@code mvn verify -Dditto.test.postgres.image=postgres:17} (failsafe passes command
 * line properties on to the forked test JVM); without it, {@value #DEFAULT_IMAGE}. The {@code initdb} arguments are
 * chosen the same way, by {@value #INITDB_ARGS_PROPERTY} or {@link #withInitdbArgs(String)}. ITs that start their own
 * {@code PostgreSQLContainer} use {@link #newContainer()} so that both properties reach them.
 * </p>
 * <p>
 * The Docker API version is left to Testcontainers' own negotiation (no {@code api.version} pin), so the ITs follow the
 * daemon's supported range.
 * </p>
 * <p>
 * The resource exposes both the DDL role (schema-owning, used by {@code PostgresSchemaManager}) and the connection
 * coordinates needed to build an r2dbc {@link ConnectionFactory}. A {@code @Rule}/{@code @ClassRule} starts the
 * container in {@link #before()} and stops it in {@link #after()}; tests that need a manual lifecycle can call
 * {@link #start()}/{@link #stop()} directly.
 * </p>
 */
@NotThreadSafe
public final class PostgresDbResource extends ExternalResource {

    /** The default PostgreSQL image — PG 16 is the oldest tested version (tested: 16, 17, 18). */
    public static final String DEFAULT_IMAGE = "postgres:16";

    /**
     * The system property that chooses the PostgreSQL image of every resource created without an explicit image, e.g.
     * {@code postgres:17}.
     */
    public static final String IMAGE_PROPERTY = "ditto.test.postgres.image";

    /**
     * The system property that sets {@value #INITDB_ARGS_ENV} (the official image's {@code initdb} arguments) for every
     * resource that does not set its own ({@link #withInitdbArgs(String)}) and for every {@link #newContainer()}
     * container, e.g. {@code -Dditto.test.postgres.initdb-args="--encoding=UTF8 --locale=C.UTF-8"}. Unset or blank:
     * none, i.e. the image's own cluster ({@code en_US.utf8} for the official image). Independent of
     * {@value #IMAGE_PROPERTY}.
     */
    public static final String INITDB_ARGS_PROPERTY = "ditto.test.postgres.initdb-args";

    /** The environment variable the official {@code postgres} image's entrypoint passes on to {@code initdb}. */
    public static final String INITDB_ARGS_ENV = "POSTGRES_INITDB_ARGS";

    /** The schema-owning DDL role (mirrors the two-role split between schema-owner and runtime user; the tests run DDL as this role). */
    public static final String DDL_USER = "ditto_ddl";
    public static final String DDL_PASSWORD = "ddl_secret";
    public static final String DATABASE = "ditto";

    private final String imageName;
    private final DockerImageName image;
    @Nullable private String initdbArgs;

    @Nullable
    private PostgreSQLContainer<?> container;

    /**
     * Creates a resource for the configured image ({@link #configuredImage()}: the {@value #IMAGE_PROPERTY} system
     * property, else {@link #DEFAULT_IMAGE}).
     */
    public PostgresDbResource() {
        this(configuredImage());
    }

    /**
     * Creates a resource for an explicit image tag, regardless of {@value #IMAGE_PROPERTY}.
     *
     * @param dockerImage the PostgreSQL image, e.g. {@code postgres:16}.
     */
    public PostgresDbResource(final String dockerImage) {
        this.imageName = dockerImage;
        this.image = compatibleImageName(dockerImage);
        this.initdbArgs = configuredInitdbArgs().orElse(null);
    }

    /**
     * @return the image chosen by the {@value #IMAGE_PROPERTY} system property, or {@link #DEFAULT_IMAGE} when it is
     * unset or blank.
     */
    public static String configuredImage() {
        final String configured = System.getProperty(IMAGE_PROPERTY);
        return configured == null || configured.isBlank() ? DEFAULT_IMAGE : configured.trim();
    }

    /**
     * @return {@link #configuredImage()} as an image name that Testcontainers' {@code PostgreSQLContainer} accepts, for
     * ITs that start their own container.
     */
    public static DockerImageName configuredImageName() {
        return compatibleImageName(configuredImage());
    }

    private static DockerImageName compatibleImageName(final String dockerImage) {
        // Declared compatible with "postgres" so that a mirrored image (another registry or repository) is accepted too.
        return DockerImageName.parse(dockerImage).asCompatibleSubstituteFor("postgres");
    }

    /**
     * @return the {@code initdb} arguments chosen by the {@value #INITDB_ARGS_PROPERTY} system property (trimmed), or
     * empty when it is unset or blank.
     */
    public static Optional<String> configuredInitdbArgs() {
        return normalizedInitdbArgs(System.getProperty(INITDB_ARGS_PROPERTY));
    }

    /**
     * A not yet started container of the configured image ({@link #configuredImageName()}) with the configured
     * {@code initdb} arguments ({@link #configuredInitdbArgs()}), for ITs that start their own container (custom
     * database, credentials or network); they add those settings and start it themselves.
     *
     * @return the container.
     */
    public static PostgreSQLContainer<?> newContainer() {
        return newContainer(configuredImageName(), configuredInitdbArgs().orElse(null));
    }

    private static PostgreSQLContainer<?> newContainer(final DockerImageName image, @Nullable final String initdbArgs) {
        final PostgreSQLContainer<?> container = new PostgreSQLContainer<>(image);
        if (initdbArgs != null) {
            container.withEnv(INITDB_ARGS_ENV, initdbArgs);
        }
        return container;
    }

    private static Optional<String> normalizedInitdbArgs(@Nullable final String initdbArgs) {
        return initdbArgs == null || initdbArgs.isBlank() ? Optional.empty() : Optional.of(initdbArgs.trim());
    }

    /**
     * @return the image this resource starts, e.g. {@code postgres:16}.
     */
    public String getImage() {
        return imageName;
    }

    /**
     * Sets this resource's {@code initdb} arguments ({@value #INITDB_ARGS_ENV}), regardless of
     * {@value #INITDB_ARGS_PROPERTY}; {@code null} or blank means none (the image's own cluster).
     *
     * @param initdbArgs e.g. {@code --encoding=UTF8 --locale=C}.
     * @return this resource.
     * @throws IllegalStateException if the container is already started.
     */
    public PostgresDbResource withInitdbArgs(@Nullable final String initdbArgs) {
        if (container != null) {
            throw new IllegalStateException("The initdb arguments of a started PostgresDbResource cannot change.");
        }
        this.initdbArgs = normalizedInitdbArgs(initdbArgs).orElse(null);
        return this;
    }

    /**
     * @return the {@code initdb} arguments this resource starts its cluster with, or empty for the image's own.
     */
    public Optional<String> getInitdbArgs() {
        return Optional.ofNullable(initdbArgs);
    }

    /**
     * Starts the container (idempotent — a no-op if already running). Throws if Docker is unreachable; ITs guard this
     * with {@code Assume.assumeNoException} so an unreachable daemon becomes a skip, not a failure.
     */
    public void start() {
        if (container == null) {
            container = newContainer(image, initdbArgs)
                    .withDatabaseName(DATABASE)
                    .withUsername(DDL_USER)
                    .withPassword(DDL_PASSWORD);
            container.start();
        }
    }

    /**
     * Stops and removes the container (idempotent).
     */
    public void stop() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }

    @Override
    protected void before() {
        start();
    }

    @Override
    protected void after() {
        stop();
    }

    private PostgreSQLContainer<?> requireRunning() {
        if (container == null) {
            throw new IllegalStateException("PostgresDbResource container is not started.");
        }
        return container;
    }

    /**
     * @return the host the mapped Postgres port is reachable on.
     */
    public String getHost() {
        return requireRunning().getHost();
    }

    /**
     * @return the host port mapped to the container's Postgres port.
     */
    public int getPort() {
        return requireRunning().getFirstMappedPort();
    }

    /**
     * @return the database name.
     */
    public String getDatabaseName() {
        return DATABASE;
    }

    /**
     * @return a fresh r2dbc {@link ConnectionFactory} authenticated as the DDL (schema-owning) role.
     */
    public ConnectionFactory newConnectionFactory() {
        return newConnectionFactory(DDL_USER, DDL_PASSWORD);
    }

    /**
     * @param user the login role.
     * @param password the login password.
     * @return a fresh r2dbc {@link ConnectionFactory} for the given credentials against this container.
     */
    public ConnectionFactory newConnectionFactory(final String user, final String password) {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, getHost())
                .option(ConnectionFactoryOptions.PORT, getPort())
                .option(ConnectionFactoryOptions.DATABASE, DATABASE)
                .option(ConnectionFactoryOptions.USER, user)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());
    }

    /**
     * @return the r2dbc URL for this container as the DDL role.
     */
    public String getR2dbcUrl() {
        return "r2dbc:postgresql://" + DDL_USER + ":" + DDL_PASSWORD + "@" + getHost() + ":" + getPort()
                + "/" + DATABASE;
    }

}
