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
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;

/**
 * Command retrieving a timeseries aggregation across many Things of one namespace.
 * <p>
 * Deliberately <em>not</em> a {@code WithEntityId} command: it targets no single Thing, so it cannot
 * be routed through the per-Thing timeseries shard region the way {@link RetrieveTimeseries} is.
 * It is dispatched to the namespace-agnostic aggregate handler instead — the same shape thing-search
 * uses for its query commands.
 *
 * @since 4.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = TimeseriesCommand.TYPE_PREFIX, name = RetrieveAggregatedTimeseries.NAME)
public final class RetrieveAggregatedTimeseries extends AbstractCommand<RetrieveAggregatedTimeseries>
        implements TimeseriesCommand<RetrieveAggregatedTimeseries> {

    /**
     * Name of the {@code RetrieveAggregatedTimeseries} command.
     */
    public static final String NAME = "retrieveAggregatedTimeseries";

    /**
     * Type of this command, used for routing.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<JsonObject> JSON_QUERY =
            JsonFactory.newJsonObjectFieldDefinition("query", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final CrossThingTimeseriesQuery query;

    private RetrieveAggregatedTimeseries(final CrossThingTimeseriesQuery query,
            final DittoHeaders dittoHeaders) {

        super(TYPE, FeatureToggle.checkTimeseriesFeatureEnabled(TYPE, dittoHeaders));
        this.query = query;
    }

    /**
     * Returns a new {@code RetrieveAggregatedTimeseries} command.
     *
     * @param query the cross-Thing query to execute.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveAggregatedTimeseries of(final CrossThingTimeseriesQuery query,
            final DittoHeaders dittoHeaders) {

        checkNotNull(query, "query");
        checkNotNull(dittoHeaders, "dittoHeaders");
        return new RetrieveAggregatedTimeseries(query, dittoHeaders);
    }

    /**
     * Creates a {@code RetrieveAggregatedTimeseries} from its JSON representation.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the command.
     * @return the parsed command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code query} field is missing.
     */
    public static RetrieveAggregatedTimeseries fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<RetrieveAggregatedTimeseries>(TYPE, jsonObject).deserialize(() -> {
            final JsonObject queryJson = jsonObject.getValueOrThrow(JSON_QUERY);
            return new RetrieveAggregatedTimeseries(CrossThingTimeseriesQuery.fromJson(queryJson),
                    dittoHeaders);
        });
    }

    /**
     * @return the cross-Thing query carried by this command.
     */
    public CrossThingTimeseriesQuery getQuery() {
        return query;
    }

    /**
     * @return the namespace this command aggregates over.
     */
    public String getNamespace() {
        return query.getNamespace();
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public RetrieveAggregatedTimeseries setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(query, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        jsonObjectBuilder.set(JSON_QUERY, query.toJson(), schemaVersion.and(thePredicate));
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RetrieveAggregatedTimeseries that = (RetrieveAggregatedTimeseries) obj;
        return that.canEqual(this) && Objects.equals(query, that.query) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveAggregatedTimeseries;
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
