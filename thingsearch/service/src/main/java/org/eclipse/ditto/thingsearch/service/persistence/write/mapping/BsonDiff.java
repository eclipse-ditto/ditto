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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonPointer;

import akka.japi.Pair;

/**
 * Diff between 2 BSON documents.
 */
public final class BsonDiff {

    final int replacementSize;
    final int diffSize;
    final Stream<Pair<JsonPointer, BsonValue>> set;
    final Stream<JsonPointer> unset;

    BsonDiff(final int replacementSize, final int diffSize,
            final Stream<Pair<JsonPointer, BsonValue>> set,
            final Stream<JsonPointer> unset) {
        this.replacementSize = replacementSize;
        this.diffSize = diffSize;
        this.set = set;
        this.unset = unset;
    }

    static BsonDiff empty(final int replacementSize) {
        return new BsonDiff(replacementSize, 0, Stream.empty(), Stream.empty());
    }

    static BsonDiff set(final int replacementSize, final JsonPointer key, final BsonValue value) {
        final int diffSize = replacementSize + key.length();
        return set(replacementSize, diffSize, key, value);
    }

    static BsonDiff set(final int replacementSize, final int diffSize, final JsonPointer key, final BsonValue value) {
        return new BsonDiff(replacementSize, diffSize, Stream.of(Pair.create(key, value)), Stream.empty());
    }

    /**
     * Compute the difference between 2 BSON documents.
     *
     * @param minuend the target BSON document.
     * @param subtrahend the starting BSON document.
     * @return the change to edit the starting BSON document into the target BSON document.
     */
    public static BsonDiff minus(final BsonDocument minuend, final BsonDocument subtrahend) {
        return new BsonDiffVisitor().eval(minuend).apply(subtrahend);
    }

    /**
     * Consume this object to create an update aggregation pipeline.
     *
     * @return Update document.
     */
    public List<BsonDocument> consumeAndExport() {
        final var result = new ArrayList<BsonDocument>(2);
        final var setDoc = consumeAndExportSet();
        if (!setDoc.isEmpty()) {
            result.add(new BsonDocument().append("$set", setDoc));
        }
        final var unsetArray = consumeAndExportUnset();
        if (!unsetArray.isEmpty()) {
            result.add(new BsonDocument().append("$unset", unsetArray));
        }
        return result;
    }

    public boolean isDiffSmaller() {
        return diffSize < replacementSize;
    }

    private BsonDocument consumeAndExportSet() {
        final BsonDocument setDocument = new BsonDocument();
        set.forEach(pair -> setDocument.append(getPathString(pair.first()), pair.second()));
        return setDocument;
    }

    private BsonArray consumeAndExportUnset() {
        final var unsetArray = new BsonArray();
        unset.forEach(path -> unsetArray.add(new BsonString(getPathString(path))));
        return unsetArray;
    }

    private static String getPathString(final JsonPointer jsonPointer) {
        return StreamSupport.stream(jsonPointer.spliterator(), false).collect(Collectors.joining("."));
    }
}
