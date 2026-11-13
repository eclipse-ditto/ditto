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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.json.JsonObject;

/**
 * An in-memory {@link PersistedPayloadStore} for the encryption-migration tests: serves the configured snapshot and
 * journal sources, records every call (streams opened, entries written back, progress saved and deleted, close) and
 * can be told to fail its rewrites or its progress operations, or to hold {@link #deleteProgress()} until a gate
 * completes so a test can prove what happens before the first store I/O completes. The cursor and the required-field
 * pointers are recorded, not applied: the configured source is returned as is, so a test on this fake proves what the
 * actor asks the store for, not that the store skips entries (that is the real store's job and is tested there).
 */
final class InMemoryPersistedPayloadStore implements PersistedPayloadStore {

    /** A snapshot entry without a backend handle: the rewritten entry simply carries the new payload. */
    record Snapshot(String cursor, String pid, JsonObject payload) implements PersistedSnapshot {

        @Override
        public PersistedSnapshot withPayload(final JsonObject newPayload) {
            return new Snapshot(cursor, pid, newPayload);
        }
    }

    /** A journal entry without a backend handle: the rewritten entry simply carries the new payloads. */
    record JournalEntry(String cursor, String pid, List<JsonObject> eventPayloads) implements PersistedJournalEntry {

        @Override
        public PersistedJournalEntry withEventPayloads(final List<JsonObject> payloads) {
            if (payloads.size() != eventPayloads.size()) {
                throw new IllegalArgumentException(
                        "Expected " + eventPayloads.size() + " payload(s), got " + payloads.size());
            }
            return new JournalEntry(cursor, pid, List.copyOf(payloads));
        }
    }

    /** One call of {@link #streamSnapshots} or {@link #streamJournalEntries}. */
    record StreamCall(@Nullable String afterCursor, List<String> requiredFieldPointers, int batchSize) {}

    final List<StreamCall> snapshotStreamCalls = Collections.synchronizedList(new ArrayList<>());
    final List<StreamCall> journalStreamCalls = Collections.synchronizedList(new ArrayList<>());
    final List<List<PersistedSnapshot>> rewrittenSnapshots = Collections.synchronizedList(new ArrayList<>());
    final List<List<PersistedJournalEntry>> rewrittenJournalEntries = Collections.synchronizedList(new ArrayList<>());
    final List<JsonObject> savedProgressRecords = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger saveProgressAttempts = new AtomicInteger();
    final AtomicInteger deleteProgressCalls = new AtomicInteger();
    final AtomicBoolean closed = new AtomicBoolean();

    private final AtomicReference<JsonObject> progress = new AtomicReference<>();
    private volatile Source<PersistedSnapshot, NotUsed> snapshots = Source.empty();
    private volatile Source<PersistedJournalEntry, NotUsed> journalEntries = Source.empty();
    private volatile boolean failRewrites;
    private volatile boolean failProgressOperations;
    @Nullable
    private volatile CompletableFuture<Void> deleteProgressGate;

    InMemoryPersistedPayloadStore withSnapshots(final PersistedSnapshot... theSnapshots) {
        return withSnapshotSource(Source.from(Arrays.asList(theSnapshots)));
    }

    InMemoryPersistedPayloadStore withSnapshotSource(final Source<PersistedSnapshot, NotUsed> source) {
        snapshots = source;
        return this;
    }

    InMemoryPersistedPayloadStore withJournalEntries(final PersistedJournalEntry... entries) {
        journalEntries = Source.from(Arrays.asList(entries));
        return this;
    }

    InMemoryPersistedPayloadStore withSavedProgress(final JsonObject savedProgress) {
        progress.set(savedProgress);
        return this;
    }

    InMemoryPersistedPayloadStore failingRewrites() {
        failRewrites = true;
        return this;
    }

    InMemoryPersistedPayloadStore failingProgressOperations() {
        failProgressOperations = true;
        return this;
    }

    InMemoryPersistedPayloadStore holdingDeleteProgressUntil(final CompletableFuture<Void> gate) {
        deleteProgressGate = gate;
        return this;
    }

    Optional<JsonObject> savedProgress() {
        return Optional.ofNullable(progress.get());
    }

    @Override
    public Source<PersistedSnapshot, NotUsed> streamSnapshots(@Nullable final String afterCursor,
            final List<String> requiredFieldPointers, final int batchSize) {
        snapshotStreamCalls.add(new StreamCall(afterCursor, List.copyOf(requiredFieldPointers), batchSize));
        return snapshots;
    }

    @Override
    public Source<PersistedJournalEntry, NotUsed> streamJournalEntries(@Nullable final String afterCursor,
            final List<String> requiredFieldPointers, final int batchSize) {
        journalStreamCalls.add(new StreamCall(afterCursor, List.copyOf(requiredFieldPointers), batchSize));
        return journalEntries;
    }

    @Override
    public CompletionStage<Long> rewriteSnapshots(final List<PersistedSnapshot> theSnapshots) {
        rewrittenSnapshots.add(List.copyOf(theSnapshots));
        return rewriteResult(theSnapshots.size());
    }

    @Override
    public CompletionStage<Long> rewriteJournalEntries(final List<PersistedJournalEntry> entries) {
        rewrittenJournalEntries.add(List.copyOf(entries));
        return rewriteResult(entries.size());
    }

    @Override
    public CompletionStage<Optional<JsonObject>> loadProgress() {
        if (failProgressOperations) {
            return CompletableFuture.failedFuture(new IllegalStateException("progress unavailable"));
        }
        return CompletableFuture.completedFuture(savedProgress());
    }

    @Override
    public CompletionStage<Void> saveProgress(final JsonObject theProgress) {
        saveProgressAttempts.incrementAndGet();
        if (failProgressOperations) {
            return CompletableFuture.failedFuture(new IllegalStateException("progress unavailable"));
        }
        progress.set(theProgress);
        savedProgressRecords.add(theProgress);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteProgress() {
        deleteProgressCalls.incrementAndGet();
        if (failProgressOperations) {
            return CompletableFuture.failedFuture(new IllegalStateException("progress unavailable"));
        }
        progress.set(null);
        final CompletableFuture<Void> gate = deleteProgressGate;
        return gate != null ? gate : CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        closed.set(true);
    }

    private CompletionStage<Long> rewriteResult(final int written) {
        if (failRewrites) {
            return CompletableFuture.failedFuture(new IllegalStateException("rewrite failed"));
        }
        return CompletableFuture.completedFuture((long) written);
    }
}
