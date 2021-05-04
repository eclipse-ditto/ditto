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

import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.internal.models.streaming.SudoStreamSnapshots;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.SourceRef;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Test for {@link SnapshotStreamingActor}.
 */
public final class SnapshotStreamingActorTest {

    private static final EntityType THING_TYPE = EntityType.of("thing");
    private ActorSystem actorSystem;
    private DittoMongoClient mockClient;
    private MongoReadJournal mockReadJournal;

    @Before
    public void initActorSystem() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        mockClient = Mockito.mock(DittoMongoClient.class);
        mockReadJournal = Mockito.mock(MongoReadJournal.class);

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
        new TestKit(actorSystem) {{
            final ActorRef underTest = createSnapshotStreamingActor();

            // WHEN
            final SudoStreamSnapshots sudoStreamSnapshots =
                    SudoStreamSnapshots.of(100, 10_000L, List.of(), DittoHeaders.empty(), THING_TYPE);
            setSnapshotStore(Source.from(List.of(
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
        Mockito.when(mockReadJournal.getNewestSnapshotsAbove(any(), anyInt(), any(), any())).thenReturn(mockSource);
    }

    private ActorRef createSnapshotStreamingActor() {
        final Props props = SnapshotStreamingActor.propsForTest(
                pid -> EntityId.of(EntityType.of(pid.substring(0, pid.indexOf(":"))),
                        pid.substring(pid.indexOf(':') + 1)),
                entityId -> THING_TYPE + ":" + entityId.toString(),
                mockClient,
                mockReadJournal
        );
        return actorSystem.actorOf(props);
    }
}
