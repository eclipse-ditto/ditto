/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops.events;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.AbstractEventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Abstract base class for all WoT validation configuration events.
 *
 * @param <T> the type of the implementing class.
 * @since 3.8.0
 */
@Immutable
public abstract class AbstractWotValidationConfigEvent<T extends AbstractWotValidationConfigEvent<T>>
        extends AbstractEventsourcedEvent<T> implements WotValidationConfigEvent<T> {

    private final WotValidationConfigId configId;

    /**
     * Constructs a new {@code AbstractWotValidationConfigEvent} instance.
     *
     * @param type the type of the event
     * @param configId the ID of the WoT validation configuration this event is associated with
     * @param revision the revision number of the configuration at the time this event was created
     * @param timestamp the timestamp when this event was created, or null if not set
     * @param dittoHeaders the headers of the event
     * @param metadata the metadata associated with this event, or null if not set
     * @throws NullPointerException if {@code type}, {@code configId}, or {@code dittoHeaders} is null
     */
    protected AbstractWotValidationConfigEvent(final String type,
            final WotValidationConfigId configId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        super(type, configId, timestamp, dittoHeaders, metadata, revision, JsonFields.CONFIG_ID);
        this.configId = checkNotNull(configId, "configId");
    }

    @Override
    public WotValidationConfigId getEntityId() {
        return configId;
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.CONFIG_ID, configId.toString(), predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AbstractWotValidationConfigEvent<?> that = (AbstractWotValidationConfigEvent<?>) o;
        return that.canEqual(this) &&
                Objects.equals(configId, that.configId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractWotValidationConfigEvent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), configId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", configId=" + configId +
                "]";
    }

    protected static final class JsonFields {

        static final JsonFieldDefinition<String> CONFIG_ID =
                JsonFactory.newStringFieldDefinition("configId", FieldType.REGULAR, JsonSchemaVersion.V_2);
        static final JsonFieldDefinition<JsonObject> CONFIG =
                JsonFactory.newJsonObjectFieldDefinition("config", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
} 