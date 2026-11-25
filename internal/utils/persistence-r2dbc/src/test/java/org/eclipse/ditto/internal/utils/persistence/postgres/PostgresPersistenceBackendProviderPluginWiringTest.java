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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSchemaBootstrapper;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Pins that {@link PostgresPersistenceBackendProvider#verifyPluginWiring(String)} runs
 * {@link PostgresPluginWiringCheck} against the actor system's effective config and the provider's own auto-start
 * plugin IDs, and that a failed boot step stops the service the way a failed schema bootstrap does: a things service on
 * the shipped {@code ditto-postgres-persistence.conf} passes, logs the verified plugin IDs and starts no shutdown; one
 * whose journal block is overridden with the MongoDB class throws, logs the mismatch and starts the coordinated
 * shutdown with {@code BootstrapFailed}, unless {@code ditto.postgresql.bootstrap.exit-on-failure} is off; a second
 * configuration error does not mask the first; and an invalid {@code ditto.postgresql} block stops the service at the
 * schema bootstrap too.
 * <p>
 * The actor system is created on the shipped profile with both Pekko auto-start lists set to {@code []}, so no plugin
 * is instantiated and no database is needed; the provider's {@code extension-config} is read from that resolved config,
 * as the extension loader does at runtime. Pekko's {@code exit-jvm} is off in this test configuration, which every
 * stopping case asserts before provoking the shutdown.
 */
public final class PostgresPersistenceBackendProviderPluginWiringTest {

    private static final String MONGO_JOURNAL_CLASS = "pekko.contrib.persistence.mongodb.MongoJournal";
    private static final String MIS_WIRED_THINGS_JOURNAL =
            "ditto-postgres-things-journal.class = \"" + MONGO_JOURNAL_CLASS + "\"\n";
    private static final String INVALID_POSTGRES_BLOCK = "ditto.postgresql.pooler-mode = \"bogus\"\n";

    @Test
    public void shippedProfilePluginClassesPassWithoutAShutdown() {
        withProvider("", fixture -> {
            assertThatCode(() -> fixture.provider().verifyPluginWiring("things")).doesNotThrowAnyException();

            assertThat(CoordinatedShutdown.get(fixture.system()).getShutdownReason()).isEmpty();
            assertThat(fixture.messagesAt(Level.INFO))
                    .anySatisfy(message -> assertThat(message)
                            .contains("plugin wiring verified for service <things>")
                            .contains("ditto-postgres-things-journal"));
        });
    }

    @Test
    public void thingsJournalWiredToMongoClassThrowsAndStopsTheService() throws Exception {
        withProvider(MIS_WIRED_THINGS_JOURNAL, fixture -> {
            assertExitJvmIsOff(fixture.system());

            assertThatThrownBy(() -> fixture.provider().verifyPluginWiring("things"))
                    .isInstanceOf(DittoConfigError.class)
                    .hasMessageContaining("ditto-postgres-things-journal")
                    .hasMessageContaining(MONGO_JOURNAL_CLASS);

            assertThat(fixture.messagesAt(Level.ERROR))
                    .anySatisfy(message -> assertThat(message)
                            .contains("plugin wiring check for service <things> failed")
                            .contains(MONGO_JOURNAL_CLASS));
            assertStoppedWithBootstrapFailed(fixture.system());
        });
    }

    @Test
    public void exitOnFailureOffThrowsButKeepsTheServiceRunning() {
        withProvider(MIS_WIRED_THINGS_JOURNAL + "ditto.postgresql.bootstrap.exit-on-failure = false\n", fixture -> {
            assertExitJvmIsOff(fixture.system());

            assertThatThrownBy(() -> fixture.provider().verifyPluginWiring("things"))
                    .isInstanceOf(DittoConfigError.class);

            assertThat(CoordinatedShutdown.get(fixture.system()).getShutdownReason())
                    .as("exit-on-failure = false keeps the JVM running without its root actor")
                    .isEmpty();
            assertThat(fixture.system().getWhenTerminated().toCompletableFuture().isDone())
                    .as("the actor system keeps running")
                    .isFalse();
        });
    }

    @Test
    public void invalidPostgresBlockDoesNotMaskTheWiringErrorAndStillStopsTheService() {
        withProvider(MIS_WIRED_THINGS_JOURNAL + INVALID_POSTGRES_BLOCK, fixture -> {
            assertExitJvmIsOff(fixture.system());

            assertThatThrownBy(() -> fixture.provider().verifyPluginWiring("things"))
                    .as("the wiring error stays the primary failure")
                    .isInstanceOf(DittoConfigError.class)
                    .hasMessageContaining("ditto-postgres-things-journal")
                    .satisfies(error -> assertThat(error.getSuppressed())
                            .as("the configuration error travels as suppressed")
                            .hasSize(1));

            assertStoppedWithBootstrapFailed(fixture.system());
        });
    }

    @Test
    public void invalidPostgresBlockStopsTheServiceAtSchemaBootstrap() {
        withProvider(INVALID_POSTGRES_BLOCK, fixture -> {
            assertExitJvmIsOff(fixture.system());

            assertThatThrownBy(() -> fixture.provider().bootstrapSchema())
                    .isInstanceOf(DittoConfigError.class)
                    .hasMessageContaining("pooler-mode");

            assertStoppedWithBootstrapFailed(fixture.system());
        });
    }

    private static void assertExitJvmIsOff(final ActorSystem system) {
        assertThat(system.settings().config().getBoolean("pekko.coordinated-shutdown.exit-jvm"))
                .as("the test must never exit the JVM")
                .isFalse();
    }

    private static void assertStoppedWithBootstrapFailed(final ActorSystem system) {
        assertThat(CoordinatedShutdown.get(system).getShutdownReason())
                .as("a failed boot step stops the service with the schema-bootstrap reason")
                .get()
                .isInstanceOf(PostgresSchemaBootstrapper.BootstrapFailed.class);
        try {
            system.getWhenTerminated().toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("the coordinated shutdown should terminate the actor system", e);
        }
    }

    private record Fixture(PostgresPersistenceBackendProvider provider, ActorSystem system,
            ListAppender<ILoggingEvent> log) {

        private java.util.List<String> messagesAt(final Level level) {
            return log.list.stream()
                    .filter(event -> event.getLevel() == level)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
        }
    }

    private static void withProvider(final String overrides, final Consumer<Fixture> assertion) {
        final Config config = ConfigFactory.parseString(overrides
                        + "pekko.persistence.journal.auto-start-journals = []\n"
                        + "pekko.persistence.snapshot-store.auto-start-snapshot-stores = []")
                .withFallback(ConfigFactory.parseResources("ditto-postgres-persistence.conf"))
                .withFallback(ConfigFactory.load())
                .resolve();
        final ActorSystem system = ActorSystem.create("PostgresPersistenceBackendProviderPluginWiringTest", config);
        final Logger providerLogger = (Logger) LoggerFactory.getLogger(PostgresPersistenceBackendProvider.class);
        final Level previousLevel = providerLogger.getLevel();
        final ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        // The test logging config caps this logger above INFO; the fixture lowers it so the INFO line is observable.
        providerLogger.setLevel(Level.INFO);
        providerLogger.addAppender(log);
        try {
            final Config extensionConfig = ScopedConfig.dittoExtension(system.settings().config())
                    .getConfig("persistence-backend-provider.extension-config");
            assertion.accept(new Fixture(new PostgresPersistenceBackendProvider(system, extensionConfig), system, log));
        } finally {
            providerLogger.detachAppender(log);
            providerLogger.setLevel(previousLevel);
            TestKit.shutdownActorSystem(system);
        }
    }
}
