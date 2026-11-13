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

import java.time.Duration;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Integration test for {@link PostgresSearchPersistenceProvider#healthCheckProps()} — the drop-in
 * {@code PostgresHealthChecker} (postgres-client) reuse — against a real PostgreSQL (PG 16) via Testcontainers.
 * <p>
 * A single scenario walks the full lifecycle the Phase F brief asks for (requirement 1): a live, reachable container
 * reports {@link StatusInfo.Status#UP}; the SAME container is then stopped out from under the SAME running actor/pool
 * (no new {@code ActorSystem}, no new provider) and subsequent probes report {@link StatusInfo.Status#DOWN} — proving
 * the transition without the actor crashing. Deliberately a SINGLE test method (rather than two independent ones):
 * killing the container is irreversible for the rest of the class's shared {@code ActorSystem}/pool, so no other test
 * method may run after it against the same fixture — a single method sidesteps any JUnit method-ordering hazard.
 * </p>
 * <p>
 * Skipped offline / when no Docker daemon is reachable (the {@link #startContainer() Assume} guard turns an
 * unreachable Docker into a skip, not a failure).
 * </p>
 */
public final class PostgresSearchHealthCheckIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();

    private static ActorSystem system;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSearchHealthCheckIT", t);
        }
        final Config config = ConfigFactory.parseString(
                        "ditto.postgresql.uri = \"" + POSTGRES.getR2dbcUrl() + "\"\n"
                                + "ditto.postgresql.ssl.mode = \"disable\"\n"
                                + "ditto.postgresql.connect-timeout = 3s\n"
                                + "ditto.postgresql.pool.initial-size = 0\n"
                                + "ditto.postgresql.pool.max-size = 2\n")
                .withFallback(ConfigFactory.load("postgres-search-test.conf"));
        system = ActorSystem.create("PostgresSearchHealthCheckIT", config);
    }

    @AfterClass
    public static void stopContainerAndSystem() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
        POSTGRES.stop();
    }

    @Test
    public void healthyDatabaseReportsUpThenGoingDownReportsDownWithoutCrashingTheActor() {
        final PostgresSearchPersistenceProvider provider =
                new PostgresSearchPersistenceProvider(system, ConfigFactory.empty());
        final ActorRef healthActor = system.actorOf(provider.healthCheckProps());
        final TestKit probe = new TestKit(system);

        // 1. Healthy, reachable container -> UP. Also proves the actor genuinely reached the DB (a non-vacuous
        // baseline for the DOWN transition below).
        healthActor.tell(RetrieveHealth.newInstance(), probe.getRef());
        final StatusInfo up = probe.expectMsgClass(Duration.ofSeconds(15L), StatusInfo.class);
        assertThat(up.getStatus())
                .as("a real SELECT 1 against a healthy, reachable PostgreSQL must report UP")
                .isEqualTo(StatusInfo.Status.UP);

        // 2. Kill the SAME container out from under the SAME live pool/actor.
        POSTGRES.stop();

        // Poll until the actor observes the outage (a stale pooled connection may serve one more probe before the pool
        // notices it is dead; a bounded retry keeps the assertion deterministic without a fixed sleep).
        StatusInfo down = null;
        final long deadline = System.nanoTime() + Duration.ofSeconds(30L).toNanos();
        while (down == null && System.nanoTime() < deadline) {
            healthActor.tell(RetrieveHealth.newInstance(), probe.getRef());
            final StatusInfo observed = probe.expectMsgClass(Duration.ofSeconds(15L), StatusInfo.class);
            if (observed.getStatus() == StatusInfo.Status.DOWN) {
                down = observed;
            }
        }
        assertThat(down)
                .as("the prober must report DOWN once the database is unreachable")
                .isNotNull();

        // 3. The actor is still alive and answering (proof it did not crash on the failed probe).
        healthActor.tell(RetrieveHealth.newInstance(), probe.getRef());
        final StatusInfo stillAnswering = probe.expectMsgClass(Duration.ofSeconds(15L), StatusInfo.class);
        assertThat(stillAnswering.getStatus())
                .as("the actor must keep answering RetrieveHealth after a failed probe, not crash")
                .isEqualTo(StatusInfo.Status.DOWN);
    }

}
