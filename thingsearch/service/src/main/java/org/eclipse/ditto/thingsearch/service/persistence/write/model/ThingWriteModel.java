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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.conversions.Bson;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.mongodb.scala.bson.BsonNumber;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;

/**
 * Write model for an entire Thing.
 */
@NotThreadSafe
public final class ThingWriteModel extends AbstractWriteModel {

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
                    Filters.eq(PersistenceConstants.FIELD_ID, new BsonString(getMetadata().getThingId().toString())),
                    Filters.eq(PersistenceConstants.FIELD_REVISION, BsonNumber.apply(previousRevision))
            );
        } else {
            return super.getFilter();
        }
    }

    @Override
    public boolean isPatchUpdate() {
        return isPatchUpdate;
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

}
