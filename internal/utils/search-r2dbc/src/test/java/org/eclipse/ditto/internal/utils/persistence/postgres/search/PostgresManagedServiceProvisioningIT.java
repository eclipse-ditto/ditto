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
package org.eclipse.ditto.internal.utils.persistence.postgres.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runs the shipped provisioning script {@code deployment/postgres/provisioning/01-roles-and-grants.sql} as the master
 * user of a managed PostgreSQL service (Amazon RDS, Azure Database for PostgreSQL, Cloud SQL): a role with
 * {@code CREATEROLE} and {@code CREATEDB} that owns the database and is <em>not</em> a superuser. CI and the other ITs
 * run the script as a superuser, which hides two things: since PostgreSQL 16 a {@code CREATEROLE} user gets only
 * {@code ADMIN OPTION} on the roles it creates and can neither {@code SET ROLE} to them nor hand them ownership, and a
 * trusted extension such as {@code pg_trgm} needs {@code CREATE} on the database, which schema ownership does not give.
 * <p>
 * The test then boots the search schema through the provider with the two-role wiring a deployment uses (runtime role
 * plus {@code ddl-credentials}), checks what each role can and cannot do, and pins the two actionable boot errors for a
 * misprovisioned database: the DDL role without {@code CREATE} on the database, and an extension a superuser installed
 * into {@code public}, off the roles' {@code search_path}.
 * </p>
 * <p>
 * Only a missing Docker environment skips the class.
 * </p>
 */
public final class PostgresManagedServiceProvisioningIT {

    private static final String SUPERUSER = "postgres";
    private static final String SUPERUSER_PASSWORD = "superuser_secret";
    /** The managed service's master user: CREATEROLE + CREATEDB, owner of the Ditto database, no superuser. */
    private static final String MASTER = "master";
    private static final String MASTER_PASSWORD = "master_secret";
    private static final String DATABASE = "ditto";
    private static final String DDL_ROLE = "ditto_ddl";
    private static final String DDL_PASSWORD = "ddl_secret";
    private static final String RUNTIME_ROLE = "ditto_app";
    private static final String RUNTIME_PASSWORD = "app_secret";
    private static final String NO_CREATE_ON_DATABASE = "no_create_on_database";
    private static final String EXTENSION_IN_PUBLIC = "extension_in_public";

    private static final Path PROVISIONING_SCRIPT =
            Path.of("deployment", "postgres", "provisioning", "01-roles-and-grants.sql");

    private static PostgreSQLContainer<?> postgres;

    @BeforeClass
    public static void provisionAsTheMasterUser() throws Exception {
        try {
            postgres = PostgresDbResource.newContainer()
                    .withDatabaseName(SUPERUSER)
                    .withUsername(SUPERUSER)
                    .withPassword(SUPERUSER_PASSWORD);
            postgres.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping "
                    + PostgresManagedServiceProvisioningIT.class.getSimpleName(), t);
        }
        // What RDS, Azure and Cloud SQL hand out: a login role with CREATEROLE and CREATEDB that owns the database.
        psql(SUPERUSER, SUPERUSER, "CREATE ROLE " + MASTER + " LOGIN CREATEROLE CREATEDB NOSUPERUSER PASSWORD '"
                + MASTER_PASSWORD + "'");
        psql(SUPERUSER, SUPERUSER, "CREATE DATABASE " + DATABASE + " OWNER " + MASTER);

