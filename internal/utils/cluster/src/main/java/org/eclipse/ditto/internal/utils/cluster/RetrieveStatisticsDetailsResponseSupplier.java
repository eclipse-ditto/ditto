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
package org.eclipse.ditto.internal.utils.cluster;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetailsResponse;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ActorRef;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.pattern.Patterns;

/**
 * Supplier of {@link RetrieveStatisticsDetailsResponse}s for a specific shard region - determines the "hot entities"
 * per namespace and aggregates them into a single {@link RetrieveStatisticsDetailsResponse}.
 */
public final class RetrieveStatisticsDetailsResponseSupplier
        implements Function<DittoHeaders, CompletionStage<RetrieveStatisticsDetailsResponse>> {

    private static final String EMPTY_ID = "<empty>";

    private final ActorRef shardRegion;
    private final String shardRegionName;
    private final DiagnosticLoggingAdapter log;

    private RetrieveStatisticsDetailsResponseSupplier(final ActorRef shardRegion, final String shardRegionName,
            final DiagnosticLoggingAdapter log) {
        this.shardRegion = shardRegion;
        this.shardRegionName = shardRegionName;
        this.log = log;
    }

    /**
     * Creates a new instance of a {@link RetrieveStatisticsDetailsResponse} supplier for the passed {@code shardRegion}
     * and {@code shardRegionName}.
     *
     * @param shardRegion the shard region ActoRef to use for retrieving the shard region state.
     * @param shardRegionName the shard region name.
     * @param log the logger to use.
     * @return the new RetrieveStatisticsDetailsResponse supplier
     */
    public static RetrieveStatisticsDetailsResponseSupplier of(final ActorRef shardRegion, final String shardRegionName,
            final DiagnosticLoggingAdapter log) {
        return new RetrieveStatisticsDetailsResponseSupplier(shardRegion, shardRegionName, log);
    }

    @Override
    public CompletionStage<RetrieveStatisticsDetailsResponse> apply(final DittoHeaders dittoHeaders) {
        return Patterns.ask(shardRegion, ShardRegion.getShardRegionStateInstance(), Duration.ofSeconds(5))
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error(throwable, "Could not determine 'ShardRegionState' for shard region <{}>",
                                shardRegionName);
                        return RetrieveStatisticsDetailsResponse.of(JsonObject.newBuilder()
                                        .set(shardRegionName, JsonFactory.newObject())
                                        .build(),
                                dittoHeaders);
                    } else if (result instanceof ShardRegion.CurrentShardRegionState currentShardRegionState) {
                        final Collector<String, ?, Map<String, Long>> stringMapCollector =
                                Collectors.groupingBy(Function.identity(),
                                        Collectors.mapping(Function.identity(), Collectors.counting()));
                        final Map<String, Long> shardStats =
                                currentShardRegionState.getShards()
                                        .stream()
                                        .map(ShardRegion.ShardState::getEntityIds)
                                        .flatMap(strSet -> strSet.stream()
                                                .map(str -> {
                                                    // groupKey may be either namespace or resource-type+namespace
                                                    final String[] groupKeys = str.split(":", 2);
                                                    // assume String.split(String, int) may not return an empty array
                                                    if (groupKeys.length == 0) {
                                                        // should not happen with Java 8 strings, but just in case
                                                        return EMPTY_ID;
                                                    }
                                                    return ensureNonemptyString(groupKeys[0]);
                                                })
                                        )
                                        .collect(stringMapCollector);

                        final JsonObject namespaceStats = shardStats.entrySet().stream()
                                .map(entry -> JsonField.newInstance(entry.getKey(),
                                        JsonValue.of(entry.getValue())))
                                .collect(JsonCollectors.fieldsToObject());

                        final JsonObject thingNamespacesStats = JsonObject.newBuilder()
                                .set(shardRegionName, namespaceStats)
                                .build();

                        return RetrieveStatisticsDetailsResponse.of(thingNamespacesStats,
                                dittoHeaders);
                    } else {
                        log.warning("Unexpected answer to " +
                                "'ShardRegion.getShardRegionStateInstance()': {}", result);
                        return RetrieveStatisticsDetailsResponse.of(JsonObject.newBuilder()
                                        .set(shardRegionName, JsonFactory.newObject())
                                        .build(),
                                dittoHeaders);
                    }
                });
    }

    private static String ensureNonemptyString(final String possiblyEmptyString) {
        return possiblyEmptyString.isEmpty() ? EMPTY_ID : possiblyEmptyString;
    }
}
