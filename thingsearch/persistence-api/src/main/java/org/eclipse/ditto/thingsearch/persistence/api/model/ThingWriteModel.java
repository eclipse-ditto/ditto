/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.api.model;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.policies.api.PolicyTag;

/**
 * Backend-neutral write model that upserts the search-index entry of a thing.
 * <p>
 * Carries the neutral {@link SearchIndexDocument} to store, plus the patch/no-op bookkeeping the updater
 * stream uses. A backend maps this into its own upsert (Mongo: a replace/aggregation update; PostgreSQL: a
 * JSONB upsert plus flattened side rows).
 *
 * @since 4.0.0
 */
@Immutable
public final class ThingWriteModel extends AbstractWriteModel {

    private final SearchIndexDocument document;
    private final boolean patchUpdate;
    private final long previousRevision;
    private final boolean noop;
    private final boolean emptiedOut;

    private ThingWriteModel(final Metadata metadata, final SearchIndexDocument document, final boolean patchUpdate,
            final long previousRevision, final boolean noop, final boolean emptiedOut) {
        super(metadata);
        this.document = Objects.requireNonNull(document, "document");
        this.patchUpdate = patchUpdate;
        this.previousRevision = previousRevision;
        this.noop = noop;
        this.emptiedOut = emptiedOut;
    }

    /**
     * Create a full-update write model for the given document.
     *
     * @param metadata the metadata of the thing.
     * @param document the neutral search-index document to store.
     * @return the write model.
     */
    public static ThingWriteModel of(final Metadata metadata, final SearchIndexDocument document) {
        return new ThingWriteModel(metadata, document, false, 0L, false, false);
    }

    /**
     * Create a write model that empties out the search-index entry of a thing, retaining only its identity and
     * revision bookkeeping (used when the thing is no longer visible to anyone).
     *
     * @param metadata the metadata of the thing.
     * @return the write model.
     */
    public static ThingWriteModel ofEmptiedOut(final Metadata metadata) {
        final PolicyTag thingPolicy = metadata.getThingPolicyTag().orElse(null);
        final SearchIndexDocument emptied = SearchIndexDocument.newBuilder(metadata.getThingId())
                .namespace(metadata.getNamespaceInPersistence())
                .revision(metadata.getThingRevision())
                .policyId(thingPolicy != null ? thingPolicy.getEntityId() : null)
                .policyRevision(thingPolicy != null ? thingPolicy.getRevision() : 0L)
                .referencedPolicies(metadata.getAllReferencedPolicyTags())
                .build();
        return new ThingWriteModel(metadata, emptied, false, 0L, false, true);
    }

    /**
     * Create a no-op write model that applies no change to the search index (the incremental diff was empty).
     *
     * @param metadata the metadata of the thing.
     * @return the no-op write model.
     */
    public static ThingWriteModel noopWriteModel(final Metadata metadata) {
        return new ThingWriteModel(metadata, SearchIndexDocument.newBuilder(metadata.getThingId()).build(), false, 0L,
                true, false);
    }

    /**
     * Return a copy of this write model marked as a patch update relative to the given previous revision.
     *
     * @param previousRevision the revision the patch is computed against.
     * @return the patch-update copy.
     */
    public ThingWriteModel asPatchUpdate(final long previousRevision) {
        return new ThingWriteModel(getMetadata(), document, true, previousRevision, noop, emptiedOut);
    }

    @Override
    public ThingWriteModel setMetadata(final Metadata metadata) {
        return new ThingWriteModel(metadata, document, patchUpdate, previousRevision, noop, emptiedOut);
    }

    /**
     * @return the neutral search-index document to store.
     */
    public SearchIndexDocument getDocument() {
        return document;
    }

    /**
     * @return whether this write model is a patch update relative to {@link #getPreviousRevision()}.
     */
    public boolean isPatchUpdate() {
        return patchUpdate;
    }

    /**
     * @return the revision this patch update is computed against (only meaningful when {@link #isPatchUpdate()}).
     */
    public long getPreviousRevision() {
        return previousRevision;
    }

    /**
     * @return whether this write model applies no change to the search index.
     */
    public boolean isNoop() {
        return noop;
    }

    /**
     * @return whether this write model empties out the search-index entry, retaining only identity and revision
     * bookkeeping (used so a backend can reproduce the historical "emptied out" on-disk shape).
     */
    public boolean isEmptiedOut() {
        return emptiedOut;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ThingWriteModel that = (ThingWriteModel) o;
        return patchUpdate == that.patchUpdate &&
                previousRevision == that.previousRevision &&
                noop == that.noop &&
                emptiedOut == that.emptiedOut &&
                Objects.equals(document, that.document) &&
                super.equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), document, patchUpdate, previousRevision, noop, emptiedOut);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", document=" + document +
                ", patchUpdate=" + patchUpdate +
                ", previousRevision=" + previousRevision +
                ", noop=" + noop +
                ", emptiedOut=" + emptiedOut +
                "]";
    }
}
