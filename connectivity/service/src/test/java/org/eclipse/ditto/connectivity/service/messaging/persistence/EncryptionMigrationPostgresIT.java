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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionLifecycle;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionCreated;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.FieldsEncryptionConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.EncryptionMigrationActor;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrateConnectionEncryption;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrateConnectionEncryptionAbort;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrateConnectionEncryptionAbortResponse;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrateConnectionEncryptionResponse;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrateConnectionEncryptionStatus;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrateConnectionEncryptionStatusResponse;
import org.eclipse.ditto.connectivity.service.util.EncryptorAesGcm;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.postgres.PostgresSnapshotCodec;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

/**
 * The connection-secret encryption migration end to end on PostgreSQL: the real {@link EncryptionMigrationActor} over
 * the real {@code PostgresPersistenceBackendProvider} — obtained through the production path
 * {@link PersistenceBackendProvider#get} from the shipped {@code ditto-postgres-persistence.conf} profile — and its
 * payload store, against a Testcontainers PostgreSQL whose {@code connections_snaps} and {@code connections_journal}
 * rows were produced by the real writers of this service: the snapshot JSON by {@link ConnectionMongoSnapshotAdapter}
 * (the {@code SnapshotSerializer}) and the event JSON by {@link ConnectivityPostgresEventAdapter#toJournal}, each
 * configured with the key state the data was persisted under. Every scenario asserts through the same readers
 * configured with ONLY the target key state, and on the raw JSONB text.
 * <p>
 * The three workflows (key rotation, initial encryption, decryption when disabling), dry-run, abort and resume with a
 * new actor instance, and the status answered by a new actor instance from the progress table; the MongoDB
 * stack-level ITs ({@code EncryptionMigrationIT} and siblings) cover the same scenarios on MongoDB through the DevOps
 * piggyback, which is unchanged by the backend-neutral migration and therefore not repeated here. The root actor's
 * start condition (nothing is started when encryption is off and no old key is set) is package-private and pinned by
 * {@code ConnectivityRootActorEncryptionMigrationTest}.
 * <p>
 * Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 */
public final class EncryptionMigrationPostgresIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static final Duration AWAIT = Duration.ofSeconds(30);
    private static final List<String> POINTERS = List.of("/uri", "/credentials/password");
    private static final String URI_PASSWORD = "uri-secret-123";
    private static final String CREDENTIALS_PASSWORD = "credentials-secret-456";
    private static final String ENCRYPTED_PREFIX = "encrypted_";

    private static String oldKey;
    private static String newKey;
    private static ConnectionFactory connectionFactory;
    private static final Map<String, ActorSystem> SYSTEMS = new LinkedHashMap<>();

    /** An encryption configuration of the connectivity service. */
    private record KeyState(String name, boolean enabled, @Nullable String key, @Nullable String oldKey) {}

    /** Encryption off, no key: writes plaintext, reads as it is. */
    private static KeyState plaintext() {
        return new KeyState("plaintext", false, null, null);
    }

    /** Encryption on with the old key only: the state the data was written in before a rotation. */
    private static KeyState oldOnly() {
        return new KeyState("old-only", true, oldKey, null);
    }

    /** Encryption on with the new key only: the target state of a rotation, and the initial-encryption state. */
    private static KeyState newOnly() {
        return new KeyState("new-only", true, newKey, null);
    }

    /** Both keys: the key-rotation state. */
    private static KeyState rotation() {
        return new KeyState("rotation", true, newKey, oldKey);
    }

    /** Encryption off, the former key kept as the old key: the disabling state. */
    private static KeyState disabling() {
        return new KeyState("disabling", false, null, oldKey);
    }

    @BeforeClass
    public static void startContainerAndBootSchema() throws Exception {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping EncryptionMigrationPostgresIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();
        oldKey = EncryptorAesGcm.generateAESKeyAsString();
        newKey = EncryptorAesGcm.generateAESKeyAsString();
    }

    @AfterClass
    public static void stop() {
        SYSTEMS.values().forEach(system -> TestKit.shutdownActorSystem(system, Duration.ofSeconds(30), false));
        SYSTEMS.clear();
        POSTGRES.stop();
    }

    @Before
    public void emptyTables() {
        execute("TRUNCATE connections_journal, connections_journal_seq, connections_snaps, "
                + PostgresSchema.ENCRYPTION_MIGRATION_TABLE, null);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the three workflows
    // ------------------------------------------------------------------------------------------------------------

    @Test
    public void keyRotationReEncryptsWhatTheOldKeyEncryptedAndSkipsWhatTheNewKeyAlreadyDid() {
        final Connection connA = connection("rotation-a");
        final Connection connB = connection("rotation-b");
        seedSnapshot(oldOnly(), connA, 1L);
        seedEvent(oldOnly(), ConnectionCreated.of(connA, 1L, Instant.now(), DittoHeaders.empty(), null), 1L);
        // an event without any secret field: not streamed, so not in the counts
        seedEvent(oldOnly(), ConnectionDeleted.of(connA.getId(), 2L, Instant.now(), DittoHeaders.empty(), null), 2L);
        // a snapshot that is already in the target state
        seedSnapshot(newOnly(), connB, 1L);
        final String snapshotBBefore = snapshotText(pid(connB));
        final String deletedEventBefore = eventText(pid(connA), 2L);

        final MigrateConnectionEncryptionStatusResponse completed = runToCompletion(rotation(), false);

        assertThat(counter(completed, "snapshots/processed")).isEqualTo(1L);
        assertThat(counter(completed, "snapshots/skipped")).as("the already-new snapshot").isEqualTo(1L);
        assertThat(counter(completed, "snapshots/failed")).isZero();
        assertThat(counter(completed, "journalEvents/processed")).isEqualTo(1L);
        assertThat(counter(completed, "journalEvents/skipped")).as("the secret-less event was not streamed").isZero();
        assertThat(counter(completed, "journalEvents/failed")).isZero();

        // the raw rows: ciphertext, decryptable with the new key only
        final String snapshotA = snapshotText(pid(connA));
        assertThat(snapshotA).contains(ENCRYPTED_PREFIX);
        assertThatExceptionOfType(RuntimeException.class).as("the old key no longer decrypts the snapshot")
                .isThrownBy(() -> JsonFieldsEncryptor.decrypt(JsonFactory.newObject(snapshotA), "", POINTERS, oldKey));
        assertThat(eventText(pid(connA), 1L)).contains(ENCRYPTED_PREFIX);
        assertThat(snapshotText(pid(connB))).as("already in the target state: untouched").isEqualTo(snapshotBBefore);
        assertThat(eventText(pid(connA), 2L)).as("no secret field: untouched").isEqualTo(deletedEventBefore);

        // the real readers, configured with the new key only, see the plaintext secrets
        assertSecretsReadable(newOnly(), connA, 1L);
        assertSnapshotSecretsReadable(newOnly(), connB);
        assertProgressRow("completed", 1L, 1L);
    }

    @Test
    public void initialEncryptionEncryptsPlaintextAndSkipsWhatIsAlreadyEncrypted() {
        final Connection connA = connection("initial-a");
        final Connection connC = connection("initial-c");
        seedSnapshot(plaintext(), connA, 1L);
        seedEvent(plaintext(), ConnectionCreated.of(connA, 1L, Instant.now(), DittoHeaders.empty(), null), 1L);
        seedSnapshot(newOnly(), connC, 1L);
        assertThat(snapshotText(pid(connA))).as("seeded in plaintext").contains(URI_PASSWORD, CREDENTIALS_PASSWORD);
        final String snapshotCBefore = snapshotText(pid(connC));

        final MigrateConnectionEncryptionStatusResponse completed = runToCompletion(newOnly(), false);

        assertThat(counter(completed, "snapshots/processed")).isEqualTo(1L);
        assertThat(counter(completed, "snapshots/skipped")).isEqualTo(1L);
        assertThat(counter(completed, "journalEvents/processed")).isEqualTo(1L);
        assertThat(snapshotText(pid(connA))).contains(ENCRYPTED_PREFIX)
                .doesNotContain(URI_PASSWORD, CREDENTIALS_PASSWORD);
        assertThat(eventText(pid(connA), 1L)).contains(ENCRYPTED_PREFIX)
                .doesNotContain(URI_PASSWORD, CREDENTIALS_PASSWORD);
        assertThat(snapshotText(pid(connC))).isEqualTo(snapshotCBefore);
        assertSecretsReadable(newOnly(), connA, 1L);
        assertProgressRow("completed", 1L, 1L);
    }

    @Test
    public void disablingEncryptionDecryptsToPlaintext() {
        final Connection connA = connection("disable-a");
        seedSnapshot(oldOnly(), connA, 1L);
        seedEvent(oldOnly(), ConnectionCreated.of(connA, 1L, Instant.now(), DittoHeaders.empty(), null), 1L);
        assertThat(snapshotText(pid(connA))).as("seeded as ciphertext").contains(ENCRYPTED_PREFIX);

        final MigrateConnectionEncryptionStatusResponse completed = runToCompletion(disabling(), false);

        assertThat(counter(completed, "snapshots/processed")).isEqualTo(1L);
        assertThat(counter(completed, "journalEvents/processed")).isEqualTo(1L);
        assertThat(snapshotText(pid(connA))).doesNotContain(ENCRYPTED_PREFIX)
                .contains(URI_PASSWORD, CREDENTIALS_PASSWORD);
        assertThat(eventText(pid(connA), 1L)).doesNotContain(ENCRYPTED_PREFIX)
                .contains(URI_PASSWORD, CREDENTIALS_PASSWORD);
        // the readers of a service with encryption off and no key at all see the plaintext secrets
        assertSecretsReadable(plaintext(), connA, 1L);
        assertProgressRow("completed", 1L, 1L);
    }

    // ------------------------------------------------------------------------------------------------------------
    // dry run, abort and resume, status after a restart
    // ------------------------------------------------------------------------------------------------------------

    @Test
    public void dryRunWritesNothingAndReportsTheWouldBeCounts() {
        final Connection connA = connection("dry-a");
        seedSnapshot(oldOnly(), connA, 1L);
        seedEvent(oldOnly(), ConnectionCreated.of(connA, 1L, Instant.now(), DittoHeaders.empty(), null), 1L);
        final String snapshotBefore = snapshotText(pid(connA));
        final String eventBefore = eventText(pid(connA), 1L);

        final MigrateConnectionEncryptionStatusResponse completed = runToCompletion(rotation(), true);

        assertThat(completed.toJson().getValue("dryRun").map(JsonValue::asBoolean)).contains(true);
        assertThat(counter(completed, "snapshots/processed")).as("would have been re-encrypted").isEqualTo(1L);
        assertThat(counter(completed, "journalEvents/processed")).isEqualTo(1L);
        assertThat(snapshotText(pid(connA))).isEqualTo(snapshotBefore);
        assertThat(eventText(pid(connA), 1L)).isEqualTo(eventBefore);
        assertThat(progressRow()).as("a dry run saves no progress").isEmpty();
        assertSecretsReadable(oldOnly(), connA, 1L);
    }

    @Test
    public void anAbortedMigrationResumesWithANewActorInstanceWithoutReprocessingAnything() {
        final List<Connection> connections = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final Connection connection = connection("resume-" + i);
            connections.add(connection);
            seedSnapshot(oldOnly(), connection, 1L);
        }
        seedEvent(oldOnly(), ConnectionCreated.of(connections.get(0), 1L, Instant.now(), DittoHeaders.empty(), null),
                1L);
        final ActorSystem system = systemFor(rotation());
        new TestKit(system) {{
            // batches of one, six snapshots per minute: Pekko's throttle lets the first one through at once and the
            // next only ten seconds later, so the abort below lands between two snapshots, deterministically
            final ActorRef throttled = system.actorOf(EncryptionMigrationActor.props(
                    connectivityConfig(rotation(), 1, 6), provider(system)));
            throttled.tell(MigrateConnectionEncryption.of(false, false, DittoHeaders.empty()), getRef());
            assertThat(expectMsgClass(MigrateConnectionEncryptionResponse.class).getHttpStatus())
                    .isEqualTo(HttpStatus.ACCEPTED);
            final MigrateConnectionEncryptionStatusResponse running = awaitStatus(this, throttled,
                    status -> status.isMigrationActive() && "in_progress:snapshots".equals(status.getPhase())
                            && counter(status, "snapshots/processed") >= 1L);

            throttled.tell(MigrateConnectionEncryptionAbort.of(DittoHeaders.empty()), getRef());
            final MigrateConnectionEncryptionAbortResponse aborted =
                    expectMsgClass(MigrateConnectionEncryptionAbortResponse.class);
            assertThat(aborted.getPhase()).isEqualTo("aborted:snapshots");
            final MigrateConnectionEncryptionStatusResponse afterAbort =
                    awaitStatus(this, throttled, status -> !status.isMigrationActive());
            final long processedInFirstRun = counter(afterAbort, "snapshots/processed");
            assertThat(afterAbort.getPhase()).isEqualTo("snapshots");
            assertThat(processedInFirstRun).isGreaterThanOrEqualTo(counter(running, "snapshots/processed"))
                    .isBetween(1L, 3L);
            assertThat(counter(afterAbort, "journalEvents/processed")).as("the journal phase was not reached")
                    .isZero();

            // the progress row holds the phase and the cursor of the last processed snapshot
            Awaitility.await().atMost(AWAIT).until(() -> progressRow()
                    .filter(row -> "snapshots".equals(string(row, "phase")))
                    .isPresent());
            final JsonObject saved = progressRow().orElseThrow();
            assertThat(string(saved, "lastProcessedSnapshotId")).isNotEmpty();
            assertThat(saved.getValue("snapshotsProcessed").map(JsonValue::asLong)).contains(processedInFirstRun);
            final Map<String, String> xminAfterAbort = snapshotXmins();
            final List<String> rewrittenInFirstRun = connections.stream().map(EncryptionMigrationPostgresIT::pid)
                    .filter(pid -> snapshotText(pid).contains(ENCRYPTED_PREFIX) && decryptsWithNewKey(pid))
                    .toList();
            assertThat(rewrittenInFirstRun).hasSize((int) processedInFirstRun);

            // the service restarts: a new actor instance, no throttling, resume = true
            watch(throttled);
            system.stop(throttled);
            expectTerminated(throttled);
            final ActorRef resumed = system.actorOf(EncryptionMigrationActor.props(
                    connectivityConfig(rotation(), 1, 0), provider(system)));
            resumed.tell(MigrateConnectionEncryption.of(false, true, DittoHeaders.empty()), getRef());
            final MigrateConnectionEncryptionResponse accepted =
                    expectMsgClass(MigrateConnectionEncryptionResponse.class);
            assertThat(accepted.getHttpStatus()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(accepted.isResumed()).isTrue();
            final MigrateConnectionEncryptionStatusResponse completed = awaitCompleted(this, resumed);

            // the counters continue from the saved progress: the totals over both runs equal the row counts
            assertThat(counter(completed, "snapshots/processed")).isEqualTo(4L);
            assertThat(counter(completed, "snapshots/skipped")).isZero();
            assertThat(counter(completed, "snapshots/failed")).isZero();
            assertThat(counter(completed, "journalEvents/processed")).isEqualTo(1L);
            // nothing was processed twice: the rows of the first run were not written again
            final Map<String, String> xminAfterResume = snapshotXmins();
            for (final String pid : rewrittenInFirstRun) {
                assertThat(xminAfterResume.get(pid)).as(pid + " was rewritten by the first run only")
                        .isEqualTo(xminAfterAbort.get(pid));
            }
            for (final Connection connection : connections) {
                assertSnapshotSecretsReadable(newOnly(), connection);
            }
            assertEventSecretsReadable(newOnly(), connections.get(0), 1L);
            assertProgressRow("completed", 4L, 1L);
        }};
    }

    @Test
    public void aNewActorInstanceAnswersTheStatusOfAFinishedRunFromTheProgressTable() {
        final Connection connA = connection("status-a");
        seedSnapshot(oldOnly(), connA, 1L);
        seedEvent(oldOnly(), ConnectionCreated.of(connA, 1L, Instant.now(), DittoHeaders.empty(), null), 1L);
        runToCompletion(rotation(), false);
        final ActorSystem system = systemFor(rotation());
        new TestKit(system) {{
            // the service restarted: a fresh actor has no progress in memory and asks the store
            final ActorRef restarted = system.actorOf(EncryptionMigrationActor.props(
                    connectivityConfig(rotation(), 10, 0), provider(system)));

            restarted.tell(MigrateConnectionEncryptionStatus.of(DittoHeaders.empty()), getRef());

            final MigrateConnectionEncryptionStatusResponse status =
                    expectMsgClass(MigrateConnectionEncryptionStatusResponse.class);
            assertThat(status.getPhase()).isEqualTo("completed");
            assertThat(status.isMigrationActive()).isFalse();
            assertThat(counter(status, "snapshots/processed")).isEqualTo(1L);
            assertThat(counter(status, "journalEvents/processed")).isEqualTo(1L);
        }};
    }

    @Test
    public void theProviderOfTheShippedProfileIsThePostgreSqlOne() {
        assertThat(provider(systemFor(rotation()))).isInstanceOf(PostgresPersistenceBackendProvider.class);
    }

    // ------------------------------------------------------------------------------------------------------------
    // running the real actor
    // ------------------------------------------------------------------------------------------------------------

    /** Starts a fresh (non-resumed) migration on a new actor with the given key state and waits for its completion. */
    private static MigrateConnectionEncryptionStatusResponse runToCompletion(final KeyState state,
            final boolean dryRun) {
        final ActorSystem system = systemFor(state);
        final MigrateConnectionEncryptionStatusResponse[] result = new MigrateConnectionEncryptionStatusResponse[1];
        new TestKit(system) {{
            final ActorRef actor = system.actorOf(EncryptionMigrationActor.props(connectivityConfig(state, 10, 0),
                    provider(system)));
            actor.tell(MigrateConnectionEncryption.of(dryRun, false, DittoHeaders.empty()), getRef());
            final MigrateConnectionEncryptionResponse accepted =
                    expectMsgClass(MigrateConnectionEncryptionResponse.class);
            assertThat(accepted.getHttpStatus()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(accepted.isDryRun()).isEqualTo(dryRun);
            assertThat(accepted.isResumed()).isFalse();
            result[0] = awaitCompleted(this, actor);
            watch(actor);
            system.stop(actor);
            expectTerminated(actor);
        }};
        return result[0];
    }

    private static MigrateConnectionEncryptionStatusResponse awaitCompleted(final TestKit testKit,
            final ActorRef actor) {
        return awaitStatus(testKit, actor,
                status -> !status.isMigrationActive() && "completed".equals(status.getPhase()));
    }

    private static MigrateConnectionEncryptionStatusResponse awaitStatus(final TestKit testKit, final ActorRef actor,
            final Predicate<MigrateConnectionEncryptionStatusResponse> condition) {
        final Instant deadline = Instant.now().plus(AWAIT);
        MigrateConnectionEncryptionStatusResponse last;
        do {
            actor.tell(MigrateConnectionEncryptionStatus.of(DittoHeaders.empty()), testKit.getRef());
            last = testKit.expectMsgClass(Duration.ofSeconds(10), MigrateConnectionEncryptionStatusResponse.class);
            if (condition.test(last)) {
                return last;
            }
            try {
                Thread.sleep(20);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for the migration status", e);
            }
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("The migration status did not reach the expected state in time; last: "
                + last.toJson());
    }

    /** The provider through the production path: the profile of the actor system's config selects it. */
    private static PersistenceBackendProvider provider(final ActorSystem system) {
        return PersistenceBackendProvider.get(system, ScopedConfig.dittoExtension(system.settings().config()));
    }

    /**
     * One actor system per key state, on the shipped PostgreSQL profile against the container, with the key state in
     * {@code ditto.connectivity.connection.encryption} for the readers that take their keys from the system config
     * ({@link ConnectivityPostgresEventAdapter}).
     */
    private static ActorSystem systemFor(final KeyState state) {
        return SYSTEMS.computeIfAbsent(state.name(), name -> {
            final String overrides = ""
                    + "ditto.postgresql {\n"
                    + "  uri = \"r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getPort() + "/"
                    + POSTGRES.getDatabaseName() + "\"\n"
                    + "  username = \"" + PostgresDbResource.DDL_USER + "\"\n"
                    + "  password = \"" + PostgresDbResource.DDL_PASSWORD + "\"\n"
                    + "  ssl.mode = \"disable\"\n"
                    + "}\n"
                    // the profile's 4-entity auto-start lists need the services' dispatchers; nothing persists here
                    + "pekko.persistence.journal.auto-start-journals = []\n"
                    + "pekko.persistence.snapshot-store.auto-start-snapshot-stores = []\n"
                    + encryptionBlock(state, 10, 0);
            final Config config = ConfigFactory.parseString(overrides)
                    .withFallback(ConfigFactory.parseResources("ditto-postgres-persistence.conf"))
                    .withFallback(TestConstants.CONFIG)
                    .resolve();
            return ActorSystem.create("EncryptionMigrationPostgresIT-" + name, config);
        });
    }

    /** The connectivity config the actor is built with: the key state plus the migration's batch size and throttle. */
    private static ConnectivityConfig connectivityConfig(final KeyState state, final int batchSize,
            final int maxDocumentsPerMinute) {
        final Config config = ConfigFactory.parseString(encryptionBlock(state, batchSize, maxDocumentsPerMinute))
                .withFallback(TestConstants.CONFIG)
                .resolve();
        return DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config));
    }

    private static String encryptionBlock(final KeyState state, final int batchSize, final int maxDocumentsPerMinute) {
        return "ditto.connectivity.connection.encryption {\n"
                + "  encryption-enabled = " + state.enabled() + "\n"
                + "  symmetrical-key = \"" + (state.key() == null ? "" : state.key()) + "\"\n"
                + "  old-symmetrical-key = \"" + (state.oldKey() == null ? "" : state.oldKey()) + "\"\n"
                + "  json-pointers = [\"/uri\", \"/credentials/password\"]\n"
                + "  migration.batch-size = " + batchSize + "\n"
                + "  migration.max-documents-per-minute = " + maxDocumentsPerMinute + "\n"
                + "}\n";
    }

    private static FieldsEncryptionConfig encryptionConfig(final KeyState state) {
        return connectivityConfig(state, 10, 0).getConnectionConfig().getFieldsEncryptionConfig();
    }

    // ------------------------------------------------------------------------------------------------------------
    // the real writers (seeding) and readers (assertions)
    // ------------------------------------------------------------------------------------------------------------

    private static Connection connection(final String id) {
        return ConnectivityModelFactory.newConnectionBuilder(ConnectionId.of(id), ConnectionType.AMQP_10,
                        ConnectivityStatus.CLOSED, "amqps://user:" + URI_PASSWORD + "@broker.example.com:5671")
                .credentials(UserPasswordCredentials.newInstance("user", CREDENTIALS_PASSWORD))
                .sources(TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT)
                .targets(TestConstants.Targets.TARGETS)
                .lifecycle(ConnectionLifecycle.ACTIVE)
                .build();
    }

    private static String pid(final Connection connection) {
        return "connection:" + connection.getId();
    }

    private static ConnectionMongoSnapshotAdapter snapshotSerializer(final KeyState state) {
        return new ConnectionMongoSnapshotAdapter(encryptionConfig(state));
    }

    private static ConnectivityPostgresEventAdapter eventAdapter(final KeyState state) {
        return new ConnectivityPostgresEventAdapter((ExtendedActorSystem) systemFor(state));
    }

    /** Inserts the snapshot row the PostgreSQL snapshot store would write for the connection under the key state. */
    private static void seedSnapshot(final KeyState state, final Connection connection, final long sn) {
        final JsonObject json = snapshotSerializer(state).toJson(connection);
        final String text = (String) PostgresSnapshotCodec.INSTANCE.encode(json);
        final String lifecycle = json.getValue("__lifecycle").filter(JsonValue::isString).map(JsonValue::asString)
                .orElse(null);
        execute("INSERT INTO connections_snaps (pid, sn, snapshot, lifecycle, written_at) "
                + "VALUES ($1, $2, $3::jsonb, $4, $5)", stmt -> {
            stmt.bind(0, pid(connection));
            stmt.bind(1, sn);
            stmt.bind(2, text);
            if (lifecycle == null) {
                stmt.bindNull(3, String.class);
            } else {
                stmt.bind(3, lifecycle);
            }
            stmt.bind(4, Instant.now());
        });
    }

    /** Inserts the journal row the PostgreSQL journal would write for the event under the key state. */
    private static void seedEvent(final KeyState state, final ConnectivityEvent<?> event, final long sn) {
        final ConnectivityPostgresEventAdapter adapter = eventAdapter(state);
        final Tagged tagged = (Tagged) adapter.toJournal(event);
        final String eventText = (String) tagged.payload();
        final String[] tags = CollectionConverters.asJava(tagged.tags()).toArray(new String[0]);
        final String manifest = adapter.manifest(event);
        final String pid = "connection:" + event.getEntityId();
        execute("INSERT INTO connections_journal (pid, sn, manifest, tags, event, written_at) "
                + "VALUES ($1, $2, $3, $4, $5::jsonb, clock_timestamp())", stmt -> {
            stmt.bind(0, pid);
            stmt.bind(1, sn);
            stmt.bind(2, manifest);
            stmt.bind(3, tags);
            stmt.bind(4, eventText);
        });
        execute("INSERT INTO connections_journal_seq (pid, highest_sn) VALUES ($1, $2) "
                + "ON CONFLICT (pid) DO UPDATE SET highest_sn = GREATEST(connections_journal_seq.highest_sn, "
                + "excluded.highest_sn)", stmt -> {
            stmt.bind(0, pid);
            stmt.bind(1, sn);
        });
    }

    private static void assertSecretsReadable(final KeyState readerState, final Connection connection,
            final long eventSn) {
        assertSnapshotSecretsReadable(readerState, connection);
        assertEventSecretsReadable(readerState, connection, eventSn);
    }

    /** The real snapshot reader, configured with the given key state only, recovers the plaintext secrets. */
    private static void assertSnapshotSecretsReadable(final KeyState readerState, final Connection connection) {
        final JsonObject stored = PostgresSnapshotCodec.INSTANCE.decode(snapshotText(pid(connection)));
        final Connection recovered = snapshotSerializer(readerState).fromJson(stored);
        assertThat(recovered).as("the snapshot of " + connection.getId() + " read with " + readerState.name())
                .isNotNull();
        assertSecrets(recovered);
    }

    /** The real event reader, configured with the given key state only, recovers the plaintext secrets. */
    private static void assertEventSecretsReadable(final KeyState readerState, final Connection connection,
            final long sn) {
        final List<Map<String, Object>> row = query("SELECT manifest, event::text AS event FROM connections_journal "
                        + "WHERE pid = $1 AND sn = $2",
                stmt -> {
                    stmt.bind(0, pid(connection));
                    stmt.bind(1, sn);
                }, (r, meta) -> Map.of("manifest", r.get("manifest", String.class), "event",
                        r.get("event", String.class)));
        assertThat(row).hasSize(1);
        final EventSeq events = eventAdapter(readerState).fromJournal(row.get(0).get("event"),
                (String) row.get(0).get("manifest"));
        final List<Object> recovered = CollectionConverters.asJava(events.events());
        assertThat(recovered).hasSize(1);
        assertThat(recovered.get(0)).isInstanceOf(ConnectionCreated.class);
        assertSecrets(((ConnectionCreated) recovered.get(0)).getConnection());
    }

    private static void assertSecrets(final Connection recovered) {
        assertThat(URI.create(recovered.getUri()).getUserInfo()).isEqualTo("user:" + URI_PASSWORD);
        assertThat(recovered.getCredentials()).isPresent();
        assertThat(((UserPasswordCredentials) recovered.getCredentials().orElseThrow()).getPassword())
                .isEqualTo(CREDENTIALS_PASSWORD);
    }

    private static boolean decryptsWithNewKey(final String pid) {
        try {
            final JsonObject decrypted = JsonFieldsEncryptor.decrypt(JsonFactory.newObject(snapshotText(pid)), "",
                    POINTERS, newKey);
            return decrypted.getValue("/credentials/password").map(JsonValue::asString)
                    .filter(CREDENTIALS_PASSWORD::equals).isPresent();
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static void assertProgressRow(final String phase, final long snapshotsProcessed,
            final long journalProcessed) {
        final JsonObject row = progressRow().orElseThrow(() -> new AssertionError("no progress row"));
        assertThat(string(row, "phase")).isEqualTo(phase);
        assertThat(row.getValue("snapshotsProcessed").map(JsonValue::asLong)).contains(snapshotsProcessed);
        assertThat(row.getValue("journalProcessed").map(JsonValue::asLong)).contains(journalProcessed);
    }

    // ------------------------------------------------------------------------------------------------------------
    // raw rows
    // ------------------------------------------------------------------------------------------------------------

    private static String snapshotText(final String pid) {
        final List<String> texts = query("SELECT snapshot::text AS s FROM connections_snaps WHERE pid = $1 "
                + "ORDER BY sn DESC, written_at DESC", stmt -> stmt.bind(0, pid), (row, meta) -> row.get("s",
                String.class));
        assertThat(texts).as("snapshot rows of " + pid).isNotEmpty();
        return texts.get(0);
    }

    private static String eventText(final String pid, final long sn) {
        final List<String> texts = query("SELECT event::text AS e FROM connections_journal WHERE pid = $1 AND sn = $2",
                stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, sn);
                }, (row, meta) -> row.get("e", String.class));
        assertThat(texts).as("event rows of " + pid + " sn " + sn).hasSize(1);
        return texts.get(0);
    }

    private static Optional<JsonObject> progressRow() {
        final List<String> rows = query("SELECT progress::text AS p FROM " + PostgresSchema.ENCRYPTION_MIGRATION_TABLE
                + " WHERE id = 'current'", null, (row, meta) -> row.get("p", String.class));
        return rows.isEmpty() ? Optional.empty() : Optional.of(JsonFactory.newObject(rows.get(0)));
    }

    private static Map<String, String> snapshotXmins() {
        final Map<String, String> xmins = new HashMap<>();
        // the row mapper must return a value (Result.map rejects null), so collect entries and copy them
        for (final Map.Entry<String, String> entry : query("SELECT pid, xmin::text AS x FROM connections_snaps", null,
                (row, meta) -> Map.entry(row.get("pid", String.class), row.get("x", String.class)))) {
            xmins.put(entry.getKey(), entry.getValue());
        }
        return xmins;
    }

    private static String string(final JsonObject json, final String key) {
        return json.getValue(key).filter(JsonValue::isString).map(JsonValue::asString)
                .orElseThrow(() -> new AssertionError("no string <" + key + "> in " + json));
    }

    private static long counter(final MigrateConnectionEncryptionStatusResponse status, final String path) {
        return status.toJson().getValue(path).map(JsonValue::asLong)
                .orElseThrow(() -> new AssertionError("no <" + path + "> in " + status.toJson()));
    }

    private static <T> List<T> query(final String sql, @Nullable final Consumer<Statement> binder,
            final BiFunction<Row, RowMetadata, T> mapper) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> {
                            final Statement statement = connection.createStatement(sql);
                            if (binder != null) {
                                binder.accept(statement);
                            }
                            return Flux.from(statement.execute())
                                    .flatMap(result -> result.map(mapper))
                                    .collectList();
                        },
                        io.r2dbc.spi.Connection::close)
                .block(Duration.ofSeconds(20));
    }

    private static void execute(final String sql, @Nullable final Consumer<Statement> binder) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> {
                            final Statement statement = connection.createStatement(sql);
                            if (binder != null) {
                                binder.accept(statement);
                            }
                            return Flux.from(statement.execute())
                                    .flatMap(io.r2dbc.spi.Result::getRowsUpdated)
                                    .then();
                        },
                        io.r2dbc.spi.Connection::close)
                .block(Duration.ofSeconds(20));
    }

}
