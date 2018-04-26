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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * This abstract function maps a specified {@link DBObject} to a {@link JsonValue}.
 * While mapping, the keys of all JSON objects can be revised by utilising a configurable function.
 */
abstract class AbstractBasicDBMapper<T extends DBObject, J extends JsonValue> implements Function<T, J> {

    final Function<String, String> jsonKeyNameReviser;

    AbstractBasicDBMapper(final Function<String, String> theJsonKeyNameReviser) {
        jsonKeyNameReviser = checkNotNull(theJsonKeyNameReviser, "The JSON key name reviser");
    }

    static JsonObject mapBasicDBObjectToJsonObject(final BasicDBObject basicDBObject,
            final Function<String, String> jsonKeyNameReviser) {
        return basicDBObject.entrySet()
                .stream()
                .map(e -> JsonFactory.newField(reviseKeyName(e.getKey(), jsonKeyNameReviser),
                        mapJavaObjectToJsonValue(e.getValue(), jsonKeyNameReviser)))
                .collect(JsonCollectors.fieldsToObject());
    }

    static JsonArray mapBasicDBListToJsonArray(final BasicDBList basicDBList,
            final Function<String, String> jsonKeyNameReviser) {
        return basicDBList.stream()
                .map(obj -> AbstractBasicDBMapper.mapJavaObjectToJsonValue(obj, jsonKeyNameReviser))
                .collect(JsonCollectors.valuesToArray());
    }

    private static JsonKey reviseKeyName(final String jsonKeyName, final Function<String, String> jsonKeyNameReviser) {
        return JsonFactory.newKey(jsonKeyNameReviser.apply(jsonKeyName));
    }

    private static JsonValue mapJavaObjectToJsonValue(@Nullable final Object object,
            final Function<String, String> jsonKeyNameReviser) {
        final JsonValue result;
        if (null == object) {
            result = JsonFactory.nullLiteral();
        } else if (object instanceof String) {
            result = JsonFactory.newValue((String) object);
        } else if (object instanceof Number) {
            result = mapJavaNumberToJsonNumber((Number) object);
        } else if (object instanceof BasicDBObject) {
            result = mapBasicDBObjectToJsonObject((BasicDBObject) object, jsonKeyNameReviser);
        } else if (object instanceof BasicDBList) {
            result = mapBasicDBListToJsonArray((BasicDBList) object, jsonKeyNameReviser);
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

}
