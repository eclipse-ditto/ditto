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

package org.eclipse.ditto.services.thingsearch.updater.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.services.models.streaming.SudoStreamSnapshots;
import org.eclipse.ditto.services.models.thingsearch.SearchNamespaceReportResult;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThing;
import org.eclipse.ditto.services.thingsearch.common.config.BackgroundSyncConfig;
import org.eclipse.ditto.services.thingsearch.common.config.DefaultBackgroundSyncConfig;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.health.RetrieveHealth;
import org.eclipse.ditto.services.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.services.utils.health.StatusDetailMessage;
import org.eclipse.ditto.services.utils.health.StatusInfo;
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

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    private static final ThingId THING_ID = ThingId.of("org.eclipse:ditto");
    private static final Instant TAGGED_TIMESTAMP = Instant.now().minus(5, ChronoUnit.MINUTES);

    private static final long REVISION_INDEXED = 1;
    private static final long REVISION_PERSISTED = REVISION_INDEXED + 1;
    private static final List<NamespacedEntityId> KNOWN_IDs = List.of(
            DefaultNamespacedEntityId.of("org.eclipse:ditto1"),
            DefaultNamespacedEntityId.of("org.eclipse:ditto2"),
            DefaultNamespacedEntityId.of("org.eclipse:ditto3"),
            DefaultNamespacedEntityId.of("org.eclipse:ditto4")
    );
    private static final List<Metadata> THINGS_INDEXED =
            KNOWN_IDs.stream()
                    .map(id -> Metadata.of(ThingId.of(id), REVISION_INDEXED, PolicyId.of(id), REVISION_INDEXED, null))
                    .collect(Collectors.toList());
    private static final List<StreamedSnapshot> THINGS_PERSISTED = KNOWN_IDs.stream()
            .map(id -> createStreamedSnapshot(id, REVISION_PERSISTED))
            .collect(Collectors.toList());
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
    public void providesHealthWarningWhenSameThingIsSynchronizedTwice() {
        final Metadata indexedThingMetadata = Metadata.of(THING_ID, 2, null, null, null);
        final long persistedRevision = indexedThingMetadata.getThingRevision() + 1;

        new TestKit(actorSystem) {{
            whenSearchPersistenceHasIndexedThings(List.of(indexedThingMetadata));
            whenTimestampPersistenceProvidesTaggedTimestamp();

            final ActorRef underTest = thenCreateBackgroundSyncActor(this);

            // first synchronization stream
            expectSyncActorToStartStreaming(pubSub);
            thenRespondWithPersistedThingsStream(pubSub, List.of(createStreamedSnapshot(THING_ID, persistedRevision)));
            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, List.of(THING_ID));

            // second synchronization stream
            whenSearchPersistenceHasIndexedThings(List.of(indexedThingMetadata));
            expectSyncActorToStartStreaming(pubSub, backgroundSyncConfig.getIdleTimeout());
            thenRespondWithPersistedThingsStream(pubSub, List.of(createStreamedSnapshot(THING_ID, persistedRevision)));
            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, List.of(THING_ID));

            // expect health to have events for both runs
            syncActorShouldHaveHealth(underTest, this, StatusInfo.Status.UP, List.of(StatusDetailMessage.Level.WARN),
                    detailMessages -> {
                        final String events = detailMessages.stream()
                                .map(StatusDetailMessage::getMessage)
                                .map(JsonValue::toString)
                                .collect(Collectors.joining());
                        assertThat(events).contains(indexedThingMetadata.toString());
                    });
        }};
    }

    @Test
    public void staysHealthyWhenSameThingIsSynchronizedWithOtherRevision() {
        final Metadata indexedThingMetadata = Metadata.of(THING_ID, 2, null, null, null);
        final long persistedRevision = indexedThingMetadata.getThingRevision() + 1;
        final Metadata nextThingMetadata = Metadata.of(THING_ID, persistedRevision, null, null, null);
        final long nextRevision = nextThingMetadata.getThingRevision() + 1;

        new TestKit(actorSystem) {{
            whenSearchPersistenceHasIndexedThings(List.of(indexedThingMetadata));
            whenTimestampPersistenceProvidesTaggedTimestamp();

            final ActorRef underTest = thenCreateBackgroundSyncActor(this);

            // first synchronization stream
            expectSyncActorToStartStreaming(pubSub);
            thenRespondWithPersistedThingsStream(pubSub, List.of(createStreamedSnapshot(THING_ID, persistedRevision)));
            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, List.of(THING_ID));

            // second synchronization stream
            whenSearchPersistenceHasIndexedThings(List.of(nextThingMetadata));
            expectSyncActorToStartStreaming(pubSub, backgroundSyncConfig.getIdleTimeout());
            thenRespondWithPersistedThingsStream(pubSub, List.of(createStreamedSnapshot(THING_ID, nextRevision)));
            expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, List.of(THING_ID));

            // expect health to have events for both runs
            syncActorShouldHaveHealth(underTest, this, StatusInfo.Status.UP, List.of(StatusDetailMessage.Level.INFO),
                    detailMessages -> {
                        final String events = detailMessages.stream()
                                .map(StatusDetailMessage::getMessage)
                                .map(JsonValue::toString)
                                .collect(Collectors.joining());
                        assertThat(events).contains(indexedThingMetadata.toString(), nextThingMetadata.toString());
                    });
        }};
    }

    private ActorRef thenCreateBackgroundSyncActor(final TestKit system) {
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
        final DistributedPubSubMediator.Send startStream =
                pubSub.expectMsgClass(withinTimeout, DistributedPubSubMediator.Send.class);
        assertThat(startStream.msg()).isInstanceOf(SudoStreamSnapshots.class);
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
        expectSyncActorToRequestThingUpdatesInSearch(thingsUpdater, KNOWN_IDs);
    }

    private void expectSyncActorToRequestThingUpdatesInSearch(final TestKit thingsUpdater,
            final List<? extends EntityId> ids) {
        ids.stream()
                .map(id -> UpdateThing.of(ThingId.of(id), DittoHeaders.empty()))
                .forEach(thingsUpdater::expectMsg);
    }

    private void expectSyncActorToBeUpAndHealthy(final ActorRef syncActor, final TestKit sender) {
        syncActorShouldHaveHealth(syncActor, sender, StatusInfo.Status.UP, List.of(StatusDetailMessage.Level.INFO),
                detailMessages -> {
                    KNOWN_IDs.forEach(thingId -> assertThat(detailMessages.stream()
                            .anyMatch(message -> message.getMessage().toString().contains(thingId))));
                    assertThat(detailMessages).allMatch(
                            message -> StatusDetailMessage.Level.INFO.equals(message.getLevel()));
                });
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
        public Source<ResultList<ThingId>, NotUsed> findAll(final Query query,
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

    private static StreamedSnapshot createStreamedSnapshot(final EntityId id, final long revision) {
        return StreamedSnapshot.of(ThingId.of(id), Thing.newBuilder()
                .setId(ThingId.of(id))
                .setRevision(revision)
                .setPolicyId(PolicyId.of(id))
                .build()
                .toJson(FieldType.all()));
    }

}
