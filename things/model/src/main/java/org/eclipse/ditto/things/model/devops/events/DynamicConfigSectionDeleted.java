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
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.WotValidationConfigCommand;

/**
 * Event that is emitted after a dynamic config section was deleted from a WoT validation config.
 * <p>
 * This event is used to track the deletion of a specific dynamic validation config section, identified by its scope ID,
 * from a WoT validation configuration. The event contains both the pointer to the deleted section and the scope ID.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableEvent(name = DynamicConfigSectionDeleted.NAME, typePrefix = WotValidationConfigEvent.TYPE_PREFIX)
public final class DynamicConfigSectionDeleted extends AbstractWotValidationConfigEvent<DynamicConfigSectionDeleted> {

    /**
     * The name of this event.
     * Used to identify the event type in the event journal and for deserialization.
     */
    public static final String NAME = "dynamicConfigSectionDeleted";

    private static final String TYPE = TYPE_PREFIX + NAME;

    private final JsonPointer sectionPointer;
    private final String scopeId;

    private DynamicConfigSectionDeleted(final WotValidationConfigId configId,
            final JsonPointer sectionPointer,
            final String scopeId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        super(TYPE, configId, revision, timestamp, dittoHeaders, metadata);
        this.sectionPointer = checkNotNull(sectionPointer, "sectionPointer");
        this.scopeId = checkNotNull(scopeId, "scope-id");
    }

    /**
     * Creates a new {@code DynamicConfigSectionDeleted} event.
     *
     * @param configId the ID of the WoT validation config.
     * @param sectionPointer the JSON pointer to the deleted section.
     * @param scopeId the scope ID of the deleted section.
     * @param revision the revision number of the config after deletion.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the Ditto headers.
     * @param metadata the metadata associated with this event.
     * @return the created event.
     * @throws NullPointerException if any required argument is {@code null}.
     * @throws IllegalArgumentException if the scopeId in the path does not match the payload.
     */
    public static DynamicConfigSectionDeleted of(final WotValidationConfigId configId,
            final JsonPointer sectionPointer,
            final String scopeId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        return new DynamicConfigSectionDeleted(configId, sectionPointer, scopeId, revision, timestamp, dittoHeaders,
                metadata);
    }

    /**
     * Creates a new {@code DynamicConfigSectionDeleted} event from a JSON object.
     *
     * @param jsonObject the JSON object to parse.
     * @param dittoHeaders the Ditto headers.
     * @return the parsed event.
     */
    public static DynamicConfigSectionDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<DynamicConfigSectionDeleted>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final WotValidationConfigId configId = WotValidationConfigId.of(
                            jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID));
                    final JsonPointer sectionPointer = JsonPointer.of(
                            jsonObject.getValueOrThrow(JsonFields.SECTION_POINTER));
                    final String scopeId = jsonObject.getValueOrThrow(JsonFields.SCOPE_ID);
                    return of(configId, sectionPointer, scopeId, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(WotValidationConfigCommand.JsonFields.CONFIG_ID, getEntityId().toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SECTION_POINTER, sectionPointer.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SCOPE_ID, scopeId, predicate);
    }

    @Override
    public DynamicConfigSectionDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), sectionPointer, scopeId, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public DynamicConfigSectionDeleted setRevision(final long revision) {
        return of(getEntityId(), sectionPointer, scopeId, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        return sectionPointer;
    }

    /**
     * @return the scope ID of the deleted section
     */
    public String getScopeId() {
        return scopeId;
    }

    @Override
    public DynamicConfigSectionDeleted setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final DynamicConfigSectionDeleted that = (DynamicConfigSectionDeleted) o;
        return that.canEqual(this) &&
                Objects.equals(sectionPointer, that.sectionPointer) &&
                Objects.equals(scopeId, that.scopeId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DynamicConfigSectionDeleted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sectionPointer, scopeId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", sectionPointer=" + sectionPointer +
                ", scopeId=" + scopeId +
                "]";
    }

    /**
     * Contains the JSON field definitions for this event.
     */
    public static final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * The JSON field for the section pointer.
         */
        public static final JsonFieldDefinition<String> SECTION_POINTER =
                JsonFactory.newStringFieldDefinition("sectionPointer", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The JSON field for the scope ID.
         */
        public static final JsonFieldDefinition<String> SCOPE_ID =
                JsonFactory.newStringFieldDefinition("scope-id", FieldType.REGULAR, JsonSchemaVersion.V_2);

    }
}
