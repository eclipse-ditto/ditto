/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.streaming;

import java.util.Objects;

/**
 * A pair of {@code String} and {@code long} to store persistence ID and sequence number of an event in a journal.
 */
public final class PidWithSeqNr {

    private final String persistenceId;
    private final long sequenceNr;

    /**
     * Creates a pair from {@code String} and {@code long}.
     *
     * @param persistenceId the Akka persistence persistenceId.
     * @param sequenceNr the sequence number.
     */
    public PidWithSeqNr(final String persistenceId, final long sequenceNr) {
        this.persistenceId = persistenceId;
        this.sequenceNr = sequenceNr;
    }

    /**
     * Retrieve the persistence ID.
     *
     * @return The persistence ID.
     */
    public String getPersistenceId() {
        return persistenceId;
    }

    /**
     * Retrieve the sequence number.
     *
     * @return The sequence number.
     */
    public long getSequenceNr() {
        return sequenceNr;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        } else {
            final PidWithSeqNr that = (PidWithSeqNr) o;
            return Objects.equals(persistenceId, that.persistenceId) &&
                    sequenceNr == that.sequenceNr;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(persistenceId, sequenceNr);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " ["
                + "persistenceId=" + persistenceId
                + ", sequenceNr=" + sequenceNr
                + "]";
    }
}
