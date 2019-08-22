/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

/**
 * Distributed relation between actor references and compressed topics implemented as an ORMultiMap.
 */
package org.eclipse.ditto.services.utils.pubsub.ddata.compressed;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import akka.util.ByteString;

/**
 * Updates of compressed DData.
 */
@NotThreadSafe
public final class CompressedUpdate {

    private final Set<ByteString> inserts;
    private final Set<ByteString> deletes;

    private CompressedUpdate(final Set<ByteString> inserts, final Set<ByteString> deletes) {
        this.inserts = inserts;
        this.deletes = deletes;
    }

    /**
     * @return An unmodifiable copy of this object for asynchronous reads.
     */
    public CompressedUpdate snapshot() {
        return new CompressedUpdate(
                Collections.unmodifiableSet(new HashSet<>(inserts)),
                Collections.unmodifiableSet(new HashSet<>(deletes))
        );
    }

    /**
     * @return Inserted byte strings.
     */
    public Set<ByteString> getInserts() {
        return inserts;
    }

    /**
     * @return Deleted byte strings.
     */
    public Set<ByteString> getDeletes() {
        return deletes;
    }

    /**
     * Add compressed topics to be inserted.
     *
     * @param newInserts compressed topics to insert.
     */
    public void insertAll(final Collection<ByteString> newInserts) {
        inserts.addAll(newInserts);
        deletes.removeAll(newInserts);
    }

    /**
     * Remove compressed topics.
     *
     * @param newDeletes compressed topics to delete.
     */
    public void deleteAll(final Collection<ByteString> newDeletes) {
        inserts.removeAll(newDeletes);
        deletes.addAll(newDeletes);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof CompressedUpdate) {
            final CompressedUpdate that = (CompressedUpdate) other;
            return inserts.equals(that.inserts) && deletes.equals(that.deletes);
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
        return getClass().getSimpleName() +
                "[inserts=" + inserts +
                ",deletes=" + deletes +
                "]";
    }
}
