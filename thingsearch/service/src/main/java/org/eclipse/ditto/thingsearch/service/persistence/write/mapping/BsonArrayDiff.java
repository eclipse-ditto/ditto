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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

import akka.japi.Pair;

/**
 * Diff between 2 BSON arrays. Only used for the flattened key-value array because Ditto has no API for array
 * operations.
 */
final class BsonArrayDiff {

    private static final String ARRAY_ELEM_AT = "$arrayElemAt";

    static BsonArray diff(final JsonPointer key, final BsonArray minuend, final BsonArray subtrahend) {
        final BsonString subtrahendExpr = getPathExpr(key);
        final Map<BsonValue, Integer> subtrahendIndexMap = IntStream.range(0, subtrahend.size()).boxed()
                .collect(Collectors.toMap(subtrahend::get, Function.identity(), (i, j) -> i));
        final BsonArray result = new BsonArray();
        for (final BsonValue element : minuend) {
            final Integer i = subtrahendIndexMap.get(element);
            if (i != null) {
                result.add(getSubtrahendElement(subtrahendExpr, i));
            } else {
                result.add(element);
            }
        }
        return result;
    }

    private static BsonDocument getSubtrahendElement(final BsonValue subtrahendExpr, final int i) {
        final BsonArray args = new BsonArray();
        args.add(subtrahendExpr);
        args.add(new BsonInt32(i));
        return new BsonDocument().append(ARRAY_ELEM_AT, args);
    }

    private static BsonString getPathExpr(final JsonPointer key) {
        return new BsonString(StreamSupport.stream(key.spliterator(), false)
                .collect(Collectors.joining(".", "$", "")));
    }
}
