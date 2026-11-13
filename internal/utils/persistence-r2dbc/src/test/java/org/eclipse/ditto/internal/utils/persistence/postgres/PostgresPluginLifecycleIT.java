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

import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.ConnectionFactory;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.Props;
import org.apache.pekko.persistence.AbstractPersistentActor;
import org.apache.pekko.persistence.RecoveryCompleted;
import org.apache.pekko.persistence.SaveSnapshotSuccess;
import org.apache.pekko.persistence.SnapshotOffer;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Boots the PostgreSQL journal and snapshot-store plugins <strong>through the real Pekko persistence lifecycle</strong>
 * — i.e. a genuine {@link AbstractPersistentActor} whose recovery makes Pekko's plugin loader instantiate
 * {@code PostgresJournal} and {@code PostgresSnapshotStore} reflectively via their {@code (Config)} constructor.
 * <p>
 * This closes the gap the other suites leave open: every other test drives the {@code *Ops} classes directly and never
 * exercises plugin instantiation, so a missing Pekko-loadable constructor (which makes the first persistent-actor
 * recovery fail with {@code ActorInitializationException}) would go undetected. Here, actor creation alone fails the
 * test if the constructor is absent; the write → snapshot → stop → recover round-trip additionally proves the plugins
 * persist and recover correctly across an actor restart.
 * </p>
 * <p>
 * Runs under failsafe ({@code *IT}); skipped offline / when no Docker daemon is reachable. The plugins build their own
 * connection pools from {@code ditto.postgresql.*} (DDL role — it owns the bootstrapped tables, so it has full DML);
 * snapshot payloads round-trip as raw JSON text (the Ditto per-entity snapshot adapter is a service-layer concern and
 * is intentionally not exercised here).
 * </p>
 */
public final class PostgresPluginLifecycleIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ActorSystem system;

    @BeforeClass
    public static void startContainerAndBootSchema() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresPluginLifecycleIT", t);
        }
        final ConnectionFactory ddlFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();

        // Boot an ActorSystem on the Postgres persistence profile: the two `things` plugin blocks point at the Postgres
        // plugin classes and carry the `entity` prefix their (Config) constructors read; ditto.postgresql.* is the
        // backend each plugin builds its own pool from. ssl.mode=disable keeps the boot guard quiet against the
        // plaintext test container.
        final String hocon = ""
                + "ditto.postgresql {\n"
                + "  uri = \"r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getPort() + "/"
                + POSTGRES.getDatabaseName() + "\"\n"
                + "  username = \"" + PostgresDbResource.DDL_USER + "\"\n"
                + "  password = \"" + PostgresDbResource.DDL_PASSWORD + "\"\n"
                + "  ssl.mode = \"disable\"\n"
                + "}\n"
                + "ditto-postgres-things-journal {\n"
                + "  class = \"org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournal\"\n"
                + "  entity = \"things\"\n"
                + "}\n"
                + "ditto-postgres-things-snapshots {\n"
                + "  class = \"org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStore\"\n"
                + "  entity = \"things\"\n"
                + "}\n"
                // pool warm-up + connect can exceed the 3s TestKit default; give recovery room.
                + "pekko.test.single-expect-default = 25s\n";
        final Config config = ConfigFactory.parseString(hocon).withFallback(ConfigFactory.load());
        system = ActorSystem.create("PostgresPluginLifecycleIT", config);
    }

    @AfterClass
    public static void stop() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
        POSTGRES.stop();
    }

    @Test
    public void pluginsBootThroughPekkoAndRecoverAcrossRestart() {
        final String pid = "thing:ns:plugin-lifecycle";
        final TestKit recovery = new TestKit(system);
        final TestKit cmd = new TestKit(system);

        // Creating the actor triggers recovery, which makes Pekko instantiate BOTH plugins via their (Config) ctor.
        // A missing Pekko-loadable constructor fails here with ActorInitializationException.
        final ActorRef first = system.actorOf(Props.create(BootTestActor.class, pid, recovery.getRef()));
        final Recovered firstRecovery = recovery.expectMsgClass(Recovered.class);
        assertThat(firstRecovery.count()).isZero();
        assertThat(firstRecovery.snapshot()).isNull();

        // Persist three events and take a snapshot through the journal/snapshot plugins.
        for (int i = 0; i < 3; i++) {
            cmd.send(first, "persist");
            cmd.expectMsg("ack-persist");
        }
        cmd.send(first, "snapshot");
        cmd.expectMsg("ack-snap");

        // Stop the persistent actor (the plugin actors stay alive for the system) and recover a fresh instance.
        cmd.watch(first);
        first.tell(PoisonPill.getInstance(), ActorRef.noSender());
        cmd.expectTerminated(first);

        final ActorRef second = system.actorOf(Props.create(BootTestActor.class, pid, recovery.getRef()));
        final Recovered secondRecovery = recovery.expectMsgClass(Recovered.class);
        // Snapshot restored the count and there were no post-snapshot events to replay. The stored JSONB round-trips as
        // normalised JSON text, so assert on the parsed value rather than an exact string.
        assertThat(secondRecovery.count()).isEqualTo(3);
        assertThat(secondRecovery.snapshot()).isNotNull();
        assertThat(JsonFactory.newObject(secondRecovery.snapshot()).getValue("count").map(JsonValue::asInt))
                .contains(3);

        // Persist two MORE events AFTER the snapshot, then stop and recover once more. Recovery must now replay the
        // snapshot AND the two post-snapshot journal events — proving recovery = snapshot + post-snapshot replay, not
        // snapshot alone.
        for (int i = 0; i < 2; i++) {
            cmd.send(second, "persist");
            cmd.expectMsg("ack-persist");
        }
        cmd.watch(second);
        second.tell(PoisonPill.getInstance(), ActorRef.noSender());
        cmd.expectTerminated(second);

        final ActorRef third = system.actorOf(Props.create(BootTestActor.class, pid, recovery.getRef()));
        final Recovered thirdRecovery = recovery.expectMsgClass(Recovered.class);
        // 3 events captured in the snapshot + 2 replayed from the journal after the snapshot.
        assertThat(thirdRecovery.count()).isEqualTo(5);
        // The snapshot offered on recovery is still the count=3 snapshot; the extra two came from journal replay.
        assertThat(thirdRecovery.snapshot()).isNotNull();
        assertThat(JsonFactory.newObject(thirdRecovery.snapshot()).getValue("count").map(JsonValue::asInt))
                .contains(3);
    }

    /**
     * Recovery outcome a {@link BootTestActor} reports to its probe once {@code RecoveryCompleted} fires: the event
     * count reached and the snapshot payload offered (raw JSON text, or {@code null} when none was offered).
     */
    public record Recovered(int count, @Nullable String snapshot) {}

    /**
     * Minimal persistent actor bound to the Postgres {@code things} journal + snapshot plugins. Events are persisted as
     * Ditto {@code JsonObject}s (the journal stores them as JSONB and replays them as JSON text); the snapshot state is
     * a small JSON document. On every {@code RecoveryCompleted} it reports its recovered state to {@code recoveryProbe}.
     */
    public static final class BootTestActor extends AbstractPersistentActor {

        private final String persistenceId;
        private final ActorRef recoveryProbe;
        private int count;
        @Nullable
        private String recoveredSnapshot;
        @Nullable
        private ActorRef pendingSnapshotSender;

        public BootTestActor(final String persistenceId, final ActorRef recoveryProbe) {
            this.persistenceId = persistenceId;
            this.recoveryProbe = recoveryProbe;
        }

        @Override
        public String persistenceId() {
            return persistenceId;
        }

        @Override
        public String journalPluginId() {
            return "ditto-postgres-things-journal";
        }

        @Override
        public String snapshotPluginId() {
            return "ditto-postgres-things-snapshots";
        }

        @Override
        public Receive createReceiveRecover() {
            return receiveBuilder()
                    .match(SnapshotOffer.class, offer -> {
                        recoveredSnapshot = String.valueOf(offer.snapshot());
                        count = JsonFactory.newObject(recoveredSnapshot).getValue("count")
                                .map(JsonValue::asInt).orElse(0);
                    })
                    // Replayed event payloads arrive as JSON text (PostgresJournalOps#toRepr).
                    .match(String.class, event -> count++)
                    .match(RecoveryCompleted.class, completed ->
                            recoveryProbe.tell(new Recovered(count, recoveredSnapshot), getSelf()))
                    .build();
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchEquals("persist", msg -> {
                        final ActorRef who = getSender();
                        persist(JsonFactory.newObject("{\"seq\":" + (count + 1) + "}"), event -> {
                            count++;
                            who.tell("ack-persist", getSelf());
                        });
                    })
                    .matchEquals("snapshot", msg -> {
                        pendingSnapshotSender = getSender();
                        saveSnapshot("{\"count\":" + count + "}");
                    })
                    .match(SaveSnapshotSuccess.class, success -> {
                        if (pendingSnapshotSender != null) {
                            pendingSnapshotSender.tell("ack-snap", getSelf());
                            pendingSnapshotSender = null;
                        }
                    })
                    .build();
        }
    }

}
