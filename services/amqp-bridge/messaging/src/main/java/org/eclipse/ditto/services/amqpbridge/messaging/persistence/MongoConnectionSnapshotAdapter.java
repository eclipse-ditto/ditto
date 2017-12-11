/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.messaging.persistence;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.services.utils.akka.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;

import com.mongodb.DBObject;

import akka.actor.ActorSystem;
import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotOffer;

/**
 * SnapshotAdapter for {@link String}s persisted to/from MongoDB.
 */
public final class MongoConnectionSnapshotAdapter implements SnapshotAdapter<ConnectionData> {

    private final ActorSystem system;

    public MongoConnectionSnapshotAdapter(final ActorSystem system) {
        this.system = system;
    }

    @Override
    public Object toSnapshotStore(final ConnectionData snapshotEntity) {
        final JsonObject jsonObject = snapshotEntity.toJson();
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        return dittoBsonJson.parse(jsonObject);
    }

    @Nullable
    @Override
    public ConnectionData fromSnapshotStore(final SnapshotOffer snapshotOffer) {
        final String persistenceId = snapshotOffer.metadata().persistenceId();
        final Object snapshotEntity = snapshotOffer.snapshot();
        return createConnectionDataFromSnapshot(persistenceId, snapshotEntity);
    }

    @Nullable
    @Override
    public ConnectionData fromSnapshotStore(final SelectedSnapshot selectedSnapshot) {
        final String persistenceId = selectedSnapshot.metadata().persistenceId();
        final Object snapshotEntity = selectedSnapshot.snapshot();
        return createConnectionDataFromSnapshot(persistenceId, snapshotEntity);
    }

    @Nullable
    private ConnectionData createConnectionDataFromSnapshot(final String persistenceId, final Object snapshotEntity) {
        if (snapshotEntity instanceof DBObject) {
            final DBObject dbObject = (DBObject) snapshotEntity;
            final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
            return tryToCreateConnectionDataFrom(dittoBsonJson.serialize(dbObject));
        } else {
            throw new IllegalArgumentException(
                    "Unable to fromSnapshotStore a non-'DBObject' object! Was: " + snapshotEntity.getClass());
        }
    }

    private ConnectionData tryToCreateConnectionDataFrom(final JsonValue json) {
        try {
            return ConnectionData.fromJson(json.asObject());
        } catch (final UnsupportedOperationException | JsonParseException e) {
            if (system != null) {
                system.log().error(e, "Could not deserialize JSON: '{}'", json);
            } else {
                System.err.println("Could not deserialize JSON: '" + json + "': " + e.getMessage());
            }
            return null;
        }
    }

}
