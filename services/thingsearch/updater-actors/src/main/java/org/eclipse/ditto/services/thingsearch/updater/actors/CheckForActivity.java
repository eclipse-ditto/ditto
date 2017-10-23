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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.util.Objects;

/**
 * Message the ThingUpdater actor can send to itself to check for activity of the Actor and terminate itself if
 * there was no activity since the last check.
 */
final class CheckForActivity {

    private final long sequenceNr;

    /**
     * Constructs a new {@code CheckForActivity} message containing the current "lastSequenceNo" of the
     * ThingUpdater actor.
     *
     * @param sequenceNr the current sequence number of the ThingUpdater actor.
     */
    CheckForActivity(final long sequenceNr) {
        this.sequenceNr = sequenceNr;
    }

    /**
     * Returns the sequence number the ThingUpdater actor had when the message was issued.
     *
     * @return the sequence number the ThingUpdater actor had when the message was issued.
     */
    long getSequenceNr() {
        return sequenceNr;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CheckForActivity that = (CheckForActivity) o;
        return Objects.equals(sequenceNr, that.sequenceNr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNr);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "sequenceNr=" + sequenceNr + "]";
    }

}
