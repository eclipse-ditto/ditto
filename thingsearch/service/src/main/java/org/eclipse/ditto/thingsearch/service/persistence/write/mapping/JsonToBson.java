/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import java.util.Map;
import java.util.stream.Stream;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.thingsearch.service.common.util.KeyEscapeUtil;

/**
 * Converts JSON to BSON.
 */
public final class JsonToBson implements JsonInternalVisitor<BsonValue> {

    /**
     * Converts a JSON value to a BSON value.
     *
     * @param jsonValue the JSON value.
     * @return the BSON value.
     */
    public static BsonValue convert(final JsonValue jsonValue) {
        return new JsonToBson().eval(jsonValue);
    }

    @Override
    public BsonValue nullValue() {
        return BsonNull.VALUE;
    }

    @Override
    public BsonValue bool(final boolean value) {
        return BsonBoolean.valueOf(value);
    }

    @Override
    public BsonValue string(final String value) {
        return new BsonString(value);
    }

    @Override
    public BsonValue number(final JsonNumber value) {
        return value.isInt()
                ? new BsonInt32(value.asInt())
                : value.isLong()
                ? new BsonInt64(value.asLong())
                : new BsonDouble(value.asDouble());
    }

    @Override
    public BsonValue array(final Stream<BsonValue> values) {
        final BsonArray bsonArray = new BsonArray();
        values.forEach(bsonArray::add);
        return bsonArray;
    }

    @Override
    public BsonValue object(final Stream<Map.Entry<String, BsonValue>> values) {
        final BsonDocument bsonDocument = new BsonDocument();
        values.forEach(entry -> bsonDocument.append(KeyEscapeUtil.escape(entry.getKey()), entry.getValue()));
        return bsonDocument;
    }
}
