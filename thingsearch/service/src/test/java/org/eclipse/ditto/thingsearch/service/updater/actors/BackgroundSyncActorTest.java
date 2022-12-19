/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.awaitility.Awaitility;
import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.ShutdownReasonFactory;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.internal.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.internal.models.streaming.SudoStreamSnapshots;
import org.eclipse.ditto.internal.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.internal.utils.health.ResetHealthEvents;
import org.eclipse.ditto.internal.utils.health.ResetHealthEventsResponse;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.internal.utils.health.StatusDetailMessage;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.SearchNamespaceReportResult;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoUpdateThing;
import org.eclipse.ditto.thingsearch.service.common.config.BackgroundSyncConfig;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultBackgroundSyncConfig;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.common.model.TimestampedThingId;
import org.eclipse.ditto.thingsearch.service.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link BackgroundSyncActor}.
 */
public final class BackgroundSyncActorTest {

    private static final DittoHeaders HEADERS = DittoHeaders.empty();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    private static final ThingId THING_ID = ThingId.of("org.eclipse:ditto");
    private static final Instant TAGGED_TIMESTAMP = Instant.now().minus(5, ChronoUnit.MINUTES);

    private static final long REVISION_INDEXED = 1;
    private static final long REVISION_PERSISTED = REVISION_INDEXED + 1;
    private static final List<ThingId> KNOWN_IDs = List.of(
            ThingId.of("org.eclipse:ditto1"),
            ThingId.of("org.eclipse:ditto2"),
            ThingId.of("org.eclipse:ditto3"),
            ThingId.of("org.eclipse:ditto4")
    );
    private static final List<Metadata> THINGS_INDEXED =
            KNOWN_IDs.stream()
                    .map(id -> Metadata.of(ThingId.of(id), REVISION_INDEXED,
                            PolicyTag.of(PolicyId.of(id), REVISION_INDEXED), Set.of(), null))
                    .toList();
    private static final List<StreamedSnapshot> THINGS_PERSISTED = KNOWN_IDs.stream()
            .map(id -> createStreamedSnapshot(id, REVISION_PERSISTED))
            .toList();
    private static final Config TEST_CONFIG = ConfigFactory.load("test");

