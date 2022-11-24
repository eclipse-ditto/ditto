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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.internal.models.streaming.SudoStreamSnapshots;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.SnapshotFilter;
import org.eclipse.ditto.json.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.SourceRef;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Test for {@link SnapshotStreamingActor}.
 */
public final class SnapshotStreamingActorTest {

    private static final EntityType THING_TYPE = EntityType.of("thing");
    private ActorSystem actorSystem;
    private DittoMongoClient mockClient;
    private MongoReadJournal mockReadJournal;
    private TestProbe pubSubMediatorTestProbe;

    @Before
    public void initActorSystem() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        mockClient = Mockito.mock(DittoMongoClient.class);
        mockReadJournal = Mockito.mock(MongoReadJournal.class);
        pubSubMediatorTestProbe = TestProbe.apply("pubSubMediator", actorSystem);
    }

    @After
    public void shutdownActorSystem() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void streamEmptySnapshotCollection() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = createSnapshotStreamingActor();

            // WHEN
            final SudoStreamSnapshots sudoStreamSnapshots =
                    SudoStreamSnapshots.of(100, 10_000L, List.of(), DittoHeaders.empty(), THING_TYPE);
            setSnapshotStore(Source.empty());
            underTest.tell(sudoStreamSnapshots, getRef());

            // THEN
            final SourceRef<?> sourceRef = expectMsgClass(SourceRef.class);
            final List<?> results = sourceRef.getSource()
                    .runWith(Sink.seq(), actorSystem)
                    .toCompletableFuture()
                    .join();

            assertThat(results).isEmpty();
        }};
    }

    @Test
    public void streamNonemptySnapshotCollection() {
        streamNonemptySnapshotCollection(
                SudoStreamSnapshots.of(100, 10_000L, List.of(), DittoHeaders.empty(), THING_TYPE),
                SnapshotFilter.of("", ""));
    }

    @Test
    public void streamNonemptyFilteredSnapshotCollection() {
        streamNonemptySnapshotCollection(
                SudoStreamSnapshots.of(100, 10_000L, List.of(), DittoHeaders.empty(), THING_TYPE)
                        .withNamespacesFilter(List.of("eclipse", "ditto")),
                SnapshotFilter.of("", "^thing:(eclipse|ditto):.*"));
    }

    @Test
    public void streamNonemptySnapshotCollectionFromLowerBound() {
        streamNonemptySnapshotCollection(
                SudoStreamSnapshots.of(100, 10_000L, List.of(), DittoHeaders.empty(), THING_TYPE)
                        .withLowerBound(EntityId.of(THING_TYPE, "snap:1")),
                SnapshotFilter.of("thing:snap:1", ""));
    }

    @Test
    public void testServiceUnbindAndServiceRequestsDone() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = createSnapshotStreamingActor();
            pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Subscribe.class);

            underTest.tell(SnapshotStreamingActor.Control.SERVICE_UNBIND, getRef());

            final var unsub1 =
                    pubSubMediatorTestProbe.expectMsgClass(DistributedPubSubMediator.Unsubscribe.class);
            pubSubMediatorTestProbe.reply(new DistributedPubSubMediator.UnsubscribeAck(unsub1));
            expectMsg(Done.getInstance());

            underTest.tell(SnapshotStreamingActor.Control.SERVICE_REQUESTS_DONE, getRef());

            expectMsg(Done.getInstance());
        }};
    }

    private void streamNonemptySnapshotCollection(final SudoStreamSnapshots sudoStreamSnapshots,
            final SnapshotFilter expectedFilter) {
        new TestKit(actorSystem) {{
            final ActorRef underTest = createSnapshotStreamingActor();

            // WHEN
            setSnapshotStore(expectedFilter, Source.from(List.of(
                    new Document().append("_id", "thing:snap:1")
                            .append("_revision", 1)
                            .append("_modified", "2001-01-01"),
                    new Document().append("_id", "thing:snap:2")
                            .append("_revision", 2)
                            .append("_modified", "2002-02-02"),
                    new Document().append("_id", "thing:snap:3")
                            .append("_revision", 3)
                            .append("_modified", "2003-03-03")
            )));
            underTest.tell(sudoStreamSnapshots, getRef());

            // THEN
            final SourceRef<?> sourceRef = expectMsgClass(SourceRef.class);
            final List<Object> results = sourceRef.getSource()
                    .<Object>map(x -> x)
                    .runWith(Sink.seq(), actorSystem)
                    .toCompletableFuture()
                    .join();

            assertThat(results).containsExactly(
                    StreamedSnapshot.of(EntityId.of(THING_TYPE, "snap:1"),
                            JsonObject.of("{\"_revision\":1,\"_modified\":\"2001-01-01\"}")),
                    StreamedSnapshot.of(EntityId.of(THING_TYPE, "snap:2"),
                            JsonObject.of("{\"_revision\":2,\"_modified\":\"2002-02-02\"}")),
                    StreamedSnapshot.of(EntityId.of(THING_TYPE, "snap:3"),
                            JsonObject.of("{\"_revision\":3,\"_modified\":\"2003-03-03\"}"))
            );
        }};

    }


    private void setSnapshotStore(final Source<Document, NotUsed> mockSource) {
        Mockito.when(mockReadJournal.getNewestSnapshotsAbove(any(SnapshotFilter.class), anyInt(), any(), any()))
                .thenReturn(mockSource);
    }

    private void setSnapshotStore(final SnapshotFilter expectedFilter, final Source<Document, NotUsed> mockSource) {
        Mockito.when(mockReadJournal.getNewestSnapshotsAbove(eq(expectedFilter), anyInt(), any(), any()))
                .thenReturn(mockSource);
    }

    private ActorRef createSnapshotStreamingActor() {
        final Props props = SnapshotStreamingActor.propsForTest(
                pid -> EntityId.of(EntityType.of(pid.substring(0, pid.indexOf(":"))),
                        pid.substring(pid.indexOf(':') + 1)),
                entityId -> THING_TYPE + ":" + entityId.toString(),
                mockClient,
                mockReadJournal,
                pubSubMediatorTestProbe.ref()
        );

        return actorSystem.actorOf(props);
    }
}
