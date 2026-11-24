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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournalOps;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.eclipse.ditto.json.JsonFactory;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;

/**
 * Reads the events of one persistence ID written to the {@code wot} tables through the per-entity read journal of
 * {@link PostgresPersistenceBackendProvider}, and shows that the shared read journal, bound to {@code things}, does
 * not see them.
 */
public final class PostgresPerEntityReadJournalIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static final String PID = "wot-validation-config:ns:per-entity-read-journal";

    private static ActorSystem system;
    private static DittoPostgresClient writerClient;
    private static PostgresPersistenceBackendProvider provider;

    @BeforeClass
    public static void startContainer() throws Exception {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresPerEntityReadJournalIT", t);
        }
        final ConnectionFactory ddlFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();

        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(ddlFactory)
                .name("per-entity-writer-pool").maxSize(2).build());
        writerClient = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        final PostgresJournalOps wotJournal =
                PostgresJournalOps.of(PostgresPersistenceOperations.of(writerClient, "wot"));
        for (long sn = 1; sn <= 3; sn++) {
            final Iterable<java.util.Optional<Exception>> results =
                    await(wotJournal.writeMessages(List.of(write(PID, sn, "{\"sn\":" + sn + "}"))));
            results.forEach(result -> assertThat(result).isEmpty());
        }

        system = bootSystem();
        final Config extensionConfig = ConfigFactory.parseString(
                "plugin-ids {\n"
                        + "  thing.journal                  = \"ditto-postgres-things-journal\"\n"
                        + "  thing.snapshot                 = \"ditto-postgres-things-snapshots\"\n"
                        + "  wot-validation-config.journal  = \"ditto-postgres-wot-journal\"\n"
                        + "  wot-validation-config.snapshot = \"ditto-postgres-wot-snapshots\"\n"
                        + "}\n"
                        + "read-journal.entity = \"things\"\n");
        provider = new PostgresPersistenceBackendProvider(system, extensionConfig);
    }

    @AfterClass
    public static void stop() {
        if (writerClient != null) {
            writerClient.close();
        }
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
        POSTGRES.stop();
    }

    @Test
    public void perEntityReadJournalReadsTheWotJournal() throws Exception {
        final DittoReadJournal readJournal = provider.getReadJournal("wot-validation-config");

        final List<EventEnvelope> envelopes = await(readJournal.currentEventsByPersistenceId(PID, 1L, 3L)
                .runWith(Sink.seq(), system));

        assertThat(envelopes).extracting(EventEnvelope::sequenceNr).containsExactly(1L, 2L, 3L);
    }

    @Test
    public void sharedReadJournalDoesNotSeeTheWotJournal() throws Exception {
        final List<EventEnvelope> envelopes = await(provider.getReadJournal()
                .currentEventsByPersistenceId(PID, 1L, 3L)
                .runWith(Sink.seq(), system));

        assertThat(envelopes).isEmpty();
    }

    private static ActorSystem bootSystem() {
        // No shipped profile: the connection block plus the two journal plugins' entity keys the provider reads.
        final Config config = ConfigFactory.parseString(""
                        + "ditto.postgresql {\n"
                        + "  uri = \"r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getPort() + "/"
                        + POSTGRES.getDatabaseName() + "\"\n"
                        + "  username = \"" + PostgresDbResource.DDL_USER + "\"\n"
                        + "  password = \"" + PostgresDbResource.DDL_PASSWORD + "\"\n"
                        + "  ssl.mode = \"disable\"\n"
                        + "}\n"
                        + "ditto-postgres-things-journal { entity = \"things\" }\n"
                        + "ditto-postgres-wot-journal { entity = \"wot\" }\n"
                        + "ditto-postgres-things-snapshots { entity = \"things\" }\n"
                        + "ditto-postgres-wot-snapshots { entity = \"wot\" }\n"
                        + "pekko.persistence.journal.auto-start-journals = []\n"
                        + "pekko.persistence.snapshot-store.auto-start-snapshot-stores = []\n")
                .withFallback(ConfigFactory.load())
                .resolve();
        final ActorSystem actorSystem = ActorSystem.create("PostgresPerEntityReadJournalIT", config);
        assertThat(actorSystem.settings().config().getBoolean("pekko.coordinated-shutdown.exit-jvm"))
                .as("a test must never let coordinated shutdown exit the JVM").isFalse();
        return actorSystem;
    }

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private static AtomicWrite write(final String pid, final long sn, final String json) {
        return AtomicWrite.apply(PersistentRepr.apply(JsonFactory.newObject(json), sn, pid, "manifest", false,
                ActorRef.noSender(), "w"));
    }

}
