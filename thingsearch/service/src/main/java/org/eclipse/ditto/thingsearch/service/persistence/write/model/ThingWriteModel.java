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
package org.eclipse.ditto.thingsearch.service.persistence.write.model;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.bson.BsonDocument;
import org.bson.BsonInvalidOperationException;
import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.BsonDiff;
import org.eclipse.ditto.thingsearch.service.updater.actors.MongoWriteModel;
import org.eclipse.ditto.thingsearch.service.updater.actors.ThingUpdater;
import org.mongodb.scala.bson.BsonNumber;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

/**
 * Write model for an entire Thing.
 */
@NotThreadSafe
public final class ThingWriteModel extends AbstractWriteModel {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory.getThreadSafeLogger(ThingUpdater.class);

    private static final Counter PATCH_UPDATE_COUNT = DittoMetrics.counter("wildcard_search_patch_updates");
    private static final Counter PATCH_SKIP_COUNT = DittoMetrics.counter("wildcard_search_patch_skips");
    private static final Counter FULL_UPDATE_COUNT = DittoMetrics.counter("wildcard_search_full_updates");

    private final BsonDocument thingDocument;
    private final boolean isPatchUpdate;
    private final long previousRevision;

    private ThingWriteModel(final Metadata metadata, final BsonDocument thingDocument, final boolean isPatchUpdate,
            final long previousRevision) {
        super(metadata);
        this.thingDocument = thingDocument;
        this.isPatchUpdate = isPatchUpdate;
        this.previousRevision = previousRevision;
    }

    /**
     * Create a Thing write model.
     *
     * @param metadata the metadata.
     * @param thingDocument the document to write into the search index.
     * @return a Thing write model.
     */
    public static ThingWriteModel of(final Metadata metadata, final BsonDocument thingDocument) {
        return new ThingWriteModel(metadata, thingDocument, false, 0L);
    }

    @Override
    public Optional<MongoWriteModel> toIncrementalMongo(@Nullable final AbstractWriteModel previousWriteModel) {
        if (previousWriteModel instanceof ThingWriteModel thingWriteModel) {
            return computeDiff(thingWriteModel);
        } else {
            return super.toIncrementalMongo(previousWriteModel);
        }
    }

    /**
     * Return a copy of this object as patch update.
     *
     * @param previousRevision The expected previous revision. The patch will not be applied if the previous revision
     * differ.
     * @return The patch update.
     */
    public ThingWriteModel asPatchUpdate(final long previousRevision) {
        return new ThingWriteModel(getMetadata(), thingDocument, true, previousRevision);
    }

    @Override
    public WriteModel<BsonDocument> toMongo() {
        return new ReplaceOneModel<>(getFilter(), thingDocument, upsertOption());
    }

    @Override
    public ThingWriteModel setMetadata(final Metadata metadata) {
        return new ThingWriteModel(metadata, thingDocument, isPatchUpdate, previousRevision);
    }

    /**
     * @return the Thing document to be written in the persistence.
     */
    public BsonDocument getThingDocument() {
        return thingDocument;
    }

    @Override
    public Bson getFilter() {
        if (isPatchUpdate) {
            return Filters.and(
                    super.getFilter(),
                    Filters.eq(PersistenceConstants.FIELD_REVISION, BsonNumber.apply(previousRevision))
            );
        } else {
            return super.getFilter();
        }
    }

    private ReplaceOptions upsertOption() {
        return new ReplaceOptions().upsert(!isPatchUpdate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ThingWriteModel that = (ThingWriteModel) o;
        return thingDocument.equals(that.thingDocument) &&
                isPatchUpdate == that.isPatchUpdate &&
                previousRevision == that.previousRevision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingDocument, isPatchUpdate, previousRevision);
    }

    @Override
    public String toString() {
        return super.toString() +
                ", thingDocument=" + thingDocument +
                ", isPatchUpdate=" + isPatchUpdate +
                ", previousRevision=" + previousRevision +
                "]";
    }

    private Optional<MongoWriteModel> computeDiff(final ThingWriteModel lastWriteModel) {
        final WriteModel<BsonDocument> mongoWriteModel;
        final boolean isPatchUpdate;

        if (isNextWriteModelOutDated(lastWriteModel, this)) {
            // possible due to background sync
            LOGGER.debug("Received out-of-date write model. this=<{}>, lastWriteModel=<{}>", this, lastWriteModel);
            PATCH_SKIP_COUNT.increment();
            return Optional.empty();
        }
        final Optional<BsonDiff> diff = tryComputeDiff(getThingDocument(), lastWriteModel.getThingDocument());
        if (diff.isPresent() && diff.get().isDiffSmaller()) {
            final var aggregationPipeline = diff.get().consumeAndExport();
            if (aggregationPipeline.isEmpty()) {
                LOGGER.debug("Skipping update due to {} <{}>", "empty diff", ((AbstractWriteModel) this).getClass().getSimpleName());
                LOGGER.trace("Skipping update due to {} <{}>", "empty diff", this);
                PATCH_SKIP_COUNT.increment();
                return Optional.empty();
            }
            final var filter = asPatchUpdate(lastWriteModel.getMetadata().getThingRevision()).getFilter();
            mongoWriteModel = new UpdateOneModel<>(filter, aggregationPipeline);
             LOGGER.debug("Using incremental update <{}>", mongoWriteModel.getClass().getSimpleName());
             LOGGER.trace("Using incremental update <{}>", mongoWriteModel);
             PATCH_UPDATE_COUNT.increment();
            isPatchUpdate = true;
        } else {
            mongoWriteModel = this.toMongo();
             LOGGER.debug("Using replacement because diff is bigger or nonexistent: <{}>",
                    mongoWriteModel.getClass().getSimpleName());
             if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Using replacement because diff is bigger or nonexistent. Diff=<{}>",
                        diff.map(BsonDiff::consumeAndExport));
             }
             FULL_UPDATE_COUNT.increment();
            isPatchUpdate = false;
        }
        return Optional.of(MongoWriteModel.of(this, mongoWriteModel, isPatchUpdate));
    }

    private Optional<BsonDiff> tryComputeDiff(final BsonDocument minuend, final BsonDocument subtrahend) {
        try {
            return Optional.of(BsonDiff.minusThingDocs(minuend, subtrahend));
        } catch (final BsonInvalidOperationException e) {
            LOGGER.error("Failed to compute BSON diff between <{}> and <{}>", minuend, subtrahend, e);
            return Optional.empty();
        }
    }

    private static boolean isNextWriteModelOutDated(final AbstractWriteModel lastWriteModel,
            final AbstractWriteModel nextWriteModel) {

        final var lastMetadata = lastWriteModel.getMetadata();
        final var nextMetadata = nextWriteModel.getMetadata();
        final boolean isStrictlyOlder = nextMetadata.getThingRevision() < lastMetadata.getThingRevision() ||
                nextMetadata.getThingRevision() == lastMetadata.getThingRevision() &&
                        nextMetadata.getPolicyRevision().flatMap(nextPolicyRevision ->
                                        lastMetadata.getPolicyRevision().map(lastPolicyRevision ->
                                                nextPolicyRevision < lastPolicyRevision))
                                .orElse(false);
        final boolean hasSameRevisions = nextMetadata.getThingRevision() == lastMetadata.getThingRevision() &&
                nextMetadata.getPolicyRevision().equals(lastMetadata.getPolicyRevision());

        return isStrictlyOlder || hasSameRevisions;
    }
}
