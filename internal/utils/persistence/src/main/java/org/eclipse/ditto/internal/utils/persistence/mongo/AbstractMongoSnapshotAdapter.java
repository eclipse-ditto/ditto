/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.bson.BsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.slf4j.Logger;

import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotOffer;

/**
 * Abstract implementation of a MongoDB specific {@link SnapshotAdapter} for a {@link Jsonifiable}.
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
     * Whether an entity is deleted.
     *
     * @param snapshotEntity the entity.
     * @return whether it has the deleted lifecycle.
     */
    protected abstract boolean isDeleted(final T snapshotEntity);

    /**
     * Return the snapshot JSON field for lifecycle of deleted entities.
     *
     * @return the empty snapshot JSON.
     */
    protected abstract JsonField getDeletedLifecycleJsonField();

    /**
     * Return the revision JSON field if present in the entity.
     *
     * @return the revision JSON field.
     */
    protected abstract Optional<JsonField> getRevisionJsonField(final T entity);

    @Override
    public Object toSnapshotStore(final T snapshotEntity) {
        final JsonObject json = convertToJson(checkNotNull(snapshotEntity, "snapshot entity"));

        onSnapshotStoreConversion(snapshotEntity, json);

        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        return dittoBsonJson.parse(json);
    }

    /**
     * This method is called exactly once when a snapshot is created.
     * It does nothing by default.
     * Subclasses may override it to inject code.
     *
     * @param snapshotEntity The entity for which the snapshot is created.
     * @param json The JSON object to store as snapshot.
     */
    protected void onSnapshotStoreConversion(final T snapshotEntity, final JsonObject json) {
        // does nothing by default
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
     * Converts the specified snapshot entity to its {@link JsonObject} representation.
     *
     * @param snapshotEntity the snapshot entity to be converted to a JsonObject.
     * @return {@code snapshotEntity} as JsonObject.
     * @throws NullPointerException if {@code snapshotEntity} is {@code null}.
     */
    protected JsonObject convertToJson(final T snapshotEntity) {
        checkNotNull(snapshotEntity, "snapshot entity");
        if (isDeleted(snapshotEntity)) {
            final var builder = JsonObject.newBuilder().set(getDeletedLifecycleJsonField());
            getRevisionJsonField(snapshotEntity).ifPresent(builder::set);
            return builder.build();
        } else {
            return snapshotEntity.toJson(snapshotEntity.getImplementedSchemaVersion(), FieldType.all());
        }
    }

    /**
     * Algorithm to convert a raw snapshot entity to a Jsonifiable.
     *
     * @param rawSnapshotEntity the snapshot entity to be converted.
     * @return a Jsonifiable whose origin is {@code rawSnapshotEntity} or {@code null}.
     * @throws NullPointerException if {@code rawSnapshotEntity} is {@code null}.
     */
    @Nullable
    private T convertSnapshotToJsonifiable(final Object rawSnapshotEntity) {
        return tryToCreateJsonifiableFrom(convertSnapshotEntityToJson(rawSnapshotEntity));
    }

    private static JsonObject convertSnapshotEntityToJson(final Object rawSnapshotEntity) {
        checkNotNull(rawSnapshotEntity, "raw snapshot entity");
        if (rawSnapshotEntity instanceof BsonValue bsonValue) {
            return convertToJson(bsonValue);
        }
        final String pattern = "Unable to create a Jsonifiable from <{0}>! Expected was a BsonDocument instance.";
        throw new IllegalArgumentException(MessageFormat.format(pattern, rawSnapshotEntity.getClass()));
    }

    /**
     * Converts the specified BsonDocument to a {@link JsonObject}.
     *
     * @param bsonValue the BsonDocument to be converted.
     * @return a JsonObject whose origin is {@code bsonValue}.
     * @throws NullPointerException if {@code bsonValue} is {@code null}.
     * @throws DittoJsonException if {@code bsonValue} cannot be serialized to a
     * JsonObject.
     */
    private static JsonObject convertToJson(final BsonValue bsonValue) {
        checkNotNull(bsonValue, "BsonValue to be converted");
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        final JsonObject jsonObject = dittoBsonJson.serialize(bsonValue).asObject();
        return DittoJsonException.wrapJsonRuntimeException(() -> jsonObject);
    }

    @Nullable
    private T tryToCreateJsonifiableFrom(final JsonObject jsonObject) {
        try {
            final var deletedLifecycleField = getDeletedLifecycleJsonField();
            if (jsonObject.getValue(deletedLifecycleField.getKey())
                    .filter(deletedLifecycleField.getValue()::equals)
                    .isPresent()) {
                return null; // entity is deleted
            } else {
                return createJsonifiableFrom(jsonObject);
            }
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
    protected abstract T createJsonifiableFrom(JsonObject jsonObject);

}
