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
package org.eclipse.ditto.services.things.persistence.serializer.things;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.akka.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DBObject;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotOffer;

/**
 * SnapshotAdapter for {@link Thing}s persisted to/from MongoDB.
 */
@ThreadSafe
abstract class MongoThingSnapshotAdapter<T extends Thing> implements SnapshotAdapter<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoThingSnapshotAdapter.class);

    private final ActorSystem system;

    /**
     * Constructs a new {@code MongoThingSnapshotAdapter} object.
     *
     * @param system if specified, provides the logger which is used if adapting the SnapshotOffer to a Thing failed.
     */
    protected MongoThingSnapshotAdapter(@Nullable final ActorSystem system) {
        this.system = system;
    }

    @Override
    public Object toSnapshotStore(final T snapshotEntity) {
        final JsonObject json = convertToJson(snapshotEntity);

        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        return dittoBsonJson.parse(json);
    }

    /**
     * Converts the specified snapshot entity to its {@link JsonObject} representation.
     *
     * @param snapshotEntity the snapshot entity to be converted to a JsonObject.
     * @return {@code snapshotEntity} as JsonObject.
     * @throws NullPointerException if {@code snapshotEntity} is {@code null}.
     */
    @Nonnull
    protected JsonObject convertToJson(@Nonnull  final T snapshotEntity) {
        checkNotNull(snapshotEntity, "snapshot entity");
        return snapshotEntity.toJson(snapshotEntity.getImplementedSchemaVersion(), FieldType.regularOrSpecial());
    }

    /**
     * Returns the string to be used as snapshot data.
     *
     * @param thingJsonObject used as base to get the string.
     * @return the string.
     */
    @Nonnull
    private static String getJsonString(@Nonnull final JsonObject thingJsonObject) {
        return thingJsonObject.toString();
    }

    @Override
    public T fromSnapshotStore(final SnapshotOffer snapshotOffer) {
        validatePersistenceId(snapshotOffer.metadata());
        return convertSnapshotToThing(snapshotOffer.snapshot());
    }

    @Override
    public T fromSnapshotStore(final SelectedSnapshot selectedSnapshot) {
        validatePersistenceId(selectedSnapshot.metadata());
        return convertSnapshotToThing(selectedSnapshot.snapshot());
    }

    private static void validatePersistenceId(final SnapshotMetadata metadata) {
        final String persistenceId = metadata.persistenceId();

        // currently only snapshots starting with "thing:" are supported
        if (!persistenceId.startsWith("thing:")) {
            final String pattern = "Unknown persistence ID <{0}>! Unable to restore Thing Snapshot.";
            throw new IllegalArgumentException(MessageFormat.format(pattern, persistenceId));
        }
    }

    /**
     * Algorithm to convert a raw snapshot entity to a Thing.
     *
     * @param rawSnapshotEntity the snapshot entity to be converted.
     * @return a Thing whose origin is {@code rawSnapshotEntity} or {@code null}.
     * @throws NullPointerException if {@code rawSnapshotEntity} is {@code null}.
     * @throws IllegalArgumentException if {@code rawSnapshotEntity} is not an instance of {@code DBObject}.
     */
    @Nullable
    private T convertSnapshotToThing(@Nonnull final Object rawSnapshotEntity) {
        final DBObject snapshotEntityAsDBObject = getSnapshotEntityAsDBObject(rawSnapshotEntity);
        final JsonObject jsonObject = convertToJson(snapshotEntityAsDBObject);
        return tryToCreateThingFrom(jsonObject);
    }

    @Nonnull
    private static DBObject getSnapshotEntityAsDBObject(@Nonnull final Object rawSnapshotEntity) {
        checkNotNull(rawSnapshotEntity, "raw snapshot entity");
        if (!(rawSnapshotEntity instanceof DBObject)) {
            final String pattern = "Unable to create a Thing from <{0}>! Expected was a DBObject instance.";
            throw new IllegalArgumentException(MessageFormat.format(pattern, rawSnapshotEntity.getClass()));
        }
        return (DBObject) rawSnapshotEntity;
    }

    /**
     * Converts the specified DBObject to a {@link JsonObject}.
     *
     * @param dbObject the DBObject to be converted.
     * @return a JsonObject whose origin is {@code dbObject}.
     * @throws NullPointerException if {@code dbObject} is {@code null}.
     * @throws DittoJsonException if {@code dbObject} cannot be serialized to a JsonObject.
     */
    @Nonnull
    private static JsonObject convertToJson(@Nonnull final DBObject dbObject) {
        checkNotNull(dbObject, "DBObject to be converted");
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        final JsonObject jsonObject = dittoBsonJson.serialize(dbObject).asObject();
        return DittoJsonException.wrapJsonRuntimeException(() -> jsonObject);
    }

    @Nullable
    private T tryToCreateThingFrom(@Nonnull final JsonObject jsonObject) {
        try {
            return createThingFrom(jsonObject);
        } catch (final JsonParseException | DittoRuntimeException e) {
            if (system != null) {
                final LoggingAdapter logger = system.log();
                logger.error(e, "Failed to deserialize JSON <{}>!", jsonObject);
            } else {
                final String pattern = "Failed to deserialize JSON <{0}>!";
                LOGGER.error(MessageFormat.format(pattern, jsonObject),  e);
            }
            return null;
        }
    }

    /**
     * Creates a Thing of type {@code T} from the specified JSON object.
     *
     * @param jsonObject a JSON Object representation of a Thing.
     * @return the Thing which originates from {@code jsonObject} or {@code null}.
     */
    @Nullable
    protected abstract T createThingFrom(@Nonnull JsonObject jsonObject);

}
