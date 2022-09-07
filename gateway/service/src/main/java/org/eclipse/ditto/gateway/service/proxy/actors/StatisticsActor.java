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
package org.eclipse.ditto.gateway.service.proxy.actors;

import static io.jsonwebtoken.lang.Strings.capitalize;

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
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatistics;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetailsResponse;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsResponse;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.gateway.service.proxy.config.StatisticsConfig;
import org.eclipse.ditto.gateway.service.proxy.config.StatisticsShardConfig;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterRoleStatus;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
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
public final class StatisticsActor extends AbstractActorWithStashWithTimers {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "statistics";

    private static final String EMPTY_STRING_TAG = "<empty>";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final StatisticsConfig statisticsConfig;

    private final ActorRef pubSubMediator;
    private final ClusterSharding clusterSharding;
    private final ClusterStatusSupplier clusterStatusSupplier;
    private final List<NamedShardGauge> gauges;

    @Nullable private Statistics currentStatistics;
    @Nullable private StatisticsDetails currentStatisticsDetails;

    @SuppressWarnings("unused")
    private StatisticsActor(final ActorRef pubSubMediator) {
        statisticsConfig = StatisticsConfig.forActor(getContext());

        this.pubSubMediator = pubSubMediator;
        this.gauges = initializeGaugesForHotEntities(statisticsConfig);

        final ActorSystem actorSystem = getContext().getSystem();
        final int numberOfShards = getNumberOfShards(actorSystem);
        clusterSharding = initClusterSharding(actorSystem, statisticsConfig, numberOfShards);
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
        getTimers().startTimerAtFixedRate(InternalRetrieveStatistics.INSTANCE, InternalRetrieveStatistics.INSTANCE,
                statisticsConfig.getUpdateInterval());
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
                .match(RetrieveStatistics.class, this::respondWithCachedStatistics)
                .match(RetrieveStatisticsDetails.class, this::hasCachedStatisticsDetails,
                        this::respondWithCachedStatisticsDetails)
                .match(RetrieveStatisticsDetails.class, retrieveStatistics -> {
                    // no cached statistics details; retrieve them from cluster members.
                    final ActorRef sender = getSender();
                    final List<ClusterRoleStatus> relevantRoles = clusterStatusSupplier.get()
                            .getRoles()
                            .stream()
                            .filter(this::hasRelevantRole)
                            .toList();
                    tellRelevantRootActorsToRetrieveStatistics(relevantRoles, retrieveStatistics);
                    becomeStatisticsDetailsAwaiting(relevantRoles, details ->
                            respondWithStatisticsDetails(retrieveStatistics, details, sender));
                })
                .matchEquals(InternalRetrieveStatistics.INSTANCE, unit -> {
                    tellShardRegionsToSendClusterShardingStats();
                    becomeStatisticsAwaiting();
                })
                .matchEquals(InternalResetStatisticsDetails.INSTANCE, this::resetStatisticsDetails)
                .match(ShardRegion.CurrentShardRegionState.class, this::unhandled) // ignore, the message is too late
                .match(ShardRegion.ClusterShardingStats.class, this::unhandled) // ignore, the message is too late
                .match(RetrieveStatisticsDetailsResponse.class, this::unhandled) // ignore, the message is too late
                .match(DistributedPubSubMediator.SubscribeAck.class, this::logSubscribeAck)
                .matchAny(m -> log.warning("Got unknown message, expected a 'RetrieveStatistics': {}", m))
                .build();
    }

    private boolean hasCachedStatisticsDetails() {
        return currentStatisticsDetails != null;
    }

    private void resetStatisticsDetails(final Object unit) {
        // cached statistics details expired; retrieve them again at next query.
        currentStatisticsDetails = null;
    }

    private void respondWithStatisticsDetails(final RetrieveStatisticsDetails command,
            @Nullable final StatisticsDetails details, final ActorRef sender) {
        final JsonObject statisticsJson = details != null ? details.toJson() : JsonObject.empty();
        sender.tell(toStatisticsDetailsResponse(statisticsJson, command), getSelf());
    }

    private void respondWithCachedStatisticsDetails(final RetrieveStatisticsDetails retrieveStatistics) {
        respondWithStatisticsDetails(retrieveStatistics, currentStatisticsDetails, getSender());
    }

    private void respondWithCachedStatistics(final RetrieveStatistics retrieveStatistics) {
        // public query - answer immediately by cached result
        final JsonObject statisticsJson = currentStatistics != null
                ? currentStatistics.toJson()
                : JsonObject.empty();
        final DittoHeaders headers = retrieveStatistics.getDittoHeaders();
        getSender().tell(RetrieveStatisticsResponse.of(statisticsJson, headers), getSelf());
    }

