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
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseries;

/**
 * Defines mapping strategies (map from signal type to {@link JsonifiableMapper}) for
 * {@link RetrieveAggregatedTimeseries} commands.
 * <p>
 * The whole query — including its namespace — is carried in the payload, so validation runs through
 * {@link CrossThingTimeseriesQuery#fromJson} exactly as it does for an HTTP request. A malformed
 * cross-Thing query is therefore rejected identically on every transport.
 *
 * @since 4.0.0
 */
final class TimeseriesAggregateCommandMappingStrategies
        extends AbstractMappingStrategies<RetrieveAggregatedTimeseries> {

    private static final TimeseriesAggregateCommandMappingStrategies INSTANCE =
            new TimeseriesAggregateCommandMappingStrategies();

    private TimeseriesAggregateCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    static TimeseriesAggregateCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<RetrieveAggregatedTimeseries>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<RetrieveAggregatedTimeseries>> strategies = new HashMap<>();
        strategies.put(RetrieveAggregatedTimeseries.TYPE,
                adaptable -> RetrieveAggregatedTimeseries.of(
                        CrossThingTimeseriesQuery.fromJson(payloadValueAsObject(adaptable)),
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
