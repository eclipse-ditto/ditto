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

import javax.annotation.concurrent.Immutable;

import org.bson.Document;

import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;

/**
 * Write model for an entire Thing.
 */
@Immutable
public final class ThingWriteModel extends AbstractWriteModel {

    private final Document thingDocument;

    private ThingWriteModel(final Metadata metadata, final Document thingDocument) {
        super(metadata);
        this.thingDocument = thingDocument;
    }

    /**
     * Create a Thing write model.
     *
     * @param metadata the metadata.
     * @param thingDocument the document to write into the search index.
     * @return a Thing write model.
     */
    public static ThingWriteModel of(final Metadata metadata, final Document thingDocument) {
        return new ThingWriteModel(metadata, thingDocument);
    }

    @Override
    public WriteModel<Document> toMongo() {
        return new ReplaceOneModel<>(getFilter(), thingDocument, upsert());
    }

    /**
     * @return the Thing document to be written in the persistence.
     */
    public Document getThingDocument() {
        return thingDocument;
    }

    private static UpdateOptions upsert() {
        return new UpdateOptions().upsert(true);
    }
}
