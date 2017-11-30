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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

/**
 * This class provides functions to map a specified {@link org.eclipse.ditto.json.JsonObject} or
 * {@link org.eclipse.ditto.json.JsonArray} to a {@link com.mongodb.DBObject}. While mapping the keys of all JSON
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
     * Returns a Function for mapping a {@link org.eclipse.ditto.json.JsonObject} to a
     * {@link com.mongodb.BasicDBObject}.
     *
     * @param jsonKeyNameReviser is used to revise the json key names of JSON objects.
     * @return the function.
     * @throws NullPointerException if {@code jsonKeyNameReviser} is {@code null}.
     */
    public static Function<JsonObject, BasicDBObject> forJsonObject(final Function<String, String> jsonKeyNameReviser) {
        final JsonValueToDbEntityMapper mapper = new JsonValueToDbEntityMapper(jsonKeyNameReviser);
        return mapper::mapJsonObjectToBasicDBObject;
    }

    /**
     * Returns a Function for mapping a {@link org.eclipse.ditto.json.JsonArray} to a
     * {@link com.mongodb.BasicDBList}.
     *
     * @param jsonKeyNameReviser is used to revise the json key names of JSON objects.
     * @return the function.
     * @throws NullPointerException if {@code jsonKeyNameReviser} is {@code null}.
     */
    public static Function<JsonArray, BasicDBList> forJsonArray(final Function<String, String> jsonKeyNameReviser) {
        final JsonValueToDbEntityMapper mapper = new JsonValueToDbEntityMapper(jsonKeyNameReviser);
        return mapper::mapJsonArrayToBasicDBList;
    }

    /**
     * Maps the specified JsonObject to a {@link com.mongodb.BasicDBObject} which can be stored in MongoDB.
     *
     * @param jsonObject the object to be mapped.
     * @return {@code jsonObject} as BasicDBObject.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    private BasicDBObject mapJsonObjectToBasicDBObject(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object to be mapped");

        final BasicDBObject result = new BasicDBObject(jsonObject.getSize());
        jsonObject.forEach(jsonField -> result.put(reviseKeyName(jsonField.getKey()),
                mapJsonValueToJavaObject(jsonField.getValue())));
        return result;
    }

    private String reviseKeyName(final JsonKey jsonKey) {
        return jsonKeyNameReviser.apply(jsonKey.toString());
    }

    /**
     * Maps the specified JsonArray to a {@link com.mongodb.BasicDBList} which can be stored in MongoDB.
     *
     * @param jsonArray the array to be mapped.
     * @return {@code jsonArray} as BasicDBList.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     */
    private BasicDBList mapJsonArrayToBasicDBList(final JsonArray jsonArray) {
        checkNotNull(jsonArray, "JSON array to be mapped");

        final BasicDBList result = new BasicDBList();
        jsonArray.forEach(jsonValue -> result.add(mapJsonValueToJavaObject(jsonValue)));
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
            result = mapJsonObjectToBasicDBObject(jsonValue.asObject());
        } else if (jsonValue.isArray()) {
            result = mapJsonArrayToBasicDBList(jsonValue.asArray());
        } else if (jsonValue.isBoolean()) {
            result = jsonValue.asBoolean();
        } else {
            result = null;
        }

        return result;
    }

    private static Number mapJsonNumberToJavaNumber(final JsonValue jsonNumberValue) {
        Number result;
        if (isDouble(jsonNumberValue)) {
            result = jsonNumberValue.asDouble();
        } else {
            try {
                result = jsonNumberValue.asInt();
            } catch (final NumberFormatException e) {
                result = jsonNumberValue.asLong();
            }
        }

        return result;
    }

    private static boolean isDouble(final JsonValue jsonNumberValue) {
        final String s = jsonNumberValue.toString();
        return 0 <= s.indexOf('.');
    }

}
