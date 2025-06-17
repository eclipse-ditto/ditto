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
import org.eclipse.ditto.base.model.signals.UnsupportedSchemaVersionException;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.WotValidationConfigCommand;

/**

 *
 * Event that is emitted after a dynamic config section was merged into a WoT validation config.
 * <p>
 * This event is used to track the merging (creation or update) of a specific dynamic validation config section, identified by its scope ID,
 * into a WoT validation configuration. The event contains both the pointer to the merged section and the merged value.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableEvent(name = DynamicConfigSectionMerged.NAME, typePrefix = WotValidationConfigEvent.TYPE_PREFIX)
public final class DynamicConfigSectionMerged extends AbstractWotValidationConfigEvent<DynamicConfigSectionMerged> {

    /**
     * Name of the "Dynamic Config Section Merged" event.
     */
    public static final String NAME = "dynamicConfigSectionMerged";

    private static final String TYPE = TYPE_PREFIX + NAME;

    private final JsonPointer sectionPointer;
    private final DynamicValidationConfig sectionValue;

    private DynamicConfigSectionMerged(final WotValidationConfigId configId,
            final JsonPointer sectionPointer,
            final DynamicValidationConfig sectionValue,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        super(TYPE, configId, revision, timestamp, dittoHeaders, metadata);
        this.sectionPointer = checkNotNull(sectionPointer, "sectionPointer");
        this.sectionValue = checkNotNull(sectionValue, "sectionValue");
    }

    /**
     * Creates an event of merged dynamic config section.
     *
     * @param configId the config ID.
     * @param sectionPointer the path where the changes were applied.
     * @param sectionValue the value describing the changes that were merged.
     * @param revision the revision of the config.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the ditto headers.
     * @param metadata the metadata to apply for the event.
     * @return the created {@code DynamicConfigSectionMerged} event.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static DynamicConfigSectionMerged of(final WotValidationConfigId configId,
            final JsonPointer sectionPointer,
            final DynamicValidationConfig sectionValue,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        return new DynamicConfigSectionMerged(configId, sectionPointer, sectionValue, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code DynamicConfigSectionMerged} event from a JSON object.
     *
     * @param jsonObject the JSON object of which the event is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the {@code DynamicConfigSectionMerged} event created from JSON.
     */
    public static DynamicConfigSectionMerged fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<DynamicConfigSectionMerged>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final WotValidationConfigId configId = WotValidationConfigId.of(
                            jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID));
                    final JsonPointer sectionPointer = JsonPointer.of(
                            jsonObject.getValueOrThrow(JsonFields.SECTION_POINTER));
                    final DynamicValidationConfig sectionValue = DynamicValidationConfig.fromJson(jsonObject.getValueOrThrow(JsonFields.SECTION_VALUE).asObject());
                    return of(configId, sectionPointer, sectionValue, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(WotValidationConfigCommand.JsonFields.CONFIG_ID, getEntityId().toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SECTION_POINTER, sectionPointer.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SECTION_VALUE, sectionValue.toJson(), predicate);
    }

    @Override
    public DynamicConfigSectionMerged setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), sectionPointer, sectionValue, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public DynamicConfigSectionMerged setRevision(final long revision) {
        return of(getEntityId(), sectionPointer, sectionValue, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }


    @Override
    public JsonPointer getResourcePath() {
        return sectionPointer;
    }

    /**
     * @return the value describing the changes that were merged.
     */
    public DynamicValidationConfig getSectionValue() {
        return sectionValue;
    }

    @Override
    public DynamicConfigSectionMerged setEntity(final JsonValue entity) {
        return of(getEntityId(), sectionPointer, DynamicValidationConfig.fromJson(entity.asObject()), getRevision(), getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
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
        final DynamicConfigSectionMerged that = (DynamicConfigSectionMerged) o;
        return that.canEqual(this) &&
                Objects.equals(sectionPointer, that.sectionPointer) &&
                Objects.equals(sectionValue, that.sectionValue);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DynamicConfigSectionMerged;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sectionPointer, sectionValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", sectionPointer=" + sectionPointer +
                ", sectionValue=" + sectionValue +
                "]";
    }

    /**
     * An enumeration of the JSON fields of a {@code DynamicConfigSectionMerged} event.
     */
    public static final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        public static final JsonFieldDefinition<String> SECTION_POINTER =
                JsonFactory.newStringFieldDefinition("sectionPointer", FieldType.REGULAR, JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<JsonValue> SECTION_VALUE =
                JsonFactory.newJsonValueFieldDefinition("sectionValue", FieldType.REGULAR, JsonSchemaVersion.V_2);
    }
} 