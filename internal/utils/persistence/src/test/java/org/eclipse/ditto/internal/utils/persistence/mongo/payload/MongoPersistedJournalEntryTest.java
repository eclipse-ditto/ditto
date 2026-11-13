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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link MongoPersistedJournalEntry}: the event payloads are the {@code p} documents of the
 * {@code events} array (events without one are skipped but kept), and {@code withEventPayloads} re-encodes only the
 * payloads that changed into a copy of the document, leaving events without a payload, unchanged payloads and the
 * original document exactly as they were — the behaviour of the connectivity encryption migration before the store
 * existed.
 */
public final class MongoPersistedJournalEntryTest {

    private static final ObjectId OBJECT_ID = new ObjectId("507f1f77bcf86cd799439012");

    @Test
    public void cursorAndPidAreReadFromTheDocument() {
        final MongoPersistedJournalEntry underTest = MongoPersistedJournalEntry.of(journalDocument());

        assertThat(underTest.cursor()).isEqualTo("507f1f77bcf86cd799439012");
        assertThat(underTest.pid()).isEqualTo("connection:mqtt-prod-sensor-01");
    }

    @Test
    public void eventPayloadsListsOnlyEventsWithAPayloadInStoredOrder() {
        final MongoPersistedJournalEntry underTest = MongoPersistedJournalEntry.of(journalDocument());

        final List<JsonObject> payloads = underTest.eventPayloads();

        assertThat(payloads).hasSize(2);
        assertThat(payloads.get(0).getValue("/connection/uri").map(v -> v.asString()))
                .contains("amqps://user:first@broker.example.com:5671");
        assertThat(payloads.get(1).getValue("/connection/uri").map(v -> v.asString()))
                .contains("amqps://user:third@broker.example.com:5671");
    }

    @Test
    public void eventPayloadsIsEmptyWithoutEvents() {
        final Document withoutEvents = journalDocument();
        withoutEvents.remove("events");
        final Document emptyEvents = journalDocument();
        emptyEvents.put("events", new ArrayList<Document>());

        assertThat(MongoPersistedJournalEntry.of(withoutEvents).eventPayloads()).isEmpty();
        assertThat(MongoPersistedJournalEntry.of(emptyEvents).eventPayloads()).isEmpty();
    }

