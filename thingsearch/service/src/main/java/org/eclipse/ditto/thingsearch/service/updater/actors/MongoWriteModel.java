/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import java.util.Objects;

import org.bson.BsonDocument;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;

import com.mongodb.client.model.WriteModel;

/**
 * Message replied by ThingUpdater when requested to compute a write model.
 */
public final class MongoWriteModel {

    private final AbstractWriteModel abstractWriteModel;
    private final WriteModel<BsonDocument> writeModel;
    private final boolean patchUpdate;

    private MongoWriteModel(final AbstractWriteModel abstractWriteModel,
            final WriteModel<BsonDocument> writeModel,
            final boolean patchUpdate) {
        this.abstractWriteModel = abstractWriteModel;
        this.writeModel = writeModel;
        this.patchUpdate = patchUpdate;
    }

    /**
     * Create a MongoWriteModel object.
     *
     * @param abstractWriteModel the Ditto write model.
     * @param writeModel the write model.
     * @param isPatchUpdate whether it is a patch update.
     * @return the MongoWriteModel object.
     */
    public static MongoWriteModel of(
            final AbstractWriteModel abstractWriteModel,
            final WriteModel<BsonDocument> writeModel,
            final boolean isPatchUpdate) {
        return new MongoWriteModel(abstractWriteModel, writeModel, isPatchUpdate);
    }

    /**
     * Get the Ditto write model.
     *
     * @return the write model.
     */
    public AbstractWriteModel getDitto() {
        return abstractWriteModel;
    }

    /**
     * Get the MongoDB client write model.
     *
     * @return the write model.
     */
    public WriteModel<BsonDocument> getBson() {
        return writeModel;
    }

    /**
     * Get whether the write model is a patch update.
     *
     * @return whether this is a patch update.
     */
    public boolean isPatchUpdate() {
        return patchUpdate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MongoWriteModel that = (MongoWriteModel) o;
        return Objects.equals(abstractWriteModel, that.abstractWriteModel) &&
                Objects.equals(writeModel, that.writeModel) &&
                patchUpdate == that.patchUpdate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(abstractWriteModel, writeModel, patchUpdate);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [" + "abstractWriteModel=" + abstractWriteModel +
                ", " + "writeModel=" + writeModel +
                ", " + "patchUpdate=" + patchUpdate +
                "]";
    }

}
