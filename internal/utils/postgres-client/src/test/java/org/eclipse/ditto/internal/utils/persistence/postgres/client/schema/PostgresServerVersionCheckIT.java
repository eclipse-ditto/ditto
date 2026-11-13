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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.DockerImageName;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The server-version check of the schema bootstrap against a real PostgreSQL (Testcontainers), on the image chosen by
 * {@value PostgresDbResource#IMAGE_PROPERTY} (default {@value PostgresDbResource#DEFAULT_IMAGE}).
 * <p>
 * On PostgreSQL 16 or newer the bootstrap logs no server-version WARN. On an older image (e.g.
 * {@code -Dditto.test.postgres.image=postgres:15}) it logs exactly the WARN of {@link PostgresServerVersion} and still
 * completes. The test also checks that the server that runs has the major version the configured image's tag names.
 * </p>
 * <p>
 * Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 * </p>
 */
public final class PostgresServerVersionCheckIT {

    private static final String COMPONENT = "server-version-it";
    private static final String TABLE = "server_version_items";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final PostgresSchemaDescriptor DESCRIPTOR = new PostgresSchemaDescriptor() {
        @Override
        public String component() {
            return COMPONENT;
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public int minCompatibleVersion() {
            return 1;
        }

        @Override
        public List<String> ddlStatements() {
            return List.of("CREATE TABLE IF NOT EXISTS " + TABLE + " (id TEXT COLLATE \"C\" PRIMARY KEY)");
        }

        @Override
        public List<TableContract> tableContracts() {
            return List.of(new TableContract(TABLE, "PRIMARY KEY (id)", Map.of("id", "text"), Set.of("id")));
        }
    };

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    private final ListAppender<ILoggingEvent> managerLog = new ListAppender<>();

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresServerVersionCheckIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Before
    public void captureLog() {
        managerLog.start();
        ((Logger) LoggerFactory.getLogger(PostgresSchemaManager.class)).addAppender(managerLog);
    }

    @After
    public void releaseLog() {
        ((Logger) LoggerFactory.getLogger(PostgresSchemaManager.class)).detachAppender(managerLog);
        managerLog.stop();
    }

    @Test
    public void theConfiguredImageIsTheServerThatRuns() {
        assertThat(POSTGRES.getImage()).isEqualTo(PostgresDbResource.configuredImage());
        final OptionalInt imageMajor = majorOfTag(POSTGRES.getImage());
        Assume.assumeTrue("image tag of " + POSTGRES.getImage() + " names no major version", imageMajor.isPresent());

        assertThat(serverVersionNum() / 10000)
                .as("major version of the server started from %s", POSTGRES.getImage())
                .isEqualTo(imageMajor.getAsInt());
    }

    @Test
    public void bootstrapWarnsOnlyBelowSixteenAndNeverRefuses() {
        final int serverVersionNum = serverVersionNum();

        PostgresSchemaManager.of(connectionFactory, DESCRIPTOR, TIMEOUT).bootstrap();

        final List<String> versionWarnings = managerLog.list.stream()
                .filter(event -> event.getLevel().equals(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("PostgreSQL server version"))
                .toList();
        if (serverVersionNum >= PostgresServerVersion.MINIMUM_TESTED_VERSION_NUM) {
            assertThat(versionWarnings).as("server_version_num %s", serverVersionNum).isEmpty();
        } else {
            assertThat(versionWarnings).containsExactly(
                    PostgresServerVersion.warning(COMPONENT, serverVersionNum).orElseThrow());
        }
        assertThat(scalar("SELECT version FROM schema_version WHERE component = '" + COMPONENT + "'"))
                .as("the bootstrap completed and wrote its schema_version row")
                .isEqualTo("1");
    }

    private static OptionalInt majorOfTag(final String image) {
        final String tag = DockerImageName.parse(image).getVersionPart();
        int end = 0;
        while (end < tag.length() && Character.isDigit(tag.charAt(end))) {
            end++;
        }
        return end == 0 ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(tag.substring(0, end)));
    }

    private static int serverVersionNum() {
        return Integer.parseInt(scalar(PostgresServerVersion.SHOW_SERVER_VERSION_NUM));
    }

    private static String scalar(final String sql) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block(TIMEOUT);
    }

}
