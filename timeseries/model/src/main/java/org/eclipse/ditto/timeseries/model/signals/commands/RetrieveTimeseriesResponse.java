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
package org.eclipse.ditto.timeseries.model.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;

/**
 * Response to {@link RetrieveTimeseries}. Carries one {@link TimeseriesQueryResult} per requested
 * path, in the same order the paths appeared in the originating query.
 *
 * @since 4.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveTimeseriesResponse.TYPE)
public final class RetrieveTimeseriesResponse extends AbstractCommandResponse<RetrieveTimeseriesResponse>
        implements TimeseriesCommandResponse<RetrieveTimeseriesResponse>, WithEntityId,
                   WithEntity<RetrieveTimeseriesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TimeseriesCommandResponse.TYPE_PREFIX + RetrieveTimeseries.NAME;

    private static final JsonFieldDefinition<String> JSON_THING_ID =
            JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonArray> JSON_RESULTS =
            JsonFactory.newJsonArrayFieldDefinition("results", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final List<TimeseriesQueryResult> results;

    private RetrieveTimeseriesResponse(final ThingId thingId,
            final List<TimeseriesQueryResult> results,
            final DittoHeaders dittoHeaders) {

        super(TYPE, HttpStatus.OK, dittoHeaders);
        this.thingId = thingId;
        this.results = results;
    }

    /**
     * Returns a new {@code RetrieveTimeseriesResponse}.
     *
     * @param thingId the Thing the results belong to.
     * @param results the per-path query results, in chronological order within each path.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveTimeseriesResponse of(final ThingId thingId,
            final List<TimeseriesQueryResult> results,
            final DittoHeaders dittoHeaders) {

        checkNotNull(thingId, "thingId");
        checkNotNull(results, "results");
        checkNotNull(dittoHeaders, "dittoHeaders");
        return new RetrieveTimeseriesResponse(
                thingId,
                Collections.unmodifiableList(new ArrayList<>(results)),
                dittoHeaders);
    }

    /**
     * Parses a {@code RetrieveTimeseriesResponse} from JSON.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the response.
     * @return the parsed response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if a required field is missing.
     * @throws org.eclipse.ditto.json.JsonParseException if a value is malformed.
     */
    public static RetrieveTimeseriesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        checkNotNull(jsonObject, "jsonObject");
        checkNotNull(dittoHeaders, "dittoHeaders");

        final ThingId thingId = ThingId.of(jsonObject.getValueOrThrow(JSON_THING_ID));
        final JsonArray resultsArray = jsonObject.getValueOrThrow(JSON_RESULTS);
        final List<TimeseriesQueryResult> results = new ArrayList<>(resultsArray.getSize());
        for (final JsonValue value : resultsArray) {
            results.add(TimeseriesQueryResult.fromJson(value.asObject()));
        }
        return of(thingId, results, dittoHeaders);
    }

    /**
     * @return the per-path query results. The list is unmodifiable.
     */
    public List<TimeseriesQueryResult> getResults() {
        return results;
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public RetrieveTimeseriesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, results, dittoHeaders);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        // For HTTP we expose only the per-path results array as the entity body — the thingId is
        // already in the URL and the headers carry correlation info, so duplicating them here
        // just bloats the payload. Clients (UI, curl) receive `[{thingId, path, query, result, data}, ...]`.
        final JsonArrayBuilder resultsBuilder = JsonFactory.newArrayBuilder();
        for (final TimeseriesQueryResult result : results) {
            resultsBuilder.add(result.toJson());
        }
        return resultsBuilder.build();
    }

    @Override
    public RetrieveTimeseriesResponse setEntity(final JsonValue entity) {
        // Used by Pekko HTTP / akka-http content negotiation pipelines that want to swap the
        // entity for a serialized form. We round-trip through fromJson on a synthesised envelope
        // so the parsing rules stay in one place.
        checkNotNull(entity, "entity");
        if (!entity.isArray()) {
            throw new IllegalArgumentException(
                    "Expected a JSON array for RetrieveTimeseriesResponse entity, got <" + entity + ">.");
        }
        final List<TimeseriesQueryResult> newResults = new ArrayList<>(entity.asArray().getSize());
        for (final JsonValue value : entity.asArray()) {
            newResults.add(TimeseriesQueryResult.fromJson(value.asObject()));
        }
        return of(thingId, newResults, getDittoHeaders());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THING_ID, thingId.toString(), predicate);

        final JsonArrayBuilder resultsBuilder = JsonFactory.newArrayBuilder();
        for (final TimeseriesQueryResult result : results) {
            resultsBuilder.add(result.toJson());
        }
        jsonObjectBuilder.set(JSON_RESULTS, resultsBuilder.build(), predicate);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RetrieveTimeseriesResponse that = (RetrieveTimeseriesResponse) obj;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(results, that.results) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveTimeseriesResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, results);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", thingId=" + thingId + ", results=" + results + "]";
    }
}