    private void subscribeForStatisticsCommands() {
        final Object subscribeForRetrieveStatistics = DistPubSubAccess
                .subscribeViaGroup(RetrieveStatistics.TYPE, ACTOR_NAME, getSelf());
        final Object subscribeForRetrieveStatisticsDetails = DistPubSubAccess
                .subscribeViaGroup(RetrieveStatisticsDetails.TYPE, ACTOR_NAME, getSelf());
        pubSubMediator.tell(subscribeForRetrieveStatistics, getSelf());
        pubSubMediator.tell(subscribeForRetrieveStatisticsDetails, getSelf());
    }

    private void logSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.info("Got <{}>", subscribeAck);
    }

    private void tellShardRegionsToSendClusterShardingStats() {
        statisticsConfig.getShards()
                .stream()
                .map(StatisticsShardConfig::getRegion)
                .forEach(this::tellShardRegionToSendClusterShardingStats);
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
        pubSubMediator.tell(DistPubSubAccess.sendToAll(rootActorPath, retrieveStatistics, false),
                getSelf());
    }

    private void tellShardRegionToSendClusterShardingStats(final String shardRegion) {
        try {
            clusterSharding.shardRegion(shardRegion).tell(
                    new ShardRegion.GetClusterShardingStats(FiniteDuration.apply(10, TimeUnit.SECONDS)),
                    getSelf());
        } catch (final IllegalArgumentException e) {
            // shard not started; there will not be any sharding stats.
            log.error(e, "Failed to query shard region <{}>", shardRegion);
        }
    }

    private void initGauges() {
        gauges.forEach(namedShardGauge -> namedShardGauge.gauge.set(0L));
    }

    private void becomeStatisticsAwaiting() {

        final Map<String, ShardStatisticsWrapper> shardStatisticsMap = new HashMap<>();

        final AskTimeoutException askTimeoutException = new AskTimeoutException("Timed out");
        getTimers().startSingleTimer(askTimeoutException, askTimeoutException, statisticsConfig.getAskTimeout());

        getContext().become(ReceiveBuilder.create()
                        .match(RetrieveStatistics.class, this::respondWithCachedStatistics)
                        .match(ShardRegion.ClusterShardingStats.class, clusterShardingStats -> {
                            final Optional<ShardStatisticsWrapper> shardStatistics =
                                    getShardStatistics(shardStatisticsMap, getSender());

                            if (shardStatistics.isPresent()) {
                                final Map<Address, ShardRegion.ShardRegionStats> regions = clusterShardingStats.getRegions();
                                shardStatistics.get().count = regions.isEmpty() ? 0 : regions.values().stream()
                                        .mapToInt(shardRegionStats -> shardRegionStats.getStats().isEmpty() ? 0 :
                                                shardRegionStats.getStats().values().stream()
                                                        .mapToInt(Integer.class::cast)
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
                            unbecome();
                        })
                        .matchEquals(InternalResetStatisticsDetails.INSTANCE, this::resetStatisticsDetails)
                        .match(DistributedPubSubMediator.SubscribeAck.class, this::logSubscribeAck)
                        .matchAny(m -> {
                            log.info("Stashing message during 'statisticsAwaiting': {}", m);
                            stash();
                        })
                        .build(),
                false);
    }

    /**
     * Resume the starting behavior of the actor.
     */
    private void unbecome() {
        getContext().unbecome();
        unstashAll();
    }

    private void becomeStatisticsDetailsAwaiting(final Collection<ClusterRoleStatus> relevantRoles,
            final Consumer<StatisticsDetails> statisticsDetailsConsumer) {

        final Map<String, ShardStatisticsWrapper> shardStatisticsMap = new HashMap<>();
        final AskTimeoutException askTimeoutException = new AskTimeoutException("Timed out");

        getTimers().startSingleTimer(askTimeoutException, askTimeoutException, statisticsConfig.getAskTimeout());

        final int reachableMembers = relevantRoles.stream()
                .mapToInt(clusterRoleStatus -> clusterRoleStatus.getReachable().size())
                .sum();

        final ShardStatisticsWrapper messageCounter = new ShardStatisticsWrapper();
        messageCounter.count = 0L;

        getContext().become(ReceiveBuilder.create()
                        .match(RetrieveStatistics.class, this::respondWithCachedStatistics)
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
                            scheduleResetStatisticsDetails();
                            unbecome();
                        })
                        .matchEquals(InternalResetStatisticsDetails.INSTANCE, this::resetStatisticsDetails)
                        .match(DistributedPubSubMediator.SubscribeAck.class, this::logSubscribeAck)
                        .matchAny(m -> {
                            log.info("Stashing message during 'statisticsDetailsAwaiting': {}", m);
                            stash();
                        })
                        .build(),
                false);
    }

    private void scheduleResetStatisticsDetails() {
        getTimers().startSingleTimer(InternalResetStatisticsDetails.INSTANCE, InternalResetStatisticsDetails.INSTANCE,
                statisticsConfig.getDetailsExpireAfter());
    }

    private boolean hasRelevantRole(final ClusterRoleStatus clusterRoleStatus) {
        return statisticsConfig.getShards()
                .stream()
                .anyMatch(shardConfig -> haveEqualRole(shardConfig, clusterRoleStatus));
    }

    private static int getNumberOfShards(final ActorSystem actorSystem) {
        return DefaultClusterConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()))
                .getNumberOfShards();
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
                .filter(sc -> senderPath.contains(sc.getRegion()))
                .findFirst();
        return shardConfig.map(sc ->
                shardStatisticsMap.computeIfAbsent(sc.getRegion(), s -> new ShardStatisticsWrapper())
        );

    }

    private static ClusterSharding initClusterSharding(final ActorSystem actorSystem,
            final StatisticsConfig statisticsConfig, final int numberOfShards) {
        // start the cluster sharding proxies for retrieving Statistics via StatisticActor about them.
        // it is okay to run this on each actor startup because proxy actors are cached and never re-created.
        final ShardRegionExtractor extractor = ShardRegionExtractor.of(numberOfShards, actorSystem);
        final ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
        statisticsConfig.getShards().forEach(shard ->
                clusterSharding.startProxy(shard.getRegion(), Optional.of(shard.getRole()), extractor));
        return clusterSharding;
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
            final String hotStuffs = hotPluralForm(shardConfig.getRegion());
            gauges.add(new NamedShardGauge(hotStuffs, shardConfig.getRegion(), DittoMetrics.gauge(hotStuffs)));
        });
        return gauges;
    }

    // return type is RetrieveStatisticsResponse instead of RetrieveStatisticsDetailsResponse in keeping with
    // the previous interface.
    private static RetrieveStatisticsResponse toStatisticsDetailsResponse(final JsonObject statisticsJson,
            final RetrieveStatisticsDetails command) {
        final List<String> shardRegions = command.getShardRegions();
        final List<String> namespaces = command.getNamespaces();
        if (shardRegions.isEmpty() && namespaces.isEmpty()) {
            return RetrieveStatisticsResponse.of(statisticsJson, command.getDittoHeaders());
        } else {
            final Stream<JsonField> relevantJsonFields =
                    shardRegions.isEmpty() ? statisticsJson.stream() :
                            shardRegions.stream().flatMap(shardRegion ->
                                    statisticsJson.getField(StatisticsDetails.toNamespacesHotness(shardRegion))
                                            .stream()
                            );
            final JsonObject filteredStatisticsJson =
                    relevantJsonFields.filter(field -> field.getValue().isObject())
                            .map(field -> JsonFactory.newField(field.getKey(),
                                    filterByNamespace(field.getValue().asObject(), namespaces))
                            )
                            .collect(JsonCollectors.fieldsToObject());
            return RetrieveStatisticsResponse.of(filteredStatisticsJson, command.getDittoHeaders());
        }
    }

    private static JsonObject filterByNamespace(final JsonObject shardDetails, final List<String> namespaces) {
        if (namespaces.isEmpty()) {
            return shardDetails;
        } else {
            return namespaces.stream()
                    .map(namespace -> shardDetails.getField(namespace)
                            .orElseGet(
                                    () -> JsonFactory.newField(JsonFactory.newKey(namespace), JsonFactory.newValue(0L)))
                    )
                    .collect(JsonCollectors.fieldsToObject());
        }
    }

    private static final class InternalRetrieveStatistics {

        private static final Object INSTANCE = new InternalRetrieveStatistics();
    }

    private static final class InternalResetStatisticsDetails {

        private static final Object INSTANCE = new InternalResetStatisticsDetails();
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
         * Returns all non-hidden marked fields of this statistics.
         *
         * @return a JSON object representation of this statistics including only non-hidden marked fields.
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
                            .map(entry -> JsonFactory.newField(JsonKey.of(toNamespacesHotness(entry.getKey())),
                                    buildHotnessMapJson(entry.getValue().hotnessMap)))
                            .collect(JsonCollectors.fieldsToObject())
            );
        }

        private static String toNamespacesHotness(final String shardRegion) {
            return simpleCamelCasePluralForm(shardRegion, false) + "NamespacesHotness";
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
         * Returns all non-hidden marked fields of this StatisticsDetails.
         *
         * @return a JSON object representation of this StatisticsDetails including only non-hidden marked fields.
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
