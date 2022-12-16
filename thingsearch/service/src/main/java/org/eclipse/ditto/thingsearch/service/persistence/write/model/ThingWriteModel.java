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

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_F_ARRAY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_GLOBAL_READ;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_NAMESPACE;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_REVISION;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REVISION;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_THING;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonInvalidOperationException;
import org.bson.BsonString;
import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.models.streaming.AbstractEntityIdWithRevision;
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

    /**
     * Create a Thing write model which only preserves "toplevel" fields:
     * <ul>
     * <li>{@link org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants#FIELD_ID}</li>
     * <li>{@link org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants#FIELD_NAMESPACE}</li>
     * <li>{@link org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants#FIELD_REVISION}</li>
     * <li>{@link org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants#FIELD_POLICY_ID}</li>
     * <li>{@link org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants#FIELD_POLICY_REVISION}</li>
     * </ul>
     * and "emtpies" all other fields, e.g. containing the thing payload.
     *
     * @param metadata the metadata.
     * @return a Thing write model.
     */
    public static ThingWriteModel ofEmptiedOut(final Metadata metadata) {
        final BsonDocument emptiedOutThingDocument = new BsonDocument()
                .append(FIELD_ID, new BsonString(metadata.getThingId().toString()))
                .append(FIELD_NAMESPACE, new BsonString(metadata.getThingId().getNamespace()))
                .append(FIELD_GLOBAL_READ, new BsonArray())
                .append(FIELD_REVISION, new BsonInt64(metadata.getThingRevision()))
                .append(FIELD_POLICY_ID, new BsonString(metadata.getPolicyIdInPersistence()))
                .append(FIELD_POLICY_REVISION, new BsonInt64(metadata.getThingPolicyTag()
                        .map(AbstractEntityIdWithRevision::getRevision).orElse(0L)))
                .append(FIELD_THING, new BsonDocument())
                .append(FIELD_POLICY, new BsonDocument())
                .append(FIELD_F_ARRAY, new BsonArray());
        return new ThingWriteModel(metadata, emptiedOutThingDocument, false, 0L);
    }

    @Override
    public Optional<MongoWriteModel> toIncrementalMongo(@Nullable final AbstractWriteModel previousWriteModel,
            final int maxWireVersion) {
        if (previousWriteModel instanceof ThingWriteModel thingWriteModel) {
            return computeDiff(thingWriteModel, maxWireVersion);
        } else {
            return super.toIncrementalMongo(previousWriteModel, maxWireVersion);
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

    private Optional<MongoWriteModel> computeDiff(final ThingWriteModel lastWriteModel, final int maxWireVersion) {
        final ThingWriteModel thingWriteModel;
        final WriteModel<BsonDocument> mongoWriteModel;
        final boolean isPatchUpdate1;

        if (isNextWriteModelOutDated(lastWriteModel, this)) {
            // possible due to background sync
            LOGGER.debug("Received out-of-date write model. this=<{}>, lastWriteModel=<{}>", this, lastWriteModel);
            PATCH_SKIP_COUNT.increment();
            return Optional.empty();
        }
        final var diff = tryComputeDiff(getThingDocument(), lastWriteModel.getThingDocument(), maxWireVersion);
        if (diff.isPresent() && diff.get().isDiffSmaller()) {
            final var aggregationPipeline = diff.get().consumeAndExport();
            if (aggregationPipeline.isEmpty()) {
                LOGGER.debug("Skipping update due to {} <{}>", "empty diff",
                        ((AbstractWriteModel) this).getClass().getSimpleName());
                LOGGER.trace("Skipping update due to {} <{}>", "empty diff", this);
                PATCH_SKIP_COUNT.increment();
                return Optional.empty();
            }
            thingWriteModel = asPatchUpdate(lastWriteModel.getMetadata().getThingRevision());
            final var filter = thingWriteModel.getFilter();
            mongoWriteModel = new UpdateOneModel<>(filter, aggregationPipeline);
            LOGGER.debug("Using incremental update <{}>", mongoWriteModel.getClass().getSimpleName());
            LOGGER.trace("Using incremental update <{}>", mongoWriteModel);
            PATCH_UPDATE_COUNT.increment();
            isPatchUpdate1 = true;
        } else {
            thingWriteModel = this;
            mongoWriteModel = this.toMongo();
            LOGGER.debug("Using replacement because diff is bigger or nonexistent: <{}>",
                    mongoWriteModel.getClass().getSimpleName());
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Using replacement because diff is bigger or nonexistent. Diff=<{}>",
                        diff.map(BsonDiff::consumeAndExport));
            }
            FULL_UPDATE_COUNT.increment();
            isPatchUpdate1 = false;
        }
        return Optional.of(MongoWriteModel.of(thingWriteModel, mongoWriteModel, isPatchUpdate1));
    }

    private Optional<BsonDiff> tryComputeDiff(final BsonDocument minuend, final BsonDocument subtrahend,
            final int maxWireVersion) {
        try {
            return Optional.of(BsonDiff.minusThingDocs(minuend, subtrahend, maxWireVersion));
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
                        anyPolicyRevisionIsOutDated(lastMetadata, nextMetadata);
        final boolean hasSameRevisions = nextMetadata.getThingRevision() == lastMetadata.getThingRevision() &&
                allPolicyRevisionsStayedTheSame(lastMetadata, nextMetadata);

        return isStrictlyOlder || hasSameRevisions;
    }

    private static boolean anyPolicyRevisionIsOutDated(final Metadata lastMetadata, final Metadata nextMetadata) {
        return nextMetadata.getAllReferencedPolicyTags().stream()
                .anyMatch(policyTag -> lastMetadata.getAllReferencedPolicyTags().stream()
                        .filter(oldPolicyTag -> oldPolicyTag.getEntityId().equals(policyTag.getEntityId()))
                        .findAny()
                        .map(oldPolicyTag -> policyTag.getRevision() < oldPolicyTag.getRevision())
                        .orElse(false));
    }

    private static boolean allPolicyRevisionsStayedTheSame(final Metadata lastMetadata, final Metadata nextMetadata) {
        return nextMetadata.getAllReferencedPolicyTags().stream()
                .allMatch(policyTag -> lastMetadata.getAllReferencedPolicyTags().stream()
                        .filter(oldPolicyTag -> oldPolicyTag.getEntityId().equals(policyTag.getEntityId()))
                        .findAny()
                        .map(oldPolicyTag -> policyTag.getRevision() == oldPolicyTag.getRevision())
                        .orElse(true));
    }

}
