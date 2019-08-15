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
package org.eclipse.ditto.services.thingsearch.persistence.write.model;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.things.ThingId;

/**
 * Data class holding information about a "thingEntities" database record.
 */
@Immutable
public final class Metadata {

    private final ThingId thingId;
    private final long thingRevision;
    @Nullable private final String policyId;
    private final long policyRevision;

    private Metadata(final ThingId thingId,
            final long thingRevision,
            @Nullable final String policyId,
            final long policyRevision) {

        this.thingId = thingId;
        this.thingRevision = thingRevision;
        this.policyId = policyId;
        this.policyRevision = policyRevision;
    }

    /**
     * Create an Metadata object with callback.
     *
     * @param thingId the Thing ID.
     * @param thingRevision the Thing revision.
     * @param policyId the Policy ID if the Thing has one.
     * @param policyRevision the Policy revision if the Thing has a policy, or the Thing revision if it does not.
     * @return the new Metadata object.
     */
    public static Metadata of(final ThingId thingId,
            final long thingRevision,
            @Nullable final String policyId,
            final long policyRevision) {

        return new Metadata(thingId, thingRevision, policyId, policyRevision);
    }

    /**
     * @return the Thing ID.
     */
    public ThingId getThingId() {
        return thingId;
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
     * @return the policyId of the Thing according to the search index.
     */
    public Optional<String> getPolicyId() {
        return Optional.ofNullable(policyId);
    }

    /**
     * @return namespace field as to be written in the persistence.
     */
    public String getNamespaceInPersistence() {
        return thingId.getNamespace();
    }

    /**
     * @return the policyId-field as to be written in the persistence.
     */
    public String getPolicyIdInPersistence() {
        return getPolicyId().orElse("");
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
        final Metadata that = (Metadata) o;
        return thingRevision == that.thingRevision &&
                policyRevision == that.policyRevision &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(policyId, that.policyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, thingRevision, policyId, policyRevision);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", thingRevision=" + thingRevision +
                ", policyId=" + policyId +
                ", policyRevision=" + policyRevision +
                "]";
    }

}
