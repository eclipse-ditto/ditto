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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;

/**
 * Special Entity ID's (with Revision).
 * When streaming Ids, persistence Ids or something similar in a batched manner, bounds are needed.
 * E.g. "give me all Ids grater then x"
 * As starting point or for resetting the lowest possible Id is needed, the lower-bound.
 */
public final class LowerBound extends AbstractEntityIdWithRevision<EntityId> {

    private LowerBound(final EntityType entityType) {
        this(emptyEntityId(entityType), 0L);
    }

    private LowerBound(final EntityId entityId, final Long revision) {
        super(entityId, revision);
    }

    public static EntityId emptyEntityId(final EntityType entityType) {
        return EntityId.of(entityType, ":_");
    }

    public static EntityIdWithRevision<EntityId> empty(final EntityType entityType) {
        return new LowerBound(entityType);
    }

    public static EntityIdWithRevision<EntityId> fromJson(JsonObject jsonObject) {
        final EntityType entityType = EntityType.of(jsonObject.getValueOrThrow(JsonFields.ENTITY_TYPE));
        final EntityId entityId =
                EntityId.of(entityType, jsonObject.getValueOrThrow(JsonFields.ENTITY_ID));
        final Long revision = jsonObject.getValueOrThrow(JsonFields.REVISION);
        return new LowerBound(entityId, revision);
    }

}
