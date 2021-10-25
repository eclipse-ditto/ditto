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

import javax.annotation.concurrent.Immutable;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;

import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.WriteModel;

/**
 * Write model for deletion of a Thing.
 */
@Immutable
public final class ThingDeleteModel extends AbstractWriteModel {

    private ThingDeleteModel(final Metadata metadata) {
        super(metadata);
    }

    /**
     * Create a DeleteModel object from Metadata.
     *
     * @param metadata the metadata.
     * @return the DeleteModel.
     */
    public static ThingDeleteModel of(final Metadata metadata) {
        return new ThingDeleteModel(metadata);
    }

    @Override
    public WriteModel<BsonDocument> toMongo() {
        final Bson filter = getFilter();
        return new DeleteOneModel<>(filter);
    }

    @Override
    public String toString() {
        return super.toString() + "]";
    }
}
