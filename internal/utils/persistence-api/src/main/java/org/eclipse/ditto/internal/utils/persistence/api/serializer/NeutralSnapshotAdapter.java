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

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;

import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotOffer;

/**
 * The single, backend-neutral {@link SnapshotAdapter} implementation: it composes the per-service
 * {@link SnapshotSerializer} (the pure domain&harr;{@link JsonObject} logic) with the active backend's
 * {@link SnapshotCodec} (the storage envelope — BSON for MongoDB, JSONB for PostgreSQL).
 * <p>
 * This unifies what a per-backend pair of snapshot adapters would otherwise do:
 * the only thing that ever differed between backends was the storage envelope, which is now the injected
 * {@link SnapshotCodec}. The serializer and codec each have exactly one home, so a snapshot adapter is assembled rather
 * than subclassed per backend.
 * <p>
 * Its behaviour mirrors the former {@code AbstractMongoSnapshotAdapter} exactly:
 * <ul>
 *   <li>{@link #toSnapshotStore(Jsonifiable)} = {@code codec.encode(serializer.toJson(entity))} — the
 *       {@link SnapshotSerializer#toJson(Jsonifiable)} call applies the {@code onSnapshotStoreConversion} side-effect
 *       hook exactly once, preserving e.g. the Thing snapshot-taken publishing and the connection fields encryption,
 *       before the codec wraps the resulting {@link JsonObject} for storage.</li>
 *   <li>the {@code fromSnapshotStore} methods = {@code serializer.fromJson(codec.decode(rawStoredValue))} — the codec
 *       decodes the raw stored value back to a {@link JsonObject}, then the serializer recovers the domain type (and
 *       returns {@code null} for a deleted snapshot, exactly as before).</li>
 * </ul>
 *
 * @param <T> the jsonifiable snapshot type.
 * @since 3.7.0
 */
@ThreadSafe
public final class NeutralSnapshotAdapter<T extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField>>
        implements SnapshotAdapter<T> {

    private final SnapshotSerializer<T> serializer;
    private final SnapshotCodec codec;

    /**
     * Constructs a new {@code NeutralSnapshotAdapter} from the per-service serializer and the active backend's codec.
     *
     * @param serializer the per-service backend-neutral domain&harr;{@link JsonObject} serializer.
     * @param codec the active backend's storage-envelope codec ({@code provider.snapshotCodec()}).
     * @throws NullPointerException if any argument is {@code null}.
     */
    public NeutralSnapshotAdapter(final SnapshotSerializer<T> serializer, final SnapshotCodec codec) {
        this.serializer = checkNotNull(serializer, "serializer");
        this.codec = checkNotNull(codec, "codec");
    }

    @Override
    public Object toSnapshotStore(final T snapshot) {
        final JsonObject json = serializer.toJson(checkNotNull(snapshot, "snapshot"));
        return codec.encode(json);
    }

    @Nullable
    @Override
    public T fromSnapshotStore(final SnapshotOffer snapshotOffer) {
        return convertSnapshotToJsonifiable(snapshotOffer.snapshot());
    }

    @Nullable
    @Override
    public T fromSnapshotStore(final SelectedSnapshot selectedSnapshot) {
        return convertSnapshotToJsonifiable(selectedSnapshot.snapshot());
    }

    @Nullable
    private T convertSnapshotToJsonifiable(final Object rawSnapshotEntity) {
        return serializer.fromJson(codec.decode(checkNotNull(rawSnapshotEntity, "raw snapshot entity")));
    }

}
