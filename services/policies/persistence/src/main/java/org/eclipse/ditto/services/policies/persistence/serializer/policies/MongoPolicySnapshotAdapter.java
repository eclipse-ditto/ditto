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
package org.eclipse.ditto.services.policies.persistence.serializer.policies;

import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.utils.akka.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;

import com.mongodb.DBObject;

import akka.actor.ActorSystem;
import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotOffer;

/**
 * SnapshotAdapter for {@link Policy}s persisted to/from MongoDB.
 */
public final class MongoPolicySnapshotAdapter implements SnapshotAdapter<Policy> {

    private static final Function<JsonObject, Policy> JSON_TO_POLICY_FUNCTION = PoliciesModelFactory::newPolicy;

    private final ActorSystem system;

    public MongoPolicySnapshotAdapter(final ActorSystem system) {
        this.system = system;
    }

    @Override
    public Object toSnapshotStore(final Policy snapshotEntity) {
        final JsonObject json =
                snapshotEntity.toJson(snapshotEntity.getImplementedSchemaVersion(), FieldType.regularOrSpecial());
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        return dittoBsonJson.parse(json);
    }

    @Nullable
    @Override
    public Policy fromSnapshotStore(final SnapshotOffer snapshotOffer) {
        final String persistenceId = snapshotOffer.metadata().persistenceId();
        final Object snapshotEntity = snapshotOffer.snapshot();
        return createPolicyFromSnapshot(persistenceId, snapshotEntity);
    }

    @Nullable
    @Override
    public Policy fromSnapshotStore(final SelectedSnapshot selectedSnapshot) {
        final String persistenceId = selectedSnapshot.metadata().persistenceId();
        final Object snapshotEntity = selectedSnapshot.snapshot();
        return createPolicyFromSnapshot(persistenceId, snapshotEntity);
    }

    @Nullable
    private Policy createPolicyFromSnapshot(final String persistenceId, final Object snapshotEntity) {
        if (snapshotEntity instanceof DBObject) {
            final DBObject dbObject = (DBObject) snapshotEntity;
            // currently only supported: snapshots starting with "policy:" are Policies
            if (persistenceId.startsWith("policy:")) {
                final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
                return tryToCreatePolicyFrom(dittoBsonJson.serialize(dbObject));
            } else {
                throw new IllegalArgumentException(
                        "Unknown persistenceId - Unable to restore Policy Snapshot! Was: " + persistenceId);
            }
        } else {
            throw new IllegalArgumentException(
                    "Unable to fromSnapshotStore a non-'DBObject' object! Was: " + snapshotEntity.getClass());
        }
    }

    private Policy tryToCreatePolicyFrom(final JsonValue json) {
        try {
            return JSON_TO_POLICY_FUNCTION.apply(json.asObject());
        } catch (final JsonParseException e) {
            if (system != null) {
                system.log().error(e, "Could not deserialize JSON: '{}'", json);
            } else {
                System.err.println("Could not deserialize JSON: '" + json + "': " + e.getMessage());
            }
            return null;
        }
    }

}
