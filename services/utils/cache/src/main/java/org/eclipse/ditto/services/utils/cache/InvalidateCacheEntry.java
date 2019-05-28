/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.cache;

import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Concierge-service internal command signaling that the cache for a specific {@link EntityId} should be invalidated.
 * Is emitted via Pub/Sub when for example a Policy is modified or a Thing's ACL changes.
 */
@Immutable
public final class InvalidateCacheEntry implements Jsonifiable<JsonObject> {

    private static final JsonFieldDefinition<String> JSON_ENTITY_ID =
            JsonFactory.newStringFieldDefinition("entityId", V_1, V_2);

    private final EntityId entityId;

    private InvalidateCacheEntry(final EntityId entityId) {this.entityId = entityId;}

    /**
     * Creates a new {@link InvalidateCacheEntry} from the passed {@code entityId}.
     *
     * @param entityId the EntityId to build the InvalidateCacheEntry for.
     * @return the created InvalidateCacheEntry instance.
     */
    public static InvalidateCacheEntry of(final EntityId entityId) {
        return new InvalidateCacheEntry(entityId);
    }

    /**
     * Creates a new {@link InvalidateCacheEntry} from a JSON object.
     *
     * @param jsonObject the JsonObject to create the InvalidateCacheEntry from.
     * @return the created InvalidateCacheEntry instance.
     */
    public static InvalidateCacheEntry fromJson(final JsonObject jsonObject) {
        final String entityIdStr = jsonObject.getValueOrThrow(JSON_ENTITY_ID);
        return new InvalidateCacheEntry(EntityId.readFrom(entityIdStr));
    }

    /**
     * @return the EntityId to invalidate caches for.
     */
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JSON_ENTITY_ID, entityId.toString())
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvalidateCacheEntry)) {
            return false;
        }
        final InvalidateCacheEntry that = (InvalidateCacheEntry) o;
        return Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entityId=" + entityId +
                "]";
    }
}
