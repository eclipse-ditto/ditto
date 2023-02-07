/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.events;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;


/**
 * Abstract base class of an event store event.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class AbstractEvent<T extends AbstractEvent<T>> implements Event<T> {

    private final String type;
    @Nullable private final Instant timestamp;
    private final DittoHeaders dittoHeaders;
    @Nullable private final Metadata metadata;

    /**
     * Constructs a new {@code AbstractEvent} object.
     *
     * @param type the type of this event.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata which was applied together with the event, relative to the event's
     * {@link #getResourcePath()}.
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     */
    protected AbstractEvent(final String type,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        this.type = checkNotNull(type, "type");
        this.timestamp = timestamp;
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders").isResponseRequired() ? dittoHeaders
                .toBuilder()
                .responseRequired(false)
                .build() : dittoHeaders;
        this.metadata = metadata;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Optional<Instant> getTimestamp() {
        return Optional.ofNullable(timestamp);
    }

    @Override
    public Optional<Metadata> getMetadata() {
        return Optional.ofNullable(metadata);
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Nonnull
    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                // TYPE is included unconditionally:
                .set(JsonFields.TYPE, type)
                .set(JsonFields.TIMESTAMP, getTimestamp().map(Instant::toString).orElse(null), predicate)
                .set(JsonFields.METADATA, getMetadata().map(Metadata::toJson).orElse(null), predicate);

        appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        return jsonObjectBuilder.build();
    }

    /**
     * Appends the event specific custom payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the custom payload to.
     * @param schemaVersion the JsonSchemaVersion used in toJson().
     * @param predicate the predicate to evaluate when adding the payload.
     */
    protected abstract void appendPayload(JsonObjectBuilder jsonObjectBuilder,
            JsonSchemaVersion schemaVersion, Predicate<JsonField> predicate);

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractEvent<?> that = (AbstractEvent<?>) o;
        return that.canEqual(this) &&
                Objects.equals(type, that.type) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(dittoHeaders, that.dittoHeaders) &&
                Objects.equals(metadata, that.metadata);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof AbstractEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, timestamp, dittoHeaders, metadata);
    }

    @Override
    public String toString() {
        return "type=" + type +
                ", timestamp=" + timestamp +
                ", dittoHeaders=" + dittoHeaders +
                ", metadata=" + metadata;
    }

}
