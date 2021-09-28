/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDbPointer;
import org.bson.BsonDecimal128;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonJavaScript;
import org.bson.BsonJavaScriptWithScope;
import org.bson.BsonObjectId;
import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.BsonSymbol;
import org.bson.BsonTimestamp;
import org.bson.BsonValue;

/**
 * Package-private visitor that visits primitive values only
 */
interface BsonPrimitiveValueVisitor<T> {

    T binary(BsonBinary value);

    T bool(BsonBoolean value);

    T dateTime(BsonDateTime value);

    T dbPointer(BsonDbPointer value);

    T decimal128(BsonDecimal128 value);

    T bsonDouble(BsonDouble value);

    T int32(BsonInt32 value);

    T int64(BsonInt64 value);

    T javascript(BsonJavaScript value);

    T javascriptWithScope(BsonJavaScriptWithScope value);

    T maxKey();

    T minKey();

    T bsonNull();

    T objectId(BsonObjectId value);

    T regularExpression(BsonRegularExpression value);

    T string(BsonString value);

    T symbol(BsonSymbol value);

    T timestamp(BsonTimestamp value);

    T undefined();

    default T evalPrimitive(final BsonValue value) {
        switch (value.getBsonType()) {
            case DOUBLE:
                return bsonDouble(value.asDouble());
            case STRING:
                return string(value.asString());
            case BINARY:
                return binary(value.asBinary());
            case UNDEFINED:
                return undefined();
            case OBJECT_ID:
                return objectId(value.asObjectId());
            case BOOLEAN:
                return bool(value.asBoolean());
            case DATE_TIME:
                return dateTime(value.asDateTime());
            case NULL:
                return bsonNull();
            case REGULAR_EXPRESSION:
                return regularExpression(value.asRegularExpression());
            case DB_POINTER:
                return dbPointer(value.asDBPointer());
            case JAVASCRIPT:
                return javascript(value.asJavaScript());
            case SYMBOL:
                return symbol(value.asSymbol());
            case JAVASCRIPT_WITH_SCOPE:
                return javascriptWithScope(value.asJavaScriptWithScope());
            case INT32:
                return int32(value.asInt32());
            case TIMESTAMP:
                return timestamp(value.asTimestamp());
            case INT64:
                return int64(value.asInt64());
            case DECIMAL128:
                return decimal128(value.asDecimal128());
            case MIN_KEY:
                return minKey();
            case MAX_KEY:
                return maxKey();
            case END_OF_DOCUMENT:
            case DOCUMENT:
            case ARRAY:
            default:
                throw new IllegalArgumentException("Unsupported primitive BsonValue " + value);
        }
    }
}
