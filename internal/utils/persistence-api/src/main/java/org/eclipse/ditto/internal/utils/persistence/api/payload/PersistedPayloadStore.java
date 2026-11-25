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
package org.eclipse.ditto.internal.utils.persistence.api.payload;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.json.JsonObject;

/**
 * Backend-neutral access to the persisted payloads of one entity type — its snapshots and journal entries rendered as
 * JSON — for a bulk rewrite, plus a small JSON progress record so that a rewrite which stops half-way can resume.
 * <p>
 * A consumer such as a bulk re-encryption of stored secrets streams the entries whose payloads hold at least one of
 * the fields it works on, rewrites those fields, writes the entries back and keeps its progress in the record. The
 * store knows nothing about what the consumer does with a payload. It only knows how the active backend stores one
 * (for example, on MongoDB the {@code s2} snapshot document and the {@code events[].p} event payloads written by the
 * {@code pekko-persistence-mongodb} plugin) and how to render it as a {@link JsonObject} and back.
 * <p>
 * The streams are Pekko Streams {@link Source}s the caller materializes; the single operations complete
 * asynchronously. Nothing blocks. Every stream is resumable through an opaque, backend-defined <em>cursor</em>. Entries
 * are emitted in a stable, backend-defined order; the cursor of an entry identifies its position in that order, so
 * passing the cursor of the last processed entry as {@code afterCursor} continues behind it.
 * <p>
 * A store owns the backend client it may have built. It is obtained from
 * {@link org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider#payloadStore(String)} in the
 * using actor's {@code preStart}, used by that one actor instance only and {@link #close() closed} in its
 * {@code postStop}.
 *
 * @since 4.0.0
 */
public interface PersistedPayloadStore extends Closeable {

    /**
     * Streams the snapshots whose payload holds at least one of the given fields, starting after {@code afterCursor}.
     * Entries are emitted in a stable, backend-defined order; the cursor of an entry identifies its position in that
     * order, so passing the cursor of the last processed entry as {@code afterCursor} continues behind it.
     *
     * @param afterCursor the {@link PersistedSnapshot#cursor() cursor} of the last snapshot already processed, or
     * {@code null} to start from the beginning.
     * @param requiredFieldPointers the JSON pointers, relative to the snapshot payload JSON, of which at least one
     * must be present for a snapshot to be streamed (for a connection snapshot e.g. {@code /uri} and
     * {@code /credentials/password}); must not be empty.
     * @param batchSize how many snapshots the backend fetches per round trip.
     * @return the snapshots, each carrying its backend handle for {@link #rewriteSnapshots(List)}.
     * @throws NullPointerException if {@code requiredFieldPointers} is {@code null}.
     * @throws IllegalArgumentException if {@code requiredFieldPointers} is empty.
     */
    Source<PersistedSnapshot, NotUsed> streamSnapshots(@Nullable String afterCursor,
            List<String> requiredFieldPointers, int batchSize);

    /**
     * Streams the journal entries of which at least one event payload holds at least one of the given fields, starting
     * after {@code afterCursor}. Entries are emitted in a stable, backend-defined order; the cursor of an entry
     * identifies its position in that order, so passing the cursor of the last processed entry as {@code afterCursor}
     * continues behind it.
     *
     * @param afterCursor the {@link PersistedJournalEntry#cursor() cursor} of the last entry already processed, or
     * {@code null} to start from the beginning.
     * @param requiredFieldPointers the JSON pointers, relative to an event payload JSON, of which at least one must be
     * present in one of the entry's event payloads for the entry to be streamed (for a connection event e.g.
     * {@code /connection/uri} and {@code /connection/credentials/password}); must not be empty.
     * @param batchSize how many entries the backend fetches per round trip.
     * @return the journal entries, each carrying its backend handle for {@link #rewriteJournalEntries(List)}.
     * @throws NullPointerException if {@code requiredFieldPointers} is {@code null}.
     * @throws IllegalArgumentException if {@code requiredFieldPointers} is empty.
     */
    Source<PersistedJournalEntry, NotUsed> streamJournalEntries(@Nullable String afterCursor,
            List<String> requiredFieldPointers, int batchSize);

    /**
     * Writes the given (rewritten) snapshots back, each replacing the stored snapshot it was obtained from. Completes
     * with the number of entries durably written; fewer than {@code snapshots.size()} means a partial write. A backend
     * may apply the batch atomically, in which case the count is either {@code snapshots.size()} or the stage fails.
     * When the stage fails, nothing of the batch may be assumed written. An entry deleted between streaming and
     * rewriting updates nothing and lowers the count; the stage does not fail for it.
     *
     * @param snapshots the snapshots to write back, as returned by {@link PersistedSnapshot#withPayload(JsonObject)}.
     * @return the number of snapshots durably written.
     * @throws NullPointerException if {@code snapshots} is {@code null}.
     * @throws IllegalArgumentException if a snapshot was not produced by this store, i.e. neither streamed by it nor
     * derived from a streamed one through {@link PersistedSnapshot#withPayload(JsonObject)}.
     */
    CompletionStage<Long> rewriteSnapshots(List<PersistedSnapshot> snapshots);

    /**
     * Writes the given (rewritten) journal entries back, each replacing the stored entry it was obtained from.
     * Completes with the number of entries durably written; fewer than {@code entries.size()} means a partial write. A
     * backend may apply the batch atomically, in which case the count is either {@code entries.size()} or the stage
     * fails. When the stage fails, nothing of the batch may be assumed written. An entry deleted between streaming
     * and rewriting updates nothing and lowers the count; the stage does not fail for it.
     *
     * @param entries the entries to write back, as returned by {@link PersistedJournalEntry#withEventPayloads(List)}.
     * @return the number of entries durably written.
     * @throws NullPointerException if {@code entries} is {@code null}.
     * @throws IllegalArgumentException if an entry was not produced by this store, i.e. neither streamed by it nor
     * derived from a streamed one through {@link PersistedJournalEntry#withEventPayloads(List)}.
     */
    CompletionStage<Long> rewriteJournalEntries(List<PersistedJournalEntry> entries);

    /**
     * Loads the progress record of this entity type's rewrite.
     *
     * @return the record as it was last {@link #saveProgress(JsonObject) saved}, or empty if none is saved.
     */
    CompletionStage<Optional<JsonObject>> loadProgress();

    /**
     * Saves the progress record of this entity type's rewrite, replacing a previously saved one. The record is a flat
     * JSON object of strings, numbers, booleans and nulls; the backend stores integral numbers as 64-bit integers.
     *
     * @param progress the record to save.
     * @return completes when the record is saved; fails with an {@link IllegalArgumentException} if the record is not
     * a flat JSON object of scalars or carries a key the backend reserves for itself.
     * @throws NullPointerException if {@code progress} is {@code null}.
     */
    CompletionStage<Void> saveProgress(JsonObject progress);

    /**
     * Deletes the saved progress record of this entity type's rewrite, if any.
     *
     * @return completes when the record is gone.
     */
    CompletionStage<Void> deleteProgress();

    /**
     * Releases the backend client this store built, if any. The store must not be used afterwards.
     */
    @Override
    void close();

}
