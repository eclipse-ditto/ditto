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
 * Event that is emitted when a WoT validation config is created.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableEvent(name = WotValidationConfigCreated.NAME, typePrefix = WotValidationConfigEvent.TYPE_PREFIX)
public final class WotValidationConfigCreated extends AbstractWotValidationConfigEvent<WotValidationConfigCreated>
        implements WotValidationConfigModifiedEvent<WotValidationConfigCreated> {

    /**
     * Name of this command.
     */
    public static final String NAME = "wotValidationConfigCreated";

    /**
     * Type of this event.
     * This is the full type identifier including the prefix.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final WotValidationConfig config;

    private WotValidationConfigCreated(final WotValidationConfigId configId,
            final WotValidationConfig config,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        super(TYPE, configId, revision, timestamp, dittoHeaders, metadata);
        this.config = config;
    }

    /**
     * Creates a new {@code WotValidationConfigCreated} event.
     *
     * @param configId the ID of the config.
     * @param config the WoT validation config.
     * @param revision the revision number of the event.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the headers of the event.
     * @param metadata the metadata of the event.
     * @return the created event.
     */
    public static WotValidationConfigCreated of(final WotValidationConfigId configId,
            final WotValidationConfig config,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {
        return new WotValidationConfigCreated(configId, config, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code WotValidationConfigCreated} from a JSON object.
     *
     * @param jsonObject the JSON object of which the event is to be created.
     * @param dittoHeaders the headers of the event.
     * @return the created event.
     */
    public static WotValidationConfigCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String configIdStr = jsonObject.getValueOrThrow(JsonFields.CONFIG_ID);
        final WotValidationConfigId configId = WotValidationConfigId.of(configIdStr);
        final WotValidationConfig config = WotValidationConfig.fromJson(
                jsonObject.getValueOrThrow(JsonFields.CONFIG));
        final long revision = jsonObject.getValueOrThrow(EventsourcedEvent.JsonFields.REVISION);
        final Instant timestamp = jsonObject.getValue(Event.JsonFields.TIMESTAMP)
                .map(Instant::parse)
                .orElse(null);
        final Metadata metadata = jsonObject.getValue(Event.JsonFields.METADATA)
                .map(JsonValue::asObject)
                .map(Metadata::newMetadata)
                .orElse(null);
        return new WotValidationConfigCreated(configId, config, revision, timestamp, dittoHeaders, metadata);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public WotValidationConfigCreated setRevision(final long revision) {
        return of(getEntityId(), config, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public WotValidationConfigCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), config, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    public WotValidationConfigCreated setEntity(final JsonValue entity) {
        try {
            final JsonObject jsonObject = entity.asObject();
            final WotValidationConfig config = WotValidationConfig.fromJson(jsonObject);
            return of(getEntityId(), config, getRevision(), getTimestamp().orElse(null), getDittoHeaders(),
                    getMetadata().orElse(null));
        } catch (final JsonParseException e) {
            throw new IllegalArgumentException("Failed to parse entity as WoTValidationConfig: " + e.getMessage(), e);
        }
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.CONFIG, config.toJson(schemaVersion, predicate), predicate);
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
    public int hashCode() {
        return Objects.hashCode(config);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WotValidationConfigCreated that = (WotValidationConfigCreated) o;
        return Objects.equals(config, that.config);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof WotValidationConfigCreated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", config=" + config +
                "]";
    }
}