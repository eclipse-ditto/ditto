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

    private Set<ByteString> inserts;
    private Set<ByteString> deletes;
    private boolean replaceAll;

    private CompressedUpdate(final Set<ByteString> inserts, final Set<ByteString> deletes, final boolean replaceAll) {
        this.inserts = inserts;
        this.deletes = deletes;
        this.replaceAll = replaceAll;
    }

    /**
     * @return An empty update.
     */
    public static CompressedUpdate empty() {
        return new CompressedUpdate(new HashSet<>(), new HashSet<>(), false);
    }

    /**
     * Replace everything associated with a subscriber in the distributed data.
     *
     * @param inserts topics to insert.
     * @return an immutable update object.
     */
    public static CompressedUpdate replaceAll(final Set<ByteString> inserts) {
        final Set<ByteString> copyOfInserts = Collections.unmodifiableSet(new HashSet<>(inserts));
        return new CompressedUpdate(copyOfInserts, Collections.emptySet(), true);
    }


    /**
     * Remove all data. Destroy immutability.
     */
    public void reset() {
        inserts = new HashSet<>();
        deletes = new HashSet<>();
        replaceAll = false;
    }

    /**
     * Export an unmodifiable copy of this object, then remove all data.
     * The result is thread-safe if no user stores references to this object's fields.
     *
     * @return An unmodifiable copy of this object for asynchronous reads.
     */
    public CompressedUpdate exportAndReset() {
        final CompressedUpdate snapshot = new CompressedUpdate(
                Collections.unmodifiableSet(new HashSet<>(inserts)),
                Collections.unmodifiableSet(new HashSet<>(deletes)),
                replaceAll);
        reset();
        return snapshot;
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
     * @return Whether the distributed data should clear all associations and replace them by inserts.
     */
    public boolean shouldReplaceAll() {
        return replaceAll;
    }

    /**
     * Add compressed topic to be inserted.
     *
     * @param newInserts compressed topic to insert.
     */
    public void insert(final ByteString newInserts) {
        inserts.add(newInserts);
        deletes.remove(newInserts);
    }

    /**
     * Remove compressed topic.
     *
     * @param newDeletes compressed topics to delete.
     */
    public void delete(final ByteString newDeletes) {
        inserts.remove(newDeletes);
        deletes.add(newDeletes);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof CompressedUpdate) {
            final CompressedUpdate that = (CompressedUpdate) other;
            return replaceAll == that.replaceAll && inserts.equals(that.inserts) && deletes.equals(that.deletes);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(inserts, deletes, replaceAll);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[inserts=" + inserts +
                ",deletes=" + deletes +
                ",replaceAll=" + replaceAll +
                "]";
    }
}
