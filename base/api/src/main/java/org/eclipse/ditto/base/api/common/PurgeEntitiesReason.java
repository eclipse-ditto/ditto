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
package org.eclipse.ditto.base.api.common;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * A dedicated {@link ShutdownReason} for purging a namespace.
 * The details are guaranteed to be the non-empty namespace.
 */
@Immutable
final class PurgeEntitiesReason implements ShutdownReason {

    private static final ShutdownReasonType type = ShutdownReasonType.Known.PURGE_ENTITIES;
    private final List<String> entityIdsToPurge;

    private PurgeEntitiesReason(final List<String> entityIdsToPurge) {
        this.entityIdsToPurge = entityIdsToPurge;
    }

    /**
     * Returns an instance of {@code PurgeNamespaceReason}.
     *
     * @param entityIdsToPurge the entities that should be purged.
     * @return the instance.
     * @throws NullPointerException if {@code namespace} is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} is empty.
     */
    public static PurgeEntitiesReason of(final List<EntityId> entityIdsToPurge) {
        final List<String> stringEntityIdsToPurge = argumentNotEmpty(entityIdsToPurge, "entities to purge")
                .stream()
                .map(String::valueOf)
                .toList();

        return new PurgeEntitiesReason(stringEntityIdsToPurge);
    }

    static PurgeEntitiesReason fromJson(final JsonObject jsonObject) {
        final List<String> entityIdsToPurge = jsonObject.getValueOrThrow(JsonFields.DETAILS).asArray().stream()
                .map(JsonValue::asString)
                .toList();

        return new PurgeEntitiesReason(entityIdsToPurge);
    }

    @Override
    public ShutdownReasonType getType() {
        return type;
    }

    @Override
    public boolean isRelevantFor(final Object value) {
        if (value instanceof CharSequence) {
            return entityIdsToPurge.contains(value.toString());
        }
        return false;
    }

    @Override
    public JsonObject toJson() {
        return toJson(FieldType.REGULAR.and(FieldType.notHidden()));
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final Predicate<JsonField> extendedPredicate = schemaVersion.and(predicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                .set(JsonFields.TYPE, getType().toString(), extendedPredicate);
        jsonObjectBuilder.set(JsonFields.DETAILS, JsonArray.of(entityIdsToPurge), extendedPredicate);

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final PurgeEntitiesReason that = (PurgeEntitiesReason) o;
        return entityIdsToPurge.equals(that.entityIdsToPurge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityIdsToPurge);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entityIdsToPurge=" + entityIdsToPurge +
                "]";
    }
}
