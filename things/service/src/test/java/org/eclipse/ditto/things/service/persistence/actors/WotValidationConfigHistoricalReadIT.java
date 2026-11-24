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
package org.eclipse.ditto.things.service.persistence.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.Document;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.raw.RawConfigSupplier;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.test.docker.mongo.MongoDbResource;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.commands.CreateWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.CreateWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigHistoryNotAccessibleException;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Historical reads of WoT validation configs against a MongoDB, through the read journal that
 * {@link PersistenceBackendProvider#getReadJournal(String)} binds to the journal of the WoT validation configs.
 */
public final class WotValidationConfigHistoricalReadIT {

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final String ENTITY_TYPE = "wot-validation-config";
    private static final String DATABASE = "wothistory";
    private static final String SNAPS_COLLECTION = "wot_validation_config_snaps";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Rule
    public final TestName testName = new TestName();

    private ActorSystem system;
    private PersistenceBackendProvider provider;
    private TestKit mediator;
    private WotValidationConfigId configId;

    @After
    public void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void revisionOneIsTheCreatedConfig() {
        startSystem(ConfigFactory.empty());
        final ActorRef supervisor = startSupervisor(provider.getReadJournal(ENTITY_TYPE));
        final TestKit client = new TestKit(system);
        writeRevisions(supervisor, client, 3);

        final JsonObject config = retrieveRevision(supervisor, client, 1);

        assertRevision(config, 1);
    }

    @Test
    public void revisionTwoIsTheFirstModification() {
        startSystem(ConfigFactory.empty());
        final ActorRef supervisor = startSupervisor(provider.getReadJournal(ENTITY_TYPE));
        final TestKit client = new TestKit(system);
        writeRevisions(supervisor, client, 3);

        final JsonObject config = retrieveRevision(supervisor, client, 2);

        assertRevision(config, 2);
    }

    @Test
    public void timestampBetweenTwoRevisionsReturnsTheEarlierOne() throws InterruptedException {
        startSystem(ConfigFactory.empty());
        final ActorRef supervisor = startSupervisor(provider.getReadJournal(ENTITY_TYPE));
        final TestKit client = new TestKit(system);
        writeRevisions(supervisor, client, 1);
        TimeUnit.MILLISECONDS.sleep(50);
        final Instant between = Instant.now();
        TimeUnit.MILLISECONDS.sleep(50);
        writeRevision(supervisor, client, 2);

        supervisor.tell(RetrieveWotValidationConfig.of(configId, historicalHeaders(
                DittoHeaderDefinition.AT_HISTORICAL_TIMESTAMP.getKey(), between.toString())), client.getRef());
        final JsonObject config = client.expectMsgClass(TIMEOUT, RetrieveWotValidationConfigResponse.class)
                .getValidationConfig()
                .asObject();

        assertRevision(config, 1);
    }

    @Test
    public void revisionBeyondTheLatestIsNotAccessible() {
        startSystem(ConfigFactory.empty());
        final ActorRef supervisor = startSupervisor(provider.getReadJournal(ENTITY_TYPE));
        final TestKit client = new TestKit(system);
        writeRevisions(supervisor, client, 3);

        supervisor.tell(RetrieveWotValidationConfig.of(configId, historicalHeaders(
                DittoHeaderDefinition.AT_HISTORICAL_REVISION.getKey(), "99")), client.getRef());

        client.expectMsgClass(TIMEOUT, WotValidationConfigHistoryNotAccessibleException.class);
    }

    @Test
    public void sharedReadJournalDoesNotFindTheHistory() {
        startSystem(ConfigFactory.empty());
        final ActorRef supervisor = startSupervisor(provider.getReadJournal(ENTITY_TYPE));
        final TestKit client = new TestKit(system);
        writeRevisions(supervisor, client, 3);
        client.watch(supervisor);
        system.stop(supervisor);
        client.expectTerminated(TIMEOUT, supervisor);

        final ActorRef supervisorWithSharedReadJournal = startSupervisor(provider.getReadJournal());
        supervisorWithSharedReadJournal.tell(RetrieveWotValidationConfig.of(configId, historicalHeaders(
                DittoHeaderDefinition.AT_HISTORICAL_REVISION.getKey(), "1")), client.getRef());

        client.expectMsgClass(TIMEOUT, WotValidationConfigHistoryNotAccessibleException.class);
    }

    @Test
    public void revisionBetweenASnapshotAndTheLatestIsReadFromSnapshotAndEvents() {
        startSystem(ConfigFactory.parseString("ditto.things.wot-validation-config.snapshot.threshold = 2"));
        final ActorRef supervisor = startSupervisor(provider.getReadJournal(ENTITY_TYPE));
        final TestKit client = new TestKit(system);
        writeRevisions(supervisor, client, 4);
        client.awaitAssert(Duration.ofSeconds(10), () -> {
            assertThat(countSnapshots("wot-validation-config:" + configId)).isEqualTo(2L);
            return null;
        });

        final JsonObject config = retrieveRevision(supervisor, client, 3);

        assertRevision(config, 3);
    }

    private void startSystem(final Config testOverrides) {
        final String mongoDbUri = String.format("mongodb://%s:%d/%s", MONGO_RESOURCE.getBindIp(),
                MONGO_RESOURCE.getPort(), DATABASE);
        final Config config = testOverrides
                .withFallback(ConfigFactory.parseString(
                        "pekko.log-dead-letters = 0\n"
                                + "pekko.remote.artery.canonical.port = 0\n"
                                + "pekko.remote.artery.bind.port = 0\n"
                                + "pekko.cluster.seed-nodes = []\n"
                                + "pekko.cluster.roles = [\"wot-validation-config-aware\", "
                                + "\"blocked-namespaces-aware\"]\n"
                                + "pekko.cluster.distributed-data.durable.keys = []\n"
                                + "pekko.coordinated-shutdown.exit-jvm = off\n"
                                + "pekko.coordinated-shutdown.terminate-actor-system = off\n"
                                + "pekko.coordinated-shutdown.run-by-actor-system-terminate = off\n"
                                + "pekko.contrib.persistence.mongodb.mongo.mongouri = \"" + mongoDbUri + "\"\n"
                                + "ditto.mongodb.uri = \"" + mongoDbUri + "\"\n"
                                // as in the unit tests of the WoT validation config actors: the modify-to-create
                                // transformer reads the distributed data the root actor fills from the pub-sub
                                // mediator, which is a probe here
                                + "ditto.extensions.signal-transformers-provider.extension-config"
                                + ".signal-transformers = []\n"))
                .withFallback(RawConfigSupplier.of("things").get());
        system = ActorSystem.create("WotValidationConfigHistoricalReadIT", config);
        final Cluster cluster = Cluster.get(system);
        cluster.join(cluster.selfAddress());
        new TestKit(system).awaitAssert(Duration.ofSeconds(10), () -> {
            assertThat(cluster.selfMember().status()).isEqualTo(MemberStatus.up());
            return null;
        });
        provider = PersistenceBackendProvider.get(system, ScopedConfig.dittoExtension(system.settings().config()));
        mediator = new TestKit(system);
        configId = WotValidationConfigId.of("ns:history-" + testName.getMethodName());
    }

    private ActorRef startSupervisor(final DittoReadJournal readJournal) {
        return system.actorOf(WotValidationConfigSupervisorActor.props(mediator.getRef(), readJournal),
                URLEncoder.encode(configId.toString(), StandardCharsets.UTF_8));
    }

    private void writeRevisions(final ActorRef supervisor, final TestKit client, final int count) {
        for (int revision = 1; revision <= count; revision++) {
            writeRevision(supervisor, client, revision);
        }
    }

    /**
     * Writes revision {@code revision}: the creation for 1 (as the modify-to-create transformer sends it in the
     * service), a modification otherwise. The flags {@code enabled} and
     * {@code log-warning-instead-of-failing-api-calls} tell the revisions 1 to 4 apart.
     */
    private void writeRevision(final ActorRef supervisor, final TestKit client, final int revision) {
        final WotValidationConfig config = WotValidationConfig.of(configId,
                enabledOf(revision),
                logWarningOf(revision),
                null,
                null,
                List.of(),
                WotValidationConfigRevision.of(revision),
                Instant.now(),
                Instant.now(),
                false,
                null);
        if (revision == 1) {
            supervisor.tell(CreateWotValidationConfig.of(configId, config, devopsHeaders()), client.getRef());
            client.expectMsgClass(TIMEOUT, CreateWotValidationConfigResponse.class);
        } else {
            supervisor.tell(ModifyWotValidationConfig.of(configId, config, devopsHeaders()), client.getRef());
            client.expectMsgClass(TIMEOUT, ModifyWotValidationConfigResponse.class);
        }
    }

    private JsonObject retrieveRevision(final ActorRef supervisor, final TestKit client, final long revision) {
        supervisor.tell(RetrieveWotValidationConfig.of(configId, historicalHeaders(
                DittoHeaderDefinition.AT_HISTORICAL_REVISION.getKey(), String.valueOf(revision))), client.getRef());
        return client.expectMsgClass(TIMEOUT, RetrieveWotValidationConfigResponse.class)
                .getValidationConfig()
                .asObject();
    }

    private static void assertRevision(final JsonObject config, final long revision) {
        assertThat(config.getValue("_revision").map(v -> v.asLong())).contains(revision);
        assertThat(config.getValue("enabled").map(v -> v.asBoolean())).contains(enabledOf(revision));
        assertThat(config.getValue("log-warning-instead-of-failing-api-calls").map(v -> v.asBoolean()))
                .contains(logWarningOf(revision));
    }

    private static boolean enabledOf(final long revision) {
        return revision == 1 || revision == 4;
    }

    private static boolean logWarningOf(final long revision) {
        return revision >= 3;
    }

    private static DittoHeaders devopsHeaders() {
        return DittoHeaders.newBuilder().putHeader("ditto-auth-type", "devops").build();
    }

    private static DittoHeaders historicalHeaders(final String key, final String value) {
        return DittoHeaders.newBuilder()
                .putHeader("ditto-auth-type", "devops")
                .putHeader(key, value)
                .build();
    }

    private long countSnapshots(final String persistenceId) {
        try (final MongoClient mongoClient = MongoClients.create(
                String.format("mongodb://%s:%d", MONGO_RESOURCE.getBindIp(), MONGO_RESOURCE.getPort()))) {
            return Source.fromPublisher(mongoClient.getDatabase(DATABASE)
                            .getCollection(SNAPS_COLLECTION, Document.class)
                            .countDocuments(Filters.eq("pid", persistenceId)))
                    .runWith(Sink.head(), system)
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("Could not count the snapshots of <" + persistenceId + ">", e);
        }
    }

}
