/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors;

import java.util.Objects;

/**
 * Message the ThingPersistenceActor can send to itself to check for activity of the Actor and terminate itself
 * if there was no activity since the last check.
 */
final class CheckForActivity {

    private final long currentSequenceNr;
    private final long currentAccessCounter;

    /**
     * Constructs a new {@code CheckForActivity} message containing the current "lastSequenceNo" of the
     * ThingPersistenceActor.
     *
     * @param currentSequenceNr the current {@code lastSequenceNr()} of the ThingPersistenceActor.
     * @param currentAccessCounter the current {@code accessCounter} of the ThingPersistenceActor.
     */
    public CheckForActivity(final long currentSequenceNr, final long currentAccessCounter) {
        this.currentSequenceNr = currentSequenceNr;
        this.currentAccessCounter = currentAccessCounter;
    }

    /**
     * Returns the current {@code ThingsModelFactory.lastSequenceNr()} of the ThingPersistenceActor.
     *
     * @return the current {@code ThingsModelFactory.lastSequenceNr()} of the ThingPersistenceActor.
     */
    public long getCurrentSequenceNr() {
        return currentSequenceNr;
    }

    /**
     * Returns the current {@code accessCounter} of the ThingPersistenceActor.
     *
     * @return the current {@code accessCounter} of the ThingPersistenceActor.
     */
    public long getCurrentAccessCounter() {
        return currentAccessCounter;
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
        return Objects.equals(currentSequenceNr, that.currentSequenceNr) &&
                Objects.equals(currentAccessCounter, that.currentAccessCounter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentSequenceNr, currentAccessCounter);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "currentSequenceNr=" + currentSequenceNr +
                ", currentAccessCounter=" + currentAccessCounter + "]";
    }

}
