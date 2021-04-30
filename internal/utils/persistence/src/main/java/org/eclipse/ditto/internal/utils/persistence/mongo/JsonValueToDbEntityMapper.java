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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonNumber;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

/**
 * This class provides functions to map a specified {@link JsonObject} or
 * {@link JsonArray} to a {@link BsonDocument} or {@link BsonArray}. While mapping the keys of all JSON
 * objects can be revised by utilising a configurable function.
 */
@AllParametersAndReturnValuesAreNonnullByDefault
@Immutable
final class JsonValueToDbEntityMapper {

    private final Function<String, String> jsonKeyNameReviser;

    private JsonValueToDbEntityMapper(final Function<String, String> theJsonKeyNameReviser) {
        jsonKeyNameReviser = checkNotNull(theJsonKeyNameReviser, "JSON key name reviser");
    }

    /**
     * Returns a Function for mapping a {@link JsonObject} to a {@link BsonDocument}.
     *
     * @param jsonKeyNameReviser is used to revise the json key names of JSON objects.
     * @return the function.
     * @throws NullPointerException if {@code jsonKeyNameReviser} is {@code null}.
     */
    public static Function<JsonObject, BsonDocument> forJsonObject(final Function<String, String> jsonKeyNameReviser) {
        final JsonValueToDbEntityMapper mapper = new JsonValueToDbEntityMapper(jsonKeyNameReviser);
        return mapper::mapJsonObjectToBsonDocument;
    }

    /**
     * Returns a Function for mapping a {@link JsonArray} to a  {@link BsonArray}.
     *
     * @param jsonKeyNameReviser is used to revise the json key names of JSON objects.
     * @return the function.
     * @throws NullPointerException if {@code jsonKeyNameReviser} is {@code null}.
     */
    public static Function<JsonArray, BsonArray> forJsonArray(final Function<String, String> jsonKeyNameReviser) {
        final JsonValueToDbEntityMapper mapper = new JsonValueToDbEntityMapper(jsonKeyNameReviser);
        return mapper::mapJsonArrayToBsonArray;
    }

    private String reviseKeyName(final JsonKey jsonKey) {
        return jsonKeyNameReviser.apply(jsonKey.toString());
    }

    /**
     * Maps the specified JsonObject to a {@link BsonDocument} which can be stored in MongoDB.
     *
     * @param jsonObject the object to be mapped.
     * @return {@code jsonObject} as BsonDocument.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    private BsonDocument mapJsonObjectToBsonDocument(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object to be mapped");

        final BsonDocument result = new BsonDocument();
        jsonObject.forEach(jsonField -> result.put(reviseKeyName(jsonField.getKey()),
                mapJsonValueToBsonValue(jsonField.getValue())));
        return result;
    }

    /**
     * Maps the specified JsonArray to a {@link BsonArray} which can be stored in MongoDB.
     *
     * @param jsonArray the array to be mapped.
     * @return {@code jsonArray} as BsonArray.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     */
    private BsonArray mapJsonArrayToBsonArray(final JsonArray jsonArray) {
        checkNotNull(jsonArray, "JSON array to be mapped");

        final BsonArray result = new BsonArray();
        jsonArray.forEach(jsonValue -> result.add(mapJsonValueToBsonValue(jsonValue)));
        return result;
    }

    /**
     * Maps the specified JsonValue to a Java Object which can be stored in MongoDB.
     *
     * @param jsonValue the value to be mapped.
     * @return {@code jsonValue} as Java Object which can be stored in MongoDB or {@code null}.
     * @throws NullPointerException if {@code jsonValue} is {@code null}.
     */
    @Nullable
    private Object mapJsonValueToJavaObject(final JsonValue jsonValue) {
        checkNotNull(jsonValue, "JSON value to be mapped");

        final Object result;
        if (jsonValue.isNull()) {
            result = null;
        } else if (jsonValue.isString()) {
            result = jsonValue.asString();
        } else if (jsonValue.isNumber()) {
            result = mapJsonNumberToJavaNumber(jsonValue);
        } else if (jsonValue.isObject()) {
            result = mapJsonObjectToBsonDocument(jsonValue.asObject());
        } else if (jsonValue.isArray()) {
            result = mapJsonArrayToBsonArray(jsonValue.asArray());
        } else if (jsonValue.isBoolean()) {
            result = jsonValue.asBoolean();
        } else {
            result = null;
        }

        return result;
    }

    /**
     * Maps the specified JsonValue to a Java Object which can be stored in MongoDB.
     *
     * @param jsonValue the value to be mapped.
     * @return {@code jsonValue} as Java Object which can be stored in MongoDB or {@code null}.
     * @throws NullPointerException if {@code jsonValue} is {@code null}.
     */
    @Nullable
    private BsonValue mapJsonValueToBsonValue(final JsonValue jsonValue) {
        checkNotNull(jsonValue, "JSON value to be mapped");

        final BsonValue result;
        if (jsonValue.isNull()) {
            result = BsonNull.VALUE;
        } else if (jsonValue.isString()) {
            result = new BsonString(jsonValue.asString());
        } else if (jsonValue.isNumber()) {
            result = mapJsonNumberToBsonNumber(jsonValue);
        } else if (jsonValue.isObject()) {
            result = mapJsonObjectToBsonDocument(jsonValue.asObject());
        } else if (jsonValue.isArray()) {
            result = mapJsonArrayToBsonArray(jsonValue.asArray());
        } else if (jsonValue.isBoolean()) {
            result = BsonBoolean.valueOf(jsonValue.asBoolean());
        } else {
            result = null;
        }

        return result;
    }

    private static Number mapJsonNumberToJavaNumber(final JsonValue jsonNumberValue) {
        final Number result;

        if (jsonNumberValue.isInt()) {
            result = jsonNumberValue.asInt();
        } else if (jsonNumberValue.isLong()) {
            result = jsonNumberValue.asLong();
        } else {
            result = jsonNumberValue.asDouble();
        }

        return result;
    }

    private static BsonNumber mapJsonNumberToBsonNumber(final JsonValue jsonNumberValue) {
        final BsonNumber result;

        if (jsonNumberValue.isInt()) {
            result = new BsonInt32(jsonNumberValue.asInt());
        } else if (jsonNumberValue.isLong()) {
            result = new BsonInt64(jsonNumberValue.asLong());
        } else {
            result = new BsonDouble(jsonNumberValue.asDouble());
        }

        return result;
    }

}
