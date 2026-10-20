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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.timeseries.model.AggregatedTimeseriesResult;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseriesResponse;

/**
 * Defines mapping strategies (map from response type to {@link JsonifiableMapper}) for
 * {@link RetrieveAggregatedTimeseriesResponse}.
 * <p>
 * The response entity carries {@code results} and {@code authorization} but not the namespace — the
 * HTTP caller already knows it from the request URL. Over the Ditto protocol it is recovered from
 * the topic path, which is why the cross-Thing topic keeps the real namespace rather than the
 * {@code _} placeholder used for the entity name.
 *
 * @since 4.0.0
 */
final class TimeseriesAggregateCommandResponseMappingStrategies
        extends AbstractMappingStrategies<RetrieveAggregatedTimeseriesResponse> {

    private static final String RESULTS = "results";
    private static final String AUTHORIZATION = "authorization";
    private static final String CONTRIBUTING_THINGS = "contributingThings";
    private static final String EXCLUDED_THINGS = "excludedThings";
    private static final String WITHHELD_BY_PATH = "withheldByPath";

    private static final TimeseriesAggregateCommandResponseMappingStrategies INSTANCE =
            new TimeseriesAggregateCommandResponseMappingStrategies();

    private TimeseriesAggregateCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    static TimeseriesAggregateCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<RetrieveAggregatedTimeseriesResponse>>
    initMappingStrategies() {

        final Map<String, JsonifiableMapper<RetrieveAggregatedTimeseriesResponse>> strategies =
                new HashMap<>();
        strategies.put(RetrieveAggregatedTimeseriesResponse.TYPE, adaptable -> {
            final String namespace = adaptable.getTopicPath().getNamespace();
            final JsonObject entity = payloadValueAsObject(adaptable);

            final JsonArray resultsArray = entity.getValue(RESULTS)
                    .filter(JsonValue::isArray)
                    .map(JsonValue::asArray)
                    .orElseGet(JsonArray::empty);
            final List<AggregatedTimeseriesResult> results = new ArrayList<>(resultsArray.getSize());
            for (final JsonValue value : resultsArray) {
                results.add(AggregatedTimeseriesResult.fromJson(value.asObject()));
            }

            final JsonObject authorization = entity.getValue(AUTHORIZATION)
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .orElseGet(JsonObject::empty);

            return RetrieveAggregatedTimeseriesResponse.of(namespace, results,
                    intOrZero(authorization, CONTRIBUTING_THINGS),
                    intOrZero(authorization, EXCLUDED_THINGS),
                    withheldFrom(authorization),
                    dittoHeadersFrom(adaptable));
        });
        return strategies;
    }

    private static int intOrZero(final JsonObject authorization, final String key) {
        return authorization.getValue(key)
                .filter(JsonValue::isNumber)
                .map(JsonValue::asInt)
                .orElse(0);
    }

    private static Map<String, Integer> withheldFrom(final JsonObject authorization) {
        final Map<String, Integer> withheld = new LinkedHashMap<>();
        authorization.getValue(WITHHELD_BY_PATH)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .ifPresent(byPath -> {
                    for (final JsonField field : byPath) {
                        if (field.getValue().isNumber()) {
                            withheld.put(field.getKeyName(), field.getValue().asInt());
                        }
                    }
                });
        return withheld;
    }

    private static JsonObject payloadValueAsObject(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonObject.empty());
    }
}
