/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo;


import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * This is a specialized MongoDB BSON converter which additionally takes care that in JSON keys dots "." and dollar
 * signs "$" are replaced with their unicode representations in the {@link #parse(JsonObject)} {@link #parse(JsonArray)}
 * function and vice versa in the {@link #serialize(BsonValue)} function.
 */
public final class DittoBsonJson {

    private static final DittoBsonJson INSTANCE = DittoBsonJson.newInstance();

    private final Function<JsonObject, BsonDocument> jsonObjectToBsonDocumentMapper;
    private final Function<JsonArray, BsonArray> jsonArrayToBsonArrayMapper;
    private final Function<BsonDocument, JsonObject> bsonDocumentToJsonObjectMapper;
    private final Function<BsonArray, JsonArray> bsonArrayToJsonObjectMapper;

    /*
     * Inhibit instantiation of this utility class.
     */
    private DittoBsonJson(final Function<JsonObject, BsonDocument> jsonObjectToBsonDocumentMapper,
            final Function<JsonArray, BsonArray> jsonArrayToBsonArrayMapper,
            final Function<BsonDocument, JsonObject> bsonDocumentToJsonObjectMapper,
            final Function<BsonArray, JsonArray> bsonArrayToJsonObjectMapper) {

        this.jsonObjectToBsonDocumentMapper = jsonObjectToBsonDocumentMapper;
        this.jsonArrayToBsonArrayMapper = jsonArrayToBsonArrayMapper;
        this.bsonDocumentToJsonObjectMapper = bsonDocumentToJsonObjectMapper;
        this.bsonArrayToJsonObjectMapper = bsonArrayToJsonObjectMapper;
    }

    /**
     * Returns an instance of {@code DittoBsonJSON}.
     *
     * @return the instance.
     */
    public static DittoBsonJson getInstance() {
        return INSTANCE;
    }

    private static DittoBsonJson newInstance() {
        final KeyNameReviser jsonToMongoDbKeyNameReviser = KeyNameReviser.escapeProblematicPlainChars();
        final KeyNameReviser jsonKeyNameReviser = KeyNameReviser.decodeKnownUnicodeChars();

        return new DittoBsonJson(
                JsonValueToDbEntityMapper.forJsonObject(jsonToMongoDbKeyNameReviser),
                JsonValueToDbEntityMapper.forJsonArray(jsonToMongoDbKeyNameReviser),
                BsonDocumentToJsonObjectMapper.getInstance(jsonKeyNameReviser),
                BsonArrayToJsonObjectMapper.getInstance(jsonKeyNameReviser));
    }

    /**
     * Serializes the specified {@link BsonValue} to Json, applying replacement of "special" characters {@code "$"} and
     * {@code "."}.
     *
     * @param bsonValue the BsonValue to be serialized.
     * @return the BsonValue serialized as JsonValue.
     * @throws NullPointerException if {@code bsonValue} is {@code null}.
     * @throws IllegalArgumentException if {@code bsonValue} is not an instance of {@link BsonDocument} or {@link
     * BsonArray}.
     */
    public JsonValue serialize(final BsonValue bsonValue) {
        checkNotNull(bsonValue, "BsonValue to be serialized");
        if (bsonValue instanceof BsonDocument) {
            return serialize((BsonDocument) bsonValue);
        } else if (bsonValue instanceof BsonArray) {
            return serialize((BsonArray) bsonValue);
        } else {
            throw new IllegalArgumentException("Can only serialize BsonDocument or BsonArray");
        }
    }

    /**
     * Serializes the specified {@link BsonDocument} to Json, applying replacement of "special" characters {@code "$"}
     * and {@code "."}.
     *
     * @param bsonDocument the BsonDocument to be serialized.
     * @return the BsonDocument serialized as JsonValue.
     * @throws NullPointerException if {@code bsonDocument} is {@code null}.
     */
    public JsonObject serialize(final BsonDocument bsonDocument) {
        return bsonDocumentToJsonObjectMapper.apply(checkNotNull(bsonDocument, "BsonDocument to be serialized"));
    }

    /**
     * Serializes the specified {@link BsonArray} to Json, applying replacement of "special" characters {@code "$"} and
     * {@code "."}.
     *
     * @param bsonArray the BsonArray to be serialized.
     * @return the BsonArray serialized as JsonValue.
     * @throws NullPointerException if {@code bsonArray} is {@code null}.
     */
    public JsonArray serialize(final BsonArray bsonArray) {
        return bsonArrayToJsonObjectMapper.apply(checkNotNull(bsonArray, "BsonArray to be serialized"));
    }

    /**
     * Parses the specified {@link JsonObject} into an {@link BsonDocument}.
     *
     * @param jsonObject the JSON object to be parsed.
     * @return the parsed JSON object as BsonDocument.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public BsonDocument parse(final JsonObject jsonObject) {
        return jsonObjectToBsonDocumentMapper.apply(jsonObject);
    }

    /**
     * Parses the passed in {@link JsonArray} into an {@link BsonArray}.
     *
     * @param jsonArray the JSON array to be parsed.
     * @return the parsed JSON array as BsonArray.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     */
    public BsonArray parse(final JsonArray jsonArray) {
        return jsonArrayToBsonArrayMapper.apply(jsonArray);
    }

}

