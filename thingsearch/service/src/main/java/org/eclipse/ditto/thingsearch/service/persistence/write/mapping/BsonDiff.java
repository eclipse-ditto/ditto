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

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_F_ARRAY;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

import akka.japi.Pair;

/**
 * Diff between 2 BSON documents.
 */
public final class BsonDiff {

    /**
     * MongoDB operator to set a value.
     */
    public static final String SET = "$set";

    /**
     * MongoDB operator to unset a value.
     */
    private static final String UNSET = "$unset";

    final int replacementSize;
    final int diffSize;
    final Stream<Pair<JsonPointer, BsonValue>> setPointers;
    final Stream<JsonPointer> unsetPointers;

    BsonDiff(final int replacementSize, final int diffSize,
            final Stream<Pair<JsonPointer, BsonValue>> setPointers,
            final Stream<JsonPointer> unsetPointers) {

        this.replacementSize = replacementSize;
        this.diffSize = diffSize;
        this.setPointers = setPointers;
        this.unsetPointers = unsetPointers;
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
     * @param recurseIntoArrays whether diff computation should descend into arrays.
     * @return the change to edit the starting BSON document into the target BSON document.
     */
    public static BsonDiff minus(final BsonDocument minuend, final BsonDocument subtrahend,
            final boolean recurseIntoArrays, final int maxWireVersion) {

        return new BsonDiffVisitor(recurseIntoArrays, maxWireVersion).eval(minuend).apply(subtrahend);
    }

    /**
     * Compute the difference between 2 Thing index documents.
     *
     * @param minuend the minuend document.
     * @param subtrahend the subtrahend document.
     * @return the difference.
     */
    public static BsonDiff minusThingDocs(final BsonDocument minuend, final BsonDocument subtrahend,
            final int maxWireVersion) {
        // compute the internal array diff especially to find similar elements by internal key
        final var minuendFeatures = minuend.getArray(FIELD_F_ARRAY);
        final var subtrahendFeatures = subtrahend.getArray(FIELD_F_ARRAY);
        final var diffFeatures = BsonArrayDiff.diffFeaturesArray(minuendFeatures, subtrahendFeatures, maxWireVersion);
        // compute the rest of the diff without the internal array
        final var minuendWithoutInternal = minuend.clone();
        final var subtrahendWithoutInternal = subtrahend.clone();
        minuendWithoutInternal.remove(FIELD_F_ARRAY);
        subtrahendWithoutInternal.remove(FIELD_F_ARRAY);
        final var diffWithoutInternal = minus(minuendWithoutInternal, subtrahendWithoutInternal, true, maxWireVersion);
        return diffWithoutInternal.concat(diffFeatures);
    }

    /**
     * Concatenate 2 diffs.
     *
     * @param that the other diff.
     * @return the concatenation.
     */
    public BsonDiff concat(final BsonDiff that) {
        return new BsonDiff(
                replacementSize + that.replacementSize,
                diffSize + that.diffSize,
                Stream.concat(setPointers, that.setPointers),
                Stream.concat(unsetPointers, that.unsetPointers)
        );
    }

    /**
     * Consume this object to create an update aggregation pipeline.
     *
     * @return Update document.
     */
    public List<BsonDocument> consumeAndExport() {
        final List<BsonDocument> result = new ArrayList<>(2);
        final var setDoc = consumeAndExportSet();
        if (!setDoc.isEmpty()) {
            result.add(new BsonDocument().append(SET, setDoc));
        }
        final var unsetArray = consumeAndExportUnset();
        if (!unsetArray.isEmpty()) {
            result.add(new BsonDocument().append(UNSET, unsetArray));
        }
        return result;
    }

    /**
     * Test if the diff size is smaller than the replacement size.
     *
     * @return whether the diff size is smaller.
     */
    public boolean isDiffSmaller() {
        return diffSize < replacementSize;
    }

    private BsonDocument consumeAndExportSet() {
        final BsonDocument setDocument = new BsonDocument();
        setPointers.forEach(pair -> setDocument.append(getPathString(pair.first()), pair.second()));
        return setDocument;
    }

    private BsonArray consumeAndExportUnset() {
        final var unsetArray = new BsonArray();
        unsetPointers.forEach(path -> unsetArray.add(new BsonString(getPathString(path))));
        return unsetArray;
    }

    private static String getPathString(final Iterable<JsonKey> jsonPointer) {
        return StreamSupport.stream(jsonPointer.spliterator(), false).collect(Collectors.joining("."));
    }
}
