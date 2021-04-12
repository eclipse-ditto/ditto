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
package org.eclipse.ditto.services.models.streaming;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;

/**
 * Special Entity ID's (with Revision).
 * When streaming Ids, persistence Ids or something similar in a batched manner, bounds are needed.
 * E.g. "give me all Ids grater then x"
 * As starting point or for resetting the lowest possible Id is needed, the lower-bound.
 */
public final class LowerBound extends AbstractEntityIdWithRevision<EntityId> {

    private static final EntityId EMPTY_ENTITY_ID = DefaultEntityId.of(":_");
    private static final LowerBound EMPTY_ENTITY_ID_WITH_REVISION = new LowerBound();

    private LowerBound() {
        this(EMPTY_ENTITY_ID, 0L);
    }

    private LowerBound(final EntityId entityId, final Long revision) {
        super(entityId, revision);
    }

    public static EntityId emptyEntityId() {
        return EMPTY_ENTITY_ID;
    }

    public static EntityIdWithRevision<EntityId> empty() {
        return EMPTY_ENTITY_ID_WITH_REVISION;
    }

    public static EntityIdWithRevision<EntityId> fromJson(JsonObject jsonObject) {
        final DefaultEntityId entityId = DefaultEntityId.of(jsonObject.getValueOrThrow(JsonFields.ID));
        final Long revision = jsonObject.getValueOrThrow(JsonFields.REVISION);
        return new LowerBound(entityId, revision);
    }

}
