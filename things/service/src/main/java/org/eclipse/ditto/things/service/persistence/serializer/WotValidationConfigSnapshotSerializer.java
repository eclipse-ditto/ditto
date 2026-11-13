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
package org.eclipse.ditto.things.service.persistence.serializer;

import java.util.Optional;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.api.serializer.SnapshotSerializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SnapshotSerializer} for a {@link WotValidationConfig}: the pure domain&harr;{@code JsonObject} logic
 * (deleted detection and revision handling). It is backend-neutral; the storage envelope (Mongo BSON / Postgres JSONB)
 * is supplied separately by the active backend's {@code SnapshotCodec} and combined with this serializer in
 * {@code org.eclipse.ditto.internal.utils.persistence.api.serializer.NeutralSnapshotAdapter}.
 * <p>
 * This serializer is required because {@code WotValidationConfigPersistenceActor} manages
 * {@link WotValidationConfig} entities, not {@link org.eclipse.ditto.things.model.Thing}s. The things-JVM-wide default
 * {@code snapshot-adapter} ({@link ThingMongoSnapshotAdapter}) only knows how to (de)serialize {@code Thing}s, so
 * letting the WoT actor inherit it caused a {@code ClassCastException} when a WoT snapshot was taken. The WoT actor
 * overrides {@code AbstractPersistenceActor#resolveSnapshotSerializer(..)} to return this serializer instead, while the
 * backend snapshot codec stays single-per-JVM and shared.
 * <p>
 * Unlike the Thing serializer, WoT validation configs have no "snapshot taken" event to publish, so
 * {@link #onSnapshotStoreConversion} is left at the base no-op.
 * <p>
 * Deletion is modelled by the boolean {@code _deleted} field
 * ({@link WotValidationConfig.JsonFields#DELETED}): a {@link WotValidationConfig} is deleted iff that field is
 * {@code true} (see {@link WotValidationConfig#isDeleted()}), and the base
 * {@link SnapshotSerializer#convertToJson}/{@link SnapshotSerializer#fromJson} pair use
 * {@link #getDeletedLifecycleJsonField()} accordingly.
 *
 * @since 4.0.0
 */
@ThreadSafe
public final class WotValidationConfigSnapshotSerializer extends SnapshotSerializer<WotValidationConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WotValidationConfigSnapshotSerializer.class);

    /**
     * Constructs a new {@code WotValidationConfigSnapshotSerializer}.
     */
    public WotValidationConfigSnapshotSerializer() {
        super(LOGGER);
    }

    @Override
    protected WotValidationConfig createJsonifiableFrom(final JsonObject jsonObject) {
        return WotValidationConfig.fromJson(jsonObject);
    }

    @Override
    protected boolean isDeleted(final WotValidationConfig snapshotEntity) {
        return snapshotEntity.isDeleted();
    }

    @Override
    protected JsonField getDeletedLifecycleJsonField() {
        final var field = WotValidationConfig.JsonFields.DELETED;
        return JsonField.newInstance(field.getPointer().getRoot().orElseThrow(), JsonValue.of(true), field);
    }

    @Override
    protected Optional<JsonField> getRevisionJsonField(final WotValidationConfig entity) {
        final var field = WotValidationConfig.JsonFields.REVISION;
        return entity.getRevision().map(revision ->
                JsonField.newInstance(field.getPointer().getRoot().orElseThrow(), JsonValue.of(revision.toLong())));
    }

}
