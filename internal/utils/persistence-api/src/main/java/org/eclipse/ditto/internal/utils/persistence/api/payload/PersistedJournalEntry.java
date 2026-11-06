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

import java.util.List;

import org.eclipse.ditto.json.JsonObject;

/**
 * A persisted journal entry as streamed by {@link PersistedPayloadStore#streamJournalEntries}: its position in the
 * stream, its persistence id and the payloads of the events it holds, rendered as JSON. A backend may persist several
 * events as one entry (the events of one atomic write); an entry therefore carries a list of event payloads. The entry
 * keeps the backend's handle on the stored entry, so that a {@link #withEventPayloads(List) rewritten} entry can be
 * written back in place of the stored one by {@link PersistedPayloadStore#rewriteJournalEntries}.
 *
 * @since 4.0.0
 */
public interface PersistedJournalEntry {

    /**
     * Returns the opaque, backend-defined cursor of this entry: what a caller records as "last processed" and passes
     * back as {@code afterCursor} to continue the stream behind it.
     *
     * @return the cursor.
     */
    String cursor();

    /**
     * @return the persistence id of the entity the entry belongs to.
     */
    String pid();

    /**
     * Returns the payloads of the events this entry holds, in stored order, one element per event that has a payload.
     * Events without a payload are not represented and survive a rewrite unchanged.
     *
     * @return the event payloads; empty if the entry holds no event with a payload.
     * @throws RuntimeException if a stored payload cannot be rendered as JSON; a caller that processes entries one by
     * one catches it and counts the entry as failed.
     */
    List<JsonObject> eventPayloads();

    /**
     * Returns this entry with other event payloads: same cursor, same pid, same backend handle, the given payloads
     * replacing the stored ones in order. The list must have exactly as many elements as {@link #eventPayloads()}. A
     * backend MAY leave an event whose payload is equal to the stored one untouched in its representation (the MongoDB
     * store does, so only changed events are re-encoded). This entry is left unchanged.
     *
     * @param payloads the new event payloads, one per event that has a payload, in stored order.
     * @return the entry with the new payloads, ready for {@link PersistedPayloadStore#rewriteJournalEntries}.
     * @throws NullPointerException if {@code payloads} is {@code null}.
     * @throws IllegalArgumentException if {@code payloads} has not exactly as many elements as
     * {@link #eventPayloads()}.
     */
    PersistedJournalEntry withEventPayloads(List<JsonObject> payloads);

}
