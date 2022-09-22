/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectivityInternalErrorException;
import org.eclipse.ditto.connectivity.service.config.ConnectionIdsRetrievalConfig;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceActor;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityErrorResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveAllConnectionIds;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveAllConnectionIdsResponse;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionCreated;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ConnectionIdsRetrievalActor}.
 */
public final class ConnectionIdsRetrievalActorTest {

    private static final List<Document> JOURNAL_ENTRIES =
            List.of(document(pid("connection-1"), ConnectionCreated.TYPE),
                    document(pid("connection-2"), ConnectionCreated.TYPE),
                    document(pid("connection-3"), ConnectionDeleted.TYPE),
                    document(pid("connection-4"), ConnectionCreated.TYPE));

    private static final List<Document> SNAPSHOT_IDS =
            // connection-3 and connection-4 is a duplicate by intention
            List.of(doc("connection-3"), doc("connection-4"), doc("connection-5"));

    private static final DittoHeaders DITTO_HEADERS =
            DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();

    private static ActorSystem actorSystem;
    private static final int BATCH_SIZE = 10;
    private MongoReadJournal mongoReadJournal;
    private ConnectionIdsRetrievalConfig connectionIdsRetrievalConfig;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Before
    public void setup() {
        mongoReadJournal = mock(MongoReadJournal.class);
        connectionIdsRetrievalConfig = mock(ConnectionIdsRetrievalConfig.class);
        when(connectionIdsRetrievalConfig.getReadJournalBatchSize()).thenReturn(BATCH_SIZE);
        when(connectionIdsRetrievalConfig.getReadSnapshotBatchSize()).thenReturn(BATCH_SIZE);
    }

    @Test
    public void testRetrieveAllConnectionIds() {
        new TestKit(actorSystem) {{
            when(mongoReadJournal.getLatestJournalEntries(eq(BATCH_SIZE), any(), any()))
                    .thenReturn(Source.from(JOURNAL_ENTRIES));
            when(mongoReadJournal.getNewestSnapshotsAbove(anyString(), eq(BATCH_SIZE), any()))
                    .thenReturn(Source.from(SNAPSHOT_IDS));
            final Props props =
                    ConnectionIdsRetrievalActor.props(mongoReadJournal, connectionIdsRetrievalConfig);

            final ActorRef reconnectActor = actorSystem.actorOf(props);
            reconnectActor.tell(RetrieveAllConnectionIds.of(DITTO_HEADERS), getRef());

            final RetrieveAllConnectionIdsResponse response = expectMsgClass(RetrieveAllConnectionIdsResponse.class);
            assertThat(response.getAllConnectionIds()).isEqualTo(
                    Set.of("connection-1", "connection-2", "connection-4", "connection-5"));
        }};
    }

    @Test
    public void internalErrorShouldResultInErrorResponse() {
        new TestKit(actorSystem) {{
            when(mongoReadJournal.getLatestJournalEntries(eq(BATCH_SIZE), any(), any()))
                    .thenThrow(new NullPointerException("expected"));
            when(mongoReadJournal.getNewestSnapshotsAbove(anyString(), eq(BATCH_SIZE), any()))
                    .thenThrow(new NullPointerException("expected"));
            final Props props =
                    ConnectionIdsRetrievalActor.props(mongoReadJournal, connectionIdsRetrievalConfig);

            final ActorRef reconnectActor = actorSystem.actorOf(props);
            reconnectActor.tell(RetrieveAllConnectionIds.of(DITTO_HEADERS), getRef());

            final ConnectivityErrorResponse error = expectMsgClass(ConnectivityErrorResponse.class);
            assertThat(error.getDittoRuntimeException()).isInstanceOf(ConnectivityInternalErrorException.class);
        }};
    }

    private static Document doc(final String id) {
        return new Document("_id", pid(id));
    }

    private static String pid(final String id) {
        return ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + id;
    }

    private static Document document(final String pid, final String manifest) {
        return new Document()
                .append("pid", pid)
                .append("manifest", manifest);
    }

}
