/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.ddata.literal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataUpdate;

/**
 * Updates in the form of a set of strings.
 */
@NotThreadSafe
public final class LiteralUpdate implements DDataUpdate<String> {

    private final Set<String> inserts;
    private final Set<String> deletes;

    private LiteralUpdate(final Set<String> inserts, final Set<String> deletes) {
        this.inserts = inserts;
        this.deletes = deletes;
    }

    /**
     * @return An empty update.
     */
    public static LiteralUpdate empty() {
        return new LiteralUpdate(new HashSet<>(), new HashSet<>());
    }

    /**
     * Create a new LiteralUpdate with the passed {@code inserts} associated with a subscriber in the distributed data.
     *
     * @param inserts topics to insert.
     * @return an immutable update object.
     */
    public static LiteralUpdate withInserts(final Set<String> inserts) {
        final Set<String> copyOfInserts = Set.copyOf(inserts);
        return new LiteralUpdate(copyOfInserts, Set.of());
    }

    @Override
    public Set<String> getInserts() {
        return inserts;
    }

    @Override
    public Set<String> getDeletes() {
        return deletes;
    }

    @Override
    public LiteralUpdate diff(final DDataUpdate<String> previousUpdate) {
        final Stream<String> insertPlus = setDifference(inserts, previousUpdate.getInserts());
        final Stream<String> deletePlus = setDifference(deletes, previousUpdate.getDeletes());
        final Stream<String> insertMinus = setDifference(previousUpdate.getInserts(), inserts);
        final Stream<String> deleteMinus = setDifference(previousUpdate.getDeletes(), deletes);
        final Set<String> newInserts = Stream.concat(insertPlus, deleteMinus).collect(Collectors.toSet());
        final Set<String> newDeletes = Stream.concat(deletePlus, insertMinus).collect(Collectors.toSet());
        return new LiteralUpdate(newInserts, newDeletes);
    }

    @Override
    public boolean isEmpty() {
        return inserts.isEmpty() && deletes.isEmpty();
    }

    @Override
    public boolean equals(final Object other) {
        if (getClass().isInstance(other)) {
            final LiteralUpdate that = getClass().cast(other);
            return Objects.equals(inserts, that.inserts) && Objects.equals(deletes, that.deletes);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(inserts, deletes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "inserts=" + inserts +
                ", deletes=" + deletes +
                "]";
    }

    private static Stream<String> setDifference(final Collection<String> minuend, final Set<String> subtrahend) {
        return minuend.stream().filter(element -> !subtrahend.contains(element));
    }
}
