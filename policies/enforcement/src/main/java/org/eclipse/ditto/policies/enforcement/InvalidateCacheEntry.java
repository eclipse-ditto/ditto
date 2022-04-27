/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Concierge-service internal command signaling that the cache for a specific {@link org.eclipse.ditto.internal.utils.cache.CacheKey} should be invalidated.
 * Is emitted via Pub/Sub when for example a Policy is modified.
 * TODO TJ candidate for removal
 */
@Immutable
public final class InvalidateCacheEntry implements Jsonifiable<JsonObject> {

    private static final JsonFieldDefinition<String> JSON_ENTITY_ID =
            JsonFactory.newStringFieldDefinition("entityId", V_2);

    private final EnforcementCacheKey entityId;

    private InvalidateCacheEntry(final EnforcementCacheKey entityId) {this.entityId = entityId;}

    /**
     * Creates a new {@link InvalidateCacheEntry} from the passed {@code entityId}.
     *
     * @param entityId the EntityId to build the InvalidateCacheEntry for.
     * @return the created InvalidateCacheEntry instance.
     */
    public static InvalidateCacheEntry of(final EnforcementCacheKey entityId) {
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
        return new InvalidateCacheEntry(EnforcementCacheKey.readFrom(entityIdStr));
    }

    /**
     * @return the EntityId to invalidate caches for.
     */
    public EnforcementCacheKey getEntityId() {
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
