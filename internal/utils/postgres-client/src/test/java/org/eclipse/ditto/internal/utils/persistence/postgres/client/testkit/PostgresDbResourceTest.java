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

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.utility.DockerImageName;

/**
 * Unit tests for the image and initdb-argument selection of {@link PostgresDbResource} (no container is started): the
 * {@value PostgresDbResource#IMAGE_PROPERTY} system property chooses the image of every resource created without an
 * explicit one, and {@value PostgresDbResource#INITDB_ARGS_PROPERTY} the {@code initdb} arguments.
 */
public final class PostgresDbResourceTest {

    @Nullable private String originalImageProperty;
    @Nullable private String originalInitdbArgsProperty;

    @Before
    public void rememberProperties() {
        originalImageProperty = System.getProperty(PostgresDbResource.IMAGE_PROPERTY);
        originalInitdbArgsProperty = System.getProperty(PostgresDbResource.INITDB_ARGS_PROPERTY);
        System.clearProperty(PostgresDbResource.INITDB_ARGS_PROPERTY);
    }

    @After
    public void restoreProperties() {
        restore(PostgresDbResource.IMAGE_PROPERTY, originalImageProperty);
        restore(PostgresDbResource.INITDB_ARGS_PROPERTY, originalInitdbArgsProperty);
    }

    private static void restore(final String property, @Nullable final String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }

    @Test
    public void defaultsToPostgresSixteen() {
        System.clearProperty(PostgresDbResource.IMAGE_PROPERTY);

        assertThat(PostgresDbResource.IMAGE_PROPERTY).isEqualTo("ditto.test.postgres.image");
        assertThat(PostgresDbResource.DEFAULT_IMAGE).isEqualTo("postgres:16");
        assertThat(PostgresDbResource.configuredImage()).isEqualTo("postgres:16");
        assertThat(new PostgresDbResource().getImage()).isEqualTo("postgres:16");
    }

    @Test
    public void systemPropertyChoosesTheImage() {
        System.setProperty(PostgresDbResource.IMAGE_PROPERTY, "postgres:18");

        assertThat(PostgresDbResource.configuredImage()).isEqualTo("postgres:18");
        assertThat(new PostgresDbResource().getImage()).isEqualTo("postgres:18");
        assertThat(PostgresDbResource.configuredImageName().asCanonicalNameString())
                .isEqualTo("postgres:18");
    }

    @Test
    public void blankPropertyFallsBackToTheDefault() {
        System.setProperty(PostgresDbResource.IMAGE_PROPERTY, "  ");

        assertThat(PostgresDbResource.configuredImage()).isEqualTo("postgres:16");
    }

    @Test
    public void explicitImageWinsOverTheProperty() {
        System.setProperty(PostgresDbResource.IMAGE_PROPERTY, "postgres:18");

        assertThat(new PostgresDbResource("postgres:17").getImage()).isEqualTo("postgres:17");
    }

    @Test
    public void acceptsAMirroredPostgresImage() {
        System.setProperty(PostgresDbResource.IMAGE_PROPERTY, "registry.example.com/mirror/postgres:17");

        // Testcontainers' PostgreSQLContainer only accepts images declared compatible with "postgres".
        assertThat(PostgresDbResource.configuredImageName().isCompatibleWith(DockerImageName.parse("postgres")))
                .isTrue();
    }

    @Test
    public void noInitdbArgumentsByDefault() {
        assertThat(PostgresDbResource.INITDB_ARGS_PROPERTY).isEqualTo("ditto.test.postgres.initdb-args");
        assertThat(PostgresDbResource.INITDB_ARGS_ENV).isEqualTo("POSTGRES_INITDB_ARGS");
        assertThat(PostgresDbResource.configuredInitdbArgs()).isEmpty();
        assertThat(new PostgresDbResource().getInitdbArgs()).isEmpty();
        // Containers are only configured here, never started; getDockerImageName() would resolve (pull) the image.
        assertThat(PostgresDbResource.newContainer().getEnvMap()).doesNotContainKey("POSTGRES_INITDB_ARGS");
    }

    @Test
    public void initdbArgumentsPropertyReachesResourcesAndOwnContainers() {
        System.setProperty(PostgresDbResource.INITDB_ARGS_PROPERTY, " --encoding=UTF8 --locale=C.UTF-8 ");

        assertThat(PostgresDbResource.configuredInitdbArgs()).contains("--encoding=UTF8 --locale=C.UTF-8");
        assertThat(new PostgresDbResource().getInitdbArgs()).contains("--encoding=UTF8 --locale=C.UTF-8");
        assertThat(PostgresDbResource.newContainer().getEnvMap())
                .containsEntry("POSTGRES_INITDB_ARGS", "--encoding=UTF8 --locale=C.UTF-8");
    }

    @Test
    public void blankInitdbArgumentsPropertyMeansNone() {
        System.setProperty(PostgresDbResource.INITDB_ARGS_PROPERTY, "  ");

        assertThat(PostgresDbResource.configuredInitdbArgs()).isEmpty();
        assertThat(PostgresDbResource.newContainer().getEnvMap()).doesNotContainKey("POSTGRES_INITDB_ARGS");
    }

    @Test
    public void explicitInitdbArgumentsWinOverTheProperty() {
        System.setProperty(PostgresDbResource.INITDB_ARGS_PROPERTY, "--locale=C");

        assertThat(new PostgresDbResource().withInitdbArgs("--locale=C.UTF-8").getInitdbArgs())
                .contains("--locale=C.UTF-8");
        assertThat(new PostgresDbResource().withInitdbArgs(null).getInitdbArgs()).isEmpty();
        assertThat(new PostgresDbResource().withInitdbArgs(" ").getInitdbArgs()).isEmpty();
    }

    @Test
    public void initdbArgumentsAreOrthogonalToTheImage() {
        System.setProperty(PostgresDbResource.IMAGE_PROPERTY, "postgres:18");
        System.setProperty(PostgresDbResource.INITDB_ARGS_PROPERTY, "--locale=C.UTF-8");

        assertThat(PostgresDbResource.configuredImageName().asCanonicalNameString()).isEqualTo("postgres:18");
        assertThat(PostgresDbResource.newContainer().getEnvMap())
                .containsEntry("POSTGRES_INITDB_ARGS", "--locale=C.UTF-8");
        assertThat(new PostgresDbResource("postgres:17").getInitdbArgs()).contains("--locale=C.UTF-8");
        assertThat(new PostgresDbResource("postgres:17").getImage()).isEqualTo("postgres:17");
    }

}
