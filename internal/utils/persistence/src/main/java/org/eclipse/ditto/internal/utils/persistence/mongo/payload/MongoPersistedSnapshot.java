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

import java.util.Objects;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.json.JsonObject;

/**
 * A {@link PersistedSnapshot} over a document of a {@code <entityType>_snaps} collection as the
 * {@code pekko-persistence-mongodb} plugin writes it: the cursor is the document's {@code _id} as a string, the pid
 * its {@code pid} field, and the payload its {@code s2} document. {@link #withPayload(JsonObject)} returns a wrapper
 * over a shallow copy of the document with the new payload put in place of {@code s2} (same field order), so the
 * rewritten document replaces the stored one by {@code _id} field for field.
 *
 * @since 4.0.0
 */
public final class MongoPersistedSnapshot implements PersistedSnapshot {

    /** The field holding the serialized snapshot. */
    static final String PAYLOAD_FIELD = "s2";

    private final Document document;

    private MongoPersistedSnapshot(final Document document) {
        this.document = document;
    }

    /**
     * Wraps a snapshot document.
     *
     * @param document the snapshot document as read from the collection.
     * @return the wrapper.
     * @throws NullPointerException if {@code document} is {@code null}.
     */
    static MongoPersistedSnapshot of(final Document document) {
        return new MongoPersistedSnapshot(checkNotNull(document, "document"));
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
    public JsonObject payload() {
        final Document payload = document.get(PAYLOAD_FIELD, Document.class);
        if (payload == null) {
            throw new IllegalStateException(
                    "The snapshot <" + cursor() + "> holds no <" + PAYLOAD_FIELD + "> payload.");
        }
        return PayloadDocuments.toJson(payload);
    }

    @Override
    public PersistedSnapshot withPayload(final JsonObject payload) {
        checkNotNull(payload, "payload");
        final Document updated = new Document(document);
        updated.put(PAYLOAD_FIELD, PayloadDocuments.toDocument(payload));
        return new MongoPersistedSnapshot(updated);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MongoPersistedSnapshot that = (MongoPersistedSnapshot) o;
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
