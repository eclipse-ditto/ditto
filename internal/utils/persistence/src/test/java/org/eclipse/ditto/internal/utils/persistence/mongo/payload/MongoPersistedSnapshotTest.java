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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link MongoPersistedSnapshot}: the cursor is the {@code _id} as a string, the payload is the
 * {@code s2} document rendered through {@link DittoBsonJson}, and {@code withPayload} puts the new payload into a
 * copy of the document exactly as the connectivity encryption migration did before the store existed
 * ({@code Document.parse(DittoBsonJson.parse(json).toJson())}), leaving the original document untouched.
 */
public final class MongoPersistedSnapshotTest {

    private static final ObjectId OBJECT_ID = new ObjectId("507f1f77bcf86cd799439011");

    @Test
    public void cursorIsTheObjectIdAsHexString() {
        final MongoPersistedSnapshot underTest = MongoPersistedSnapshot.of(snapshotDocument(OBJECT_ID));

        assertThat(underTest.cursor()).isEqualTo("507f1f77bcf86cd799439011");
    }

    @Test
    public void cursorIsAStringIdAsItIs() {
        final MongoPersistedSnapshot underTest = MongoPersistedSnapshot.of(snapshotDocument("connection:plain-id"));

        assertThat(underTest.cursor()).isEqualTo("connection:plain-id");
    }

    @Test
    public void pidIsReadFromTheDocument() {
        final MongoPersistedSnapshot underTest = MongoPersistedSnapshot.of(snapshotDocument(OBJECT_ID));

        assertThat(underTest.pid()).isEqualTo("connection:mqtt-prod-sensor-01");
    }

    @Test
    public void payloadIsTheS2DocumentAsJson() {
        final MongoPersistedSnapshot underTest = MongoPersistedSnapshot.of(snapshotDocument(OBJECT_ID));

        final JsonObject payload = underTest.payload();

        assertThat(payload.getValue("/uri").map(v -> v.asString()))
                .contains("amqps://user:secret@broker.example.com:5671");
        assertThat(payload.getValue("/credentials/password").map(v -> v.asString())).contains("mypassword");
        assertThat(payload.getValue("/__lifecycle").map(v -> v.asString())).contains("ACTIVE");
        // a BSON int64 arrives as a long JSON number (DittoBsonJson's mapping, as before)
        assertThat(payload.getValue("/_revision").map(v -> v.asLong())).contains(42L);
    }

    @Test
    public void payloadWithoutS2Throws() {
        final Document document = snapshotDocument(OBJECT_ID);
        document.remove("s2");
        final MongoPersistedSnapshot underTest = MongoPersistedSnapshot.of(document);

        assertThatIllegalStateException().isThrownBy(underTest::payload)
                .withMessageContaining("507f1f77bcf86cd799439011");
    }

    @Test
    public void payloadWithANonDocumentS2Throws() {
        final Document document = snapshotDocument(OBJECT_ID);
        document.put("s2", List.of(new Document("uri", "amqps://a:b@c")));
        final MongoPersistedSnapshot underTest = MongoPersistedSnapshot.of(document);

        assertThatThrownBy(underTest::payload).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void withPayloadPutsTheNewS2IntoACopyAndLeavesTheOriginalUntouched() {
        final Document original = snapshotDocument(OBJECT_ID);
        final Document originalCopy = new Document(original);
        final MongoPersistedSnapshot underTest = MongoPersistedSnapshot.of(original);
        final JsonObject newPayload = JsonFactory.newObjectBuilder()
                .set("uri", "amqps://user:encrypted_abc@broker.example.com:5671")
                .set("credentials", JsonFactory.newObjectBuilder().set("password", "encrypted_xyz").build())
                .set("_revision", 42L)
                .build();

        final PersistedSnapshot rewritten = underTest.withPayload(newPayload);

        // the original document (and the original entry) are unchanged
        assertThat(original).isEqualTo(originalCopy);
        assertThat(underTest.payload().getValue("/credentials/password").map(v -> v.asString()))
                .contains("mypassword");

        // the rewritten one keeps every other field, including the id, and holds the new s2 as before
        final Document rewrittenDocument = ((MongoPersistedSnapshot) rewritten).document();
        final Document expectedS2 = Document.parse(DittoBsonJson.getInstance().parse(newPayload).toJson());
        assertThat(rewrittenDocument.get("s2")).isEqualTo(expectedS2);
        assertThat(rewrittenDocument.get("_id")).isEqualTo(OBJECT_ID);
        assertThat(rewrittenDocument.getString("pid")).isEqualTo("connection:mqtt-prod-sensor-01");
        assertThat(rewrittenDocument.getLong("sn")).isEqualTo(7L);
        assertThat(List.copyOf(rewrittenDocument.keySet())).isEqualTo(List.copyOf(original.keySet()));
        assertThat(rewritten.cursor()).isEqualTo(underTest.cursor());
        assertThat(rewritten.pid()).isEqualTo(underTest.pid());
        assertThat(rewritten.payload().getValue("/credentials/password").map(v -> v.asString()))
                .contains("encrypted_xyz");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MongoPersistedSnapshot.class)
                .withNonnullFields("document")
                .verify();
    }

    @Test
    public void toStringNamesTheIdAndThePid() {
        final MongoPersistedSnapshot underTest = MongoPersistedSnapshot.of(snapshotDocument(OBJECT_ID));

        assertThat(underTest.toString())
                .contains("507f1f77bcf86cd799439011")
                .contains("connection:mqtt-prod-sensor-01");
    }

    private static Document snapshotDocument(final Object id) {
        return new Document("_id", id)
                .append("pid", "connection:mqtt-prod-sensor-01")
                .append("sn", 7L)
                .append("ts", 1234567890L)
                .append("s2", new Document("uri", "amqps://user:secret@broker.example.com:5671")
                        .append("credentials", new Document("password", "mypassword"))
                        .append("__lifecycle", "ACTIVE")
                        .append("_revision", 42L));
    }
}
