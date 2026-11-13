/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.api.serializer;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.slf4j.Logger;

import com.typesafe.config.Config;

import org.apache.pekko.actor.ActorSystem;

/**
 * Backend-neutral serializer that converts a {@link Jsonifiable} snapshot entity to/from its {@link JsonObject}
 * representation.
 * <p>
 * This class holds the entire domain&harr;{@code JsonObject} serialization logic for snapshots: deleted-lifecycle
 * detection, revision extraction and the "snapshot taken" conversion hook. It contains <em>no</em> backend-specific
 * encoding (no BSON, no JSONB). A snapshot adapter is assembled from this per-service serializer plus the active
 * backend's {@link org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec} (BSON for MongoDB, JSONB for
 * PostgreSQL) by {@link NeutralSnapshotAdapter}; the backend-specific envelope is the only thing that differs between
 * backends.
 * <p>
 * It is a {@link DittoExtensionPoint}: each service selects its default concrete serializer via the {@code
 * ditto.extensions.snapshot-adapter} config key, and {@code org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceActor}
 * resolves it (through its overridable {@code resolveSnapshotSerializer} hook, so an actor managing a different entity
 * type than the JVM default — e.g. the WoT validation-config actor in the Things JVM — can supply its own) and combines
 * it with the single-per-JVM {@code provider.snapshotCodec()}.
 *
 * @param <T> the jsonifiable type to snapshot.
 */
@ThreadSafe
public abstract class SnapshotSerializer<T extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField>>
        implements DittoExtensionPoint {

    private final Logger logger;

    protected SnapshotSerializer(final Logger logger) {
        this.logger = logger;
    }

    /**
     * Whether an entity is deleted.
     *
     * @param snapshotEntity the entity.
     * @return whether it has the deleted lifecycle.
     */
    protected abstract boolean isDeleted(T snapshotEntity);

    /**
     * Return the snapshot JSON field for lifecycle of deleted entities.
     *
     * @return the deleted-lifecycle snapshot JSON field.
     */
    protected abstract JsonField getDeletedLifecycleJsonField();

    /**
     * Return the revision JSON field if present in the entity.
     *
     * @param entity the entity.
     * @return the revision JSON field.
     */
    protected abstract Optional<JsonField> getRevisionJsonField(T entity);

    /**
     * Creates a Jsonifiable of type {@code T} from the specified JSON object.
     *
     * @param jsonObject a JSON Object representation of a Jsonifiable.
     * @return the Jsonifiable which originates from {@code jsonObject}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} does not have the correct format.
     */
    protected abstract T createJsonifiableFrom(JsonObject jsonObject);

    /**
     * Converts the specified snapshot entity to its {@link JsonObject} representation, applying the
     * {@link #onSnapshotStoreConversion} side-effect hook exactly once.
     *
     * @param snapshotEntity the snapshot entity to be converted.
     * @return {@code snapshotEntity} as JsonObject.
     */
    public JsonObject toJson(final T snapshotEntity) {
        final JsonObject json = convertToJson(checkNotNull(snapshotEntity, "snapshot entity"));
        onSnapshotStoreConversion(snapshotEntity, json);
        return json;
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

    /**
     * Converts the raw, backend-decoded {@link JsonObject} representation of a snapshot back to its domain type,
     * returning {@code null} when the snapshot represents a deleted entity or could not be parsed.
     *
     * @param jsonObject the JSON object representation of the snapshot (already decoded from the backend envelope).
     * @return the domain type or {@code null}.
     */
    @Nullable
    public T fromJson(final JsonObject jsonObject) {
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
     * Loads the per-service {@code SnapshotSerializer} configured for the {@code ActorSystem} at the
     * {@code ditto.extensions.snapshot-adapter} config key (the key MongoDB deployments have always used; it now selects
     * only the backend-neutral serializer, the storage envelope comes from the persistence backend provider).
     *
     * @param actorSystem the actor system in which the serializer should be loaded.
     * @param config the config the extension is configured from.
     * @param <T> the jsonifiable type to snapshot.
     * @return the configured {@code SnapshotSerializer} implementation.
     * @throws NullPointerException if any argument is {@code null}.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField>> SnapshotSerializer<T> get(
            final ActorSystem actorSystem, final Config config) {

        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return (SnapshotSerializer<T>) DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    /**
     * ID of the actor-system extension that resolves the per-service snapshot serializer.
     */
    @SuppressWarnings("rawtypes")
    static final class ExtensionId extends DittoExtensionPoint.ExtensionId<SnapshotSerializer> {

        private static final String CONFIG_KEY = "snapshot-adapter";

        private ExtensionId(final ExtensionIdConfig<SnapshotSerializer> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<SnapshotSerializer> computeConfig(final Config config) {
            return ExtensionIdConfig.of(SnapshotSerializer.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
