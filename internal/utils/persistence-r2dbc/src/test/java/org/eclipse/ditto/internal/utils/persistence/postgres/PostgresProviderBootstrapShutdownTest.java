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
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapAbortedException;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.TcpGate;
import org.junit.After;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * A coordinated shutdown (e.g. SIGTERM) during the persistence provider's wait for an unreachable PostgreSQL ends the
 * wait at once, through the wired boot seam {@link PersistenceBackendProvider#bootstrapSchema()}. The "database" is a
 * refused loopback port; no Docker needed.
 */
public final class PostgresProviderBootstrapShutdownTest {

    private static final String RETRY_LOGGER =
            "org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootstrapRetry";
    private static final Duration WAIT = Duration.ofSeconds(10);

    private enum TestReason implements CoordinatedShutdown.Reason { INSTANCE }

    @Nullable private ActorSystem system;
    @Nullable private ListAppender<ILoggingEvent> retryLog;

    @After
    public void stop() {
        if (retryLog != null) {
            ((Logger) LoggerFactory.getLogger(RETRY_LOGGER)).detachAppender(retryLog);
            retryLog.stop();
        }
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void coordinatedShutdownAbortsTheWaitForPostgresql() throws Exception {
        system = ActorSystem.create("PostgresProviderBootstrapShutdownTest", ConfigFactory.parseString(""
                        + "ditto.postgresql {\n"
                        + "  uri = \"r2dbc:postgresql://127.0.0.1:" + TcpGate.freePort() + "/ditto\"\n"
                        + "  username = \"ditto\"\n  password = \"ditto\"\n  ssl.mode = \"disable\"\n"
                        + "  bootstrap.retry { max-duration = 0s, min-backoff = 100ms, max-backoff = 200ms }\n"
                        + "}\n"
                        + "pekko.coordinated-shutdown.run-by-jvm-shutdown-hook = off\n")
                .withFallback(ConfigFactory.parseResources("ditto-postgres-persistence.conf"))
                .withFallback(ConfigFactory.load())
                .resolve());
        assertThat(system.settings().config().getBoolean("pekko.coordinated-shutdown.exit-jvm")).isFalse();
        final PersistenceBackendProvider provider = PersistenceBackendProvider.get(system,
                ScopedConfig.dittoExtension(system.settings().config()));
        final ListAppender<ILoggingEvent> log = captureRetryLog();
        final CompletableFuture<Throwable> outcome =
                CompletableFuture.supplyAsync(() -> catchThrowable(provider::bootstrapSchema));
        awaitTrue(() -> warnings(log) >= 2);

        CoordinatedShutdown.get(system).run(TestReason.INSTANCE);

        assertThat(outcome.get(5, TimeUnit.SECONDS)).isInstanceOf(SchemaBootstrapAbortedException.class);
        assertThat(CoordinatedShutdown.get(system).getShutdownReason()).contains(TestReason.INSTANCE);
        system.getWhenTerminated().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private ListAppender<ILoggingEvent> captureRetryLog() {
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(RETRY_LOGGER)).addAppender(appender);
        retryLog = appender;
        return appender;
    }

    private static long warnings(final ListAppender<ILoggingEvent> log) {
        return log.list.stream().filter(event -> event.getLevel() == Level.WARN).count();
    }

    private static void awaitTrue(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + WAIT);
            }
            Thread.sleep(10L);
        }
    }

}
