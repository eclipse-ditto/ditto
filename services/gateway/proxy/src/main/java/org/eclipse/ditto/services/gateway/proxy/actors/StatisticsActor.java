/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.models.thingsearch.ThingsSearchConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor collecting statistics in the cluster.
 */
public final class StatisticsActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "statistics";

    private static final String SR_THING = ThingsMessagingConstants.SHARD_REGION;
    private static final String SR_POLICY = PoliciesMessagingConstants.SHARD_REGION;
    private static final String SR_CONCIERGE = ConciergeMessagingConstants.SHARD_REGION;
    private static final String SR_SEARCH_UPDATER = ThingsSearchConstants.SHARD_REGION;

    private static final String THINGS_ROOT = ThingsMessagingConstants.ROOT_ACTOR_PATH;
    private static final String POLICIES_ROOT = PoliciesMessagingConstants.ROOT_ACTOR_PATH;
    private static final String CONCIERGE_ROOT = ConciergeMessagingConstants.ROOT_ACTOR_PATH;
    private static final String SEARCH_UPDATER_ROOT = ThingsSearchConstants.UPDATER_ROOT_ACTOR_PATH;

    /**
     * The wait time in milliseconds to gather all statistics from the cluster nodes.
     */
    private static final int WAIT_TIME_MS = 250;

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final ClusterSharding clusterSharding;

    private Statistics currentStatistics;

    private StatisticsActor(final ActorRef pubSubMediator) {
        this.pubSubMediator = pubSubMediator;
        clusterSharding = ClusterSharding.get(getContext().getSystem());
    }

    /**
     * Creates Akka configuration object Props for this StatisticsActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator) {
        return Props.create(StatisticsActor.class, new Creator<StatisticsActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public StatisticsActor create() {
                return new StatisticsActor(pubSubMediator);
            }
        });
    }

    @Override
    public Receive createReceive() {
        // ignore - the message is too late
        return ReceiveBuilder.create()
                .match(RetrieveStatistics.class, retrieveStatistics -> {

                    tellRootActorToGetShardRegionState(THINGS_ROOT);
                    tellRootActorToGetShardRegionState(POLICIES_ROOT);
                    tellRootActorToGetShardRegionState(CONCIERGE_ROOT);
                    tellRootActorToGetShardRegionState(SEARCH_UPDATER_ROOT);

                    final Map<String, ShardStatisticsWrapper> shardStatisticsMap = new HashMap<>();
                    tellShardRegionToSendClusterShardingStats(SR_THING, shardStatisticsMap);
                    tellShardRegionToSendClusterShardingStats(SR_POLICY, shardStatisticsMap);
                    tellShardRegionToSendClusterShardingStats(SR_CONCIERGE, shardStatisticsMap);
                    tellShardRegionToSendClusterShardingStats(SR_SEARCH_UPDATER, shardStatisticsMap);

                    final ActorRef self = getSelf();
                    final ActorRef sender = getSender();
                    becomeStatisticsAwaiting(shardStatisticsMap, statistics ->
                            sender.tell(RetrieveStatisticsResponse.of(null, null, statistics.toJson(),
                                    retrieveStatistics.getDittoHeaders()), self)
                    );
                })
                .match(ShardRegion.CurrentShardRegionState.class, this::unhandled) // ignore, the message is too late
                .match(ShardRegion.ClusterShardingStats.class, this::unhandled) // ignore, the message is too late
                .matchAny(m -> log.warning("Got unknown message, expected a 'RetrieveStatistics': {}", m))
                .build();
    }

    private void tellRootActorToGetShardRegionState(final String rootActorPath) {
        pubSubMediator.tell(new DistributedPubSubMediator.SendToAll(
                rootActorPath, ShardRegion.getShardRegionStateInstance(), false), getSelf());
    }

    private void tellShardRegionToSendClusterShardingStats(final String shardRegion,
            final Map<String, ShardStatisticsWrapper> shardStatisticsMap) {
        shardStatisticsMap.put(shardRegion, new ShardStatisticsWrapper());
        clusterSharding.shardRegion(shardRegion).tell(
                new ShardRegion.GetClusterShardingStats(FiniteDuration.apply(10, TimeUnit.SECONDS)),
                getSelf());
    }

    private void becomeStatisticsAwaiting(final Map<String, ShardStatisticsWrapper> shardStatisticsMap,
            final Consumer<Statistics> statisticsConsumer) {

        getContext().getSystem()
                .scheduler()
                .scheduleOnce(FiniteDuration.apply(WAIT_TIME_MS, TimeUnit.MILLISECONDS), getSelf(),
                        new AskTimeoutException("Timed out"), getContext().getSystem().dispatcher(), getSelf());

        getContext().become(ReceiveBuilder.create()
                .match(RetrieveStatistics.class, rs -> currentStatistics != null,
                        retrieveStatistics -> getSender().tell(RetrieveStatisticsResponse.of(null, null,
                                currentStatistics.toJson(), retrieveStatistics.getDittoHeaders()), getSelf())
                )
                .match(ShardRegion.CurrentShardRegionState.class, currentShardRegionState -> {
                    final ShardStatisticsWrapper shardStatistics = getShardStatistics(shardStatisticsMap);
                    final Map<String, Long> shards = currentShardRegionState.getShards()
                            .stream()
                            .map(ShardRegion.ShardState::getEntityIds)
                            .flatMap(strSet -> strSet.stream()
                                    .map(str -> {
                                        // groupKey may be either namespace or resource-type+namespace (in case of concierge)
                                        final String[] groupKeys = str.split(":", 3);
                                        if (groupKeys.length == 1 && groupKeys[0].isEmpty()) {
                                            return "<empty>";
                                        } else if (groupKeys.length == 2) {
                                            // normal: namespace
                                            return groupKeys[0];
                                        } else {
                                            // concierge: resource-type + namespace
                                            return groupKeys[0] + ":" + groupKeys[1];
                                        }
                                    })
                            )
                            .collect(Collectors.groupingBy(Function.identity(),
                                    Collectors.mapping(Function.identity(), Collectors.counting())));

                    shards.forEach((key, value) -> {
                        if (shardStatistics.hotnessMap.containsKey(key)) {
                            shardStatistics.hotnessMap.put(key, shardStatistics.hotnessMap.get(key) + value);
                        } else {
                            shardStatistics.hotnessMap.put(key, value);
                        }
                    });
                })
                .match(ShardRegion.ClusterShardingStats.class, clusterShardingStats -> {
                    final ShardStatisticsWrapper shardStatistics = getShardStatistics(shardStatisticsMap);
                    final Map<Address, ShardRegion.ShardRegionStats> regions = clusterShardingStats.getRegions();
                    shardStatistics.count = regions.isEmpty() ? 0 : regions.values().stream()
                            .mapToInt(shardRegionStats -> shardRegionStats.getStats().isEmpty() ? 0 :
                                    shardRegionStats.getStats().values().stream()
                                            .mapToInt(o -> (Integer) o)
                                            .sum())
                            .sum();
                })
                .match(AskTimeoutException.class, askTimeout -> {
                    currentStatistics = new Statistics(
                            shardStatisticsMap.get(SR_THING).count,
                            shardStatisticsMap.get(SR_THING).hotnessMap,
                            shardStatisticsMap.get(SR_POLICY).count,
                            shardStatisticsMap.get(SR_POLICY).hotnessMap,
                            shardStatisticsMap.get(SR_CONCIERGE).count,
                            shardStatisticsMap.get(SR_CONCIERGE).hotnessMap,
                            shardStatisticsMap.get(SR_SEARCH_UPDATER).count,
                            shardStatisticsMap.get(SR_SEARCH_UPDATER).hotnessMap
                    );
                    statisticsConsumer.accept(currentStatistics);
                    getContext().unbecome();
                })
                .matchAny(m -> log.warning("Got unknown message during 'statisticsAwaiting': {}", m))
                .build()
        );
    }

    private ShardStatisticsWrapper getShardStatistics(final Map<String, ShardStatisticsWrapper> shardStatisticsMap) {
        ShardStatisticsWrapper shardStatistics = shardStatisticsMap.get(getSender().path().name());
        if (shardStatistics == null) {
            if (getSender().path().toStringWithoutAddress().contains(SR_SEARCH_UPDATER)) {
                shardStatistics = shardStatisticsMap.get(SR_SEARCH_UPDATER);
            } else if (getSender().path().toStringWithoutAddress().contains(SR_CONCIERGE)) {
                shardStatistics = shardStatisticsMap.get(SR_CONCIERGE);
            } else if (getSender().path().toStringWithoutAddress().contains(SR_POLICY)) {
                shardStatistics = shardStatisticsMap.get(SR_POLICY);
            } else if (getSender().path().toStringWithoutAddress().contains(SR_THING)) {
                shardStatistics = shardStatisticsMap.get(SR_THING);
            }
        }
        return shardStatistics;
    }

    private static final class ShardStatisticsWrapper {

        private long count = -1L;
        private final Map<String, Long> hotnessMap = new HashMap<>();
    }

    /**
     * Representation publicly available statistics about stuff within the Things service.
     */
    @Immutable
    private static final class Statistics implements Jsonifiable.WithPredicate<JsonObject, JsonField> {

        private static final JsonFieldDefinition<Long> HOT_THINGS_COUNT =
                JsonFactory.newLongFieldDefinition("hotThingsCount", FieldType.REGULAR);

        private static final JsonFieldDefinition<JsonObject> THINGS_NAMESPACE_HOTNESS =
                JsonFactory.newJsonObjectFieldDefinition("thingsNamespacesHotness", FieldType.REGULAR);

        private static final JsonFieldDefinition<Long> HOT_POLICIES_COUNT =
                JsonFactory.newLongFieldDefinition("hotPoliciesCount", FieldType.REGULAR);

        private static final JsonFieldDefinition<JsonObject> POLICIES_NAMESPACE_HOTNESS =
                JsonFactory.newJsonObjectFieldDefinition("policiesNamespacesHotness", FieldType.REGULAR);

        private static final JsonFieldDefinition<Long> HOT_CONCIERGE_ENFORCERS_COUNT =
                JsonFactory.newLongFieldDefinition("hotConciergeEnforcersCount", FieldType.REGULAR);

        private static final JsonFieldDefinition<JsonObject> CONCIERGE_ENFORCERS_HOTNESS =
                JsonFactory.newJsonObjectFieldDefinition("conciergeEnforcersHotness", FieldType.REGULAR);

        private static final JsonFieldDefinition<Long> HOT_SEARCH_UPDATERS_COUNT =
                JsonFactory.newLongFieldDefinition("hotSearchUpdatersCount", FieldType.REGULAR);

        private static final JsonFieldDefinition<JsonObject> SEARCH_UPDATERS_NAMESPACE_HOTNESS =
                JsonFactory.newJsonObjectFieldDefinition("searchUpdatersNamespacesHotness", FieldType.REGULAR);

        private final long hotThingsCount;
        private final Map<String, Long> thingsNamespacesHotness;
        private final long hotPoliciesCount;
        private final Map<String, Long> policiesNamespacesHotness;
        private final long hotConciergeEnforcersCount;
        private final Map<String, Long> conciergeEnforcersHotness;
        private final long hotSearchUpdatersCount;
        private final Map<String, Long> searchUpdatersNamespacesHotness;

        private Statistics(final long hotThingsCount,
                final Map<String, Long> thingsNamespacesHotness,
                final long hotPoliciesCount,
                final Map<String, Long> policiesNamespacesHotness,
                final long hotConciergeEnforcersCount,
                final Map<String, Long> conciergeEnforcersHotness,
                final long hotSearchUpdatersCount,
                final Map<String, Long> searchUpdatersNamespacesHotness) {

            this.hotThingsCount = hotThingsCount;
            this.thingsNamespacesHotness = thingsNamespacesHotness;
            this.hotPoliciesCount = hotPoliciesCount;
            this.policiesNamespacesHotness = policiesNamespacesHotness;
            this.hotConciergeEnforcersCount = hotConciergeEnforcersCount;
            this.conciergeEnforcersHotness = conciergeEnforcersHotness;
            this.hotSearchUpdatersCount = hotSearchUpdatersCount;
            this.searchUpdatersNamespacesHotness = searchUpdatersNamespacesHotness;
        }

        /**
         * Returns all non hidden marked fields of this statistics.
         *
         * @return a JSON object representation of this statistics including only non hidden marked fields.
         */
        @Override
        public JsonObject toJson() {
            return toJson(FieldType.notHidden());
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
            return JsonFactory.newObjectBuilder()
                    .set(HOT_THINGS_COUNT, hotThingsCount, predicate)
                    .set(THINGS_NAMESPACE_HOTNESS, buildHotnessMapJson(thingsNamespacesHotness), predicate)
                    .set(HOT_POLICIES_COUNT, hotPoliciesCount, predicate)
                    .set(POLICIES_NAMESPACE_HOTNESS, buildHotnessMapJson(policiesNamespacesHotness), predicate)
                    .set(HOT_CONCIERGE_ENFORCERS_COUNT, hotConciergeEnforcersCount, predicate)
                    .set(CONCIERGE_ENFORCERS_HOTNESS, buildHotnessMapJson(conciergeEnforcersHotness), predicate)
                    .set(HOT_SEARCH_UPDATERS_COUNT, hotSearchUpdatersCount, predicate)
                    .set(SEARCH_UPDATERS_NAMESPACE_HOTNESS, buildHotnessMapJson(searchUpdatersNamespacesHotness),
                            predicate)
                    .build();
        }

        private static JsonObject buildHotnessMapJson(final Map<String, Long> hotnessMap) {
            final JsonObjectBuilder objectBuilder = JsonFactory.newObjectBuilder();
            // sort it:
            hotnessMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEachOrdered(e -> {
                        final String nonemptyKey = ensureNonemptyKey(e.getKey(), hotnessMap);
                        objectBuilder.set(nonemptyKey, e.getValue());
                    });
            return objectBuilder.build();
        }

        private static String ensureNonemptyKey(final String key, final Map<String, ?> hotnessMap) {
            if (key.isEmpty()) {
                String candidate = "<empty>";
                for (int i = 0; ; ++i) {
                    if (!hotnessMap.containsKey(candidate)) {
                        return candidate;
                    }
                    candidate = String.format("<empty%d>", i);
                }
            } else {
                return key;
            }
        }

        @SuppressWarnings("OverlyComplexMethod")
        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) return false;
            final Statistics that = (Statistics) o;
            return hotThingsCount == that.hotThingsCount &&
                    hotPoliciesCount == that.hotPoliciesCount &&
                    hotConciergeEnforcersCount == that.hotConciergeEnforcersCount &&
                    hotSearchUpdatersCount == that.hotSearchUpdatersCount &&
                    Objects.equals(thingsNamespacesHotness, that.thingsNamespacesHotness) &&
                    Objects.equals(policiesNamespacesHotness, that.policiesNamespacesHotness) &&
                    Objects.equals(conciergeEnforcersHotness, that.conciergeEnforcersHotness) &&
                    Objects.equals(searchUpdatersNamespacesHotness, that.searchUpdatersNamespacesHotness);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hotThingsCount, thingsNamespacesHotness, hotPoliciesCount,
                    policiesNamespacesHotness, hotConciergeEnforcersCount,
                    conciergeEnforcersHotness,
                    hotSearchUpdatersCount, searchUpdatersNamespacesHotness);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "hotThingsCount=" + hotThingsCount +
                    ", thingsNamespacesHotness=" + thingsNamespacesHotness +
                    ", hotPoliciesCount=" + hotPoliciesCount +
                    ", policiesNamespacesHotness=" + policiesNamespacesHotness +
                    ", hotConciergeEnforcersCount=" + hotConciergeEnforcersCount +
                    ", conciergeEnforcersHotness=" + conciergeEnforcersHotness +
                    ", hotSearchUpdatersCount=" + hotSearchUpdatersCount +
                    ", searchUpdatersNamespacesHotness=" + searchUpdatersNamespacesHotness +
                    "]";
        }

    }

}
