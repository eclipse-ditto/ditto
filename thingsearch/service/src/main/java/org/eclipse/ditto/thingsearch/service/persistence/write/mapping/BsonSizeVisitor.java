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

import java.nio.charset.StandardCharsets;

import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDbPointer;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
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
import org.eclipse.ditto.json.JsonPointer;

/**
 * Package-private visitor that descends into BSON objects and BSON arrays.
 */
final class BsonSizeVisitor implements BsonValueVisitor<Integer>, BsonPrimitiveValueVisitor<Integer> {

    private static final int WORD = 4;
    private static final int DWORD = 8;
    private static final int TWORD = 12;
    private static final int QWORD = 16;

    @Override
    public Integer primitive(final JsonPointer key, final BsonValue value) {
        return evalPrimitive(value);
    }

    @Override
    public Integer array(final JsonPointer key, final BsonArray value) {
        return value.stream().mapToInt(this::eval).sum();
    }

    @Override
    public Integer object(final JsonPointer key, final BsonDocument value) {
        return value.entrySet()
                .stream()
                .mapToInt(entry -> entry.getKey().getBytes(StandardCharsets.UTF_8).length + eval(entry.getValue()))
                .sum();
    }

    @Override
    public Integer binary(final BsonBinary value) {
        return value.getData().length + 1;
    }

    @Override
    public Integer bool(final BsonBoolean value) {
        return WORD;
    }

    @Override
    public Integer dateTime(final BsonDateTime value) {
        return DWORD;
    }

    @Override
    public Integer dbPointer(final BsonDbPointer value) {
        return value.getNamespace().length() + TWORD;
    }

    @Override
    public Integer decimal128(final BsonDecimal128 value) {
        return QWORD;
    }

    @Override
    public Integer bsonDouble(final BsonDouble value) {
        return DWORD;
    }

    @Override
    public Integer int32(final BsonInt32 value) {
        return WORD;
    }

    @Override
    public Integer int64(final BsonInt64 value) {
        return DWORD;
    }

    @Override
    public Integer javascript(final BsonJavaScript value) {
        return value.getCode().length();
    }

    @Override
    public Integer javascriptWithScope(final BsonJavaScriptWithScope value) {
        return value.getCode().length() + object(JsonPointer.empty(), value.getScope());
    }

    @Override
    public Integer maxKey() {
        return WORD;
    }

    @Override
    public Integer minKey() {
        return WORD;
    }

    @Override
    public Integer bsonNull() {
        return WORD;
    }

    @Override
    public Integer objectId(final BsonObjectId value) {
        return TWORD;
    }

    @Override
    public Integer regularExpression(final BsonRegularExpression value) {
        return value.getPattern().length();
    }

    @Override
    public Integer string(final BsonString value) {
        return value.getValue().length();
    }

    @Override
    public Integer symbol(final BsonSymbol value) {
        return value.getSymbol().length();
    }

    @Override
    public Integer timestamp(final BsonTimestamp value) {
        return DWORD;
    }

    @Override
    public Integer undefined() {
        return WORD;
    }
}
