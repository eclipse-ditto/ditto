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

import java.util.stream.Stream;

import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonPointer;

import akka.japi.Pair;

/**
 * Diff between 2 BSON documents.
 */
final class BsonDiff {

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
}
