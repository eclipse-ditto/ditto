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
import java.util.function.Function;
import java.util.stream.Stream;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

import akka.japi.Pair;

/**
 * Compute the diff between 2 BSON documents.
 */
final class BsonDiffVisitor implements BsonValueVisitor<Function<BsonValue, BsonDiff>> {

    private static final String DOLLAR = "$";
    private static final String LITERAL = "$literal";

    private final BsonSizeVisitor bsonSizeVisitor = new BsonSizeVisitor();
    private final boolean recurseIntoArrays;
    private final int maxWireVersion;

    BsonDiffVisitor(final boolean recurseIntoArrays, final int maxWireVersion) {
        this.recurseIntoArrays = recurseIntoArrays;
        this.maxWireVersion = maxWireVersion;
    }

    @Override
    public Function<BsonValue, BsonDiff> primitive(final JsonPointer key, final BsonValue value) {
        final int replacementSize = bsonSizeVisitor.eval(value);
        return oldValue -> {
            if (value.equals(oldValue)) {
                return BsonDiff.empty(replacementSize);
            } else if (value.isString() && value.asString().getValue().startsWith(DOLLAR)) {
                final var literalValue = new BsonDocument().append(LITERAL, value);
                return BsonDiff.set(replacementSize, key, literalValue);
            } else {
                return BsonDiff.set(replacementSize, key, value);
            }
        };
    }

    @Override
    public Function<BsonValue, BsonDiff> array(final JsonPointer key, final BsonArray value) {
        if (!recurseIntoArrays) {
            // no recursive diff for array elements: elements are only replaced
            return primitive(key, value);
        }
        final int replacementSize = bsonSizeVisitor.eval(value);
        return oldValue -> {
            if (value.equals(oldValue)) {
                return BsonDiff.empty(replacementSize);
            } else if (oldValue.isArray()) {
                final var bsonArrayDiff = BsonArrayDiff.diff(key, value, oldValue.asArray(), maxWireVersion);
                final var diffSize = key.length() + bsonSizeVisitor.eval(bsonArrayDiff);
                if (diffSize <= replacementSize) {
                    return BsonDiff.set(replacementSize, diffSize, key, bsonArrayDiff);
                } else {
                    return BsonDiff.set(replacementSize, key, value);
                }
            } else {
                return BsonDiff.set(replacementSize, key, value);
            }
        };
    }

    @Override
    public Function<BsonValue, BsonDiff> object(final JsonPointer key, final BsonDocument value) {
        return oldValue -> {
            if (!oldValue.isDocument() || value.isEmpty()) {
                return BsonDiff.set(bsonSizeVisitor.eval(value), key, literal(value));
            }
            if (value.equals(oldValue)) {
                return BsonDiff.empty(bsonSizeVisitor.eval(value));
            }
            final var oldDocument = oldValue.asDocument();
            Stream<Pair<JsonPointer, BsonValue>> set = Stream.empty();
            Stream<JsonPointer> unset = Stream.empty();
            int replacementSize = 0;
            int diffSize = 0;
            for (final var entry : value.entrySet()) {
                final JsonPointer nextKey = key.addLeaf(JsonKey.of(entry.getKey()));
                final var nextValue = entry.getValue();
                if (!oldDocument.containsKey(entry.getKey())) {
                    set = Stream.concat(set, Stream.of(Pair.create(nextKey, literal(nextValue))));
                    final var nextSize = bsonSizeVisitor.eval(nextValue);
                    diffSize += nextKey.length() + nextSize;
                    replacementSize += nextSize + entry.getKey().length();
                } else {
                    final var prevValue = oldDocument.get(entry.getKey());
                    if (!nextValue.equals(prevValue)) {
                        final var nextDiff = eval(nextKey, nextValue).apply(prevValue);
                        final var nextReplacementSize = nextDiff.replacementSize + nextKey.length();
                        if (nextDiff.diffSize <= nextReplacementSize) {
                            set = Stream.concat(set, nextDiff.setPointers);
                            unset = Stream.concat(unset, nextDiff.unsetPointers);
                            diffSize += nextDiff.diffSize;
                        } else {
                            set = Stream.concat(set, Stream.of(Pair.create(nextKey, literal(nextValue))));
                            diffSize += nextReplacementSize;
                        }
                        replacementSize += nextDiff.replacementSize + entry.getKey().length();
                    } else {
                        replacementSize += bsonSizeVisitor.eval(nextValue) + entry.getKey().length();
                    }
                }
            }
            final List<JsonPointer> deletedKeys = oldDocument.keySet()
                    .stream()
                    .filter(oldKey -> !value.containsKey(oldKey))
                    .map(oldKey -> key.addLeaf(JsonKey.of(oldKey)))
                    .toList();
            unset = Stream.concat(unset, deletedKeys.stream());
            diffSize += deletedKeys.stream().mapToInt(JsonPointer::length).sum();
            return new BsonDiff(replacementSize, diffSize, set, unset);
        };
    }

    private static BsonValue literal(final BsonValue value) {
        return value.isDocument() ? new BsonDocument().append(LITERAL, value) : value;
    }
}
