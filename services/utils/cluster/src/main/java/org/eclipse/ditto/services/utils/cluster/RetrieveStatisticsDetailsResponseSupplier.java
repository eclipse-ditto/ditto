/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.cluster;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetailsResponse;

import akka.actor.ActorRef;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.pattern.PatternsCS;

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
        return PatternsCS.ask(shardRegion, ShardRegion.getShardRegionStateInstance(),
                Duration.ofSeconds(5))
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error(throwable, "Could not determine 'ShardRegionState' for shard region <{}>",
                                shardRegionName);
                        return RetrieveStatisticsDetailsResponse.of(JsonObject.newBuilder()
                                        .set(shardRegionName, JsonFactory.newObject())
                                        .build(),
                                dittoHeaders);
                    } else if (result instanceof ShardRegion.CurrentShardRegionState) {
                        final Map<String, Long> shardStats =
                                ((ShardRegion.CurrentShardRegionState) result).getShards()
                                        .stream()
                                        .map(ShardRegion.ShardState::getEntityIds)
                                        .flatMap(strSet -> strSet.stream()
                                                .map(str -> {
                                                    // groupKey may be either namespace or resource-type+namespace (in case of concierge)
                                                    final String[] groupKeys = str.split(":", 3);
                                                    // assume String.split(String, int) may not return an empty array
                                                    switch (groupKeys.length) {
                                                        case 0:
                                                            // should not happen with Java 8 strings, but just in case
                                                            return EMPTY_ID;
                                                        case 1:
                                                        case 2:
                                                            // normal: namespace
                                                            return ensureNonemptyString(
                                                                    groupKeys[0]);
                                                        default:
                                                            // concierge: resource-type + namespace
                                                            return groupKeys[0] + ":" +
                                                                    groupKeys[1];
                                                    }
                                                })
                                        )
                                        .collect(Collectors.groupingBy(Function.identity(),
                                                Collectors.mapping(Function.identity(),
                                                        Collectors.counting())));

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
