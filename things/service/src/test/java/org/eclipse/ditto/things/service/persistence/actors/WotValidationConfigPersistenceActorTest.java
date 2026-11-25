/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;
import org.apache.pekko.event.Logging;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.persistence.Persistence;
import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotProtocol;
import org.apache.pekko.persistence.SnapshotSelectionCriteria;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.FeatureValidationConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.FeatureValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationEnforceConfig;
import org.eclipse.ditto.things.model.devops.ThingValidationForbidConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.WotValidationConfigRevision;
import org.eclipse.ditto.things.model.devops.commands.DeleteWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.DeleteWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.ModifyWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfig;
import org.eclipse.ditto.things.model.devops.commands.RetrieveWotValidationConfigResponse;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigNotAccessibleException;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import scala.jdk.javaapi.OptionConverters;


public class WotValidationConfigPersistenceActorTest extends PersistenceActorTestBase {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final Logger LOGGER = LoggerFactory.getLogger(WotValidationConfigPersistenceActorTest.class);

    @Rule
    public final TestWatcher watchman = new TestedMethodLoggingWatcher(LOGGER);

    private WotValidationConfigId configId;
    private WotValidationConfig config;
    private ActorRef supervisorActor;
    private TestProbe pubSubMediatorProbe;
    private TestProbe errorProbe;
    private Cluster cluster;
    private Config baseConfigWithoutWotBlock;

    @Mock
    private MongoReadJournal mongoReadJournal;

    private static final DittoHeaders TEST_HEADERS = DittoHeaders.newBuilder()
            .putHeader("ditto-auth-type", "devops")
            .build();

    private static final String WOT_BLOCK = "ditto.things.wot-validation-config";
    private static final String SNAPSHOT_STORE_PLUGIN_ID =
            "pekko-contrib-mongodb-persistence-wot-validation-config-snapshots";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Use a fixed port for testing
        final int port = 25520; // Standard Pekko port
        final String hostname = "127.0.0.1";

        baseConfigWithoutWotBlock = ConfigFactory.empty()
                .withValue("pekko.actor.provider",
                        ConfigValueFactory.fromAnyRef("org.apache.pekko.cluster.ClusterActorRefProvider"))
                .withValue("pekko.cluster.roles",
                        ConfigValueFactory.fromIterable(Arrays.asList(
                                "wot-validation-config-aware",
                                "blocked-namespaces-aware"
                        )))
                .withValue("pekko.remote.artery.canonical.port",
                        ConfigValueFactory.fromAnyRef(port))
                .withValue("pekko.remote.artery.canonical.hostname",
                        ConfigValueFactory.fromAnyRef(hostname))
                .withValue("pekko.cluster.seed-nodes",
                        ConfigValueFactory.fromIterable(Collections.singletonList(
                                "pekko://PekkoTestSystem@" + hostname + ":" + port)))
                .withValue("pekko.cluster.min-nr-of-members",
                        ConfigValueFactory.fromAnyRef(1))
                .withValue("pekko.cluster.auto-down-unreachable-after",
                        ConfigValueFactory.fromAnyRef("0s"))
                .withValue("pekko.persistence.journal.plugin",
                        ConfigValueFactory.fromAnyRef("pekko-contrib-mongodb-persistence-wot-validation-config-journal"))
                .withValue("pekko.persistence.snapshot-store.plugin",
                        ConfigValueFactory.fromAnyRef("pekko-contrib-mongodb-persistence-wot-validation-config-snapshots"))
                .withValue("pekko.persistence.journal.auto-start-journals",
                        ConfigValueFactory.fromIterable(Collections.singletonList("pekko-contrib-mongodb-persistence-wot-validation-config-journal")))
                .withValue("pekko.persistence.snapshot-store.auto-start-snapshot-stores",
                        ConfigValueFactory.fromIterable(Collections.singletonList("pekko-contrib-mongodb-persistence-wot-validation-config-snapshots")))
                .withValue("ditto.persistence.things.activity-check.interval",
                        ConfigValueFactory.fromAnyRef("1s"))
                .withValue("ditto.persistence.things.activity-check.inactive-timeout",
                        ConfigValueFactory.fromAnyRef("2s"))
                .withValue("ditto.tracing.enabled",
                        ConfigValueFactory.fromAnyRef(false))
                .withValue("pekko.test.single-expect-default",
                        ConfigValueFactory.fromAnyRef("10s"))
                .withValue("pekko.test.default-timeout",
                        ConfigValueFactory.fromAnyRef("10s"))
                .withValue("pekko.remote.artery.bind.timeout",
                        ConfigValueFactory.fromAnyRef("5s"))
                .withValue("pekko.remote.artery.bind.hostname",
                        ConfigValueFactory.fromAnyRef(hostname))
                .withValue("pekko.remote.artery.bind.port",
                        ConfigValueFactory.fromAnyRef(port))
                .withValue("pekko.cluster.distributed-data.durable.keys",
                        ConfigValueFactory.fromIterable(Collections.singletonList("WotValidationConfig")))
                .withValue("pekko.cluster.distributed-data.durable.lmdb.dir",
                        ConfigValueFactory.fromAnyRef("target/ddata"))
                .withValue("pekko.cluster.distributed-data.durable.lmdb.write-behind-interval",
                        ConfigValueFactory.fromAnyRef("200ms"))
                .withValue("pekko.cluster.distributed-data.durable.lmdb.cleanup-interval",
                        ConfigValueFactory.fromAnyRef("1h"));

