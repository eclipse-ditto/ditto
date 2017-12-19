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

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

/**
 * This function maps a specified {@link com.mongodb.BasicDBObject} to a {@link org.eclipse.ditto.json.JsonObject}.
 * While mapping, the keys of all JSON objects can be revised by utilising a configurable function.
 */
@AllParametersAndReturnValuesAreNonnullByDefault
@Immutable
final class BasicDBObjectToJsonObjectMapper implements Function<BasicDBObject, JsonObject> {

    private final Function<String, String> jsonKeyNameReviser;

    private BasicDBObjectToJsonObjectMapper(final Function<String, String> theJsonKeyNameReviser) {
        jsonKeyNameReviser = checkNotNull(theJsonKeyNameReviser, "The JSON key name reviser");
    }

    /**
     * Returns an instance of {@code BasicDBObjectToJsonObjectMapper}.
     *
     * @param jsonKeyNameReviser is used to revise the json key names of JSON objects.
     * @return the instance.
     * @throws NullPointerException if {@code jsonKeyNameReviser} is {@code null}.
     */
    public static BasicDBObjectToJsonObjectMapper getInstance(final Function<String, String> jsonKeyNameReviser) {
        return new BasicDBObjectToJsonObjectMapper(jsonKeyNameReviser);
    }

    @Override
    public JsonObject apply(final BasicDBObject basicDBObject) {
        return mapBasicDBObjectToJsonObject(checkNotNull(basicDBObject, "BasicDBObject to be mapped"));
    }

    private JsonObject mapBasicDBObjectToJsonObject(final BasicDBObject basicDBObject) {
        return basicDBObject.entrySet()
                .stream()
                .map(e -> JsonFactory.newField(reviseKeyName(e.getKey()), mapJavaObjectToJsonValue(e.getValue())))
                .collect(JsonCollectors.fieldsToObject());
    }

    private JsonKey reviseKeyName(final String jsonKeyName) {
        return JsonFactory.newKey(jsonKeyNameReviser.apply(jsonKeyName));
    }

    private JsonValue mapJavaObjectToJsonValue(@Nullable final Object object) {
        final JsonValue result;
        if (null == object) {
            result = JsonFactory.nullLiteral();
        } else if (object instanceof String) {
            result = JsonFactory.newValue((String) object);
        } else if (object instanceof Number) {
            result = mapJavaNumberToJsonNumber((Number) object);
        } else if (object instanceof BasicDBObject) {
            result = mapBasicDBObjectToJsonObject((BasicDBObject) object);
        } else if (object instanceof BasicDBList) {
            result = mapBasicDBListToJsonArray((BasicDBList) object);
        } else if (object instanceof Boolean) {
            result = JsonFactory.newValue((Boolean) object);
        } else {
            result = JsonFactory.nullLiteral();
        }

        return result;
    }

    private static JsonValue mapJavaNumberToJsonNumber(final Number number) {
        final JsonValue result;
        final Class<? extends Number> numberClass = number.getClass();
        if (Integer.class.isAssignableFrom(numberClass)) {
            result = JsonFactory.newValue(number.intValue());
        } else if (Double.class.isAssignableFrom(numberClass)) {
            result = JsonFactory.newValue(number.doubleValue());
        } else {
            result = JsonFactory.newValue(number.longValue());
        }

        return result;
    }

    private JsonValue mapBasicDBListToJsonArray(final BasicDBList basicDBList) {
        return basicDBList.stream()
                .map(this::mapJavaObjectToJsonValue)
                .collect(JsonCollectors.valuesToArray());
    }

}
