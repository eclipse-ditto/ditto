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
package org.eclipse.ditto.services.utils.pubsub.ddata;

import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Updates consisting of insertions and deletions.
 *
 * @param <S> type of insertions and deletions.
 * @param <T> concrete type of this object.
 */
@NotThreadSafe
public interface IndelUpdate<S, T extends IndelUpdate<S, T>> {

    /**
     * Remove all data. Destroy immutability.
     */
    void reset();

    /**
     * Export an unmodifiable copy of this object.
     *
     * @return an unmodifiable copy.
     */
    T snapshot();

    /**
     * Export an unmodifiable copy of this object, then remove all data.
     * The result is thread-safe if no user stores references to this object's fields.
     *
     * @return An unmodifiable copy of this object for asynchronous reads.
     */
    default T exportAndReset() {
        final T snapshot = snapshot();
        reset();
        return snapshot;
    }

    /**
     * @return Inserted elements.
     */
    Set<S> getInserts();

    /**
     * @return Deleted elements.
     */
    Set<S> getDeletes();

    /**
     * @return Whether the distributed data should clear all associations and replace them by inserts.
     */
    boolean shouldReplaceAll();

    /**
     * Add an element to be inserted.
     *
     * @param newInsert element to insert.
     */
    void insert(final S newInsert);

    /**
     * Remove an element.
     *
     * @param newDelete element to delete.
     */
    void delete(final S newDelete);
}
