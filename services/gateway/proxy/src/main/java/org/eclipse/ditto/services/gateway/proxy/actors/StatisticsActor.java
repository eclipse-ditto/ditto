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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.models.thingsearch.ThingsSearchConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetailsResponse;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
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
    static final String ACTOR_NAME = "statistics";

    private static final String SR_THING = ThingsMessagingConstants.SHARD_REGION;
    private static final String SR_POLICY = PoliciesMessagingConstants.SHARD_REGION;
    private static final String SR_SEARCH_UPDATER = ThingsSearchConstants.SHARD_REGION;

    private static final String THINGS_ROOT = ThingsMessagingConstants.ROOT_ACTOR_PATH;
    private static final String POLICIES_ROOT = PoliciesMessagingConstants.ROOT_ACTOR_PATH;
    private static final String SEARCH_UPDATER_ROOT = ThingsSearchConstants.UPDATER_ROOT_ACTOR_PATH;

    private static final String EMPTY_STRING_TAG = "<empty>";

    /**
     * The wait time in milliseconds to gather all statistics from the cluster nodes.
     */
    private static final int WAIT_TIME_MS = 250;
    /**
     * The time in milliseconds to retrieve all Hot Entities from the cluster nodes.
     */
    private static final int SCHEDULE_INTERNAL_RETRIEVE_COMMAND = 15000;

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final ClusterSharding clusterSharding;
    private Gauge hotThings;
    private Gauge hotPolicies;
    private Gauge hotSearchUpdaters;
    private Statistics currentStatistics;
    private StatisticsDetails currentStatisticsDetails;

    @SuppressWarnings("unused")
    private StatisticsActor(final ActorRef pubSubMediator) {
        this.pubSubMediator = pubSubMediator;
        hotThings = DittoMetrics.gauge(Statistics.HOT_THINGS);
        hotPolicies = DittoMetrics.gauge(Statistics.HOT_POLICIES);
        hotSearchUpdaters = DittoMetrics.gauge(Statistics.HOT_SEARCH_UPDATERS);

        clusterSharding = ClusterSharding.get(getContext().getSystem());
        scheduleInternalRetrieveHotEntities();
    }

    /**
     * Creates Akka configuration object Props for this StatisticsActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use.
     * @return the Akka configuration Props object.
     */
    static Props props(final ActorRef pubSubMediator) {

        return Props.create(StatisticsActor.class, pubSubMediator);
    }

    private void scheduleInternalRetrieveHotEntities() {
        initHotMetrics();
        getContext().getSystem()
                .scheduler()
                .schedule(FiniteDuration.apply(WAIT_TIME_MS, TimeUnit.MILLISECONDS),
                        FiniteDuration.apply(SCHEDULE_INTERNAL_RETRIEVE_COMMAND, TimeUnit.MILLISECONDS), getSelf(),
                        InternalRetrieveStatistics.newInstance(), getContext().getSystem().dispatcher(),
                        ActorRef.noSender());
    }

    private static Map<String, ShardStatisticsWrapper> initShardStatisticsMap() {
        final Map<String, ShardStatisticsWrapper> shardStatisticsMap = new HashMap<>();
        shardStatisticsMap.put(SR_THING, new ShardStatisticsWrapper());
        shardStatisticsMap.put(SR_POLICY, new ShardStatisticsWrapper());
        shardStatisticsMap.put(SR_SEARCH_UPDATER, new ShardStatisticsWrapper());
        return shardStatisticsMap;
    }

    @Override
    public Receive createReceive() {
        // ignore - the message is too late
        return ReceiveBuilder.create()
                .match(InternalRetrieveStatistics.class, unused -> {
                    tellShardRegionToSendClusterShardingStats();

                    becomeStatisticsAwaiting(statistics -> {
                        hotThings.set(statistics.hotThingsCount);
                        hotPolicies.set(statistics.hotPoliciesCount);
                        hotSearchUpdaters.set(statistics.hotSearchUpdatersCount);
                    });
                })
                .match(RetrieveStatistics.class, retrieveStatistics -> {
                    tellShardRegionToSendClusterShardingStats();

                    final ActorRef self = getSelf();
                    final ActorRef sender = getSender();
                    becomeStatisticsAwaiting(statistics ->
                            sender.tell(RetrieveStatisticsResponse.of(statistics.toJson(),
                                    retrieveStatistics.getDittoHeaders()), self)
                    );
                })
                .match(RetrieveStatisticsDetails.class, retrieveStatistics -> {

                    tellRootActorToRetrieveStatistics(THINGS_ROOT, retrieveStatistics);
                    tellRootActorToRetrieveStatistics(POLICIES_ROOT, retrieveStatistics);
                    tellRootActorToRetrieveStatistics(SEARCH_UPDATER_ROOT, retrieveStatistics);

                    final ActorRef self = getSelf();
                    final ActorRef sender = getSender();
                    becomeStatisticsDetailsAwaiting(statistics ->
                            sender.tell(RetrieveStatisticsResponse.of(statistics.toJson(),
                                    retrieveStatistics.getDittoHeaders()), self)
                    );
                })
                .match(ShardRegion.CurrentShardRegionState.class, this::unhandled) // ignore, the message is too late
                .match(ShardRegion.ClusterShardingStats.class, this::unhandled) // ignore, the message is too late
                .matchAny(m -> log.warning("Got unknown message, expected a 'RetrieveStatistics': {}", m))
                .build();
    }

    private void tellShardRegionToSendClusterShardingStats() {
        tellShardRegionToSendClusterShardingStats(SR_THING);
        tellShardRegionToSendClusterShardingStats(SR_POLICY);
        tellShardRegionToSendClusterShardingStats(SR_SEARCH_UPDATER);
    }

    private void tellRootActorToRetrieveStatistics(final String rootActorPath,
            final RetrieveStatisticsDetails retrieveStatistics) {
        pubSubMediator.tell(new DistributedPubSubMediator.SendToAll(rootActorPath, retrieveStatistics, false),
                getSelf());
    }

    private void tellShardRegionToSendClusterShardingStats(final String shardRegion) {
        clusterSharding.shardRegion(shardRegion).tell(
                new ShardRegion.GetClusterShardingStats(FiniteDuration.apply(10, TimeUnit.SECONDS)),
                getSelf());
    }

    private void initHotMetrics() {
        hotThings.set(0L);
        hotPolicies.set(0L);
        hotSearchUpdaters.set(0L);
    }

    private void becomeStatisticsAwaiting(final Consumer<Statistics> statisticsConsumer) {

        final Map<String, ShardStatisticsWrapper> shardStatisticsMap = initShardStatisticsMap();

        getContext().getSystem()
                .scheduler()
                .scheduleOnce(FiniteDuration.apply(WAIT_TIME_MS, TimeUnit.MILLISECONDS), getSelf(),
                        new AskTimeoutException("Timed out"), getContext().getSystem().dispatcher(), getSelf());

        getContext().become(ReceiveBuilder.create()
                .match(RetrieveStatistics.class, rs -> currentStatistics != null,
                        retrieveStatistics -> getSender().tell(RetrieveStatisticsResponse.of(
                                currentStatistics.toJson(), retrieveStatistics.getDittoHeaders()), getSelf())
                )
                .match(ShardRegion.ClusterShardingStats.class, clusterShardingStats -> {
                    final ShardStatisticsWrapper shardStatistics = getShardStatistics(shardStatisticsMap,
                            getSender().path().name());
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
                            shardStatisticsMap.get(SR_POLICY).count,
                            shardStatisticsMap.get(SR_SEARCH_UPDATER).count
                    );
                    statisticsConsumer.accept(currentStatistics);
                    getContext().unbecome();
                })
                .matchAny(m -> log.warning("Got unknown message during 'statisticsAwaiting': {}", m))
                .build()
        );
    }

    private void becomeStatisticsDetailsAwaiting(final Consumer<StatisticsDetails> statisticsDetailsConsumer) {

        final Map<String, ShardStatisticsWrapper> shardStatisticsMap = initShardStatisticsMap();

        getContext().getSystem()
                .scheduler()
                .scheduleOnce(FiniteDuration.apply(WAIT_TIME_MS * 8L, TimeUnit.MILLISECONDS), getSelf(),
                        new AskTimeoutException("Timed out"), getContext().getSystem().dispatcher(), getSelf());

        getContext().become(ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, rs -> currentStatisticsDetails != null,
                        retrieveStatistics -> getSender().tell(RetrieveStatisticsResponse.of(
                                currentStatisticsDetails.toJson(), retrieveStatistics.getDittoHeaders()), getSelf())
                )
                .match(RetrieveStatisticsDetailsResponse.class, retrieveStatisticsDetailsResponse -> {
                    final String shardRegion = retrieveStatisticsDetailsResponse.getStatisticsDetails()
                            .stream()
                            .findFirst()
                            .map(JsonField::getKeyName)
                            .orElse(null);

                    if (shardRegion != null) {
                        final ShardStatisticsWrapper shardStatistics =
                                getShardStatistics(shardStatisticsMap, shardRegion);

                        retrieveStatisticsDetailsResponse.getStatisticsDetails().getValue(shardRegion)
                                .map(JsonValue::asObject)
                                .map(JsonObject::stream)
                                .ifPresent(namespaceEntries -> namespaceEntries.forEach(field ->
                                        shardStatistics.hotnessMap
                                                .merge(field.getKeyName(), field.getValue().asLong(), Long::sum)
                                ));
                    }
                })
                .match(AskTimeoutException.class, askTimeout -> {
                    currentStatisticsDetails = new StatisticsDetails(
                            shardStatisticsMap.get(SR_THING).hotnessMap,
                            shardStatisticsMap.get(SR_POLICY).hotnessMap,
                            shardStatisticsMap.get(SR_SEARCH_UPDATER).hotnessMap
                    );
                    statisticsDetailsConsumer.accept(currentStatisticsDetails);
                    getContext().unbecome();
                })
                .matchAny(m -> log.warning("Got unknown message during 'statisticsDetailsAwaiting': {}", m))
                .build()
        );
    }

    private ShardStatisticsWrapper getShardStatistics(final Map<String, ShardStatisticsWrapper> shardStatisticsMap,
            final String shardRegion) {

        ShardStatisticsWrapper shardStatistics = shardStatisticsMap.get(shardRegion);
        if (shardStatistics == null) {
            if (getSender().path().toStringWithoutAddress().contains(SR_SEARCH_UPDATER)) {
                shardStatistics = shardStatisticsMap.get(SR_SEARCH_UPDATER);
            } else if (getSender().path().toStringWithoutAddress().contains(SR_POLICY)) {
                shardStatistics = shardStatisticsMap.get(SR_POLICY);
            } else if (getSender().path().toStringWithoutAddress().contains(SR_THING)) {
                shardStatistics = shardStatisticsMap.get(SR_THING);
            }
        }
        return shardStatistics;
    }

    private static final class ShardStatisticsWrapper {

        private final Map<String, Long> hotnessMap = new HashMap<>();
        private long count = -1L;
    }

    /**
     * Representation publicly available statistics about hot entities within Ditto.
     */
    @Immutable
    private static final class Statistics implements Jsonifiable.WithPredicate<JsonObject, JsonField> {

        private static final String HOT_THINGS = "hotThingsCount";
        private static final String HOT_POLICIES = "hotPoliciesCount";
        private static final String HOT_SEARCH_UPDATERS = "hotSearchUpdatersCount";

        private static final JsonFieldDefinition<Long> HOT_THINGS_COUNT =
                JsonFactory.newLongFieldDefinition(HOT_THINGS, FieldType.REGULAR);

        private static final JsonFieldDefinition<Long> HOT_POLICIES_COUNT =
                JsonFactory.newLongFieldDefinition(HOT_POLICIES, FieldType.REGULAR);

        private static final JsonFieldDefinition<Long> HOT_SEARCH_UPDATERS_COUNT =
                JsonFactory.newLongFieldDefinition(HOT_SEARCH_UPDATERS, FieldType.REGULAR);

        private final long hotThingsCount;
        private final long hotPoliciesCount;
        private final long hotSearchUpdatersCount;

        private Statistics(final long hotThingsCount,
                final long hotPoliciesCount,
                final long hotSearchUpdatersCount) {

            this.hotThingsCount = hotThingsCount;
            this.hotPoliciesCount = hotPoliciesCount;
            this.hotSearchUpdatersCount = hotSearchUpdatersCount;
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
        public JsonObject toJson(@Nonnull final JsonSchemaVersion schemaVersion,
                @Nonnull final Predicate<JsonField> predicate) {
            return JsonFactory.newObjectBuilder()
                    .set(HOT_THINGS_COUNT, hotThingsCount, predicate)
                    .set(HOT_POLICIES_COUNT, hotPoliciesCount, predicate)
                    .set(HOT_SEARCH_UPDATERS_COUNT, hotSearchUpdatersCount, predicate)
                    .build();
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
                    hotSearchUpdatersCount == that.hotSearchUpdatersCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(hotThingsCount, hotPoliciesCount, hotSearchUpdatersCount);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "hotThingsCount=" + hotThingsCount +
                    ", hotPoliciesCount=" + hotPoliciesCount +
                    ", hotSearchUpdatersCount=" + hotSearchUpdatersCount +
                    "]";
        }

    }

    /**
     * Representation statistics details about namespace hotness within Ditto.
     */
    @Immutable
    private static final class StatisticsDetails implements Jsonifiable.WithPredicate<JsonObject, JsonField> {

        private static final JsonFieldDefinition<JsonObject> THINGS_NAMESPACE_HOTNESS =
                JsonFactory.newJsonObjectFieldDefinition("thingsNamespacesHotness", FieldType.REGULAR);

        private static final JsonFieldDefinition<JsonObject> POLICIES_NAMESPACE_HOTNESS =
                JsonFactory.newJsonObjectFieldDefinition("policiesNamespacesHotness", FieldType.REGULAR);

        private static final JsonFieldDefinition<JsonObject> SEARCH_UPDATERS_NAMESPACE_HOTNESS =
                JsonFactory.newJsonObjectFieldDefinition("searchUpdatersNamespacesHotness", FieldType.REGULAR);

        private final Map<String, Long> thingsNamespacesHotness;
        private final Map<String, Long> policiesNamespacesHotness;
        private final Map<String, Long> searchUpdatersNamespacesHotness;

        private StatisticsDetails(final Map<String, Long> thingsNamespacesHotness,
                final Map<String, Long> policiesNamespacesHotness,
                final Map<String, Long> searchUpdatersNamespacesHotness) {

            this.thingsNamespacesHotness = thingsNamespacesHotness;
            this.policiesNamespacesHotness = policiesNamespacesHotness;
            this.searchUpdatersNamespacesHotness = searchUpdatersNamespacesHotness;
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
                String candidate = EMPTY_STRING_TAG;
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

        /**
         * Returns all non hidden marked fields of this StatisticsDetails.
         *
         * @return a JSON object representation of this StatisticsDetails including only non hidden marked fields.
         */
        @Override
        public JsonObject toJson() {
            return toJson(FieldType.notHidden());
        }

        @Override
        public JsonObject toJson(@Nonnull final JsonSchemaVersion schemaVersion,
                @Nonnull final Predicate<JsonField> predicate) {
            return JsonFactory.newObjectBuilder()
                    .set(THINGS_NAMESPACE_HOTNESS, buildHotnessMapJson(thingsNamespacesHotness), predicate)
                    .set(POLICIES_NAMESPACE_HOTNESS, buildHotnessMapJson(policiesNamespacesHotness), predicate)
                    .set(SEARCH_UPDATERS_NAMESPACE_HOTNESS, buildHotnessMapJson(searchUpdatersNamespacesHotness),
                            predicate)
                    .build();
        }

        @SuppressWarnings("OverlyComplexMethod")
        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) return false;
            final StatisticsDetails that = (StatisticsDetails) o;
            return Objects.equals(thingsNamespacesHotness, that.thingsNamespacesHotness) &&
                    Objects.equals(policiesNamespacesHotness, that.policiesNamespacesHotness) &&
                    Objects.equals(searchUpdatersNamespacesHotness, that.searchUpdatersNamespacesHotness);
        }

        @Override
        public int hashCode() {
            return Objects.hash(thingsNamespacesHotness, policiesNamespacesHotness, searchUpdatersNamespacesHotness);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "thingsNamespacesHotness=" + thingsNamespacesHotness +
                    ", policiesNamespacesHotness=" + policiesNamespacesHotness +
                    ", searchUpdatersNamespacesHotness=" + searchUpdatersNamespacesHotness +
                    "]";
        }

    }

    private static class InternalRetrieveStatistics {

        private InternalRetrieveStatistics() {
            // no-op
        }

        static InternalRetrieveStatistics newInstance() {
            return new InternalRetrieveStatistics();
        }
    }

}
