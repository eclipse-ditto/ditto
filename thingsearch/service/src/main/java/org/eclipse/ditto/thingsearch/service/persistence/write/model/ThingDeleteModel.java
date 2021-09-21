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

import javax.annotation.concurrent.Immutable;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;

import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;

/**
 * Write model for deletion of a Thing.
 */
@Immutable
public final class ThingDeleteModel extends AbstractWriteModel {

    private final boolean deleteImmediately;

    private ThingDeleteModel(final Metadata metadata, final boolean deleteImmediately) {
        super(metadata);
        this.deleteImmediately = deleteImmediately;
    }

    /**
     * Create a DeleteModel object from Metadata.
     *
     * @param metadata the metadata.
     * @param deleteImmediately whether to delete entries immediately.
     * @return the DeleteModel.
     */
    public static ThingDeleteModel of(final Metadata metadata, final boolean deleteImmediately) {
        return new ThingDeleteModel(metadata, deleteImmediately);
    }

    @Override
    public WriteModel<BsonDocument> toMongo() {
        final Bson filter = getFilter();
        if (deleteImmediately || getMetadata().isShouldAcknowledge()) {
            return new DeleteOneModel<>(filter);
        } else {
            final Bson update = new BsonDocument().append(SET,
                    new BsonDocument().append(PersistenceConstants.FIELD_DELETE_AT, new BsonDateTime(0L)));
            final UpdateOptions updateOptions = new UpdateOptions().bypassDocumentValidation(true);
            return new UpdateOneModel<>(filter, update, updateOptions);
        }
    }

    @Override
    public boolean equals(final Object other) {
        return super.equals(other) && deleteImmediately == ((ThingDeleteModel) other).deleteImmediately;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), deleteImmediately);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getMetadata() +
                ", deleteImmediately" + deleteImmediately
                + "]";
    }
}
