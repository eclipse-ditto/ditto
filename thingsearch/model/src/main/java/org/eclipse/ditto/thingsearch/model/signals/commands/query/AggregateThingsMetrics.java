/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.thingsearch.model.signals.commands.query;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * A command to aggregate metrics for things.
 */
@JsonParsableCommand(typePrefix = AggregateThingsMetrics.TYPE_PREFIX, name = AggregateThingsMetrics.NAME)
public final class AggregateThingsMetrics extends AbstractCommand<AggregateThingsMetrics> {

    public static final String NAME = "things-metrics";
    /**
     * Aggregation resource type.
     */
    static final String RESOURCE_TYPE = "aggregation";
    /**
     * Type prefix of aggregation command.
     */
    public static final String TYPE_PREFIX = RESOURCE_TYPE + "." + TYPE_QUALIFIER + ":";
    /**
     * The name of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FILTER =
            JsonFactory.newStringFieldDefinition("filter", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<String> METRIC_NAME =
            JsonFactory.newStringFieldDefinition("metric-name", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonObject> GROUPING_BY =
            JsonFactory.newJsonObjectFieldDefinition("grouping-by", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<String> FILTER =
            JsonFactory.newStringFieldDefinition("filter", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonArray> NAMESPACES =
            JsonFactory.newJsonArrayFieldDefinition("namespaces", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);


    private final String metricName;
    private final Map<String, String> groupingBy;
    @Nullable
    private final String filter;
    private final DittoHeaders dittoHeaders;
    private final Set<String> namespaces;

    private AggregateThingsMetrics(final String metricName, final Map<String, String> groupingBy,
            @Nullable final String filter, final Set<String> namespaces,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.metricName = metricName;
        this.groupingBy = Collections.unmodifiableMap(groupingBy);
        this.filter = filter;
        this.namespaces = Collections.unmodifiableSet(namespaces);
        this.dittoHeaders = dittoHeaders;
    }

    /**
     * Creates a new {@link AggregateThingsMetrics} instance.
     *
     * @param metricName the name of the metric to aggregate.
     * @param groupingBy the fields we want our metric aggregation to be grouped by.
     * @param filter the filter to use for the $match expression in the aggregation.
     * @param namespaces the namespaces the metric should be executed for.
     * @param dittoHeaders the headers to use for the command.
     * @return a new {@link AggregateThingsMetrics} instance.
     */
    public static AggregateThingsMetrics of(final String metricName, final Map<String, String> groupingBy,
            @Nullable final String filter, final List<String> namespaces,
            final DittoHeaders dittoHeaders) {
        return of(metricName, groupingBy, filter, new HashSet<>(namespaces), dittoHeaders);
    }

    private static AggregateThingsMetrics of(final String metricName, final Map<String, String> groupingBy,
            @Nullable final String filter, final Set<String> namespaces,
            final DittoHeaders dittoHeaders) {
        return new AggregateThingsMetrics(metricName, groupingBy, filter, namespaces, dittoHeaders);
    }

    /**
     * Creates a new {@code AggregateThingsMetrics} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static AggregateThingsMetrics fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code AggregateThingsMetrics} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static AggregateThingsMetrics fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<AggregateThingsMetrics>(TYPE, jsonObject).deserialize(() -> {
            final String metricName = jsonObject.getValue(METRIC_NAME).orElseThrow(getJsonMissingFieldExceptionSupplier(METRIC_NAME.getPointer().toString(), jsonObject));
            final JsonObject extractedGroupingBy = jsonObject.getValue(GROUPING_BY).orElseThrow(getJsonMissingFieldExceptionSupplier(GROUPING_BY.getPointer().toString(), jsonObject));
            final HashMap<String, String> groupingBy = new HashMap<>();
            extractedGroupingBy.forEach(jf -> groupingBy.put(jf.getKey().toString(), jf.getValue().asString()));

            final String extractedFilter = jsonObject.getValue(JSON_FILTER).orElse(null);

            final Set<String> extractedNamespaces = jsonObject.getValue(NAMESPACES)
                    .map(jsonValues -> jsonValues.stream()
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .collect(Collectors.toSet()))
                    .orElse(Collections.emptySet());

            return new AggregateThingsMetrics(metricName, groupingBy, extractedFilter, extractedNamespaces, dittoHeaders);
        });
    }

    public String getMetricName() {
        return metricName;
    }

    public Map<String, String> getGroupingBy() {
        return groupingBy;
    }

    @Nullable
    public String getFilter() {
        return filter;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(METRIC_NAME, metricName, predicate);
        final JsonObjectBuilder groupingBy = JsonFactory.newObjectBuilder();
        this.groupingBy.forEach(groupingBy::set);
        jsonObjectBuilder.set(GROUPING_BY, groupingBy.build(), predicate);
        jsonObjectBuilder.set(FILTER, filter, predicate);
        final JsonArray array =
                JsonFactory.newArrayBuilder(namespaces.stream().map(JsonFactory::newValue).collect(
                        Collectors.toSet())).build();
        jsonObjectBuilder.set(NAMESPACES, array, predicate);

    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public Category getCategory() {
        return Category.STREAM;
    }

    @Override
    public AggregateThingsMetrics setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getMetricName(), getGroupingBy(), getFilter(), getNamespaces(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final AggregateThingsMetrics that = (AggregateThingsMetrics) o;
        return Objects.equals(metricName, that.metricName) &&
                Objects.equals(groupingBy, that.groupingBy) &&
                Objects.equals(filter, that.filter) &&
                Objects.equals(dittoHeaders, that.dittoHeaders) &&
                Objects.equals(namespaces, that.namespaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricName, groupingBy, filter, dittoHeaders, namespaces);
    }

    @Override
    public String toString() {
        return "AggregateThingsMetrics{" +
                "metricName='" + metricName + '\'' +
                ", groupingBy=" + groupingBy +
                ", filter=" + filter +
                ", dittoHeaders=" + dittoHeaders +
                ", namespaces=" + namespaces +
                '}';
    }
    private static Supplier<RuntimeException> getJsonMissingFieldExceptionSupplier(final String field, final JsonObject jsonObject) {
        return () -> JsonMissingFieldException.newBuilder().fieldName(field).description(jsonObject.asString()).build();
    }
}
