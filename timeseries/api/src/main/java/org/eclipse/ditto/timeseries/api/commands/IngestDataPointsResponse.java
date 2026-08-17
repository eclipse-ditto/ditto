/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.api.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Acknowledgement reply to an {@link IngestDataPoints} command. Sent by the
 * {@code TimeseriesIngestActor} entity to the publisher only after the batch has been written
 * to the MongoDB Time Series collection — receipt by the publisher confirms the batch is
 * durable in the time-series store, so the publisher can drop it from its retry queue.
 * <p>
 * Carries no payload other than the {@link ThingId} and an HTTP 200 status. The
 * publisher matches the response to its in-flight request via the {@code correlation-id}
 * which {@code Patterns.ask} propagates automatically.
 *
 * @since 4.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = IngestDataPointsResponse.TYPE)
public final class IngestDataPointsResponse extends AbstractCommandResponse<IngestDataPointsResponse>
        implements WithEntityId {

    /**
     * Type prefix for ingest responses. Mirrors the command's prefix so a debugger
     * eyeballing cluster traffic sees command/response come in pairs.
     */
    public static final String TYPE_PREFIX = "timeseries.ingest." + CommandResponse.TYPE_QUALIFIER + ":";

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + IngestDataPoints.NAME;

    /**
     * Resource type. See {@link IngestDataPoints#RESOURCE_TYPE}.
     */
    public static final String RESOURCE_TYPE = IngestDataPoints.RESOURCE_TYPE;

    private static final JsonFieldDefinition<String> JSON_THING_ID =
            JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;

    private IngestDataPointsResponse(final ThingId thingId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = thingId;
    }

    /**
     * Returns a successful ack response.
     *
     * @param thingId the Thing the original batch targeted.
     * @param dittoHeaders the headers of the response — typically copied from the
     * originating {@link IngestDataPoints} command so the publisher's
     * {@code Patterns.ask} future correlates correctly.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static IngestDataPointsResponse of(final ThingId thingId, final DittoHeaders dittoHeaders) {
        checkNotNull(thingId, "thingId");
        checkNotNull(dittoHeaders, "dittoHeaders");
        return new IngestDataPointsResponse(thingId, HttpStatus.OK, dittoHeaders);
    }

    /**
     * Parses a response from JSON. Required by {@link JsonParsableCommandResponse} for
     * cross-cluster deserialization.
     */
    public static IngestDataPointsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        checkNotNull(jsonObject, "jsonObject");
        checkNotNull(dittoHeaders, "dittoHeaders");
        final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(JSON_THING_ID));
        return new IngestDataPointsResponse(thingId, HttpStatus.OK, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public IngestDataPointsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new IngestDataPointsResponse(thingId, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THING_ID, thingId.toString(), predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof IngestDataPointsResponse;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final IngestDataPointsResponse that = (IngestDataPointsResponse) obj;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                super.equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", thingId=" + thingId + "]";
    }
}
