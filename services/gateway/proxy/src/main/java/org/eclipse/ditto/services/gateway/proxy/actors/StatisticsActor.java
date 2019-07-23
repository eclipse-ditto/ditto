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

import static io.jsonwebtoken.lang.Strings.capitalize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.gateway.proxy.config.StatisticsConfig;
import org.eclipse.ditto.services.gateway.proxy.config.StatisticsShardConfig;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.models.thingsearch.ThingsSearchConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.health.cluster.ClusterRoleStatus;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetailsResponse;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsResponse;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.Cluster;
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
public final class StatisticsActor extends AbstractActorWithTimers {

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

    private final StatisticsConfig statisticsConfig;

    private final ActorRef pubSubMediator;
    private final ClusterSharding clusterSharding;
    private final ClusterStatusSupplier clusterStatusSupplier;
    private List<NamedShardGauge> gauges;
    private Statistics currentStatistics;
    private StatisticsDetails currentStatisticsDetails;

    @SuppressWarnings("unused")
    private StatisticsActor(final ActorRef pubSubMediator) {
        statisticsConfig = StatisticsConfig.forActor(getContext());

        this.pubSubMediator = pubSubMediator;
        this.gauges = initializeGaugesForHotEntities(statisticsConfig);

        clusterSharding = ClusterSharding.get(getContext().getSystem());
        clusterStatusSupplier = new ClusterStatusSupplier(Cluster.get(getContext().getSystem()));
        scheduleInternalRetrieveHotEntities();
        subscribeForStatisticsCommands();
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
        initGauges();
        final InternalRetrieveStatistics internalRetrieveStatistics = InternalRetrieveStatistics.newInstance();
        getTimers().startPeriodicTimer(internalRetrieveStatistics, internalRetrieveStatistics,
                Duration.ofMillis(SCHEDULE_INTERNAL_RETRIEVE_COMMAND));
        getSelf().tell(internalRetrieveStatistics, getSelf());
    }

    private void updateGauges(final Map<String, ShardStatisticsWrapper> shardStatisticsWrapperMap) {
        gauges.forEach(namedShardGauge ->
                shardStatisticsWrapperMap.computeIfPresent(namedShardGauge.shard, (k, wrapper) -> {
                    namedShardGauge.gauge.set(wrapper.count);
                    return wrapper;
                }));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(InternalRetrieveStatistics.class, unused -> {
                    tellShardRegionToSendClusterShardingStats();

                    becomeStatisticsAwaiting(statistics -> {});
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

                    final ActorRef self = getSelf();
                    final ActorRef sender = getSender();
                    final List<ClusterRoleStatus> relevantRoles = clusterStatusSupplier.get()
                            .getRoles()
                            .stream()
                            .filter(this::hasRelevantRole)
                            .collect(Collectors.toList());
                    tellRelevantRootActorsToRetrieveStatistics(relevantRoles, retrieveStatistics);
                    becomeStatisticsDetailsAwaiting(relevantRoles, statistics ->
                            sender.tell(RetrieveStatisticsResponse.of(statistics.toJson(),
                                    retrieveStatistics.getDittoHeaders()), self)
                    );
                })
                .match(ShardRegion.CurrentShardRegionState.class, this::unhandled) // ignore, the message is too late
                .match(ShardRegion.ClusterShardingStats.class, this::unhandled) // ignore, the message is too late
                .match(DistributedPubSubMediator.SubscribeAck.class, this::logSubscribeAck)
                .matchAny(m -> log.warning("Got unknown message, expected a 'RetrieveStatistics': {}", m))
                .build();
    }

    private void subscribeForStatisticsCommands() {
        final Object subscribeForRetrieveStatistics =
                new DistributedPubSubMediator.Subscribe(RetrieveStatistics.TYPE, ACTOR_NAME, getSelf());
        final Object subscribeForRetrieveStatisticsDetails =
                new DistributedPubSubMediator.Subscribe(RetrieveStatisticsDetails.TYPE, ACTOR_NAME, getSelf());
        pubSubMediator.tell(subscribeForRetrieveStatistics, getSelf());
        pubSubMediator.tell(subscribeForRetrieveStatisticsDetails, getSelf());
    }

