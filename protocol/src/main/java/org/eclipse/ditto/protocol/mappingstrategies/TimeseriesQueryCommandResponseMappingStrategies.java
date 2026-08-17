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
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseriesResponse;

/**
 * Defines mapping strategies (map from response type to {@link JsonifiableMapper}) for
 * {@link RetrieveTimeseriesResponse}.
 *
 * @since 4.0.0
 */
final class TimeseriesQueryCommandResponseMappingStrategies
        extends AbstractMappingStrategies<RetrieveTimeseriesResponse> {

    private static final TimeseriesQueryCommandResponseMappingStrategies INSTANCE =
            new TimeseriesQueryCommandResponseMappingStrategies();

    private TimeseriesQueryCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    static TimeseriesQueryCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<RetrieveTimeseriesResponse>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<RetrieveTimeseriesResponse>> strategies = new HashMap<>();
        strategies.put(RetrieveTimeseriesResponse.TYPE, adaptable -> {
            final ThingId thingId = ThingId.of(adaptable.getTopicPath().getNamespace() + ":" +
                    adaptable.getTopicPath().getEntityName());
            final JsonArray results = adaptable.getPayload().getValue()
                    .filter(JsonValue::isArray)
                    .map(JsonValue::asArray)
                    .orElseGet(JsonArray::empty);
            final List<TimeseriesQueryResult> parsed = new ArrayList<>(results.getSize());
            for (final JsonValue value : results) {
                parsed.add(TimeseriesQueryResult.fromJson(value.asObject()));
            }
            return RetrieveTimeseriesResponse.of(thingId, parsed, dittoHeadersFrom(adaptable));
        });
        return strategies;
    }
}
