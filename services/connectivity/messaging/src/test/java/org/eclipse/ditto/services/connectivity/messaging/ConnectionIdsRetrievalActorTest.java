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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityInternalErrorException;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceActor;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityErrorResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveAllConnectionIds;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveAllConnectionIdsResponse;
import org.junit.AfterClass;
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

    private static final List<String> JOURNAL_IDS =
            List.of(pid("connection-1"), pid("connection-2"), pid("connection-3"));

    private static final List<Document> SNAPSHOT_IDS =
            // connection-3 is a duplicate by intention
            List.of(doc("connection-3"), doc("connection-4"), doc("connection-5"));

    private static final DittoHeaders DITTO_HEADERS =
            DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build();

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Test
    public void testRetrieveAllConnectionIds() {
        new TestKit(actorSystem) {{
            final Props props =
                    ConnectionIdsRetrievalActor.props(() -> Source.from(JOURNAL_IDS), () -> Source.from(SNAPSHOT_IDS));

            final ActorRef reconnectActor = actorSystem.actorOf(props);
            reconnectActor.tell(RetrieveAllConnectionIds.of(DITTO_HEADERS), getRef());

            final RetrieveAllConnectionIdsResponse response = expectMsgClass(RetrieveAllConnectionIdsResponse.class);
            assertThat(response.getAllConnectionIds()).isEqualTo(
                    Set.of("connection-1", "connection-2", "connection-3", "connection-4", "connection-5"));
        }};
    }

    @Test
    public void testNullExpectErrorResponse() {
        new TestKit(actorSystem) {{
            final Props props =
                    ConnectionIdsRetrievalActor.props(() -> Source.single(null), () -> Source.single(null));

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
}
