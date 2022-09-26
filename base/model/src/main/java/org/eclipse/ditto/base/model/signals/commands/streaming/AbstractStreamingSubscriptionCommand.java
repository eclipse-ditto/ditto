/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands.streaming;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.EntityIdJsonDeserializer;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.entity.type.EntityTypeJsonDeserializer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Abstract base class for streaming commands.
 *
 * @param <T> the type of the AbstractStreamingSubscriptionCommand
 * @since 3.2.0
 */
@Immutable
abstract class AbstractStreamingSubscriptionCommand<T extends AbstractStreamingSubscriptionCommand<T>>
        extends AbstractCommand<T>
        implements StreamingSubscriptionCommand<T> {

    protected final EntityId entityId;
    protected final JsonPointer resourcePath;

    protected AbstractStreamingSubscriptionCommand(final String type,
            final EntityId entityId,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders) {

        super(type, dittoHeaders);
        this.entityId = checkNotNull(entityId, "entityId");
        this.resourcePath = checkNotNull(resourcePath, "resourcePath");
    }

    protected static EntityId deserializeEntityId(final JsonObject jsonObject) {
        return EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                StreamingSubscriptionCommand.JsonFields.JSON_ENTITY_ID,
                EntityTypeJsonDeserializer.deserializeEntityType(jsonObject,
                        StreamingSubscriptionCommand.JsonFields.JSON_ENTITY_TYPE));
    }

    @Override
    public Category getCategory() {
        return Category.STREAM;
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public EntityType getEntityType() {
        return entityId.getEntityType();
    }

    @Override
    public JsonPointer getResourcePath() {
        return resourcePath;
    }

    @Override
    public String getResourceType() {
        return getEntityType().toString();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(StreamingSubscriptionCommand.JsonFields.JSON_ENTITY_TYPE,
                entityId.getEntityType().toString(), predicate);
        jsonObjectBuilder.set(StreamingSubscriptionCommand.JsonFields.JSON_ENTITY_ID,
                entityId.toString(), predicate);
        jsonObjectBuilder.set(StreamingSubscriptionCommand.JsonFields.JSON_RESOURCE_PATH,
                resourcePath.toString(), predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityId, resourcePath);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final AbstractStreamingSubscriptionCommand<?> other = (AbstractStreamingSubscriptionCommand<?>) obj;

        return other.canEqual(this) &&
                super.equals(other) &&
                Objects.equals(entityId, other.entityId) &&
                Objects.equals(resourcePath, other.resourcePath);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractStreamingSubscriptionCommand;
    }

    @Override
    public String toString() {
        return super.toString() +
                ", entityId=" + entityId +
                ", entityType=" + getEntityType() +
                ", resourcePath=" + resourcePath;
    }

}
