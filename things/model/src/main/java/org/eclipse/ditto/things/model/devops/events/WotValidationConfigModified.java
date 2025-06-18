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
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Event that is emitted after a WoT validation configuration has been modified.
 * This event contains the updated configuration and is used to track changes to the WoT validation
 * settings in the event journal.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableEvent(name = WotValidationConfigModified.NAME, typePrefix = WotValidationConfigEvent.TYPE_PREFIX)
public final class WotValidationConfigModified extends AbstractWotValidationConfigEvent<WotValidationConfigModified>
        implements WotValidationConfigModifiedEvent<WotValidationConfigModified> {

    /**
     * Name of this command.
     */
    public static final String NAME = "wotValidationConfigModified";

    private final WotValidationConfig config;

    public static final String TYPE = TYPE_PREFIX + NAME;


    /**
     * Constructs a new {@code WotValidationConfigModified} event.
     *
     * @param configId the ID of the WoT validation configuration that was modified
     * @param config the updated WoT validation configuration
     * @param revision the revision number of the configuration after the modification
     * @param timestamp the timestamp when this event was created
     * @param dittoHeaders the headers of the command which caused this event
     * @param metadata the metadata associated with this event
     */
    private WotValidationConfigModified(final WotValidationConfigId configId,
            final WotValidationConfig config,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        super(TYPE, configId, revision, timestamp, dittoHeaders, metadata);
        this.config = config;
    }

    /**
     * Creates a new {@code WotValidationConfigModified} event.
     *
     * @param configId the ID of the WoT validation configuration that was modified
     * @param config the updated WoT validation configuration
     * @param revision the revision number of the configuration after the modification
     * @param timestamp the timestamp when this event was created
     * @param dittoHeaders the headers of the command which caused this event
     * @param metadata the metadata associated with this event
     * @return the created WotValidationConfigModified event
     */
    public static WotValidationConfigModified of(final WotValidationConfigId configId,
            final WotValidationConfig config,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        return new WotValidationConfigModified(configId, config, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code WotValidationConfigModified} event from a JSON object.
     * This method is used for deserialization of the event from its JSON representation.
     *
     * @param jsonObject the JSON object from which to create the event
     * @param dittoHeaders the headers of the command which caused this event
     * @return the created WotValidationConfigModified event
     */
    public static WotValidationConfigModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String configIdStr = jsonObject.getValueOrThrow(AbstractWotValidationConfigEvent.JsonFields.CONFIG_ID);
        final WotValidationConfigId configId = WotValidationConfigId.of(configIdStr);
        final long revision = jsonObject.getValueOrThrow(
                EventsourcedEvent.JsonFields.REVISION);
        JsonObject configJson = jsonObject.getValueOrThrow(AbstractWotValidationConfigEvent.JsonFields.CONFIG);
        configJson = configJson.toBuilder().set("_revision", revision).build();
        final WotValidationConfig config = WotValidationConfig.fromJson(configJson);
        final Instant timestamp =
                jsonObject.getValue(Event.JsonFields.TIMESTAMP)
                        .map(Instant::parse)
                        .orElse(null);
        final Metadata metadata =
                jsonObject.getValue(Event.JsonFields.METADATA)
                        .map(JsonValue::asObject)
                        .map(Metadata::newMetadata)
                        .orElse(null);
        return new WotValidationConfigModified(configId, config, revision, timestamp, dittoHeaders, metadata);
    }

    @Override
    public JsonSchemaVersion getImplementedSchemaVersion() {
        return JsonSchemaVersion.V_2;
    }

    /**
     * Sets the entity (configuration) of this event.
     * This method is used to update the event with a new configuration.
     *
     * @param entity the new configuration as a JSON value
     * @return a new instance of this event with the updated configuration
     * @throws JsonParseException if the entity cannot be parsed as a WoT validation configuration
     */
    @Override
    public WotValidationConfigModified setEntity(final JsonValue entity) {
        final WotValidationConfig config = WotValidationConfig.fromJson(entity.asObject());
        return of(getEntityId(), config, getRevision(), getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public WotValidationConfigModified setRevision(final long revision) {
        return of(getEntityId(), config, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public WotValidationConfigModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), config, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(AbstractWotValidationConfigEvent.JsonFields.CONFIG,
                config.toJson(schemaVersion, predicate), predicate);
    }

    @Override
    public WotValidationConfig getConfig() {
        return config;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(config.toJson(schemaVersion));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), config);
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
        final WotValidationConfigModified that = (WotValidationConfigModified) o;
        return Objects.equals(config, that.config);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof WotValidationConfigModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", config=" + config +
                "]";
    }
}