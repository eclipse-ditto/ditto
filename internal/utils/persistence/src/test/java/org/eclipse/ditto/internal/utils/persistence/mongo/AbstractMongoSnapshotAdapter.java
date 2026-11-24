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

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.SnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.SnapshotSerializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.slf4j.Logger;

import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotOffer;

/**
 * Abstract implementation of a MongoDB specific {@link SnapshotAdapter} for a {@link Jsonifiable}.
 * <p>
 * The pure domain&harr;{@link JsonObject} serialization logic lives in {@link SnapshotSerializer}, which this class
 * extends. This class adds <em>only</em> the BSON envelope, delegating it to {@link MongoSnapshotCodec}:
 * {@link #toSnapshotStore} encodes the serializer's JsonObject to BSON via the codec, and the
 * {@code fromSnapshotStore} methods decode a stored BSON value back to a JsonObject via the codec before feeding the
 * serializer. A PostgreSQL snapshot adapter reuses the same serializer with a JSONB envelope instead.
 *
 * @param <T> the jsonifiable type to snapshot.
 */
@ThreadSafe
public abstract class AbstractMongoSnapshotAdapter<T extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField>>
        extends SnapshotSerializer<T>
        implements SnapshotAdapter<T> {

    private static final MongoSnapshotCodec CODEC = MongoSnapshotCodec.INSTANCE;

    protected AbstractMongoSnapshotAdapter(final Logger logger) {
        super(logger);
    }

    @Override
    public Object toSnapshotStore(final T snapshotEntity) {
        final JsonObject json = toJson(checkNotNull(snapshotEntity, "snapshot entity"));
        return CODEC.encode(json);
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
     * Algorithm to convert a raw snapshot entity to a Jsonifiable.
     *
     * @param rawSnapshotEntity the snapshot entity to be converted.
     * @return a Jsonifiable whose origin is {@code rawSnapshotEntity} or {@code null}.
     * @throws NullPointerException     if {@code rawSnapshotEntity} is {@code null}.
     * @throws IllegalArgumentException if {@code rawSnapshotEntity} is not a {@link org.bson.BsonValue}.
     */
    @Nullable
    private T convertSnapshotToJsonifiable(final Object rawSnapshotEntity) {
        return fromJson(CODEC.decode(checkNotNull(rawSnapshotEntity, "raw snapshot entity")));
    }

}
