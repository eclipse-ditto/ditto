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

import java.time.Instant;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNumber;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * This abstract function maps a specified {@link BsonDocument} to a {@link JsonValue}.
 * While mapping, the keys of all JSON objects can be revised by utilising a configurable function.
 */
abstract class AbstractBasicDBMapper<T, J extends JsonValue> implements Function<T, J> {

    final Function<String, String> jsonKeyNameReviser;

    AbstractBasicDBMapper(final  Function<String, String> theJsonKeyNameReviser) {
        jsonKeyNameReviser = checkNotNull(theJsonKeyNameReviser, "The JSON key name reviser");
    }

    static JsonObject mapBsonDocumentToJsonObject(final BsonDocument bsonDocument,
            final Function<String, String> jsonKeyNameReviser) {
        return bsonDocument.entrySet()
                .stream()
                .map(e -> JsonFactory.newField(reviseKeyName(e.getKey(), jsonKeyNameReviser),
                        mapBsonValueToJsonValue(e.getValue(), jsonKeyNameReviser)))
                .collect(JsonCollectors.fieldsToObject());
    }

    static JsonArray mapBsonArrayToJsonArray(final BsonArray bsonArray,
            final Function<String, String> jsonKeyNameReviser) {
        return bsonArray.stream()
                .map(obj -> AbstractBasicDBMapper.mapBsonValueToJsonValue(obj, jsonKeyNameReviser))
                .collect(JsonCollectors.valuesToArray());
    }

    private static JsonKey reviseKeyName(final String jsonKeyName, final Function<String, String> jsonKeyNameReviser) {
        return JsonFactory.newKey(jsonKeyNameReviser.apply(jsonKeyName));
    }

    private static JsonValue mapBsonValueToJsonValue(@Nullable final BsonValue bsonValue,
            final Function<String, String> jsonKeyNameReviser) {
        final JsonValue result;
        if (bsonValue == null || bsonValue.isNull()) {
            result = JsonFactory.nullLiteral();
        } else if (bsonValue.isString()) {
            result = JsonFactory.newValue(bsonValue.asString().getValue());
        } else if (bsonValue.isNumber()) {
            result = mapBsonNumberToJsonNumber(bsonValue.asNumber());
        } else if (bsonValue.isDocument()) {
            result = mapBsonDocumentToJsonObject(bsonValue.asDocument(), jsonKeyNameReviser);
        } else if (bsonValue.isArray()) {
            result = mapBsonArrayToJsonArray(bsonValue.asArray(), jsonKeyNameReviser);
        } else if (bsonValue.isBoolean()) {
            result = JsonFactory.newValue(bsonValue.asBoolean().getValue());
        } else if (bsonValue.isTimestamp()) {
            final Instant instant = Instant.ofEpochSecond(bsonValue.asTimestamp().getTime());
            result = JsonFactory.newValue(instant.toString());
        } else {
            result = JsonFactory.nullLiteral();
        }
        return result;
    }

    private static JsonValue mapBsonNumberToJsonNumber(final BsonNumber asNumber) {
        final JsonValue result;
        if (asNumber.isDouble()) {
            result = JsonFactory.newValue(asNumber.asDouble().doubleValue());
        } else if (asNumber.isInt64()) {
            result = JsonFactory.newValue(asNumber.asInt64().longValue());
        } else {
            result = JsonFactory.newValue(asNumber.asInt32().intValue());
        }
        return result;
    }

}
