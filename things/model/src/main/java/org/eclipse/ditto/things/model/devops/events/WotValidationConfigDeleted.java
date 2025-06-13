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

import java.time.Instant;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Event that is emitted after a WoT validation configuration has been deleted.
 * This event is used to track the deletion of WoT validation settings in the event journal.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableEvent(name = WotValidationConfigDeleted.NAME, typePrefix = WotValidationConfigEvent.TYPE_PREFIX)
public final class WotValidationConfigDeleted extends AbstractWotValidationConfigEvent<WotValidationConfigDeleted> {

    /**
     * Name of this event.
     * This is used to identify the event type in the event journal and for deserialization.
     */
    public static final String NAME = "deleted";

    /**
     * Type of this event.
     * This is the full type identifier including the prefix.
     */
    public static final String TYPE = WotValidationConfigEvent.TYPE_PREFIX + NAME;

    /**
     * Constructs a new {@code WotValidationConfigDeleted} event.
     *
     * @param configId the ID of the WoT validation configuration that was deleted
     * @param revision the revision number of the configuration after deletion
     * @param timestamp the timestamp when this event was created
     * @param dittoHeaders the headers of the command which caused this event
     * @param metadata the metadata associated with this event
     */
    private WotValidationConfigDeleted(final WotValidationConfigId configId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        super(TYPE, configId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code WotValidationConfigDeleted} event.
     *
     * @param configId the ID of the WoT validation configuration that was deleted
     * @param revision the revision number of the configuration after deletion
     * @param timestamp the timestamp when this event was created
     * @param dittoHeaders the headers of the command which caused this event
     * @param metadata the metadata associated with this event
     * @return the created WotValidationConfigDeleted event
     */
    public static WotValidationConfigDeleted of(final WotValidationConfigId configId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        return new WotValidationConfigDeleted(configId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code WotValidationConfigDeleted} event from a JSON object.
     * This method is used for deserialization of the event from its JSON representation.
     *
     * @param jsonObject the JSON object from which to create the event
     * @param dittoHeaders the headers of the command which caused this event
     * @return the created WotValidationConfigDeleted event
     */
    public static WotValidationConfigDeleted fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<WotValidationConfigDeleted>(TYPE, jsonObject)
                .deserialize((revision, timestamp, eventMetadata) -> {
                    final String extractedConfigId = jsonObject.getValueOrThrow(JsonFields.CONFIG_ID);
                    final WotValidationConfigId configId = WotValidationConfigId.of(extractedConfigId);
                    return new WotValidationConfigDeleted(configId, revision, timestamp, dittoHeaders, eventMetadata);
                });
    }

    @Override
    public WotValidationConfigDeleted setRevision(final long revision) {
        return of(getEntityId(), revision, getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public WotValidationConfigDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new WotValidationConfigDeleted(getEntityId(), getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    public WotValidationConfigDeleted setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WotValidationConfigDeleted that = (WotValidationConfigDeleted) o;
        return super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof WotValidationConfigDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                "]";
    }

    private static final class JsonFields {
        static final JsonFieldDefinition<String> CONFIG_ID =
                JsonFactory.newStringFieldDefinition("configId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
} 