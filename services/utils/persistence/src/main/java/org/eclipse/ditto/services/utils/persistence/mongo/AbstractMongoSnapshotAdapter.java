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
package org.eclipse.ditto.services.utils.persistence.mongo;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.slf4j.Logger;

import com.mongodb.DBObject;

import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotOffer;

/**
 * Abstract implementation of a MongoDB specific
 * {@link org.eclipse.ditto.services.utils.persistence.SnapshotAdapter} for a
 * {@link org.eclipse.ditto.model.base.json.Jsonifiable}.
 *
 * @param <T> the jsonifiable type to snapshot.
 */
@ThreadSafe
public abstract class AbstractMongoSnapshotAdapter<T extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField>>
        implements SnapshotAdapter<T> {

    private final Logger logger;

    protected AbstractMongoSnapshotAdapter(final Logger logger) {
        this.logger = logger;
    }

    /**
     * This method is called exactly once when a snapshot is created. It does nothing by default. Subclasses
     * may override it to inject code.
     *
     * @param snapshotEntity The entity for which the snapshot is created.
     * @param json The JSON object to store as snapshot.
     */
    protected void onSnapshotStoreConversion(final T snapshotEntity, final JsonObject json) {
        // does nothing by default
    }

    @Override
    public Object toSnapshotStore(final T snapshotEntity) {
        checkNotNull(snapshotEntity, "snapshot entity");
        final JsonObject json = convertToJson(snapshotEntity);

        onSnapshotStoreConversion(snapshotEntity, json);

        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        return dittoBsonJson.parse(json);
    }

    @Override
    public T fromSnapshotStore(final SnapshotOffer snapshotOffer) {
        return convertSnapshotToJsonifiable(snapshotOffer.snapshot());
    }

    @Override
    public T fromSnapshotStore(final SelectedSnapshot selectedSnapshot) {
        return convertSnapshotToJsonifiable(selectedSnapshot.snapshot());
    }

    /**
     * Converts the specified snapshot entity to its {@link org.eclipse.ditto.json.JsonObject} representation.
     *
     * @param snapshotEntity the snapshot entity to be converted to a JsonObject.
     * @return {@code snapshotEntity} as JsonObject.
     * @throws NullPointerException if {@code snapshotEntity} is {@code null}.
     */
    protected JsonObject convertToJson(final T snapshotEntity) {
        checkNotNull(snapshotEntity, "snapshot entity");
        return snapshotEntity.toJson(snapshotEntity.getImplementedSchemaVersion(), FieldType.regularOrSpecial());
    }

    /**
     * Algorithm to convert a raw snapshot entity to a Jsonifiable.
     *
     * @param rawSnapshotEntity the snapshot entity to be converted.
     * @return a Jsonifiable whose origin is {@code rawSnapshotEntity} or {@code null}.
     * @throws NullPointerException if {@code rawSnapshotEntity} is {@code null}.
     * @throws IllegalArgumentException if {@code rawSnapshotEntity} is not an instance of {@code DBObject}.
     */
    @Nullable
    private T convertSnapshotToJsonifiable(@Nonnull final Object rawSnapshotEntity) {
        final DBObject snapshotEntityAsDBObject = getSnapshotEntityAsDBObject(rawSnapshotEntity);
        final JsonObject jsonObject = convertToJson(snapshotEntityAsDBObject);
        return tryToCreateJsonifiableFrom(jsonObject);
    }

    private static DBObject getSnapshotEntityAsDBObject(@Nonnull final Object rawSnapshotEntity) {
        checkNotNull(rawSnapshotEntity, "raw snapshot entity");
        if (!(rawSnapshotEntity instanceof DBObject)) {
            final String pattern = "Unable to create a Jsonifiable from <{0}>! Expected was a DBObject instance.";
            throw new IllegalArgumentException(MessageFormat.format(pattern, rawSnapshotEntity.getClass()));
        }
        return (DBObject) rawSnapshotEntity;
    }

    /**
     * Converts the specified DBObject to a {@link org.eclipse.ditto.json.JsonObject}.
     *
     * @param dbObject the DBObject to be converted.
     * @return a JsonObject whose origin is {@code dbObject}.
     * @throws NullPointerException if {@code dbObject} is {@code null}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code dbObject} cannot be serialized to a
     * JsonObject.
     */
    private static JsonObject convertToJson(@Nonnull final DBObject dbObject) {
        checkNotNull(dbObject, "DBObject to be converted");
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        final JsonObject jsonObject = dittoBsonJson.serialize(dbObject).asObject();
        return DittoJsonException.wrapJsonRuntimeException(() -> jsonObject);
    }

    @Nullable
    private T tryToCreateJsonifiableFrom(@Nonnull final JsonObject jsonObject) {
        try {
            return createJsonifiableFrom(jsonObject);
        } catch (final JsonParseException | DittoRuntimeException e) {
            final String pattern = "Failed to deserialize JSON <{0}>!";
            logger.error(MessageFormat.format(pattern, jsonObject), e);
            return null;
        }
    }

    /**
     * Creates a Jsonifiable of type {@code T} from the specified JSON object.
     *
     * @param jsonObject a JSON Object representation of a Jsonifiable.
     * @return the Jsonifiable which originates from {@code jsonObject}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} does not have the correct format.
     */
    protected abstract T createJsonifiableFrom(@Nonnull JsonObject jsonObject);

}