        postgres.copyFileToContainer(MountableFile.forHostPath(locateProvisioningScript()),
                "/tmp/01-roles-and-grants.sql");
        final Container.ExecResult result = postgres.execInContainer("psql", "-X", "-v", "ON_ERROR_STOP=1",
                "-U", MASTER, "-d", DATABASE, "-v", "ddl_pw=" + DDL_PASSWORD, "-v", "app_pw=" + RUNTIME_PASSWORD,
                "-f", "/tmp/01-roles-and-grants.sql");
        assertThat(result.getExitCode())
                .as("the provisioning script run as the master user failed: %s", result.getStderr())
                .isZero();
    }

    @AfterClass
    public static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    public void theDdlRoleBootsTheSearchSchemaAndTheRuntimeRoleGetsDmlOnly() {
        bootstrapThroughTheProvider(DATABASE);

        final ConnectionFactory asMaster = connectionFactory(DATABASE, MASTER, MASTER_PASSWORD);
        assertThat(single(asMaster, "SELECT n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace"
                + " WHERE e.extname = 'pg_trgm'"))
                .as("the DDL role created pg_trgm itself, in the schema on the roles' search_path")
                .isEqualTo("ditto");
        assertThat(single(asMaster, "SELECT component FROM ditto.schema_version WHERE component = '"
                + PostgresSearchSchema.COMPONENT + "'")).isEqualTo(PostgresSearchSchema.COMPONENT);
        assertThat(rows(asMaster, "SELECT tablename || ' ' || has_table_privilege('" + RUNTIME_ROLE
                + "', 'ditto.' || tablename, 'SELECT,INSERT,UPDATE,DELETE') || ' ' || has_table_privilege('"
                + RUNTIME_ROLE + "', 'ditto.' || tablename, 'TRUNCATE') FROM pg_tables WHERE schemaname = 'ditto'"
                + " ORDER BY tablename"))
                .as("DML yes, TRUNCATE no, on every table the DDL role created after provisioning")
                .allMatch(row -> row.endsWith(" true false"))
                .anyMatch(row -> row.startsWith("search_things "));
        assertThat(single(asMaster, "SELECT has_schema_privilege('" + RUNTIME_ROLE + "', 'ditto', 'CREATE')::text"))
                .isEqualTo("false");
        assertThat(single(asMaster, "SELECT has_database_privilege('" + DDL_ROLE + "', '" + DATABASE
                + "', 'CREATE')::text")).isEqualTo("true");

        final ConnectionFactory asRuntime = connectionFactory(DATABASE, RUNTIME_ROLE, RUNTIME_PASSWORD);
        execute(asRuntime, "INSERT INTO search_things (thing_id, namespace, revision, thing)"
                + " VALUES ('org.eclipse.ditto:provisioned', 'org.eclipse.ditto', 1, '{}'::jsonb)");
        assertThat(single(asRuntime, "SELECT count(*)::text FROM search_things")).isEqualTo("1");
        assertThatThrownBy(() -> execute(asRuntime, "CREATE TABLE not_allowed (i int)"))
                .as("the runtime role has no CREATE on the schema")
                .isInstanceOf(R2dbcException.class)
                .extracting(t -> ((R2dbcException) t).getSqlState())
                .isEqualTo("42501");
    }

    @Test
    public void withoutCreateOnTheDatabaseTheBootNamesTheGrant() throws Exception {
        // the schema handover works (master is a member of ditto_ddl), only the database privilege is missing
        psql(MASTER, SUPERUSER, "CREATE DATABASE " + NO_CREATE_ON_DATABASE + " OWNER " + MASTER);
        psql(MASTER, NO_CREATE_ON_DATABASE, "CREATE SCHEMA ditto AUTHORIZATION " + DDL_ROLE);
        psql(MASTER, NO_CREATE_ON_DATABASE, "GRANT USAGE ON SCHEMA ditto TO " + RUNTIME_ROLE);

        assertThatThrownBy(() -> bootstrapThroughTheProvider(NO_CREATE_ON_DATABASE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GRANT CREATE ON DATABASE")
                .hasMessageContaining("CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA ditto;")
                .satisfies(e -> assertThat(sqlStates(e)).contains("42501"));
        assertNothingWasCreated(NO_CREATE_ON_DATABASE);
    }

    @Test
    public void anExtensionASuperuserInstalledIntoPublicIsNamedWithItsRemedy() throws Exception {
        psql(MASTER, SUPERUSER, "CREATE DATABASE " + EXTENSION_IN_PUBLIC + " OWNER " + MASTER);
        // a plain CREATE EXTENSION as a superuser lands in public, off the roles' search_path = ditto
        psql(SUPERUSER, EXTENSION_IN_PUBLIC, "CREATE EXTENSION pg_trgm");
        psql(MASTER, EXTENSION_IN_PUBLIC, "CREATE SCHEMA ditto AUTHORIZATION " + DDL_ROLE);
        psql(MASTER, EXTENSION_IN_PUBLIC, "GRANT USAGE ON SCHEMA ditto TO " + RUNTIME_ROLE);
        psql(MASTER, EXTENSION_IN_PUBLIC, "GRANT CREATE ON DATABASE " + EXTENSION_IN_PUBLIC + " TO " + DDL_ROLE);

        assertThatThrownBy(() -> bootstrapThroughTheProvider(EXTENSION_IN_PUBLIC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALTER EXTENSION pg_trgm SET SCHEMA ditto;")
                .satisfies(e -> assertThat(sqlStates(e)).contains("42704"));
        assertNothingWasCreated(EXTENSION_IN_PUBLIC);

        // the remedy the message names
        psql(SUPERUSER, EXTENSION_IN_PUBLIC, "ALTER EXTENSION pg_trgm SET SCHEMA ditto");
        bootstrapThroughTheProvider(EXTENSION_IN_PUBLIC);
        assertThat(single(connectionFactory(EXTENSION_IN_PUBLIC, MASTER, MASTER_PASSWORD),
                "SELECT count(*)::text FROM pg_tables WHERE schemaname = 'ditto' AND tablename = 'search_flat'"))
                .isEqualTo("1");
    }

    private static void assertNothingWasCreated(final String database) {
        assertThat(single(connectionFactory(database, MASTER, MASTER_PASSWORD),
                "SELECT count(*)::text FROM pg_tables WHERE schemaname = 'ditto'"))
                .as("the failed bootstrap transaction left no table behind")
                .isEqualTo("0");
    }

    /** Boots the search schema as a deployment does: runtime role on the pool, the DDL role for the schema. */
    private static void bootstrapThroughTheProvider(final String database) {
        final Config config = ConfigFactory.parseString(
                        "ditto.postgresql {\n"
                                + "  uri = \"r2dbc:postgresql://" + postgres.getHost() + ":"
                                + postgres.getFirstMappedPort() + "/" + database + "\"\n"
                                + "  username = \"" + RUNTIME_ROLE + "\"\n"
                                + "  password = \"" + RUNTIME_PASSWORD + "\"\n"
                                + "  pool.ddl-credentials {\n"
                                + "    username = \"" + DDL_ROLE + "\"\n"
                                + "    password = \"" + DDL_PASSWORD + "\"\n"
                                + "  }\n"
                                + "  ssl.mode = disable\n"
                                + "}\n")
                .withFallback(ConfigFactory.load("postgres-search-test.conf"));
        final ActorSystem system = ActorSystem.create("PostgresManagedServiceProvisioningIT", config);
        try {
            new PostgresSearchPersistenceProvider(system, ConfigFactory.empty()).bootstrapSchema();
        } finally {
            TestKit.shutdownActorSystem(system);
        }
    }

    /** Runs one statement over the container's unix socket, which the official image trusts for local logins. */
    private static void psql(final String user, final String database, final String sql) throws Exception {
        final Container.ExecResult result = postgres.execInContainer("psql", "-X", "-v", "ON_ERROR_STOP=1",
                "-U", user, "-d", database, "-c", sql);
        assertThat(result.getExitCode()).as("psql -U %s -d %s -c \"%s\": %s", user, database, sql,
                result.getStderr()).isZero();
    }

    private static List<String> sqlStates(final Throwable throwable) {
        final List<String> states = new ArrayList<>();
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof R2dbcException r2dbc && r2dbc.getSqlState() != null) {
                states.add(r2dbc.getSqlState());
            }
        }
        return states;
    }

    private static ConnectionFactory connectionFactory(final String database, final String user,
            final String password) {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, postgres.getHost())
                .option(ConnectionFactoryOptions.PORT, postgres.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, database)
                .option(ConnectionFactoryOptions.USER, user)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());
    }

    private static void execute(final ConnectionFactory connectionFactory, final String sql) {
        Flux.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Flux.from(connection.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated),
                        Connection::close)
                .blockLast();
    }

    private static List<String> rows(final ConnectionFactory connectionFactory, final String sql) {
        return Flux.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Flux.from(connection.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> row.get(0, String.class))),
                        Connection::close)
                .collectList()
                .block();
    }

    private static String single(final ConnectionFactory connectionFactory, final String sql) {
        final List<String> result = rows(connectionFactory, sql);
        assertThat(result).as("one row for: %s", sql).hasSize(1);
        return result.get(0);
    }

    /** Finds the provisioning script from the module directory (or any directory below the repository root). */
    private static Path locateProvisioningScript() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            final Path candidate = dir.resolve(PROVISIONING_SCRIPT);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Provisioning script " + PROVISIONING_SCRIPT + " not found above "
                + Path.of("").toAbsolutePath());
    }

}
