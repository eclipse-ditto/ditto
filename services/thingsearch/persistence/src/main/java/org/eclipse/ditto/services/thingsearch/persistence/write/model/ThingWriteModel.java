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
package org.eclipse.ditto.services.thingsearch.persistence.write.model;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.bson.Document;

import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;

/**
 * Write model for an entire Thing.
 */
@NotThreadSafe
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
        return thingDocument.equals(that.thingDocument);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingDocument);
    }

}
