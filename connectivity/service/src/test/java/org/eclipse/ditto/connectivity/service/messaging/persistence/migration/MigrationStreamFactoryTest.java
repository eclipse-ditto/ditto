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
package org.eclipse.ditto.connectivity.service.messaging.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.InMemoryPersistedPayloadStore.JournalEntry;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.InMemoryPersistedPayloadStore.Snapshot;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.InMemoryPersistedPayloadStore.StreamCall;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.json.JsonObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for {@link MigrationStreamFactory}: the full pointers handed to the store are the configured pointers
 * prefixed the way the payloads have them, the store is asked to continue behind the last processed entry, and
 * throttling is applied only when enabled.
 */
public final class MigrationStreamFactoryTest {

    private static final List<String> POINTERS = List.of("/uri", "/credentials/password");

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("MigrationStreamFactoryTest");
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void snapshotPointersAreTheConfiguredPointers() {
        assertThat(MigrationStreamFactory.requiredFieldPointers("", POINTERS)).isEqualTo(POINTERS);
    }

    @Test
    public void journalPointersArePrefixedWithTheEntityType() {
        assertThat(MigrationStreamFactory.requiredFieldPointers("connection", POINTERS))
                .containsExactly("/connection/uri", "/connection/credentials/password");
        // the same prefixing JsonFieldsEncryptor applies: a prefix with a leading slash is taken as it is
        assertThat(MigrationStreamFactory.requiredFieldPointers("/connection", POINTERS))
                .containsExactly("/connection/uri", "/connection/credentials/password");
    }

    @Test
    public void snapshotStreamContinuesBehindTheLastProcessedSnapshot() throws Exception {
        final Snapshot snapshot = new Snapshot("s-2", "connection:two", JsonObject.empty());
        final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore().withSnapshots(snapshot);
        final MigrationProgress progress = new MigrationProgress().withLastSnapshot("s-1", "connection:one");

        final Source<PersistedSnapshot, NotUsed> source =
                MigrationStreamFactory.buildSnapshotStream(store, progress, POINTERS, 25, 0);

        assertThat(store.snapshotStreamCalls).containsExactly(new StreamCall("s-1", POINTERS, 25));
        assertThat(store.journalStreamCalls).isEmpty();
        assertThat(source.runWith(Sink.seq(), actorSystem).toCompletableFuture().get(5, TimeUnit.SECONDS))
                .containsExactly(snapshot);
    }

    @Test
    public void journalStreamContinuesBehindTheLastProcessedEntry() throws Exception {
        final JournalEntry entry = new JournalEntry("j-2", "connection:two", List.of());
        final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore().withJournalEntries(entry);
        final MigrationProgress progress = new MigrationProgress().withLastJournal("j-1", "connection:one");

        final Source<PersistedJournalEntry, NotUsed> source =
                MigrationStreamFactory.buildJournalStream(store, progress, POINTERS, 25, 0);

        assertThat(store.journalStreamCalls).containsExactly(
                new StreamCall("j-1", List.of("/connection/uri", "/connection/credentials/password"), 25));
        assertThat(store.snapshotStreamCalls).isEmpty();
        assertThat(source.runWith(Sink.seq(), actorSystem).toCompletableFuture().get(5, TimeUnit.SECONDS))
                .containsExactly(entry);
    }

    @Test
    public void aFreshStartStreamsFromTheBeginning() {
        final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore();

        MigrationStreamFactory.buildSnapshotStream(store, new MigrationProgress(), POINTERS, 10, 0);
        MigrationStreamFactory.buildJournalStream(store, new MigrationProgress(), POINTERS, 10, 0);

        assertThat(store.snapshotStreamCalls).containsExactly(new StreamCall(null, POINTERS, 10));
        assertThat(store.journalStreamCalls).containsExactly(
                new StreamCall(null, List.of("/connection/uri", "/connection/credentials/password"), 10));
    }

    @Test
    public void throttlingIsNotAppliedWhenDisabled() {
        final Source<Integer, NotUsed> source = Source.range(1, 3);

        assertThat(MigrationStreamFactory.applyThrottling(source, 0)).isSameAs(source);
        assertThat(MigrationStreamFactory.applyThrottling(source, -1)).isSameAs(source);
    }

    @Test
    public void throttlingKeepsEveryElement() throws Exception {
        final Source<Integer, NotUsed> throttled = MigrationStreamFactory.applyThrottling(Source.range(1, 3), 600);

        assertThat(throttled).isNotSameAs(Source.range(1, 3));
        assertThat(throttled.runWith(Sink.seq(), actorSystem).toCompletableFuture().get(10, TimeUnit.SECONDS))
                .containsExactly(1, 2, 3);
    }
}
