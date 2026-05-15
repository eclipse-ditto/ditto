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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;

/**
 * Command issued against the Timeseries service to retrieve historical data for a single Thing
 * over a specified time range, optionally with downsampling, aggregation and a fill strategy.
 *
 * @since 4.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = TimeseriesCommand.TYPE_PREFIX, name = RetrieveTimeseries.NAME)
public final class RetrieveTimeseries extends AbstractCommand<RetrieveTimeseries>
        implements TimeseriesCommand<RetrieveTimeseries>, WithEntityId {

    /**
     * Name of the {@code RetrieveTimeseries} command.
     */
    public static final String NAME = "retrieveTimeseries";

    /**
     * Type of this command, used for routing.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<JsonObject> JSON_QUERY =
            JsonFactory.newJsonObjectFieldDefinition("query", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final TimeseriesQuery query;

    private RetrieveTimeseries(final TimeseriesQuery query, final DittoHeaders dittoHeaders) {
        super(TYPE, FeatureToggle.checkTimeseriesFeatureEnabled(TYPE, dittoHeaders));
        this.query = query;
    }

    /**
     * Returns a new {@code RetrieveTimeseries} command.
     *
     * @param query the timeseries query to execute.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveTimeseries of(final TimeseriesQuery query, final DittoHeaders dittoHeaders) {
        checkNotNull(query, "query");
        checkNotNull(dittoHeaders, "dittoHeaders");
        return new RetrieveTimeseries(query, dittoHeaders);
    }

    /**
     * Creates a {@code RetrieveTimeseries} from its JSON representation.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the command.
     * @return the parsed command.
     * @throws NullPointerException if {@code jsonObject} or {@code dittoHeaders} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code query} field is missing.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code query} cannot be parsed.
     */
    public static RetrieveTimeseries fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<RetrieveTimeseries>(TYPE, jsonObject).deserialize(() -> {
            final JsonObject queryJson = jsonObject.getValueOrThrow(JSON_QUERY);
            final TimeseriesQuery parsedQuery = TimeseriesQuery.fromJson(queryJson);
            return new RetrieveTimeseries(parsedQuery, dittoHeaders);
        });
    }

    /**
     * @return the timeseries query carried by this command.
     */
    public TimeseriesQuery getQuery() {
        return query;
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public ThingId getEntityId() {
        return query.getThingId();
    }

    @Override
    public JsonPointer getResourcePath() {
        // Fine-grained policy enforcement uses each path's resource separately at the service
        // level. The command's overall resource path stays at root; per-path enforcement is the
        // service's responsibility.
        return JsonPointer.empty();
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public RetrieveTimeseries setDittoHeaders(final DittoHeaders dittoHeaders) {
        // Goes through the public constructor, which re-evaluates
        // FeatureToggle.checkTimeseriesFeatureEnabled. Matches MergeThing.setDittoHeaders:
        // because the toggle is uniformly distributed via DittoService.injectSystemPropertiesLimits
        // across all services, re-checking here is a defence-in-depth gate (e.g. a node whose
        // sysprop wasn't injected for some reason cannot accidentally pass the command through).
        return of(query, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_QUERY, query.toJson(), predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveTimeseries;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RetrieveTimeseries that = (RetrieveTimeseries) obj;
        return that.canEqual(this) && Objects.equals(query, that.query) && super.equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), query);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", query=" + query + "]";
    }
}
