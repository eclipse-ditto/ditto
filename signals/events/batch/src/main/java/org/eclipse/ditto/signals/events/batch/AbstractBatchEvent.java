/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.events.batch;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Abstract base class of an event store event.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class AbstractBatchEvent<T extends AbstractBatchEvent> implements BatchEvent<T> {

    private final String type;
    private final String batchId;
    @Nullable private final Instant timestamp;
    private final DittoHeaders dittoHeaders;

    /**
     * Constructs a new {@code AbstractBatchEvent} object.
     *
     * @param type the type of this event.
     * @param batchId the identifier of the batch.
     * @param timestamp the timestamp of the event.
     * @param dittoHeaders the headers of the command which was the cause of this event.   @throws NullPointerException
     * if any argument is {@code null}.
     */
    protected AbstractBatchEvent(final String type,
            final String batchId,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        this.type = checkNotNull(type, "Event type");
        this.batchId = checkNotNull(batchId, "batch ID");
        this.timestamp = timestamp;
        this.dittoHeaders = checkNotNull(dittoHeaders, "command headers");
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
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Nonnull
    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public String getBatchId() {
        return batchId;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                // TYPE is included unconditionally
                .set(Event.JsonFields.TYPE, type)
                .set(Event.JsonFields.TIMESTAMP, getTimestamp().map(Instant::toString).orElse(null), predicate);

        appendPayloadAndBuild(jsonObjectBuilder, schemaVersion, thePredicate);

        return jsonObjectBuilder.build();
    }

    /**
     * Appends the event specific custom payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the custom payload to.
     * @param schemaVersion the JsonSchemaVersion used in toJson().
     * @param predicate the predicate to evaluate when adding the payload.
     */
    protected abstract void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate);

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractBatchEvent that = (AbstractBatchEvent) o;
        return that.canEqual(this)
                && Objects.equals(type, that.type)
                && Objects.equals(batchId, that.batchId)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(dittoHeaders, that.dittoHeaders);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractBatchEvent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, batchId, timestamp, dittoHeaders);
    }

    @Override
    public String toString() {
        return "type=" + type + ", batchId=" + batchId + ", timestamp=" + timestamp + ", " + "dittoHeaders=" +
                dittoHeaders;
    }

}
