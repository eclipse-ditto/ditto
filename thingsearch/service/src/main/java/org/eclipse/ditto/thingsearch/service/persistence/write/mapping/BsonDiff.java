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

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REVISION;

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

    private static final BsonDocument DUMMY_SET_DOC = new BsonDocument()
            .append(FIELD_REVISION, new BsonString("$" + FIELD_REVISION));

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
     * Compute the difference between 2 Thing index documents.
     *
     * @param minuend the minuend document.
     * @param subtrahend the subtrahend document.
     * @return the difference.
     */
    public static BsonDiff minusThingDocs(final BsonDocument minuend, final BsonDocument subtrahend) {
        // compute the internal array diff especially to find similar elements by internal key
        final var minuendInternal = minuend.getArray(FIELD_INTERNAL);
        final var subtrahendInternal = subtrahend.getArray(FIELD_INTERNAL);
        final var diffInternal = BsonArrayDiff.diffInternalArray(minuendInternal, subtrahendInternal);
        // compute the rest of the diff without the internal array
        final var minuendWithoutInternal = minuend.clone();
        final var subtrahendWithoutInternal = subtrahend.clone();
        minuendWithoutInternal.remove(FIELD_INTERNAL);
        subtrahendWithoutInternal.remove(FIELD_INTERNAL);
        final var diffWithoutInternal = minus(minuendWithoutInternal, subtrahendWithoutInternal);
        return diffWithoutInternal.concat(diffInternal);
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
                Stream.concat(set, that.set),
                Stream.concat(unset, that.unset)
        );
    }

    /**
     * Consume this object to create an update aggregation pipeline.
     *
     * @return Update document.
     */
    public List<BsonDocument> consumeAndExport() {
        final var result = new ArrayList<BsonDocument>(2);
        final var setDoc = consumeAndExportSet();
        final var setOp = "$set";
        final var unsetOp = "$unset";
        if (!setDoc.isEmpty()) {
            result.add(new BsonDocument().append(setOp, setDoc));
        }
        final var unsetArray = consumeAndExportUnset();
        if (!unsetArray.isEmpty()) {
            result.add(new BsonDocument().append(unsetOp, unsetArray));
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

    /**
     * Destroy this object and convert the set and unset fields into lists.
     *
     * @return exported set and unset lists.
     */
    BsonDiffList consumeAndExportToList() {
        return new BsonDiffList(set.collect(Collectors.toList()), unset.collect(Collectors.toList()));
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
