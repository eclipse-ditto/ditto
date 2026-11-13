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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig.RememberStorePluginIds;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.ConnectionFactory;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.sharding.ClusterSharding;
import org.apache.pekko.cluster.sharding.ClusterShardingSettings;
import org.apache.pekko.cluster.sharding.ShardRegion;
import org.apache.pekko.testkit.javadsl.TestKit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Proves connectivity's remember-entities on PostgreSQL end to end: Pekko cluster sharding with connectivity's
 * remember-entities settings ({@code remember-entities = on}, {@code remember-entities-store = eventsourced}, the
 * cluster role, the constant entity-recovery strategy, and the journal and snapshot plugin ids the provider's plugin
 * config names for {@code connection}) on a single-node cluster over the real shipped profile and a Testcontainers
 * PostgreSQL. Entities started in one actor system come back by themselves in a new actor system on the same database
 * — restored from the {@code connection_remember_*} tables, through the Pekko-serialization envelope of the remember
 * plugins — without anyone sending them a message, after a graceful stop (a pod's SIGTERM) and after a crash alike.
 * {@code snapshot-after = 1} with {@code keep-nr-of-batches = 0} makes the shard stores snapshot after every event and
 * prune the journal behind the snapshot, so the second life recovers from the snapshot envelope and the delete paths
 * of the remember plugins run too.
 * <p>
 * Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 * </p>
 */
public final class PostgresRememberEntitiesIT {

    /** connectivity's shard region type name ({@code ConnectivityMessagingConstants.SHARD_REGION}). */
    private static final String TYPE_NAME = "connection";
    /** connectivity's cluster role ({@code ConnectivityMessagingConstants.CLUSTER_ROLE}). */
    private static final String CLUSTER_ROLE = "connectivity";
    private static final Duration RESTART_TIMEOUT = Duration.ofSeconds(60);

    private static final Set<String> CONNECTION_IDS = Set.of("conn-a", "conn-b", "conn-c");

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;
    private static final List<ActorSystem> SYSTEMS = new ArrayList<>();

    @BeforeClass
    public static void startContainerAndBootSchema() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresRememberEntitiesIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();
    }

    @AfterClass
    public static void stop() {
        SYSTEMS.forEach(system -> TestKit.shutdownActorSystem(system, Duration.ofSeconds(30), false));
        SYSTEMS.clear();
        POSTGRES.stop();
    }

    @Before
    public void forgetEverything() {
        // each test starts from an empty remember store: the shard stores' persistence ids are the same in every test
        execute("TRUNCATE connection_remember_journal, connection_remember_journal_seq, connection_remember_snaps");
    }

    @Test
    public void rememberedConnectionsRestartByThemselvesAfterAGracefulStop() throws Exception {
        // CoordinatedShutdown runs on terminate: the region hands its shards off and the node leaves — a pod's SIGTERM.
        // Pekko's hand-off stops the entities without recording them as stopped, so the remembered set survives.
        rememberedConnectionsRestartByThemselves(true);
    }

    @Test
    public void rememberedConnectionsRestartByThemselvesAfterACrash() throws Exception {
        // no CoordinatedShutdown: the actor system just terminates, no hand-off, no leave — a killed pod.
        rememberedConnectionsRestartByThemselves(false);
    }

    private static void rememberedConnectionsRestartByThemselves(final boolean gracefulStop) throws Exception {
        // ── first life: the shard region starts three connection entities on demand ──────────────────────────────
        final ActorSystem first = startClusterNode("first", gracefulStop);
        final TestKit firstProbe = new TestKit(first);
        final ActorRef firstRegion = startConnectionRegion(first, firstProbe.getRef());
        CONNECTION_IDS.forEach(id -> firstRegion.tell(new Envelope(id, "open"), ActorRef.noSender()));
        assertThat(startedEntities(firstProbe, CONNECTION_IDS.size())).isEqualTo(CONNECTION_IDS);

        // the remember stores persisted the ids, snapshotted their state (snapshot-after = 1) and pruned the journal
        // behind the snapshot (keep-nr-of-batches = 0), so the second life must recover from the snapshot envelope
        awaitCount("SELECT count(*) FROM connection_remember_snaps", 1);
        awaitCount("SELECT count(*) FROM connection_remember_journal_seq WHERE deleted_to > 0", 1);

        // ── the service restarts: a new actor system on the same database, nothing is sent ────────────────────────
        TestKit.shutdownActorSystem(first, Duration.ofSeconds(30), false);
        SYSTEMS.remove(first);

        final ActorSystem second = startClusterNode("second", true);
        final TestKit secondProbe = new TestKit(second);
        startConnectionRegion(second, secondProbe.getRef());

        assertThat(startedEntities(secondProbe, CONNECTION_IDS.size()))
                .as("the remembered connections restart on their own")
                .isEqualTo(CONNECTION_IDS);
        secondProbe.expectNoMessage(Duration.ofSeconds(2));

        // the remember-entities store never touches the connection entity's own tables
        assertThat(count("SELECT count(*) FROM connections_journal")).isZero();
        assertThat(count("SELECT count(*) FROM connections_snaps")).isZero();
        TestKit.shutdownActorSystem(second, Duration.ofSeconds(30), false);
        SYSTEMS.remove(second);
    }

    /**
     * Boots a single-node cluster on the real shipped Postgres profile with connectivity's sharding settings and waits
     * until the node is Up.
     *
     * @param life names the actor system.
     * @param coordinatedShutdown whether terminating the actor system runs CoordinatedShutdown (hand-off, leave) or
     * just stops, as a crash does.
     */
    private static ActorSystem startClusterNode(final String life, final boolean coordinatedShutdown)
            throws InterruptedException {
        final String overrides = ""
                + "ditto.postgresql {\n"
                + "  uri = \"r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getPort() + "/"
                + POSTGRES.getDatabaseName() + "\"\n"
                + "  username = \"" + PostgresDbResource.DDL_USER + "\"\n"
                + "  password = \"" + PostgresDbResource.DDL_PASSWORD + "\"\n"
                + "  ssl.mode = \"disable\"\n"
                + "}\n"
                // the profile's 4-entity auto-start lists need the services' dispatchers; this node persists nothing
                // but the remember-entities store, whose plugins are not auto-started (as on MongoDB)
                + "pekko.persistence.journal.auto-start-journals = []\n"
                + "pekko.persistence.snapshot-store.auto-start-snapshot-stores = []\n"
                + "pekko.actor.provider = cluster\n"
                + "pekko.remote.artery.canonical { hostname = \"127.0.0.1\", port = 0 }\n"
                + "pekko.cluster.jmx.multi-mbeans-in-same-jvm = on\n"
                + "pekko.cluster.roles = [" + CLUSTER_ROLE + "]\n"
                + "pekko.coordinated-shutdown.run-by-actor-system-terminate = " + (coordinatedShutdown ? "on" : "off")
                + "\n"
                + "pekko.cluster.sharding {\n"
                // as connectivity.conf
                + "  remember-entities = on\n"
                + "  remember-entities-store = eventsourced\n"
                + "  waiting-for-state-timeout = 10s\n"
                + "  updating-state-timeout = 15s\n"
                + "  passivation.strategy = off\n"
                + "  entity-recovery-strategy = constant\n"
                + "  entity-recovery-constant-rate-strategy { frequency = 100 ms, number-of-entities = 10 }\n"
                // every event snapshots and the journal is pruned behind the snapshot at once (connectivity keeps
                // 200 events and 2 batches), so the snapshot and delete paths of the remember plugins run here
                + "  snapshot-after = 1\n"
                + "  keep-nr-of-batches = 0\n"
                // keep the test from writing ddata LMDB files into the module directory
                + "  distributed-data.durable.keys = []\n"
                + "}\n";
        final Config config = ConfigFactory.parseString(overrides)
                .withFallback(ConfigFactory.parseResources("ditto-postgres-persistence.conf"))
                .withFallback(ConfigFactory.load())
                .resolve();
        final ActorSystem system = ActorSystem.create("PostgresRememberEntitiesIT-" + life, config);
        SYSTEMS.add(system);

        final Cluster cluster = Cluster.get(system);
        final CountDownLatch up = new CountDownLatch(1);
        cluster.registerOnMemberUp(up::countDown);
        cluster.join(cluster.selfAddress());
        assertThat(up.await(30, TimeUnit.SECONDS)).as("the single node joins itself").isTrue();
        return system;
    }

    /**
     * Starts the connection shard region as {@code ConnectivityRootActor} does: the remember-store journal and snapshot
     * plugin ids come from the active provider's plugin config, never from HOCON.
     */
    private static ActorRef startConnectionRegion(final ActorSystem system, final ActorRef reporter) {
        final PostgresPersistenceBackendProvider provider = new PostgresPersistenceBackendProvider(system,
                system.settings().config().getConfig("ditto.extensions.persistence-backend-provider.extension-config"));
        final RememberStorePluginIds ids = provider.pluginConfig().getRememberStorePluginIds(TYPE_NAME);
        final ClusterShardingSettings settings = ClusterShardingSettings.create(system)
                .withRole(CLUSTER_ROLE)
                .withJournalPluginId(ids.journalPluginId())
                .withSnapshotPluginId(ids.snapshotPluginId());
        return ClusterSharding.get(system).start(TYPE_NAME, ConnectionEntity.props(reporter), settings,
                new TwoShardExtractor());
    }

    private static Set<String> startedEntities(final TestKit probe, final int expected) {
        final Set<String> started = new HashSet<>();
        for (final Object message : probe.receiveN(expected, RESTART_TIMEOUT)) {
            started.add(((Started) message).entityId());
        }
        return started;
    }

    private static void awaitCount(final String countSql, final long atLeast) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (count(countSql) < atLeast) {
            assertThat(System.nanoTime()).as("<%s> reaches %d within 30 s", countSql, atLeast).isLessThan(deadline);
            Thread.sleep(200);
        }
    }

    private static long count(final String countSql) {
        final Long count = Mono.from(connectionFactory.create())
                .flatMapMany(connection -> Flux.from(connection.createStatement(countSql).execute())
                        .flatMap(result -> result.map((row, meta) -> row.get(0, Long.class)))
                        .concatWith(Flux.from(connection.close()).then(Mono.empty())))
                .blockFirst(Duration.ofSeconds(10));
        return count == null ? 0L : count;
    }

    private static void execute(final String sql) {
        Mono.from(connectionFactory.create())
                .flatMapMany(connection -> Flux.from(connection.createStatement(sql).execute())
                        .flatMap(result -> Flux.from(result.getRowsUpdated()))
                        .concatWith(Flux.from(connection.close()).then(Mono.empty())))
                .blockLast(Duration.ofSeconds(10));
    }

    /** What the shard region routes: the entity id and the message for it. */
    public record Envelope(String entityId, Object payload) {}

    /** Sent to the test probe by every entity that starts, whether on demand or remembered. */
    public record Started(String entityId) {}

    /**
     * A stand-in for Ditto's {@code ShardRegionExtractor} (in {@code internal/utils/cluster}, which this module does
     * not depend on): two shards by a plain hash, so the coordinator remembers more than one shard and both shard
     * stores persist. Like Ditto's extractor it routes Pekko's {@link ShardRegion.StartEntity}, which is how remembered
     * entities are restarted; an extractor that drops it makes the shard log "Shard must not be empty" and forget
     * them. The shard function itself is immaterial here.
     */
    private static final class TwoShardExtractor implements ShardRegion.MessageExtractor {

        @Override
        public String entityId(final Object message) {
            if (message instanceof ShardRegion.StartEntity startEntity) {
                return startEntity.entityId();
            }
            return message instanceof Envelope envelope ? envelope.entityId() : null;
        }

        @Override
        public Object entityMessage(final Object message) {
            return message instanceof Envelope envelope ? envelope.payload() : message;
        }

        @Override
        public String shardId(final Object message) {
            if (message instanceof ShardRegion.StartEntity startEntity) {
                return shardOf(startEntity.entityId());
            }
            return message instanceof Envelope envelope ? shardOf(envelope.entityId()) : null;
        }

        private static String shardOf(final String entityId) {
            return String.valueOf(Math.abs(entityId.hashCode() % 2));
        }
    }

    /**
     * The sharded entity standing in for a connection supervisor: it reports its start to the probe and acknowledges
     * every message. Sharding names the entity actor after its (URL-encoded) entity id, which is why the ids above
     * contain no characters that would be encoded.
     */
    public static final class ConnectionEntity extends AbstractActor {

        private final ActorRef reporter;

        public ConnectionEntity(final ActorRef reporter) {
            this.reporter = reporter;
        }

        static Props props(final ActorRef reporter) {
            return Props.create(ConnectionEntity.class, () -> new ConnectionEntity(reporter));
        }

        @Override
        public void preStart() {
            reporter.tell(new Started(getSelf().path().name()), getSelf());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(message -> getSender().tell("ack", getSelf()))
                    .build();
        }
    }

}
