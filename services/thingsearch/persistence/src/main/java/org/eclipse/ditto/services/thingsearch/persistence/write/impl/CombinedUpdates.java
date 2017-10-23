/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.bson.conversions.Bson;

/**
 * Combines a variable number of {@link Bson} values.
 */
final class CombinedUpdates {

    private final List<Bson> updates;

    private CombinedUpdates(final List<Bson> updates) {
        this.updates = updates;
    }

    /**
     * Returns new {@code CombinedUpdates} for the given {@code updates}.
     *
     * @param updates the updates.
     * @return the combined updates.
     */
    static CombinedUpdates of(final Bson... updates) {
        final List<Bson> updatesList = new ArrayList<>(updates.length);
        Collections.addAll(updatesList, updates);

        return new CombinedUpdates(updatesList);
    }

    /**
     * Returns the updates.
     *
     * @return the updates.
     */
    List<Bson> getUpdates() {
        return updates;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CombinedUpdates that = (CombinedUpdates) o;
        return Objects.equals(updates, that.updates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(updates);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", updates=" + updates +
                "]";
    }

}
