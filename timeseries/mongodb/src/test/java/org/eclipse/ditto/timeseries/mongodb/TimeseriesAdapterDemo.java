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
package org.eclipse.ditto.timeseries.mongodb;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Standalone demo for exercising {@link MongoDbTimeseriesAdapter} against a real local MongoDB.
 * <p>
 * Intended for hand-running while developing the timeseries feature — writes a handful of fake
 * temperature readings for a unique demo Thing, queries them back, prints the result, and drops
 * the data on the way out.
 *
 * <h2>Running</h2>
 * Default URI is {@code mongodb://localhost:27017}; override via the
 * {@code TIMESERIES_MONGODB_TEST_URI} environment variable.
 * <pre>
 *   # from the project root
 *   mvn -pl timeseries/mongodb compile test-compile
 *   mvn -pl timeseries/mongodb exec:java \
 *       -Dexec.classpathScope=test \
 *       -Dexec.mainClass=org.eclipse.ditto.timeseries.mongodb.TimeseriesAdapterDemo
 * </pre>
 * Or run the {@code main} method directly from your IDE.
 *
 * <p>Each invocation uses a fresh, unique demo Thing namespace ({@code demo.&lt;timestamp&gt;}) so
 * runs do not collide with each other. The adapter writes into a regular MongoDB collection — no
 * {@code timeseries} options on createCollection — so the demo works against MongoDB versions
 * older than 5.0 too.
 */
public final class TimeseriesAdapterDemo {

    private static final String DEFAULT_URI = "mongodb://localhost:27017";
    private static final String DATABASE = "ditto_ts_demo";

    private TimeseriesAdapterDemo() {
        throw new AssertionError();
    }

    public static void main(final String[] args) throws Exception {
        final String uri = System.getenv().getOrDefault("TIMESERIES_MONGODB_TEST_URI", DEFAULT_URI);

        final ThingId thingId = ThingId.of("demo.timeseries",
                "sensor-" + UUID.randomUUID().toString().substring(0, 8));
        final JsonPointer path = JsonPointer.of("/features/environment/properties/temperature");

        final Config rootConfig = ConfigFactory.parseString(String.format(
                "ditto.mongodb.uri = \"%s\"\nditto.mongodb.database = \"%s\"\n", uri, DATABASE));
        final MongoDbConfig mongoDbConfig =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(rootConfig));
        final MongoDbTimeseriesAdapterConfig config = DefaultMongoDbTimeseriesAdapterConfig.of(
                mongoDbConfig, "ts_", Granularity.SECONDS);
        final MongoDbTimeseriesAdapter adapter = new MongoDbTimeseriesAdapter();

        System.out.println("=== Connecting to MongoDB at " + uri + " ===");
        adapter.initialize(config).toCompletableFuture().get();
        System.out.println("Adapter health: " + adapter.getHealth());

        try {
            final List<TimeseriesDataPoint> writes = sampleData(thingId, path);
            System.out.println();
            System.out.println("=== Writing " + writes.size() + " data points for " + thingId + " ===");
            adapter.writeBatch(writes).toCompletableFuture().get();
            System.out.println("Write batch complete.");

            // Slight pause for write-acknowledgement determinism on slower drivers.
            Thread.sleep(150);

            final TimeseriesQuery query = TimeseriesQuery.of(
                    thingId,
                    Collections.singletonList(path),
                    Instant.parse("2026-01-14T00:00:00Z"),
                    Instant.parse("2026-01-14T01:00:00Z"));

            System.out.println();
            System.out.println("=== Querying the data back ===");
            System.out.println("from=" + query.getFrom() + " to=" + query.getTo());
            final List<TimeseriesQueryResult> results =
                    adapter.query(query).toCompletableFuture().get();

            System.out.println("Got " + results.size() + " result(s).");
            for (final TimeseriesQueryResult result : results) {
                System.out.println("  path=" + result.getPath());
                System.out.println("  meta=" + result.getMeta());
                result.getData().forEach(value ->
                        System.out.println("    " + value.getTimestamp() + " -> " + value.getValue().orElse(null)));
            }

            System.out.println();
            System.out.println("=== Done ===");
        } finally {
            adapter.shutdown().toCompletableFuture().get();
            System.out.println("Adapter shut down.");
        }
    }

    private static List<TimeseriesDataPoint> sampleData(final ThingId thingId, final JsonPointer path) {
        final Map<String, String> tags = new LinkedHashMap<>();
        tags.put("attributes/building", "A");
        tags.put("attributes/floor", "2");

        final List<TimeseriesDataPoint> points = new ArrayList<>();
        Instant t = Instant.parse("2026-01-14T00:00:00Z");
        final double[] temperatures = {22.0, 22.3, 22.5, 22.4, 22.2};
        for (int i = 0; i < temperatures.length; i++) {
            points.add(TimeseriesDataPoint.of(
                    thingId,
                    path,
                    t,
                    JsonValue.of(temperatures[i]),
                    100L + i,
                    tags,
                    "cel"));
            t = t.plus(10, ChronoUnit.MINUTES);
        }
        return points;
    }
}
