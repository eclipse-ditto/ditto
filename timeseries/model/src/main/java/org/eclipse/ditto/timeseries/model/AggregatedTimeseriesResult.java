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
package org.eclipse.ditto.timeseries.model;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;

/**
 * One aggregated series produced by a {@link CrossThingTimeseriesQuery}: the values for a single
 * {@code (group, path)} combination.
 * <p>
 * Unlike {@link TimeseriesQueryResult} this carries no {@code thingId} — a group generally spans
 * many Things. When the query groups by {@link GroupBy#thingId()} the Thing ID appears as an
 * ordinary entry in {@link #getGroup()} instead, which keeps the shape uniform across grouping
 * choices rather than special-casing one dimension.
 *
 * @since 4.0.0
 */
public interface AggregatedTimeseriesResult extends Jsonifiable<JsonObject> {

    /**
     * Returns a new {@code AggregatedTimeseriesResult}.
     *
     * @param group the group identity: one entry per {@code groupBy} dimension, keyed by
     * {@link GroupBy#getGroupKey()}. Empty when the query declared no grouping.
     * @param path the path this series belongs to.
     * @param meta metadata about the data array.
     * @param data the aggregated values in chronological order; may be empty.
     * @return the new result.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AggregatedTimeseriesResult of(final Map<String, String> group,
            final JsonPointer path,
            final TimeseriesResultMeta meta,
            final List<TimeseriesDataValue> data) {

        return ImmutableAggregatedTimeseriesResult.of(group, path, meta, data);
    }

    /**
     * Parses an {@code AggregatedTimeseriesResult} from the given JSON object.
     *
     * @param jsonObject the JSON object.
     * @return the parsed result.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if a required field is missing.
     */
    static AggregatedTimeseriesResult fromJson(final JsonObject jsonObject) {
        return ImmutableAggregatedTimeseriesResult.fromJson(jsonObject);
    }

    /**
     * @return the group identity; empty when the query declared no grouping dimensions.
     */
    Map<String, String> getGroup();

    /**
     * @return the path this series belongs to.
     */
    JsonPointer getPath();

    /**
     * @return metadata about the data array.
     */
    TimeseriesResultMeta getMeta();

    /**
     * @return the aggregated values in chronological order.
     */
    List<TimeseriesDataValue> getData();
}
