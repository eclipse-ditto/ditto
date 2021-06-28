/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.streaming;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.EntityIdJsonDeserializer;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.entity.type.EntityTypeJsonDeserializer;
import org.eclipse.ditto.json.JsonObject;

/**
 * Special Entity IDs with Revision.
 * When streaming IDs, persistence IDs or something similar in a batched manner, bounds are needed.
 * E.g. "give me all IDs greater than x".
 * As starting point or for resetting, the lowest possible ID is needed, i.e. the lower-bound.
 */
public final class LowerBound extends AbstractEntityIdWithRevision<EntityId> {

    private LowerBound(final EntityId entityId, final Long revision) {
        super(entityId, revision);
    }

    /**
     * Returns an empty lower bound entity ID.
     *
     * @param entityType the type of the returned ID.
     * @return the empty lower bound entity ID.
     * @throws NullPointerException if {@code entityType} is {@code null}.
     * @see #emptyEntityId(EntityType)
     */
    public static LowerBound empty(final EntityType entityType) {
        return new LowerBound(emptyEntityId(entityType), 0L);
    }

    public static LowerBound fromJson(final JsonObject jsonObject) {
        return new LowerBound(deserializeEntityId(jsonObject), jsonObject.getValueOrThrow(JsonFields.REVISION));
    }

    private static EntityId deserializeEntityId(final JsonObject jsonObject) {
        return EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                JsonFields.ENTITY_ID,
                EntityTypeJsonDeserializer.deserializeEntityType(jsonObject, JsonFields.ENTITY_TYPE));
    }

    /**
     * Returns {@code ":_"} as {@code EntityId} for the specified type
     *
     * @param entityType determines the type of the returned {@code EntityId}.
     * @return the {@code EntityId}.
     * @throws NullPointerException if {@code entityType} is {@code null}.
     * @throws org.eclipse.ditto.base.model.entity.id.EntityIdInvalidException if {@code ":_"} is invalid for an ID of
     * type {@code entityType}.
     */
    public static EntityId emptyEntityId(final EntityType entityType) {
        return EntityId.of(entityType, ":_");
    }

}
