/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.model;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETE_AT;

import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;

/**
 * Write model for deletion of a Thing.
 */
public class ThingDeleteModel extends AbstractWriteModel {

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
    public WriteModel<Document> toMongo() {
        final Bson filter = getFilter();
        final Bson update = new BsonDocument().append(SET,
                new BsonDocument().append(FIELD_DELETE_AT, new BsonDateTime(0L)));
        final UpdateOptions updateOptions = new UpdateOptions().bypassDocumentValidation(true);
        return new UpdateOneModel<>(filter, update, updateOptions);
    }

}
