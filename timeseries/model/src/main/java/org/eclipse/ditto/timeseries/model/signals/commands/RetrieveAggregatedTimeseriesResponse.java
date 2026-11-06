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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
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
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.model.AggregatedTimeseriesResult;

/**
 * Response to {@link RetrieveAggregatedTimeseries}. Carries one
 * {@link AggregatedTimeseriesResult} per {@code (group, path)} combination.
 *
 * @since 4.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveAggregatedTimeseriesResponse.TYPE)
public final class RetrieveAggregatedTimeseriesResponse
        extends AbstractCommandResponse<RetrieveAggregatedTimeseriesResponse>
        implements TimeseriesCommandResponse<RetrieveAggregatedTimeseriesResponse>,
                   WithEntity<RetrieveAggregatedTimeseriesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE =
            TimeseriesCommandResponse.TYPE_PREFIX + RetrieveAggregatedTimeseries.NAME;

    private static final JsonFieldDefinition<String> JSON_NAMESPACE =
            JsonFactory.newStringFieldDefinition("namespace", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonArray> JSON_RESULTS =
            JsonFactory.newJsonArrayFieldDefinition("results", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonObject> JSON_AUTHORIZATION =
            JsonFactory.newJsonObjectFieldDefinition("authorization", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<Integer> JSON_CONTRIBUTING =
            JsonFactory.newIntFieldDefinition("contributingThings", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<Integer> JSON_EXCLUDED =
            JsonFactory.newIntFieldDefinition("excludedThings", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<Boolean> JSON_PARTIAL =
            JsonFactory.newBooleanFieldDefinition("partial", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonObject> JSON_WITHHELD_BY_PATH =
            JsonFactory.newJsonObjectFieldDefinition("withheldByPath", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final String namespace;
    private final List<AggregatedTimeseriesResult> results;
    private final int contributingThings;
    private final int excludedThings;
    private final Map<String, Integer> withheldByPath;

    private RetrieveAggregatedTimeseriesResponse(final String namespace,
            final List<AggregatedTimeseriesResult> results,
            final int contributingThings,
            final int excludedThings,
            final Map<String, Integer> withheldByPath,
            final DittoHeaders dittoHeaders) {

        super(TYPE, HttpStatus.OK, dittoHeaders);
        this.namespace = namespace;
        this.results = results;
        this.contributingThings = contributingThings;
        this.excludedThings = excludedThings;
        this.withheldByPath = withheldByPath;
    }

    /**
     * Returns a new {@code RetrieveAggregatedTimeseriesResponse}.
     *
     * @param namespace the namespace that was aggregated.
     * @param results one result per {@code (group, path)} combination.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveAggregatedTimeseriesResponse of(final String namespace,
            final List<AggregatedTimeseriesResult> results,
            final DittoHeaders dittoHeaders) {

        return of(namespace, results, 0, 0, Collections.emptyMap(), dittoHeaders);
    }

    /**
     * Returns a new {@code RetrieveAggregatedTimeseriesResponse} carrying the authorization summary.
     *
     * @param namespace the namespace that was aggregated.
     * @param results one result per {@code (group, path)} combination.
     * @param contributingThings how many Things had data matching the query and were permitted.
     * @param excludedThings how many Things had matching data but were withheld because the caller
     * may not read them. A non-zero value means the aggregates are computed over a subset — reported
     * explicitly so a partial answer cannot be mistaken for a complete one.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code namespace}, {@code results} or {@code dittoHeaders} is
     * {@code null}.
     * @throws IllegalArgumentException if either count is negative.
     */
    public static RetrieveAggregatedTimeseriesResponse of(final String namespace,
            final List<AggregatedTimeseriesResult> results,
            final int contributingThings,
            final int excludedThings,
            final Map<String, Integer> withheldByPath,
            final DittoHeaders dittoHeaders) {

        checkNotNull(namespace, "namespace");
        checkNotNull(results, "results");
        checkNotNull(withheldByPath, "withheldByPath");
        checkNotNull(dittoHeaders, "dittoHeaders");
        if (contributingThings < 0 || excludedThings < 0) {
            throw new IllegalArgumentException("Thing counts must not be negative, were <" +
                    contributingThings + "> and <" + excludedThings + ">.");
        }
        return new RetrieveAggregatedTimeseriesResponse(namespace,
                Collections.unmodifiableList(new ArrayList<>(results)), contributingThings,
                excludedThings, Collections.unmodifiableMap(new LinkedHashMap<>(withheldByPath)),
                dittoHeaders);
    }

    /**
     * Creates a {@code RetrieveAggregatedTimeseriesResponse} from its JSON representation.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the response.
     * @return the parsed response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveAggregatedTimeseriesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        checkNotNull(jsonObject, "jsonObject");
        checkNotNull(dittoHeaders, "dittoHeaders");

        final String namespace = jsonObject.getValueOrThrow(JSON_NAMESPACE);
        final JsonArray resultsArray = jsonObject.getValueOrThrow(JSON_RESULTS);
        final List<AggregatedTimeseriesResult> results = new ArrayList<>(resultsArray.getSize());
        for (final JsonValue value : resultsArray) {
            results.add(AggregatedTimeseriesResult.fromJson(value.asObject()));
        }
        final JsonObject auth = jsonObject.getValue(JSON_AUTHORIZATION).orElseGet(JsonObject::empty);
        return of(namespace, results,
                auth.getValue(JSON_CONTRIBUTING).orElse(0),
                auth.getValue(JSON_EXCLUDED).orElse(0),
                withheldFromJson(auth.getValue(JSON_WITHHELD_BY_PATH).orElseGet(JsonObject::empty)),
                dittoHeaders);
    }

    /**
     * @return the aggregated results. The list is unmodifiable.
     */
    public List<AggregatedTimeseriesResult> getResults() {
        return results;
    }

    /**
     * @return the namespace that was aggregated.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @return how many Things contributed data to these aggregates.
     */
    public int getContributingThings() {
        return contributingThings;
    }

    /**
     * @return how many Things had matching data but were withheld for lack of permission.
     */
    public int getExcludedThings() {
        return excludedThings;
    }

    /**
     * @return whether the aggregates cover only a subset of the matching data, i.e. at least one
     * Thing was excluded for lack of permission.
     */
    public boolean isPartial() {
        return !withheldByPath.isEmpty();
    }

    /**
     * @return per requested path, how many Things had matching data but were withheld from that path.
     * Only non-zero entries are present. Because {@code READ_TS} is grantable per property, a Thing can
     * appear here for one path while still contributing to another.
     */
    public Map<String, Integer> getWithheldByPath() {
        return withheldByPath;
    }

    private JsonObject authorizationJson() {
        return JsonFactory.newObjectBuilder()
                .set(JSON_CONTRIBUTING, contributingThings)
                .set(JSON_EXCLUDED, excludedThings)
                .set(JSON_PARTIAL, isPartial())
                .set(JSON_WITHHELD_BY_PATH, withheldByPathJson())
                .build();
    }

    private JsonObject withheldByPathJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        for (final Map.Entry<String, Integer> entry : withheldByPath.entrySet()) {
            // JsonKey.of, never a raw String: the keys are JSON pointers, and
            // JsonObjectBuilder.set(CharSequence, ...) interprets a slash-bearing key as a *pointer*,
            // which would silently nest "/features/x/y" into {"features":{"x":{"y":…}}} instead of
            // keeping it a flat key. Same reason ReadGrant.toJson() wraps its path keys.
            builder.set(JsonKey.of(entry.getKey()), entry.getValue());
        }
        return builder.build();
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public RetrieveAggregatedTimeseriesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(namespace, results, contributingThings, excludedThings, withheldByPath,
                dittoHeaders);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        // Unlike the single-Thing endpoint (whose body is a bare array), a cross-Thing body is an
        // object so it can carry the authorization summary alongside the series. An aggregate computed
        // over a permitted subset must not be indistinguishable from one computed over everything —
        // the caller cannot detect that from the numbers alone, so it is stated.
        return JsonFactory.newObjectBuilder()
                .set(JSON_RESULTS, resultsToArray())
                .set(JSON_AUTHORIZATION, authorizationJson())
                .build();
    }

    @Override
    public RetrieveAggregatedTimeseriesResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        if (!entity.isObject()) {
            throw new IllegalArgumentException("Expected a JSON object for " +
                    "RetrieveAggregatedTimeseriesResponse entity, got <" + entity + ">.");
        }
        final JsonObject entityObject = entity.asObject();
        final JsonArray resultsArray = entityObject.getValue(JSON_RESULTS).orElseGet(JsonArray::empty);
        final List<AggregatedTimeseriesResult> newResults = new ArrayList<>(resultsArray.getSize());
        for (final JsonValue value : resultsArray) {
            newResults.add(AggregatedTimeseriesResult.fromJson(value.asObject()));
        }
        final JsonObject auth = entityObject.getValue(JSON_AUTHORIZATION).orElseGet(JsonObject::empty);
        return of(namespace, newResults,
                auth.getValue(JSON_CONTRIBUTING).orElse(contributingThings),
                auth.getValue(JSON_EXCLUDED).orElse(excludedThings),
                auth.getValue(JSON_WITHHELD_BY_PATH)
                        .map(RetrieveAggregatedTimeseriesResponse::withheldFromJson)
                        .orElse(withheldByPath),
                getDittoHeaders());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_NAMESPACE, namespace, predicate);
        jsonObjectBuilder.set(JSON_RESULTS, resultsToArray(), predicate);
        jsonObjectBuilder.set(JSON_AUTHORIZATION, authorizationJson(), predicate);
    }

    private static Map<String, Integer> withheldFromJson(final JsonObject json) {
        final Map<String, Integer> result = new LinkedHashMap<>();
        for (final JsonField field : json) {
            final JsonValue value = field.getValue();
            if (value.isNumber()) {
                result.put(field.getKeyName(), value.asInt());
            }
        }
        return result;
    }

    private JsonArray resultsToArray() {
        final JsonArrayBuilder builder = JsonFactory.newArrayBuilder();
        for (final AggregatedTimeseriesResult result : results) {
            builder.add(result.toJson());
        }
        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RetrieveAggregatedTimeseriesResponse that = (RetrieveAggregatedTimeseriesResponse) obj;
        return that.canEqual(this)
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(results, that.results)
                && contributingThings == that.contributingThings
                && excludedThings == that.excludedThings
                && Objects.equals(withheldByPath, that.withheldByPath)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveAggregatedTimeseriesResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), namespace, results, contributingThings, excludedThings, withheldByPath);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", namespace=" + namespace
                + ", results=" + results
                + ", contributingThings=" + contributingThings
                + ", excludedThings=" + excludedThings
                + ", withheldByPath=" + withheldByPath
                + "]";
    }
}
