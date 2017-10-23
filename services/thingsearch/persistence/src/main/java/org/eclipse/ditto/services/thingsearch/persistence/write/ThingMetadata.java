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
package org.eclipse.ditto.services.thingsearch.persistence.write;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * Data class holding information about a "thingEntities" database record.
 */
@ParametersAreNonnullByDefault
@Immutable
public final class ThingMetadata {

    private final long thingRevision;
    private final String policyId;
    private final long policyRevision;

    /**
     * Creates a new {@code ThingMetadata} instance.
     *
     * @param thingRevision the revision of the Thing according to the search index.
     * @param policyId the policyId of the Thing according to the search index.
     * @param policyRevision the revision of the Policy according to the search index.
     */
    public ThingMetadata(final long thingRevision, @Nullable final String policyId, final long policyRevision) {
        this.thingRevision = thingRevision;
        this.policyId = policyId;
        this.policyRevision = policyRevision;
    }

    /**
     * Returns the revision of the Thing according to the search index.
     *
     * @return the revision of the Thing according to the search index.
     */
    public long getThingRevision() {
        return thingRevision;
    }

    /**
     * Returns the policyId of the Thing according to the search index.
     *
     * @return the policyId of the Thing according to the search index.
     */
    public String getPolicyId() {
        return policyId;
    }

    /**
     * Returns the revision of the Policy according to the search index.
     *
     * @return the revision of the Policy according to the search index.
     */
    public long getPolicyRevision() {
        return policyRevision;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ThingMetadata that = (ThingMetadata) o;
        return thingRevision == that.thingRevision &&
                policyRevision == that.policyRevision &&
                Objects.equals(policyId, that.policyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingRevision, policyId, policyRevision);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingRevision=" + thingRevision +
                ", policyId=" + policyId +
                ", policyRevision=" + policyRevision +
                "]";
    }

}