    private void logSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.info("Got <{}>", subscribeAck);
    }

    private void tellShardRegionToSendClusterShardingStats() {
        tellShardRegionToSendClusterShardingStats(SR_THING);
        tellShardRegionToSendClusterShardingStats(SR_POLICY);
        tellShardRegionToSendClusterShardingStats(SR_SEARCH_UPDATER);
    }

    private void tellRelevantRootActorsToRetrieveStatistics(final Collection<ClusterRoleStatus> relevantRoles,
            final RetrieveStatisticsDetails command) {

        relevantRoles.forEach(clusterRoleStatus ->
                statisticsConfig.getShards()
                        .stream()
                        .filter(shardConfig -> haveEqualRole(shardConfig, clusterRoleStatus))
                        .forEach(shardConfig -> tellRootActorToRetrieveStatistics(shardConfig.getRoot(), command)));
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

    private void initGauges() {
        gauges.forEach(namedShardGauge -> namedShardGauge.gauge.set(0L));
    }

    private void becomeStatisticsAwaiting(final Consumer<Statistics> statisticsConsumer) {

        final Map<String, ShardStatisticsWrapper> shardStatisticsMap = new HashMap<>();

        final AskTimeoutException askTimeoutException = new AskTimeoutException("Timed out");
        getTimers().startSingleTimer(askTimeoutException, askTimeoutException, Duration.ofMillis(WAIT_TIME_MS));

        getContext().become(ReceiveBuilder.create()
                .match(RetrieveStatistics.class, rs -> currentStatistics != null,
                        retrieveStatistics -> getSender().tell(RetrieveStatisticsResponse.of(
                                currentStatistics.toJson(), retrieveStatistics.getDittoHeaders()), getSelf())
                )
                .match(ShardRegion.ClusterShardingStats.class, clusterShardingStats -> {
                    final Optional<ShardStatisticsWrapper> shardStatistics =
                            getShardStatistics(shardStatisticsMap, getSender());

                    if (shardStatistics.isPresent()) {
                        final Map<Address, ShardRegion.ShardRegionStats> regions = clusterShardingStats.getRegions();
                        shardStatistics.get().count = regions.isEmpty() ? 0 : regions.values().stream()
                                .mapToInt(shardRegionStats -> shardRegionStats.getStats().isEmpty() ? 0 :
                                        shardRegionStats.getStats().values().stream()
                                                .mapToInt(o -> (Integer) o)
                                                .sum())
                                .sum();
                    } else {
                        log.warning("Got stats from unknown shard <{}>: <{}>", getSender(), clusterShardingStats);
                    }

                    // all shard statistics are present; no need to wait more.
                    if (shardStatisticsMap.size() >= statisticsConfig.getShards().size()) {
                        getTimers().cancel(askTimeoutException);
                        getSelf().tell(askTimeoutException, getSelf());
                    }
                })
                .matchEquals(askTimeoutException, unit -> {
                    updateGauges(shardStatisticsMap);
                    currentStatistics = Statistics.fromGauges(gauges);
                    statisticsConsumer.accept(currentStatistics);
                    getContext().unbecome();
                })
                .match(DistributedPubSubMediator.SubscribeAck.class, this::logSubscribeAck)
                .matchAny(m -> log.warning("Got unknown message during 'statisticsAwaiting': {}", m))
                .build()
        );
    }

    private void becomeStatisticsDetailsAwaiting(final Collection<ClusterRoleStatus> relevantRoles,
            final Consumer<StatisticsDetails> statisticsDetailsConsumer) {

        final Map<String, ShardStatisticsWrapper> shardStatisticsMap = new HashMap<>();
        final AskTimeoutException askTimeoutException = new AskTimeoutException("Timed out");

        getTimers().startSingleTimer(askTimeoutException, askTimeoutException,
                Duration.ofMillis(WAIT_TIME_MS).multipliedBy(8L));

        final int reachableMembers = relevantRoles.stream()
                .mapToInt(clusterRoleStatus -> clusterRoleStatus.getReachable().size())
                .sum();

        final ShardStatisticsWrapper messageCounter = new ShardStatisticsWrapper();
        messageCounter.count = 0L;

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
                                shardStatisticsMap.computeIfAbsent(shardRegion, s -> new ShardStatisticsWrapper());

                        retrieveStatisticsDetailsResponse.getStatisticsDetails().getValue(shardRegion)
                                .map(JsonValue::asObject)
                                .map(JsonObject::stream)
                                .ifPresent(namespaceEntries -> namespaceEntries.forEach(field ->
                                        shardStatistics.hotnessMap
                                                .merge(field.getKeyName(), field.getValue().asLong(), Long::sum)
                                ));

                        // all reachable members sent reply; stop waiting.
                        if (++messageCounter.count >= reachableMembers) {
                            getTimers().cancel(askTimeoutException);
                            getSelf().tell(askTimeoutException, getSelf());
                        }
                    }
                })
                .match(AskTimeoutException.class, askTimeout -> {
                    currentStatisticsDetails = StatisticsDetails.fromShardStatisticsWrappers(shardStatisticsMap);
                    statisticsDetailsConsumer.accept(currentStatisticsDetails);
                    getContext().unbecome();
                })
                .match(DistributedPubSubMediator.SubscribeAck.class, this::logSubscribeAck)
                .matchAny(m -> log.warning("Got unknown message during 'statisticsDetailsAwaiting': {}", m))
                .build()
        );
    }

    private boolean hasRelevantRole(final ClusterRoleStatus clusterRoleStatus) {
        return statisticsConfig.getShards()
                .stream()
                .anyMatch(shardConfig -> haveEqualRole(shardConfig, clusterRoleStatus));
    }

    private static boolean haveEqualRole(final StatisticsShardConfig shardConfig,
            final ClusterRoleStatus clusterRoleStatus) {
        return shardConfig.getRole().equals(clusterRoleStatus.getRole());
    }

    private Optional<ShardStatisticsWrapper> getShardStatistics(
            final Map<String, ShardStatisticsWrapper> shardStatisticsMap,
            final ActorRef sender) {

        final String senderPath = sender.path().toStringWithoutAddress();
        final Optional<StatisticsShardConfig> shardConfig = statisticsConfig.getShards()
                .stream()
                .filter(sc -> senderPath.contains(sc.getShard()))
                .findFirst();
        return shardConfig.map(sc ->
                shardStatisticsMap.computeIfAbsent(sc.getShard(), s -> new ShardStatisticsWrapper())
        );

    }

    private static final class ShardStatisticsWrapper {

        private final Map<String, Long> hotnessMap = new HashMap<>();
        private long count = -1L;
    }

    private static String simpleCamelCasePluralForm(final String singular, final boolean capitalize) {
        final String[] words = singular.split("\\W");
        if (words.length > 0) {
            // capitalization by io.jsonwebtoken
            for (int i = capitalize ? 0 : 1; i < words.length; ++i) {
                words[i] = capitalize(words[i]);
            }
            // This simple pluralization rule needs only work on the configured shard region names.
            // Exceptions such as "mothers-in-law" and "mitochondria" are not handled.
            final int lastIndex = words.length - 1;
            final String lastWord = words[lastIndex];
            if (lastWord.endsWith("y")) {
                words[lastIndex] = lastWord.substring(0, lastWord.length() - 1) + "ies";
            } else {
                words[lastIndex] += "s";
            }
            return String.join("", words);
        } else {
            // singular consists entirely of non-word characters
            return capitalize(singular) + "s";
        }
    }

    private static String hotPluralForm(final String singular) {
        return "hot" + simpleCamelCasePluralForm(singular, true);
    }

    private static List<NamedShardGauge> initializeGaugesForHotEntities(final StatisticsConfig statisticsConfig) {
        final List<NamedShardGauge> gauges = new ArrayList<>(statisticsConfig.getShards().size());
        statisticsConfig.getShards().forEach(shardConfig -> {
            final String hotStuffs = hotPluralForm(shardConfig.getShard());
            gauges.add(new NamedShardGauge(hotStuffs, shardConfig.getShard(), DittoMetrics.gauge(hotStuffs)));
        });
        return gauges;
    }

    private static class InternalRetrieveStatistics {

        private InternalRetrieveStatistics() {
            // no-op
        }

        static InternalRetrieveStatistics newInstance() {
            return new InternalRetrieveStatistics();
        }
    }

    @Immutable
    private static final class NamedShardGauge {

        private final String name;
        private final String shard;
        private final Gauge gauge;

        private NamedShardGauge(final String name, final String shard,
                final Gauge gauge) {
            this.name = name;
            this.shard = shard;
            this.gauge = gauge;
        }
    }

    /**
     * Representation publicly available statistics about hot entities within Ditto.
     */
    @Immutable
    private static final class Statistics implements Jsonifiable.WithPredicate<JsonObject, JsonField> {

        private final JsonObject hotEntitiesCount;

        private Statistics(final JsonObject hotEntitiesCount) {
            this.hotEntitiesCount = hotEntitiesCount;
        }

        private static Statistics fromGauges(final Collection<NamedShardGauge> gauges) {
            return new Statistics(
                    gauges.stream()
                            .map(namedShardGauge ->
                                    JsonFactory.newField(JsonKey.of(namedShardGauge.name),
                                            JsonFactory.newValue(namedShardGauge.gauge.get())))
                            .collect(JsonCollectors.fieldsToObject())
            );
        }

        /**
         * Returns all non hidden marked fields of this statistics.
         *
         * @return a JSON object representation of this statistics including only non hidden marked fields.
         */
        @Override
        public JsonObject toJson() {
            return hotEntitiesCount;
        }

        @Override
        public JsonObject toJson(@Nonnull final JsonSchemaVersion schemaVersion,
                @Nonnull final Predicate<JsonField> predicate) {
            return hotEntitiesCount.stream()
                    .filter(predicate)
                    .collect(JsonCollectors.fieldsToObject());
        }

        @SuppressWarnings("OverlyComplexMethod")
        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) return false;
            final Statistics that = (Statistics) o;
            return Objects.equals(hotEntitiesCount, that.hotEntitiesCount);
        }

        @Override
        public int hashCode() {
            return hotEntitiesCount.hashCode();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "hotEntitiesCount=" + hotEntitiesCount +
                    "]";
        }

    }

    /**
     * Representation statistics details about namespace hotness within Ditto.
     */
    @Immutable
    private static final class StatisticsDetails implements Jsonifiable.WithPredicate<JsonObject, JsonField> {

        private final JsonObject namespacesHotness;

        private StatisticsDetails(final JsonObject namespacesHotness) {
            this.namespacesHotness = namespacesHotness;
        }

        private static StatisticsDetails fromShardStatisticsWrappers(
                final Map<String, ShardStatisticsWrapper> shardStatisticsWrapperMap) {

            return new StatisticsDetails(
                    shardStatisticsWrapperMap.entrySet()
                            .stream()
                            .map(entry -> {
                                final String entitiesNamespacesHotness =
                                        simpleCamelCasePluralForm(entry.getKey(), false) + "NamespacesHotness";
                                return JsonFactory.newField(JsonKey.of(entitiesNamespacesHotness),
                                        buildHotnessMapJson(entry.getValue().hotnessMap));
                            })
                            .collect(JsonCollectors.fieldsToObject())
            );
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
            return namespacesHotness;
        }

        @Override
        public JsonObject toJson(@Nonnull final JsonSchemaVersion schemaVersion,
                @Nonnull final Predicate<JsonField> predicate) {
            return namespacesHotness.stream()
                    .filter(predicate)
                    .collect(JsonCollectors.fieldsToObject());
        }

        @SuppressWarnings("OverlyComplexMethod")
        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) return false;
            final StatisticsDetails that = (StatisticsDetails) o;
            return Objects.equals(namespacesHotness, that.namespacesHotness);
        }

        @Override
        public int hashCode() {
            return namespacesHotness.hashCode();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "namespacesHotness=" + namespacesHotness +
                    "]";
        }

    }

}
