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

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

import akka.japi.Pair;

/**
 * Exported diffs of a document.
 */
public final class BsonDiffList {

    private static final String MERGE_OBJECTS = "$mergeObjects";

    /**
     * $unsetField only possible for top-level fields
     */
    private static final String UNSET_FIELD = "$unsetField";
    private static final String FIELD = "field";
    private static final String INPUT = "input";
    private static final String VALUE = "value";

    final List<Pair<JsonPointer, BsonValue>> set;
    final List<JsonPointer> unset;

    BsonDiffList(final List<Pair<JsonPointer, BsonValue>> set, final List<JsonPointer> unset) {
        this.set = set;
        this.unset = unset;
    }

    /**
     * Create a BSON document to use in an aggregation pipeline.
     * This is not trivial if there is a nested unset pointer, in which case we give up.
     *
     * @return the diff document, or an empty optional if we gave up.
     */
    Optional<BsonValue> toBsonInPipeline(final BsonValue previousDocumentExpression, final boolean isUnsetAllowed) {
        final boolean hasNestedUnset = unset.stream().anyMatch(pointer -> pointer.getLevelCount() > 1);
        final boolean unsetNotAllowed = !isUnsetAllowed && !unset.isEmpty();
        if (hasNestedUnset || unsetNotAllowed) {
            return Optional.empty();
        }
        final BsonValue beforeUnset;
        if (set.isEmpty()) {
            beforeUnset = previousDocumentExpression;
        } else {
            final var doc = new BsonDocument();
            for (final var pair : set) {
                setBsonDocument(doc, pair.first(), pair.second());
            }
            beforeUnset = mergeObjects(previousDocumentExpression, doc);
        }
        return Optional.of(buildUnsetDocument(beforeUnset, unset.iterator()));
    }

    private static BsonDocument mergeObjects(final BsonValue prev, final BsonValue next) {
        final BsonArray args = new BsonArray();
        args.add(prev);
        args.add(next);
        return new BsonDocument().append(MERGE_OBJECTS, args);
    }

    private static BsonValue buildUnsetDocument(final BsonValue beforeUnset, final Iterator<JsonPointer> keys) {
        if (keys.hasNext()) {
            final String key = keys.next().getRoot().map(JsonKey::toString).orElseThrow();
            final BsonDocument nextDoc = new BsonDocument()
                    .append(UNSET_FIELD, new BsonDocument()
                            .append(FIELD, new BsonString(key))
                            .append(INPUT, beforeUnset)
                    );
            return buildUnsetDocument(nextDoc, keys);
        } else {
            return beforeUnset;
        }
    }

    private static void setBsonDocument(final BsonDocument doc, final JsonPointer pointer, final BsonValue value) {
        final String key = pointer.getRoot().map(JsonKey::toString).orElseThrow();
        if (pointer.getLevelCount() > 1) {
            if (doc.containsKey(key)) {
                setBsonDocument(doc.getDocument(key), pointer.nextLevel(), value);
            } else {
                final var newDocument = new BsonDocument();
                setBsonDocument(newDocument, pointer.nextLevel(), value);
                doc.append(key, newDocument);
            }
        } else {
            doc.append(key, value);
        }
    }
}