    @Test
    public void eventPayloadsWithANonDocumentEventThrows() {
        final Document document = journalDocument();
        document.put("events", List.of("not an event document"));

        assertThatThrownBy(() -> MongoPersistedJournalEntry.of(document).eventPayloads())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void withEventPayloadsReplacesOnlyChangedPayloadsAndKeepsTheRest() {
        final Document original = journalDocument();
        final Document originalCopy = new Document(original);
        final List<Document> originalEvents = original.getList("events", Document.class);
        final MongoPersistedJournalEntry underTest = MongoPersistedJournalEntry.of(original);
        final List<JsonObject> payloads = underTest.eventPayloads();
        final JsonObject changedFirst = JsonFactory.newObjectBuilder()
                .set("type", "connectivity.events:connectionCreated")
                .set("connection", JsonFactory.newObjectBuilder()
                        .set("uri", "amqps://user:encrypted_first@broker.example.com:5671")
                        .build())
                .build();

        final PersistedJournalEntry rewritten = underTest.withEventPayloads(List.of(changedFirst, payloads.get(1)));

        // the original document and the original entry are unchanged
        assertThat(original).isEqualTo(originalCopy);
        assertThat(underTest.eventPayloads()).isEqualTo(payloads);

        final Document rewrittenDocument = ((MongoPersistedJournalEntry) rewritten).document();
        final List<Document> rewrittenEvents = rewrittenDocument.getList("events", Document.class);
        assertThat(rewrittenEvents).hasSize(3);
        // first event: a copy with the new payload encoded as Document.parse(DittoBsonJson.parse(json).toJson())
        final Document expectedFirstPayload = Document.parse(DittoBsonJson.getInstance().parse(changedFirst).toJson());
        assertThat(rewrittenEvents.get(0).get("p")).isEqualTo(expectedFirstPayload);
        assertThat(rewrittenEvents.get(0).getLong("sn")).isEqualTo(1L);
        assertThat(rewrittenEvents.get(0).getString("manifest")).isEqualTo("connectivity.events:connectionCreated");
        assertThat(List.copyOf(rewrittenEvents.get(0).keySet())).isEqualTo(List.copyOf(originalEvents.get(0).keySet()));
        // the event without a payload and the event whose payload did not change are the very same documents
        assertThat(rewrittenEvents.get(1)).isSameAs(originalEvents.get(1));
        assertThat(rewrittenEvents.get(2)).isSameAs(originalEvents.get(2));
        // everything else is kept
        assertThat(rewrittenDocument.get("_id")).isEqualTo(OBJECT_ID);
        assertThat(rewrittenDocument.getString("pid")).isEqualTo("connection:mqtt-prod-sensor-01");
        assertThat(rewrittenDocument.getLong("from")).isEqualTo(1L);
        assertThat(List.copyOf(rewrittenDocument.keySet())).isEqualTo(List.copyOf(original.keySet()));
        assertThat(rewritten.cursor()).isEqualTo(underTest.cursor());
        assertThat(rewritten.eventPayloads().get(0)).isEqualTo(changedFirst);
    }

    @Test
    public void withEventPayloadsRejectsAWrongNumberOfPayloads() {
        final MongoPersistedJournalEntry underTest = MongoPersistedJournalEntry.of(journalDocument());
        final JsonObject payload = underTest.eventPayloads().get(0);

        assertThatIllegalArgumentException().isThrownBy(() -> underTest.withEventPayloads(List.of(payload)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.withEventPayloads(List.of(payload, payload, payload)));
    }

    @Test
    public void withEventPayloadsReportsTheCountMismatchEvenIfAStoredPayloadCannotBeRendered() {
        final Document document = journalDocument();
        // the third event's payload has no BSON codec: rendering it as JSON throws
        document.getList("events", Document.class).get(2).put("p", new Document("unencodable", new Object()));
        final MongoPersistedJournalEntry underTest = MongoPersistedJournalEntry.of(document);
        final JsonObject payload = JsonFactory.newObjectBuilder().set("type", "connectivity.events:x").build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.withEventPayloads(List.of(payload)))
                .withMessageContaining("holds 2 event payload(s)")
                .withMessageContaining("but 1 were given");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MongoPersistedJournalEntry.class)
                .withNonnullFields("document")
                .verify();
    }

    @Test
    public void toStringNamesTheIdAndThePid() {
        final MongoPersistedJournalEntry underTest = MongoPersistedJournalEntry.of(journalDocument());

        assertThat(underTest.toString())
                .contains("507f1f77bcf86cd799439012")
                .contains("connection:mqtt-prod-sensor-01");
    }

    /**
     * A journal document as the pekko-persistence-mongodb plugin writes it: three events, the second one without a
     * payload (an empty event of the persistence actor).
     */
    private static Document journalDocument() {
        final List<Document> events = new ArrayList<>();
        events.add(event(1L, "amqps://user:first@broker.example.com:5671"));
        events.add(new Document("v", 1).append("pid", "connection:mqtt-prod-sensor-01").append("sn", 2L)
                .append("manifest", "persistence-actor-internal:empty-event"));
        events.add(event(3L, "amqps://user:third@broker.example.com:5671"));
        return new Document("_id", OBJECT_ID)
                .append("pid", "connection:mqtt-prod-sensor-01")
                .append("from", 1L)
                .append("to", 3L)
                .append("events", events);
    }

    private static Document event(final long sequenceNumber, final String uri) {
        return new Document("v", 1)
                .append("pid", "connection:mqtt-prod-sensor-01")
                .append("sn", sequenceNumber)
                .append("manifest", "connectivity.events:connectionCreated")
                .append("p", new Document("type", "connectivity.events:connectionCreated")
                        .append("connection", new Document("uri", uri)));
    }
}
