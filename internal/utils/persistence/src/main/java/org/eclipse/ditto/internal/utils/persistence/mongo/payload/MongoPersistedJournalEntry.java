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
package org.eclipse.ditto.internal.utils.persistence.mongo.payload;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.json.JsonObject;

/**
 * A {@link PersistedJournalEntry} over a document of a {@code <entityType>_journal} collection as the
 * {@code pekko-persistence-mongodb} plugin writes it: the cursor is the document's {@code _id} as a string, the pid its
 * {@code pid} field, and the event payloads are the {@code p} documents of the entries of its {@code events} array,
 * in array order; an event without a {@code p} field (an empty event of the persistence actor) contributes no payload.
 * {@link #withEventPayloads(List)} returns a wrapper over a shallow copy of the document whose {@code events} array
 * holds, per event, either the stored event document itself (no payload, or a payload equal to the stored one) or a
 * shallow copy of it with the new payload put in place of {@code p} — so only what changed is re-encoded, and the
 * rewritten document replaces the stored one by {@code _id} field for field.
 *
 * @since 4.0.0
 */
public final class MongoPersistedJournalEntry implements PersistedJournalEntry {

    /** The array of events of one journal document. */
    static final String EVENTS_FIELD = "events";

    /** The payload of one event. */
    static final String PAYLOAD_FIELD = "p";

    private final Document document;

    private MongoPersistedJournalEntry(final Document document) {
        this.document = document;
    }

    /**
     * Wraps a journal document.
     *
     * @param document the journal document as read from the collection.
     * @return the wrapper.
     * @throws NullPointerException if {@code document} is {@code null}.
     */
    static MongoPersistedJournalEntry of(final Document document) {
        return new MongoPersistedJournalEntry(checkNotNull(document, "document"));
    }

    /**
     * @return the wrapped document, the one that is written back by a rewrite.
     */
    Document document() {
        return document;
    }

    @Override
    public String cursor() {
        return document.get(PayloadDocuments.ID_FIELD).toString();
    }

    @Override
    public String pid() {
        return document.getString(PayloadDocuments.PID_FIELD);
    }

    @Override
    public List<JsonObject> eventPayloads() {
        final List<Document> events = storedEvents();
        final List<JsonObject> payloads = new ArrayList<>(events.size());
        for (final Document event : events) {
            final Document payload = event.get(PAYLOAD_FIELD, Document.class);
            if (payload != null) {
                payloads.add(PayloadDocuments.toJson(payload));
            }
        }
        return Collections.unmodifiableList(payloads);
    }

    @Override
    public PersistedJournalEntry withEventPayloads(final List<JsonObject> payloads) {
        checkNotNull(payloads, "payloads");
        final List<Document> events = storedEvents();
        final List<Document> updatedEvents = new ArrayList<>(events.size());
        int next = 0;
        for (final Document event : events) {
            final Document storedPayload = event.get(PAYLOAD_FIELD, Document.class);
            if (storedPayload == null) {
                updatedEvents.add(event);
            } else {
                if (next >= payloads.size()) {
                    throw payloadCountMismatch(payloads.size());
                }
                final JsonObject payload = payloads.get(next++);
                if (payload.equals(PayloadDocuments.toJson(storedPayload))) {
                    updatedEvents.add(event);
                } else {
                    final Document updatedEvent = new Document(event);
                    updatedEvent.put(PAYLOAD_FIELD, PayloadDocuments.toDocument(payload));
                    updatedEvents.add(updatedEvent);
                }
            }
        }
        if (next != payloads.size()) {
            throw payloadCountMismatch(payloads.size());
        }
        final Document updated = new Document(document);
        updated.put(EVENTS_FIELD, updatedEvents);
        return new MongoPersistedJournalEntry(updated);
    }

    private List<Document> storedEvents() {
        @Nullable final List<Document> events = document.getList(EVENTS_FIELD, Document.class);
        return events == null ? List.of() : events;
    }

    private IllegalArgumentException payloadCountMismatch(final int given) {
        return new IllegalArgumentException("The journal entry <" + cursor() + "> holds " + storedPayloadCount() +
                " event payload(s), but " + given + " were given to replace them.");
    }

    /**
     * Counts the events that have a payload without rendering any payload as JSON, so that a payload which cannot be
     * rendered does not hide the count mismatch it is reported for.
     */
    private long storedPayloadCount() {
        return storedEvents().stream()
                .filter(event -> event.get(PAYLOAD_FIELD) != null)
                .count();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MongoPersistedJournalEntry that = (MongoPersistedJournalEntry) o;
        return Objects.equals(document, that.document);
    }

    @Override
    public int hashCode() {
        return Objects.hash(document);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "_id=" + document.get(PayloadDocuments.ID_FIELD) +
                ", pid=" + document.get(PayloadDocuments.PID_FIELD) +
                ']';
    }

}
