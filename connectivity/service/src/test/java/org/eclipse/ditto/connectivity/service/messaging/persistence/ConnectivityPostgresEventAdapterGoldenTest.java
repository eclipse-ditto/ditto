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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.After;
import org.junit.Test;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Pins the journal rows of {@link ConnectivityPostgresEventAdapter} to {@code golden/journal/connection-postgres.json},
 * written with field encryption on and key {@link ConnectionJournalEncryptionFixture#K_OLD}: every event is written
 * with the stored manifest and tags, rows with an encrypted connection never contain the plain secret (their payload
 * is not compared, the AES-GCM IV is random), and every stored row replays to the recovered event with the key it was
 * written with and after a key rotation that keeps it as the old key. The events are those of
 * {@link ConnectivityMongoEventAdapterGoldenTest}.
 */
public final class ConnectivityPostgresEventAdapterGoldenTest {

    static final String FIXTURE = "connection-postgres.json";

    private final List<ActorSystem> systems = new ArrayList<>();

    @After
    public void shutdownSystems() {
        systems.forEach(TestKit::shutdownActorSystem);
    }

    @Test
    public void writesTheFixtureRows() throws IOException {
        final ConnectivityPostgresEventAdapter underTest = adapter(ConnectionJournalEncryptionFixture.K_OLD, null);
        final List<JsonObject> rows = rows();
        final List<ConnectivityEvent<?>> written = ConnectivityMongoEventAdapterGoldenTest.writtenEvents();
        assertThat(rows).hasSameSizeAs(written);

        for (int i = 0; i < rows.size(); i++) {
            final JsonObject row = rows.get(i);
            final ConnectivityEvent<?> event = written.get(i);
            assertThat(underTest.manifest(event)).isEqualTo(manifest(row));
            final Tagged tagged = (Tagged) underTest.toJournal(event);
            assertThat(CollectionConverters.asJava(tagged.tags())).isEqualTo(tags(row));
            if (payload(row).contains("connection")) {
                assertThat((String) tagged.payload()).doesNotContain(ConnectivityMongoEventAdapterGoldenTest.SECRET);
            } else {
                assertThat(tagged.payload()).isEqualTo(payload(row).toString());
            }
        }
    }

    @Test
    public void replaysTheFixtureRowsToEqualEvents() throws IOException {
        assertThat(replay(adapter(ConnectionJournalEncryptionFixture.K_OLD, null)))
                .containsExactlyElementsOf(ConnectivityMongoEventAdapterGoldenTest.recoveredEvents());
    }

    @Test
    public void replaysWithTheKeyTheRowWasWrittenWith() throws IOException {
        assertReplaysToPlainUri(adapter(ConnectionJournalEncryptionFixture.K_OLD, null));
    }

    @Test
    public void replaysAfterAKeyRotationWithItsKeyAsTheOldKey() throws IOException {
        assertReplaysToPlainUri(
                adapter(ConnectionJournalEncryptionFixture.K_NEW, ConnectionJournalEncryptionFixture.K_OLD));
    }

    @Test
    public void oneArgumentFromJournalJsonDecrypts() throws IOException {
        final ConnectivityPostgresEventAdapter underTest = adapter(ConnectionJournalEncryptionFixture.K_OLD, null);

        final List<Object> events =
                CollectionConverters.asJava(underTest.fromJournalJson(payload(rows().get(0))).events());

        assertThat(events).containsExactly(ConnectivityMongoEventAdapterGoldenTest.recoveredEvents().get(0));
    }

    @Test
    public void constructionLogsTheEncryptionFlagUnderTheAdapterLogger() {
        ConnectivityMongoEventAdapterGoldenTest.assertConstructionLogsTheEncryptionFlagUnder(
                ConnectivityPostgresEventAdapter.class.getName(),
                () -> adapter(ConnectionJournalEncryptionFixture.K_OLD, null));
    }

    private static void assertReplaysToPlainUri(final ConnectivityPostgresEventAdapter underTest) throws IOException {
        final List<Object> events = replay(underTest);
        assertThat(events).containsExactlyElementsOf(ConnectivityMongoEventAdapterGoldenTest.recoveredEvents());
        ConnectivityMongoEventAdapterGoldenTest.assertPlainUri(events);
    }

    private static List<Object> replay(final ConnectivityPostgresEventAdapter underTest) throws IOException {
        final List<Object> events = new ArrayList<>();
        for (final JsonObject row : rows()) {
            final List<Object> rowEvents = CollectionConverters.asJava(
                    underTest.fromJournal(payload(row).toString(), manifest(row)).events());
            assertThat(rowEvents).hasSize(1);
            events.addAll(rowEvents);
        }
        return events;
    }

    private ConnectivityPostgresEventAdapter adapter(final String key, @Nullable final String oldKey) {
        final ExtendedActorSystem system = ConnectivityMongoEventAdapterGoldenTest.actorSystem(
                "ConnectivityPostgresEventAdapterGoldenTest", key, oldKey);
        systems.add(system);
        return new ConnectivityPostgresEventAdapter(system);
    }

    private static List<JsonObject> rows() throws IOException {
        return JsonObject.of(ConnectivityMongoEventAdapterGoldenTest.readFixture(FIXTURE))
                .getValueOrThrow(JsonFieldDefinition.ofJsonArray("rows"))
                .stream()
                .map(JsonValue::asObject)
                .toList();
    }

    private static String manifest(final JsonObject row) {
        return row.getValueOrThrow(JsonFieldDefinition.ofString("manifest"));
    }

    private static Set<String> tags(final JsonObject row) {
        return row.getValueOrThrow(JsonFieldDefinition.ofJsonArray("tags")).stream()
                .map(JsonValue::asString)
                .collect(Collectors.toSet());
    }

    private static JsonObject payload(final JsonObject row) {
        return row.getValueOrThrow(JsonFieldDefinition.ofJsonObject("payload"));
    }

}
