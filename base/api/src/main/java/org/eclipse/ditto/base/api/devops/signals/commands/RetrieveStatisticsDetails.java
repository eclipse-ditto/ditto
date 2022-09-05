/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.api.devops.signals.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Command which retrieves publicly available statistics about the stored Things currently present.
 */
@Immutable
@JsonParsableCommand(typePrefix = DevOpsCommand.TYPE_PREFIX, name = RetrieveStatisticsDetails.NAME)
public final class RetrieveStatisticsDetails extends AbstractDevOpsCommand<RetrieveStatisticsDetails> {

    /**
     * Name of the command.
     */
    public static final String NAME = "retrieveStatisticsDetails";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final List<String> shardRegions;
    private final List<String> namespaces;

    private RetrieveStatisticsDetails(final List<String> shardRegions, final List<String> namespaces,
            final DittoHeaders dittoHeaders) {
        super(TYPE, null, null, dittoHeaders);
        this.shardRegions = Collections.unmodifiableList(new ArrayList<>(shardRegions));
        this.namespaces = Collections.unmodifiableList(new ArrayList<>(namespaces));
    }

    /**
     * Returns a Command for retrieving statistics about all namespaces in all shard regions.
     *
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving statistics.
     */
    public static RetrieveStatisticsDetails of(final DittoHeaders dittoHeaders) {
        return new RetrieveStatisticsDetails(Collections.emptyList(), Collections.emptyList(), dittoHeaders);
    }

    /**
     * Returns a Command for retrieving statistics about certain namespaces in certain shard regions.
     *
     * @param shardRegions shard regions to query, or an empty list to query all shard regions.
     * @param namespaces namespaces to query, or an empty list to query all namespaces.
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving statistics.
     */
    public static RetrieveStatisticsDetails of(final List<String> shardRegions, final List<String> namespaces,
            final DittoHeaders dittoHeaders) {
        return new RetrieveStatisticsDetails(shardRegions, namespaces, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveStatisticsDetails} from a JSON string.
     *
     * @param jsonString contains the data of the RetrieveStatisticsDetails command.
     * @param dittoHeaders the headers of the request.
     * @return the RetrieveStatisticsDetails command which is based on the data of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveStatisticsDetails fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveStatisticsDetails} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveStatisticsDetails fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveStatisticsDetails>(TYPE, jsonObject)
                .deserialize(() -> {
                    final List<String> shardRegions = jsonObject.getValue(JsonFields.SHARD_REGIONS)
                            .map(RetrieveStatisticsDetails::toStringListOrThrow)
                            .orElseGet(Collections::emptyList);
                    final List<String> namespaces = jsonObject.getValue(JsonFields.NAMESPACES)
                            .map(RetrieveStatisticsDetails::toStringListOrThrow)
                            .orElseGet(Collections::emptyList);
                    return RetrieveStatisticsDetails.of(shardRegions, namespaces, dittoHeaders);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        if (!shardRegions.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.SHARD_REGIONS,
                    shardRegions.stream().map(JsonFactory::newValue).collect(JsonCollectors.valuesToArray()));
        }
        if (!namespaces.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.NAMESPACES,
                    namespaces.stream().map(JsonFactory::newValue).collect(JsonCollectors.valuesToArray()));
        }
    }

    /**
     * Return the shard regions to query for statistics-details, or an empty list to query all shard regions.
     *
     * @return the relevant shard regions.
     */
    public List<String> getShardRegions() {
        return shardRegions;
    }

    /**
     * Return the namespaces to query for statistics-details, or an empty list to query all namespaces.
     *
     * @return the relevant namespaces.
     */
    public List<String> getNamespaces() {
        return namespaces;
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public RetrieveStatisticsDetails setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dittoHeaders);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveStatisticsDetails that = (RetrieveStatisticsDetails) o;
        return that.canEqual(this) &&
                shardRegions.equals(that.shardRegions) &&
                namespaces.equals(that.namespaces) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveStatisticsDetails;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), shardRegions, namespaces);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", shardRegions=" + shardRegions +
                ", namespaces=" + namespaces +
                "]";
    }

    private static List<String> toStringListOrThrow(final JsonArray jsonArray) {
        return jsonArray.stream().map(JsonValue::asString).toList();
    }

    /**
     * JSON fields of this command.
     */
    public static final class JsonFields {

        /**
         * JSON fields to limit the shard regions to query.
         */
        public static final JsonFieldDefinition<JsonArray> SHARD_REGIONS =
                JsonFactory.newJsonArrayFieldDefinition("shardRegions");

        /**
         * JSON fields to limit statistics details to the namespaces.
         */
        public static final JsonFieldDefinition<JsonArray> NAMESPACES =
                JsonFactory.newJsonArrayFieldDefinition("namespaces");
    }

}
