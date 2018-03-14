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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;

import com.mongodb.DBObject;

import akka.actor.ActorSystem;
import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotOffer;

/**
 * SnapshotAdapter for {@link Set}s of {@link String}s persisted to/from MongoDB.
 */
public final class MongoReconnectSnapshotAdapter implements SnapshotAdapter<Set<String>> {

    private final ActorSystem system;

    public MongoReconnectSnapshotAdapter(final ActorSystem system) {
        this.system = system;
    }

    @Override
    public Object toSnapshotStore(final Set<String> snapshotEntity) {
        final JsonArray values = snapshotEntity.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        return dittoBsonJson.parse(values);
    }

    @Nullable
    @Override
    public Set<String> fromSnapshotStore(final SnapshotOffer snapshotOffer) {
        final String persistenceId = snapshotOffer.metadata().persistenceId();
        final Object snapshotEntity = snapshotOffer.snapshot();
        return createSetFromSnapshot(persistenceId, snapshotEntity);
    }

    @Nullable
    @Override
    public Set<String> fromSnapshotStore(final SelectedSnapshot selectedSnapshot) {
        final String persistenceId = selectedSnapshot.metadata().persistenceId();
        final Object snapshotEntity = selectedSnapshot.snapshot();
        return createSetFromSnapshot(persistenceId, snapshotEntity);
    }

    @Nullable
    private Set<String> createSetFromSnapshot(final String persistenceId, final Object snapshotEntity) {
        if (snapshotEntity instanceof DBObject) {
            final DBObject dbObject = (DBObject) snapshotEntity;
            final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
            return tryToCreateSetFrom(dittoBsonJson.serialize(dbObject));
        } else {
            throw new IllegalArgumentException(
                    "Unable to fromSnapshotStore a non-'DBObject' object! Was: " + snapshotEntity.getClass());
        }
    }

    private Set<String> tryToCreateSetFrom(final JsonValue json) {
        try {
            return json.asArray().stream()
                    .filter(JsonValue::isString)
                    .map(JsonValue::asString)
                    .collect(Collectors.toSet());
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