        final Config customConfigWithBlock = baseConfigWithoutWotBlock
                .withValue(WOT_BLOCK + ".snapshot.threshold", ConfigValueFactory.fromAnyRef(1))
                .withValue(WOT_BLOCK + ".activity-check.inactive-interval", ConfigValueFactory.fromAnyRef("1s"))
                .withValue(WOT_BLOCK + ".activity-check.deleted-interval", ConfigValueFactory.fromAnyRef("1s"));

        startActorSystem(customConfigWithBlock);
    }

    private void startActorSystem(final Config customConfig) {
        setup(customConfig);
        errorProbe = TestProbe.apply("errorProbe", actorSystem);
        actorSystem.getEventStream().subscribe(errorProbe.ref(), Logging.Error.class);

        cluster = Cluster.get(actorSystem);
        cluster.join(cluster.selfAddress());

        final long startTime = System.currentTimeMillis();
        final long timeout = 10000; // 10 seconds
        while (System.currentTimeMillis() - startTime < timeout) {
            if (cluster.selfMember().status().equals(MemberStatus.up())) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(cluster.selfMember().status()).isEqualTo(MemberStatus.up());

        pubSubMediatorProbe = TestProbe.apply("pubSubMediator", actorSystem);
        pubSubMediator = pubSubMediatorProbe.ref();

        configId = WotValidationConfigId.of("ns:test-id");
        final ThingValidationEnforceConfig thingEnforce = ThingValidationEnforceConfig.of(true, true, true, true, true);
        final ThingValidationForbidConfig thingForbid = ThingValidationForbidConfig.of(false, true, false, true);
        final ThingValidationConfig thingConfig = ThingValidationConfig.of(thingEnforce, thingForbid);

        final FeatureValidationEnforceConfig featureEnforce = FeatureValidationEnforceConfig.of(true, false, true, false, true, false, true);
        final FeatureValidationForbidConfig featureForbid = FeatureValidationForbidConfig.of(false, true, false, true, false, true);
        final FeatureValidationConfig featureConfig = FeatureValidationConfig.of(featureEnforce, featureForbid);

        config = WotValidationConfig.of(
                configId,
                true,
                false,
                thingConfig,
                featureConfig,
                Collections.emptyList(),
                WotValidationConfigRevision.of(0L),
                Instant.now(),
                Instant.now(),
                false,
                null
        );

        when(mongoReadJournal.getLatestEventSeqNo(any())).thenReturn(Source.single(Optional.empty()));
        when(mongoReadJournal.getSmallestEventSeqNo(any())).thenReturn(Source.single(Optional.empty()));

        supervisorActor = actorSystem.actorOf(
                WotValidationConfigSupervisorActor.props(pubSubMediator, mongoReadJournal),
                URLEncoder.encode(configId.toString(), StandardCharsets.UTF_8)
        );
    }

    @After
    public void tearDown() {
        if (supervisorActor != null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (cluster != null) {
            cluster.leave(cluster.selfAddress());
        }
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    private Optional<SelectedSnapshot> latestSnapshot(final String persistenceId) {
        final ActorRef snapshotStore =
                Persistence.get(actorSystem).snapshotStoreFor(SNAPSHOT_STORE_PLUGIN_ID, ConfigFactory.empty());
        final SnapshotProtocol.LoadSnapshot loadSnapshot =
                new SnapshotProtocol.LoadSnapshot(persistenceId, SnapshotSelectionCriteria.latest(), Long.MAX_VALUE);
        try {
            return Patterns.ask(snapshotStore, loadSnapshot, Duration.ofSeconds(3))
                    .thenApply(SnapshotProtocol.LoadSnapshotResult.class::cast)
                    .thenApply(result -> OptionConverters.toJava(result.snapshot()))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("Could not load the latest snapshot of <" + persistenceId + ">", e);
        }
    }

    private String persistenceIdOf(final WotValidationConfigId id) {
        return "wot-validation-config:" + id;
    }

    private void awaitSnapshotAtSequenceNumber(final TestKit testKit, final long expectedSequenceNr) {
        testKit.awaitAssert(Duration.ofSeconds(10), () -> {
            final Optional<SelectedSnapshot> snapshot = latestSnapshot(persistenceIdOf(configId));
            assertThat(snapshot).isPresent();
            assertThat(snapshot.get().metadata().sequenceNr()).isEqualTo(expectedSequenceNr);
            return null;
        });
    }

    private void restartSupervisor() {
        final TestProbe terminationProbe = new TestProbe(actorSystem);
        terminationProbe.watch(supervisorActor);
        actorSystem.stop(supervisorActor);
        terminationProbe.expectTerminated(supervisorActor, scala.concurrent.duration.Duration.create(5, "seconds"));
        supervisorActor = createPersistenceActorFor(configId);
    }

    private WotValidationConfig modifiedConfig() {
        final ThingValidationEnforceConfig newThingEnforce =
                ThingValidationEnforceConfig.of(false, false, false, false, false);
        final ThingValidationForbidConfig newThingForbid = ThingValidationForbidConfig.of(true, false, true, false);
        final ThingValidationConfig newThingConfig = ThingValidationConfig.of(newThingEnforce, newThingForbid);

        final FeatureValidationEnforceConfig newFeatureEnforce =
                FeatureValidationEnforceConfig.of(false, true, false, true, false, true, false);
        final FeatureValidationForbidConfig newFeatureForbid =
                FeatureValidationForbidConfig.of(true, false, true, false, true, false);
        final FeatureValidationConfig newFeatureConfig =
                FeatureValidationConfig.of(newFeatureEnforce, newFeatureForbid);

        return WotValidationConfig.of(
                configId,
                false,
                true,
                newThingConfig,
                newFeatureConfig,
                config.getDynamicConfigs(),
                WotValidationConfigRevision.of(2L),
                config.getCreated().orElse(null),
                Instant.now(),
                false,
                null
        );
    }

    private static JsonObject withoutTimestamps(final JsonObject json) {
        return JsonObject.newBuilder()
                .setAll(json)
                .remove("_created")
                .remove("_modified")
                .build();
    }

    private ActorRef createPersistenceActorFor(final WotValidationConfigId configId) {
        return actorSystem.actorOf(
                WotValidationConfigSupervisorActor.props(pubSubMediator, mongoReadJournal),
                URLEncoder.encode(configId.toString(), StandardCharsets.UTF_8)
        );
    }

    @Test
    public void testCreateConfig() {
        new TestKit(actorSystem) {{
            final ModifyWotValidationConfig command = ModifyWotValidationConfig.of(configId, config, TEST_HEADERS);

            supervisorActor.tell(command, getRef());

            final ModifyWotValidationConfigResponse response = expectMsgClass(ModifyWotValidationConfigResponse.class);
            assertThat(response.getConfigId().toString()).isEqualTo(configId.toString());

            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
        }};
    }

    @Test
    public void testModifyConfig() {
        new TestKit(actorSystem) {{
            final ModifyWotValidationConfig createCommand = ModifyWotValidationConfig.of(configId, config, TEST_HEADERS);
            supervisorActor.tell(createCommand, getRef());
            expectMsgClass(ModifyWotValidationConfigResponse.class);
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

            final ThingValidationEnforceConfig newThingEnforce = ThingValidationEnforceConfig.of(false, false, false, false, false);
            final ThingValidationForbidConfig newThingForbid = ThingValidationForbidConfig.of(true, false, true, false);
            final ThingValidationConfig newThingConfig = ThingValidationConfig.of(newThingEnforce, newThingForbid);

            final FeatureValidationEnforceConfig newFeatureEnforce = FeatureValidationEnforceConfig.of(false, true, false, true, false, true, false);
            final FeatureValidationForbidConfig newFeatureForbid = FeatureValidationForbidConfig.of(true, false, true, false, true, false);
            final FeatureValidationConfig newFeatureConfig = FeatureValidationConfig.of(newFeatureEnforce, newFeatureForbid);

            final WotValidationConfig newConfig = WotValidationConfig.of(
                    configId,
                    false,
                    true,
                    newThingConfig,
                    newFeatureConfig,
                    config.getDynamicConfigs(),
                    WotValidationConfigRevision.of(2L),
                    config.getCreated().orElse(null),
                    Instant.now(),
                    false,
                    null
            );
            final ModifyWotValidationConfig command = ModifyWotValidationConfig.of(configId, newConfig, TEST_HEADERS);

            supervisorActor.tell(command, getRef());

            final ModifyWotValidationConfigResponse response = expectMsgClass(ModifyWotValidationConfigResponse.class);
            assertThat(response.getConfigId().toString()).isEqualTo(configId.toString());

            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
        }};
    }

    @Test
    public void testDeleteConfig() {
        new TestKit(actorSystem) {{
            final ModifyWotValidationConfig createCommand = ModifyWotValidationConfig.of(configId, config, TEST_HEADERS);
            supervisorActor.tell(createCommand, getRef());
            expectMsgClass(ModifyWotValidationConfigResponse.class);
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

            final DeleteWotValidationConfig command = DeleteWotValidationConfig.of(configId, TEST_HEADERS);
            supervisorActor.tell(command, getRef());

            final DeleteWotValidationConfigResponse response = expectMsgClass(DeleteWotValidationConfigResponse.class);
            assertThat(response.getConfigId().toString()).isEqualTo(configId.toString());

            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

            final RetrieveWotValidationConfig retrieveCommand = RetrieveWotValidationConfig.of(configId, TEST_HEADERS);
            supervisorActor.tell(retrieveCommand, getRef());
            expectMsgClass(org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigNotAccessibleException.class);
        }};
    }

    @Test
    public void testRetrieveConfig() {
        new TestKit(actorSystem) {{
            final ModifyWotValidationConfig createCommand = ModifyWotValidationConfig.of(configId, config, TEST_HEADERS);
            supervisorActor.tell(createCommand, getRef());
            expectMsgClass(ModifyWotValidationConfigResponse.class);
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

            final RetrieveWotValidationConfig command = RetrieveWotValidationConfig.of(configId, TEST_HEADERS);
            supervisorActor.tell(command, getRef());

            final RetrieveWotValidationConfigResponse response = expectMsgClass(RetrieveWotValidationConfigResponse.class);
            assertThat(response.getConfigId().toString()).isEqualTo(configId.toString());

            final JsonObject retrievedConfig = response.getValidationConfig().asObject();
            final JsonObject thingConfig = retrievedConfig.getValue("thing").get().asObject();
            final JsonObject thingEnforce = thingConfig.getValue("enforce").get().asObject();
            final JsonObject thingForbid = thingConfig.getValue("forbid").get().asObject();
            assertThat(thingEnforce.getValue("thing-description-modification").get().asBoolean()).isTrue();
            assertThat(thingEnforce.getValue("attributes").get().asBoolean()).isTrue();
            assertThat(thingForbid.getValue("thing-description-deletion").get().asBoolean()).isFalse();
            assertThat(thingForbid.getValue("non-modeled-inbox-messages").get().asBoolean()).isFalse();

            final JsonObject featureConfig = retrievedConfig.getValue("feature").get().asObject();
            final JsonObject featureEnforce = featureConfig.getValue("enforce").get().asObject();
            final JsonObject featureForbid = featureConfig.getValue("forbid").get().asObject();
            assertThat(featureEnforce.getValue("feature-description-modification").get().asBoolean()).isTrue();
            assertThat(featureEnforce.getValue("presence-of-modeled-features").get().asBoolean()).isFalse();
            assertThat(featureForbid.getValue("feature-description-deletion").get().asBoolean()).isFalse();
            assertThat(featureForbid.getValue("non-modeled-features").get().asBoolean()).isTrue();
        }};
    }

    @Test
    public void testRecovery() {
        new TestKit(actorSystem) {{
            final ModifyWotValidationConfig createCommand = ModifyWotValidationConfig.of(configId, config, TEST_HEADERS);
            supervisorActor.tell(createCommand, getRef());
            final ModifyWotValidationConfigResponse createResponse = expectMsgClass(ModifyWotValidationConfigResponse.class);
            assertThat(createResponse.getConfigId().toString()).isEqualTo(configId.toString());
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

            final RetrieveWotValidationConfig initialCommand = RetrieveWotValidationConfig.of(configId, TEST_HEADERS);
            supervisorActor.tell(initialCommand, getRef());
            final RetrieveWotValidationConfigResponse initialResponse = expectMsgClass(RetrieveWotValidationConfigResponse.class);
            assertThat(initialResponse.getConfigId().toString()).isEqualTo(configId.toString());
            assertThat(initialResponse.getValidationConfig().asObject().getValue("_revision").get().asLong()).isEqualTo(1L);

            TestProbe terminationProbe = new TestProbe(actorSystem);
            terminationProbe.watch(supervisorActor);
            actorSystem.stop(supervisorActor);
            terminationProbe.expectTerminated(supervisorActor, scala.concurrent.duration.Duration.create(5, "seconds"));

            supervisorActor = actorSystem.actorOf(
                    WotValidationConfigSupervisorActor.props(pubSubMediator, mongoReadJournal),
                    URLEncoder.encode(configId.toString(), StandardCharsets.UTF_8)
            );


            final RetrieveWotValidationConfig command = RetrieveWotValidationConfig.of(configId, TEST_HEADERS);
            supervisorActor.tell(command, getRef());
            final RetrieveWotValidationConfigResponse response = expectMsgClass(RetrieveWotValidationConfigResponse.class);
            assertThat(response.getConfigId().toString()).isEqualTo(configId.toString());

            final JsonObject expectedJson = initialResponse.getValidationConfig().asObject();
            final JsonObject actualJson = response.getValidationConfig().asObject();

            final JsonObject expectedWithoutTimestamps = JsonObject.newBuilder()
                    .setAll(expectedJson)
                    .remove("_created")
                    .remove("_modified")
                    .build();
            final JsonObject actualWithoutTimestamps = JsonObject.newBuilder()
                    .setAll(actualJson)
                    .remove("_created")
                    .remove("_modified")
                    .build();


            assertThat(actualWithoutTimestamps).isEqualTo(expectedWithoutTimestamps);
        }};
    }

    @Test
    public void testActivityCheck() {
        new TestKit(actorSystem) {{
            final ModifyWotValidationConfig createCommand = ModifyWotValidationConfig.of(configId, config, TEST_HEADERS);
            supervisorActor.tell(createCommand, getRef());
            expectMsgClass(ModifyWotValidationConfigResponse.class);
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

            final RetrieveWotValidationConfig command = RetrieveWotValidationConfig.of(configId, TEST_HEADERS);
            supervisorActor.tell(command, getRef());
            final RetrieveWotValidationConfigResponse response = expectMsgClass(RetrieveWotValidationConfigResponse.class);
            assertThat(response.getConfigId().toString()).isEqualTo(configId.toString());
        }};
    }

    @Test
    public void snapshotOfAnActiveConfigIsTakenAndRecovered() {
        new TestKit(actorSystem) {{
            supervisorActor.tell(ModifyWotValidationConfig.of(configId, config, TEST_HEADERS), getRef());
            expectMsgClass(ModifyWotValidationConfigResponse.class);
            final WotValidationConfig newConfig = modifiedConfig();
            supervisorActor.tell(ModifyWotValidationConfig.of(configId, newConfig, TEST_HEADERS), getRef());
            expectMsgClass(ModifyWotValidationConfigResponse.class);
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

            awaitSnapshotAtSequenceNumber(this, 2L);

            restartSupervisor();

            supervisorActor.tell(RetrieveWotValidationConfig.of(configId, TEST_HEADERS), getRef());
            final RetrieveWotValidationConfigResponse response =
                    expectMsgClass(RetrieveWotValidationConfigResponse.class);
            final JsonObject actualJson = response.getValidationConfig().asObject();
            assertThat(actualJson.getValue("_revision").get().asLong()).isEqualTo(2L);
            assertThat(withoutTimestamps(actualJson)).isEqualTo(withoutTimestamps(newConfig.toJson().asObject()));

            errorProbe.expectNoMessage(scala.concurrent.duration.Duration.create(1, "second"));
        }};
    }

    @Test
    public void snapshotOfADeletedConfigIsTakenAndRecovered() {
        new TestKit(actorSystem) {{
            supervisorActor.tell(ModifyWotValidationConfig.of(configId, config, TEST_HEADERS), getRef());
            expectMsgClass(ModifyWotValidationConfigResponse.class);
            supervisorActor.tell(DeleteWotValidationConfig.of(configId, TEST_HEADERS), getRef());
            expectMsgClass(DeleteWotValidationConfigResponse.class);
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

            // the deletion leaves no entity to snapshot: recovery replays it on top of the creation's snapshot
            awaitSnapshotAtSequenceNumber(this, 1L);
            expectNoMessage(Duration.ofSeconds(2));
            awaitSnapshotAtSequenceNumber(this, 1L);

            restartSupervisor();

            supervisorActor.tell(RetrieveWotValidationConfig.of(configId, TEST_HEADERS), getRef());
            expectMsgClass(WotValidationConfigNotAccessibleException.class);

            errorProbe.expectNoMessage(scala.concurrent.duration.Duration.create(1, "second"));
        }};
    }

    @Test
    public void activityCheckSettingsOfTheWotBlockAreUsed() {
        final WotValidationConfigId otherId = WotValidationConfigId.of("ns:activity-check-id");
        final WotValidationConfig otherConfig = WotValidationConfig.of(otherId, true, false,
                config.getThingConfig().orElse(null),
                config.getFeatureConfig().orElse(null), config.getDynamicConfigs(), WotValidationConfigRevision.of(0L),
                Instant.now(), Instant.now(), false, null);
        final TestKit parent = new TestKit(actorSystem);
        final TestKit sender = new TestKit(actorSystem);
        final ActorRef persistenceActor = parent.childActorOf(
                WotValidationConfigPersistenceActor.props(otherId, mongoReadJournal, pubSubMediator));

        persistenceActor.tell(ModifyWotValidationConfig.of(otherId, otherConfig, TEST_HEADERS), sender.getRef());
        sender.expectMsgClass(ModifyWotValidationConfigResponse.class);

        parent.expectMsgEquals(AbstractPersistenceSupervisor.Control.PA_RECOVERED);
        parent.expectMsgEquals(Duration.ofSeconds(10), AbstractPersistenceSupervisor.Control.PASSIVATE);
    }

    @Test
    public void missingWotBlockFallsBackToTheDefaults() {
        cluster.leave(cluster.selfAddress());
        TestKit.shutdownActorSystem(actorSystem);
        startActorSystem(baseConfigWithoutWotBlock);
        assertThat(actorSystem.settings().config().hasPath(WOT_BLOCK)).isFalse();

        new TestKit(actorSystem) {{
            supervisorActor.tell(ModifyWotValidationConfig.of(configId, config, TEST_HEADERS), getRef());
            expectMsgClass(ModifyWotValidationConfigResponse.class);
            supervisorActor.tell(ModifyWotValidationConfig.of(configId, modifiedConfig(), TEST_HEADERS), getRef());
            expectMsgClass(ModifyWotValidationConfigResponse.class);

            supervisorActor.tell(RetrieveWotValidationConfig.of(configId, TEST_HEADERS), getRef());
            final RetrieveWotValidationConfigResponse response =
                    expectMsgClass(RetrieveWotValidationConfigResponse.class);
            assertThat(response.getConfigId().toString()).isEqualTo(configId.toString());

            expectNoMessage(Duration.ofSeconds(1));
            assertThat(latestSnapshot(persistenceIdOf(configId))).isEmpty();
        }};
    }
}
