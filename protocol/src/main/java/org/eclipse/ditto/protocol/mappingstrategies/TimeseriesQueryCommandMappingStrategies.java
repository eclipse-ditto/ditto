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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries;

/**
 * Defines mapping strategies (map from signal type to {@link JsonifiableMapper}) for
 * {@link RetrieveTimeseries} commands.
 *
 * @since 4.0.0
 */
final class TimeseriesQueryCommandMappingStrategies extends AbstractMappingStrategies<RetrieveTimeseries> {

    private static final TimeseriesQueryCommandMappingStrategies INSTANCE =
            new TimeseriesQueryCommandMappingStrategies();

    private TimeseriesQueryCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    static TimeseriesQueryCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<RetrieveTimeseries>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<RetrieveTimeseries>> strategies = new HashMap<>();
        strategies.put(RetrieveTimeseries.TYPE,
                adaptable -> RetrieveTimeseries.of(
                        TimeseriesQuery.fromJson(payloadValueAsObject(adaptable)),
                        dittoHeadersFrom(adaptable)));
        return strategies;
    }

    private static JsonObject payloadValueAsObject(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonObject.empty());
    }
}
