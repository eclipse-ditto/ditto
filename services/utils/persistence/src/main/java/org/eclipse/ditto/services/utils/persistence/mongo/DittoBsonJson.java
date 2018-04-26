/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.persistence.mongo;


import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * This is a specialized MongoDB BSON converter which additionally takes care that in JSON keys dots "." and dollar
 * signs "$" are replaced with their unicode representations in the {@link #parse(org.eclipse.ditto.json.JsonObject)}
 * {@link #parse(org.eclipse.ditto.json.JsonArray)} function and vice versa in
 * the {@link #serialize(com.mongodb.DBObject)} function.
 */
public final class DittoBsonJson {

    private static final DittoBsonJson INSTANCE = DittoBsonJson.newInstance();

    private final Function<JsonObject, BasicDBObject> jsonObjectToBasicDBObjectMapper;
    private final Function<JsonArray, BasicDBList> jsonArrayToBasicDBListMapper;
    private final Function<BasicDBObject, JsonObject> basicDBObjectToJsonObjectMapper;
    private final Function<BasicDBList, JsonArray> basicDBListToJsonObjectMapper;

    /*
     * Inhibit instantiation of this utility class.
     */
    private DittoBsonJson(final Function<JsonObject, BasicDBObject> jsonObjectToBasicDBObjectMapper,
            final Function<JsonArray, BasicDBList> jsonArrayToBasicDBListMapper,
            final Function<BasicDBObject, JsonObject> basicDBObjectToJsonObjectMapper,
            final Function<BasicDBList, JsonArray> basicDBListToJsonObjectMapper) {

        this.jsonObjectToBasicDBObjectMapper = jsonObjectToBasicDBObjectMapper;
        this.jsonArrayToBasicDBListMapper = jsonArrayToBasicDBListMapper;
        this.basicDBObjectToJsonObjectMapper = basicDBObjectToJsonObjectMapper;
        this.basicDBListToJsonObjectMapper = basicDBListToJsonObjectMapper;
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

        return new DittoBsonJson(JsonValueToDbEntityMapper.forJsonObject(jsonToMongoDbKeyNameReviser),
                JsonValueToDbEntityMapper.forJsonArray(jsonToMongoDbKeyNameReviser),
                BasicDBObjectToJsonObjectMapper.getInstance(jsonKeyNameReviser),
                BasicDBListToJsonObjectMapper.getInstance(jsonKeyNameReviser));
    }

    /**
     * Serializes the specified {@link com.mongodb.DBObject} to Json, applying replacement of "special" characters
     * {@code "$"} and {@code "."}.
     *
     * @param object the DBObject to be serialized.
     * @return the DBObject serialized as JsonValue.
     * @throws NullPointerException if {@code object} is {@code null}.
     * @throws IllegalArgumentException if {@code object} is not an instance of {@link com.mongodb.BasicDBObject}.
     */
    public JsonValue serialize(final DBObject object) {
        checkNotNull(object, "DBObject to be serialized");
        if (object instanceof BasicDBObject) {
            return serialize((BasicDBObject) object);
        } else if (object instanceof BasicDBList) {
            return serialize((BasicDBList) object);
        } else {
            throw new IllegalArgumentException("Can only serialize BasicDBObjects");
        }
    }

    /**
     * Serializes the specified {@link com.mongodb.BasicDBObject} to Json, applying replacement of "special" characters
     * {@code "$"} and {@code "."}.
     *
     * @param basicDBObject the BasicDBObject to be serialized.
     * @return the BasicDBObject serialized as JsonValue.
     * @throws NullPointerException if {@code basicDBObject} is {@code null}.
     */
    public JsonObject serialize(final BasicDBObject basicDBObject) {
        return basicDBObjectToJsonObjectMapper.apply(checkNotNull(basicDBObject, "BasicDBObject to be serialized"));
    }

    /**
     * Serializes the specified {@link com.mongodb.BasicDBList} to Json, applying replacement of "special" characters
     * {@code "$"} and {@code "."}.
     *
     * @param basicDBList the BasicDBList to be serialized.
     * @return the BasicDBList serialized as JsonValue.
     * @throws NullPointerException if {@code basicDBList} is {@code null}.
     */
    public JsonArray serialize(final BasicDBList basicDBList) {
        return basicDBListToJsonObjectMapper.apply(checkNotNull(basicDBList, "BasicDBList to be serialized"));
    }

    /**
     * Parses the specified {@link org.eclipse.ditto.json.JsonObject} into an {@link com.mongodb.DBObject}.
     *
     * @param jsonObject the JSON object to be parsed.
     * @return the parsed JSON object as DBObject.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public DBObject parse(final JsonObject jsonObject) {
        return jsonObjectToBasicDBObjectMapper.apply(jsonObject);
    }

    /**
     * Parses the passed in {@link org.eclipse.ditto.json.JsonArray} into an {@link com.mongodb.DBObject}.
     *
     * @param jsonArray the JSON array to be parsed.
     * @return the parsed JSON array as DBObject.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     */
    public DBObject parse(final JsonArray jsonArray) {
        return jsonArrayToBasicDBListMapper.apply(jsonArray);
    }

}