    private ActorSystem actorSystem;
    private TestKit thingsUpdater;
    private TestKit pubSub;
    private TestKit policiesShardRegion;
    private MockThingsSearchPersistence searchPersistence;
    private MockTimestampPersistence timestampPersistence;
    private BackgroundSyncConfig backgroundSyncConfig;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TEST_CONFIG);
        thingsUpdater = new TestKit(actorSystem);
        pubSub = new TestKit(actorSystem);
        policiesShardRegion = new TestKit(actorSystem);
        searchPersistence = new MockThingsSearchPersistence();
        timestampPersistence = new MockTimestampPersistence();
        backgroundSyncConfig = DefaultBackgroundSyncConfig.parse(ConfigFactory.load("background-sync-test.conf"));
    }

    @After
    public void tearDown() {
        if (Objects.nonNull(actorSystem)) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void synchronizesThings() {
        new TestKit(actorSystem) {{
            whenSearchPersistenceHasIndexedThings();
            whenTimestampPersistenceProvidesTaggedTimestamp();

            final ActorRef underTest = thenCreateBackgroundSyncActor(this);

            expectSyncActorToStartStreaming(pubSub);
            thenRespondWithPersistedThingsStream(pubSub);

            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater);

            expectSyncActorToBeUpAndHealthy(underTest, this);
        }};
    }

    @Test
    public void synchronizesThingsWithFilterAfterShutdown() {
        new TestKit(actorSystem) {{
            whenSearchPersistenceHasIndexedThings();
            whenTimestampPersistenceProvidesTaggedTimestamp();

            final ActorRef underTest = thenCreateBackgroundSyncActor(this, DefaultBackgroundSyncConfig.parse(
                    ConfigFactory.parseString("quiet-period=200ms")
                            .withFallback(ConfigFactory.load("background-sync-test.conf"))));

            expectDefaultSyncIteration();
            expectSyncActorToBeUpAndHealthy(underTest, this);

            underTest.tell(Shutdown.getInstance(ShutdownReasonFactory.fromJson(JsonObject.empty()),
                    DittoHeaders.newBuilder()
                            .putHeader("force-update", Boolean.TRUE.toString())
                            .putHeader("namespaces", JsonArray.of("namespace1", "namespace2").toString())
                            .build()), getRef());

            // next iteration is forced and limited to given namespaces
            expectForcedSyncIteration();
            expectSyncActorToBeUpAndHealthy(underTest, this);

            // next iteration is default again
            expectDefaultSyncIteration();

            expectSyncActorToBeUpAndHealthy(underTest, this);
        }};
    }

    private void expectDefaultSyncIteration() {
        expectSyncActorToStartStreaming(pubSub, DEFAULT_TIMEOUT, msg -> assertThat(msg.getNamespaces()).isEmpty());
        thenRespondWithPersistedThingsStream(pubSub);
        expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater);
    }

    private void expectForcedSyncIteration() {
        expectSyncActorToStartStreaming(pubSub, DEFAULT_TIMEOUT,
                msg -> assertThat(msg.getNamespaces()).containsExactly("namespace1", "namespace2"));
        thenRespondWithPersistedThingsStream(pubSub);
        expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, false,
                DittoHeaders.newBuilder().putHeader("force-update", "true").build());
    }

    @Test
    public void providesHealthWarningWhenSyncStreamFails() {

        new TestKit(actorSystem) {{
            whenSearchPersistenceHasIndexedThings();
            whenTimestampPersistenceProvidesTaggedTimestamp();

            final ActorRef underTest = thenCreateBackgroundSyncActor(this);

            expectSyncActorToStartStreaming(pubSub);

            thenRespondWithFailingPersistedThingsStream(pubSub);

            expectSyncActorToBeUpWithWarning(underTest, this);
        }};

    }

    @Test
    public void resettingHealthEventsAfterSyncStreamFailureClearsErrors() {
        final Metadata indexedThingMetadata = Metadata.of(THING_ID, 2, null, Set.of(), null);
        final long persistedRevision = indexedThingMetadata.getThingRevision() + 1;

        new TestKit(actorSystem) {{
            whenSearchPersistenceHasIndexedThings(List.of(indexedThingMetadata));
            whenTimestampPersistenceProvidesTaggedTimestamp();

            final ActorRef underTest = thenCreateBackgroundSyncActor(this);

            expectSyncActorToStartStreaming(pubSub);
            thenRespondWithPersistedThingsStream(pubSub,
                    List.of(createStreamedSnapshot(THING_ID, persistedRevision + 1)));
            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater,
                    List.of(SudoUpdateThing.of(THING_ID, true, false, UpdateReason.BACKGROUND_SYNC, HEADERS)));

            expectSyncActorToBeUpWithWarning(underTest, this);

            underTest.tell(ResetHealthEvents.newInstance(), getRef());
            final ResetHealthEventsResponse response = expectMsgClass(ResetHealthEventsResponse.class);
            assertThat(response.getHttpStatus()).isEqualTo(HttpStatus.NO_CONTENT);

            expectSyncActorToBeUpAndHealthy(underTest, this);
        }};
    }

    @Test
    public void noHealthWarningAfterSuccessfulStream() {
        final Metadata indexedThingMetadata = Metadata.of(THING_ID, 2, null, Set.of(), null);
        final long persistedRevision = indexedThingMetadata.getThingRevision() + 1;
        final Metadata persistedThingMetadata = Metadata.of(THING_ID, persistedRevision, null, Set.of(), null);
        final var streamedSnapshots = List.of(createStreamedSnapshot(THING_ID, persistedRevision));
        final var streamedSnapshotsWithoutPolicyId =
                List.of(createStreamedSnapshotWithoutPolicyId(THING_ID, persistedRevision));

        new TestKit(actorSystem) {{
            whenSearchPersistenceHasIndexedThings(List.of(indexedThingMetadata));
            whenTimestampPersistenceProvidesTaggedTimestamp();

            final ActorRef underTest = thenCreateBackgroundSyncActor(this);

            // first synchronization stream
            expectSyncActorToStartStreaming(pubSub);
            thenRespondWithPersistedThingsStream(pubSub, streamedSnapshots);
            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater,
                    List.of(SudoUpdateThing.of(THING_ID, true, false, UpdateReason.BACKGROUND_SYNC, HEADERS)));

            // second synchronization stream
            whenSearchPersistenceHasIndexedThings(List.of(indexedThingMetadata));
            expectSyncActorToStartStreaming(pubSub, backgroundSyncConfig.getIdleTimeout());
            thenRespondWithPersistedThingsStream(pubSub, streamedSnapshots);
            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater,
                    List.of(SudoUpdateThing.of(THING_ID, true, false, UpdateReason.BACKGROUND_SYNC, HEADERS)));

            // third synchronization stream
            whenSearchPersistenceHasIndexedThings(List.of(persistedThingMetadata));
            expectSyncActorToStartStreaming(pubSub, backgroundSyncConfig.getIdleTimeout());
            thenRespondWithPersistedThingsStream(pubSub, streamedSnapshotsWithoutPolicyId);
            thingsUpdater.expectNoMessage();

            // fourth synchronization stream
            whenSearchPersistenceHasIndexedThings(List.of(indexedThingMetadata));
            expectSyncActorToStartStreaming(pubSub, backgroundSyncConfig.getIdleTimeout());
            thenRespondWithPersistedThingsStream(pubSub, streamedSnapshotsWithoutPolicyId);

            // expect health to recover after successful sync
            expectSyncActorToBeUpAndHealthy(underTest, this);
        }};

    }

    @Test
    public void staysHealthyWhenSameThingIsSynchronizedWithOtherRevision() {
        final Metadata indexedThingMetadata = Metadata.of(THING_ID, 2, null, Set.of(), null);
        final long persistedRevision = indexedThingMetadata.getThingRevision() + 1;
        final Metadata nextThingMetadata = Metadata.of(THING_ID, persistedRevision, null, Set.of(), null);
        final long nextRevision = nextThingMetadata.getThingRevision() + 1;

        new TestKit(actorSystem) {{
            whenSearchPersistenceHasIndexedThings(List.of(indexedThingMetadata));
            whenTimestampPersistenceProvidesTaggedTimestamp();

            final ActorRef underTest = thenCreateBackgroundSyncActor(this);

            // first synchronization stream
            expectSyncActorToStartStreaming(pubSub);
            thenRespondWithPersistedThingsStream(pubSub, List.of(createStreamedSnapshot(THING_ID, persistedRevision)));
            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, List.of(
                    SudoUpdateThing.of(THING_ID, true, false, UpdateReason.BACKGROUND_SYNC, HEADERS)));

            // second synchronization stream
            whenSearchPersistenceHasIndexedThings(List.of(nextThingMetadata));
            expectSyncActorToStartStreaming(pubSub, backgroundSyncConfig.getIdleTimeout());
            thenRespondWithPersistedThingsStream(pubSub, List.of(createStreamedSnapshot(THING_ID, nextRevision)));
            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, List.of(
                    SudoUpdateThing.of(THING_ID, true, false, UpdateReason.BACKGROUND_SYNC, HEADERS)));

            // expect health to recover after successful sync
            expectSyncActorToBeUpAndHealthy(underTest, this);
        }};
    }

    private ActorRef thenCreateBackgroundSyncActor(final TestKit system) {
        return thenCreateBackgroundSyncActor(system, backgroundSyncConfig);
    }

    private ActorRef thenCreateBackgroundSyncActor(final TestKit system,
            final BackgroundSyncConfig backgroundSyncConfig) {
        return system.childActorOf(BackgroundSyncActor.props(
                backgroundSyncConfig,
                pubSub.getRef(),
                searchPersistence,
                timestampPersistence,
                policiesShardRegion.getRef(),
                thingsUpdater.getRef()
        ));
    }

    private void expectSyncActorToStartStreaming(final TestKit pubSub) {
        expectSyncActorToStartStreaming(pubSub, DEFAULT_TIMEOUT);
    }

    private void expectSyncActorToStartStreaming(final TestKit pubSub, final Duration withinTimeout) {
        expectSyncActorToStartStreaming(pubSub, withinTimeout, msg -> {});
    }

    private void expectSyncActorToStartStreaming(final TestKit pubSub, final Duration withinTimeout,
            final Consumer<SudoStreamSnapshots> msgAssertions) {
        final DistributedPubSubMediator.Publish startStream =
                pubSub.expectMsgClass(withinTimeout, DistributedPubSubMediator.Publish.class);
        assertThat(startStream.msg()).isInstanceOf(SudoStreamSnapshots.class);
        msgAssertions.accept(((SudoStreamSnapshots) startStream.msg()));
    }

    private void thenRespondWithFailingPersistedThingsStream(final TestKit pubSub) {
        final CompletableFuture<StreamedSnapshot> failingSnapshot = new CompletableFuture<>();
        failingSnapshot.completeExceptionally(new IllegalStateException("Fail the stream with error"));
        final Source<StreamedSnapshot, NotUsed> persistedThingSource = Source.fromCompletionStage(failingSnapshot);
        pubSub.reply(persistedThingSource.runWith(StreamRefs.sourceRef(), Materializer.apply(actorSystem)));
    }

    private void thenRespondWithPersistedThingsStream(final TestKit pubSub) {
        thenRespondWithPersistedThingsStream(pubSub, THINGS_PERSISTED);
    }

    private void thenRespondWithPersistedThingsStream(final TestKit pubSub, final List<StreamedSnapshot> things) {
        final SourceRef<StreamedSnapshot> streamedSnapshotSourceRef = Source.from(things)
                .runWith(StreamRefs.sourceRef(), Materializer.apply(actorSystem));
        pubSub.reply(streamedSnapshotSourceRef);
    }

    private void expectSyncActorToRequestThingUpdatesInSearch(final TestKit thingsUpdater) {
        expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, true, HEADERS);
    }

    private void expectSyncActorToRequestThingUpdatesInSearch(final TestKit thingsUpdater,
            final boolean invalidateThing, final DittoHeaders headers) {
        expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, List.of(
                SudoUpdateThing.of(KNOWN_IDs.get(0), invalidateThing, false, UpdateReason.BACKGROUND_SYNC, headers),
                SudoUpdateThing.of(KNOWN_IDs.get(1), invalidateThing, false, UpdateReason.BACKGROUND_SYNC, headers),
                SudoUpdateThing.of(KNOWN_IDs.get(2), invalidateThing, false, UpdateReason.BACKGROUND_SYNC, headers),
                SudoUpdateThing.of(KNOWN_IDs.get(3), invalidateThing, false, UpdateReason.BACKGROUND_SYNC, headers)
        ));
    }

    private void expectSyncActorToRequestThingUpdatesInSearch(final TestKit thingsUpdater,
            final List<SudoUpdateThing> commands) {
        commands.forEach(command -> {
            thingsUpdater.expectMsg(DEFAULT_TIMEOUT, command);
            thingsUpdater.reply(toAcknowledgement(command));
        });
    }

    private Acknowledgement toAcknowledgement(final SudoUpdateThing sudoUpdateThing) {
        return Acknowledgement.of(DittoAcknowledgementLabel.SEARCH_PERSISTED, sudoUpdateThing.getEntityId(),
                HttpStatus.OK,
                DittoHeaders.empty());
    }

    private void expectSyncActorToBeUpAndHealthy(final ActorRef syncActor, final TestKit sender) {
        syncActorShouldHaveHealth(syncActor, sender, StatusInfo.Status.UP, List.of(StatusDetailMessage.Level.INFO),
                detailMessages -> assertThat(detailMessages).allMatch(
                        message -> StatusDetailMessage.Level.INFO.equals(message.getLevel())));
    }

    private void expectSyncActorToBeUpWithWarning(final ActorRef syncActor, final TestKit sender) {
        syncActorShouldHaveHealth(syncActor, sender, StatusInfo.Status.UP, List.of(StatusDetailMessage.Level.WARN),
                statusDetailMessages -> {
                    // expect one of the messages contains an non-empty-error
                    assertThat(statusDetailMessages.stream()
                            .map(StatusDetailMessage::getMessage)
                            .map(JsonValue::toString)
                            .collect(Collectors.toList()))
                            .anyMatch(message -> !message.contains("Error=<>"));
                }
        );
    }

    private void syncActorShouldHaveHealth(final ActorRef syncActor, final TestKit sender,
            final StatusInfo.Status status,
            final List<StatusDetailMessage.Level> expectedMessageLevels,
            final Consumer<List<? extends StatusDetailMessage>> statusMessagesMatch) {
        // using awaitility since the stream doesn't finish instantly
        Awaitility.waitAtMost(DEFAULT_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    syncActor.tell(RetrieveHealth.newInstance(), sender.getRef());
                    final RetrieveHealthResponse response = sender.expectMsgClass(RetrieveHealthResponse.class);
                    assertThat(response.getStatusInfo().getStatus()).isEqualTo(status);
                    assertThat(response.getStatusInfo().getDetails()).satisfies(statusMessagesMatch);
                    assertThat(response.getStatusInfo().getDetails().stream()
                            .map(StatusDetailMessage::getLevel).collect(Collectors.toList()))
                            .containsExactlyInAnyOrderElementsOf(expectedMessageLevels);
                });
    }

    private void whenSearchPersistenceHasIndexedThings() {
        whenSearchPersistenceHasIndexedThings(THINGS_INDEXED);
    }

    private void whenSearchPersistenceHasIndexedThings(final List<Metadata> indexed) {
        searchPersistence.provideMetadata(indexed);
    }

    private void whenTimestampPersistenceProvidesTaggedTimestamp() {
        whenTimestampPersistenceProvidesTaggedTimestamp(TAGGED_TIMESTAMP, THING_ID.toString());
    }

    private void whenTimestampPersistenceProvidesTaggedTimestamp(final Instant timestamp, final String tag) {
        timestampPersistence.setTaggedTimestamp(timestamp, tag);
    }

    private static class MockThingsSearchPersistence implements ThingsSearchPersistence {

        private List<Metadata> metadata;

        private void provideMetadata(final List<Metadata> toProvide) {
            this.metadata = toProvide;
        }

        @Override
        public CompletionStage<Void> initializeIndices() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Source<SearchNamespaceReportResult, NotUsed> generateNamespaceCountReport() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Source<Long, NotUsed> count(final Query query, final List<String> authorizationSubjectIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Source<Long, NotUsed> sudoCount(final Query query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Source<ResultList<TimestampedThingId>, NotUsed> findAll(final Query query,
                final List<String> authorizationSubjectIds,
                @Nullable final Set<String> namespaces) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Source<ThingId, NotUsed> findAllUnlimited(final Query query, final List<String> authorizationSubjectIds,
                @Nullable final Set<String> namespaces) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Source<Metadata, NotUsed> sudoStreamMetadata(final EntityId lowerBound) {
            checkNotNull(this.metadata,
                    "Metadata may not be null when #sudoStreamMetadata is called. Use #provideMetadata beforehand.");
            return Source.from(this.metadata);
        }

    }

    private static class MockTimestampPersistence implements TimestampPersistence {

        private Instant timestamp;
        private String tag;

        @Override
        public Source<NotUsed, NotUsed> setTimestamp(final Instant timestamp) {
            this.timestamp = timestamp;
            return Source.empty();
        }

        @Override
        public Source<Done, NotUsed> setTaggedTimestamp(final Instant timestamp, @Nullable final String tag) {
            this.timestamp = timestamp;
            this.tag = tag;
            return Source.single(Done.done());
        }

        @Override
        public Source<Optional<Instant>, NotUsed> getTimestampAsync() {
            return Source.single(Optional.ofNullable(timestamp));
        }

        @Override
        public Source<Optional<Pair<Instant, String>>, NotUsed> getTaggedTimestamp() {
            return Source.single(Optional.ofNullable(Pair.create(timestamp, tag)));
        }

    }

    private static StreamedSnapshot createStreamedSnapshot(final ThingId id, final long revision) {
        return StreamedSnapshot.of(id, Thing.newBuilder()
                .setId(id)
                .setRevision(revision)
                .setPolicyId(PolicyId.of(id))
                .build()
                .toJson(FieldType.all()));
    }

    private static StreamedSnapshot createStreamedSnapshotWithoutPolicyId(final EntityId id, final long revision) {
        return StreamedSnapshot.of(ThingId.of(id), Thing.newBuilder()
                .setId(ThingId.of(id))
                .setRevision(revision)
                .build()
                .toJson(FieldType.all()));
    }

}
