/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.eclipse.ditto.base.model.entity.id.RegexPatterns;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;

/**
 * The {@code _id} (thing ID) ranges of namespaces in the search collection.
 * <p>
 * Every thing ID is {@code <namespace>:<name>}, and {@code ';'} is the character right after {@code ':'}, so all
 * things of a namespace lie in the {@code _id} range {@code ["<namespace>:", "<namespace>;")}. As namespaces contain
 * neither {@code ':'} nor {@code ';'}, the ranges of different namespaces are disjoint. Namespaces are ASCII, so
 * Java's {@link String} order equals MongoDB's binary string order.
 * <p>
 * A {@code _namespace} filter alone does not bound a query plan which walks an index in {@code _id} order, e.g. the
 * {@code _id_} index answering a cursor's "thing ID after the last one": on a page with fewer matches than its limit,
 * such a plan walks on to the end of the collection. MongoDB's plan selection cannot rule that plan out, as every
 * candidate plan finds the first matches equally fast.
 */
final class NamespaceThingIdRanges {

    private static final String RANGE_START = RegexPatterns.NAMESPACE_DELIMITER;
    private static final String RANGE_END = ";";

    private NamespaceThingIdRanges() {
        throw new AssertionError();
    }

    /**
     * @param namespace the namespace.
     * @return a filter for the {@code _id} range of the things in the namespace.
     */
    static BsonDocument of(final String namespace) {
        return range(namespace + RANGE_START, namespace + RANGE_END);
    }

    /**
     * @param namespaces the namespaces, not empty.
     * @return a filter for one {@code _id} range spanning the things of all namespaces.
     * @throws NoSuchElementException if {@code namespaces} is empty.
     */
    static BsonDocument spanning(final Collection<String> namespaces) {
        // compare with the delimiter appended: "a.b.c:" sorts before "a.b:", although "a.b" sorts before "a.b.c"
        final String start = namespaces.stream()
                .map(namespace -> namespace + RANGE_START)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        final String end = namespaces.stream()
                .map(namespace -> namespace + RANGE_END)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return range(start, end);
    }

    /**
     * @param namespaces the namespaces.
     * @param direction the direction of a sort by thing ID.
     * @return the namespaces in the order their things are sorted in by thing ID.
     */
    static List<String> inThingIdOrder(final Collection<String> namespaces, final SortDirection direction) {
        final Comparator<String> ascending = Comparator.comparing(namespace -> namespace + RANGE_START);
        return namespaces.stream()
                .sorted(direction == SortDirection.DESC ? ascending.reversed() : ascending)
                .toList();
    }

    private static BsonDocument range(final String start, final String end) {
        return new BsonDocument(PersistenceConstants.FIELD_ID,
                new BsonDocument("$gte", new BsonString(start)).append("$lt", new BsonString(end)));
    }

}
