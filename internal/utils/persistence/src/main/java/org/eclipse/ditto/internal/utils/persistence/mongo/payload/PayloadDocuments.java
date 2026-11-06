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

import org.bson.BsonDocument;
import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonObject;

import com.mongodb.MongoClientSettings;

/**
 * The two conversions between a stored payload {@link Document} and its {@link JsonObject} rendering, exactly as the
 * connectivity encryption migration performed them before the payload store existed: BSON &rarr; JSON through
 * {@link DittoBsonJson#serialize(BsonDocument)} (which decodes the key names the MongoDB writer escaped), and JSON
 * &rarr; BSON through {@link DittoBsonJson#parse(JsonObject)} followed by {@code Document.parse(bson.toJson())}.
 */
final class PayloadDocuments {

    static final String ID_FIELD = "_id";
    static final String PID_FIELD = "pid";

    private PayloadDocuments() {
        throw new AssertionError();
    }

    /**
     * Renders a stored payload document as JSON.
     *
     * @param payload the stored payload.
     * @return the payload as JSON.
     */
    static JsonObject toJson(final Document payload) {
        final BsonDocument bson = payload.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
        return DittoBsonJson.getInstance().serialize(bson);
    }

    /**
     * Encodes a payload JSON into the document to store.
     *
     * @param payload the payload as JSON.
     * @return the document to store.
     */
    static Document toDocument(final JsonObject payload) {
        final BsonDocument bson = DittoBsonJson.getInstance().parse(payload);
        return Document.parse(bson.toJson());
    }

}
