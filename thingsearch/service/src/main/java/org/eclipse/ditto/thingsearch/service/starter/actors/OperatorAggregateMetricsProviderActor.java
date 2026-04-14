/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import static org.eclipse.ditto.thingsearch.service.starter.actors.AggregateThingsMetricsActor.CLUSTER_ROLE;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cluster.ClusterUtil;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.KamonGauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.KamonTagSetConverter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetrics;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetricsResponse;
import org.eclipse.ditto.thingsearch.service.common.config.CustomAggregationMetricConfig;
import org.eclipse.ditto.thingsearch.service.common.config.OperatorMetricsConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsAggregationPersistence;
import org.eclipse.ditto.thingsearch.service.placeholders.GroupByPlaceholderResolver;

import kamon.Kamon;

/**
 * Actor which is started as singleton for "search" role and is responsible for querying for extended operator defined
 * "custom metrics" (configured via Ditto search service configuration) to expose as {@link Gauge} via Prometheus.
 */
public final class OperatorAggregateMetricsProviderActor extends AbstractActorWithTimers {

    /**
     * This Actor's actor name.
     */
    public static final String ACTOR_NAME = "operatorSearchMetricsProvider";

    private static final int MIN_INITIAL_DELAY_SECONDS = 30;
    private static final int MAX_INITIAL_DELAY_SECONDS = 90;
    private static final String METRIC_NAME = "metric-name";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final ActorRef aggregateThingsMetricsActorSingletonProxy;
    private final Map<String, CustomAggregationMetricConfig> customSearchMetricConfigMap;
    private final Map<GageIdentifier, TimestampedGauge> metricsGauges;
    private final Gauge customSearchMetricsGauge;

    @SuppressWarnings("unused")
    private OperatorAggregateMetricsProviderActor(final SearchConfig searchConfig) {
        this.aggregateThingsMetricsActorSingletonProxy = initializeAggregationThingsMetricsActor(searchConfig);
        this.customSearchMetricConfigMap = searchConfig.getOperatorMetricsConfig().getCustomAggregationMetricConfigs();
        this.metricsGauges = new HashMap<>();
        this.customSearchMetricsGauge = KamonGauge.newGauge("custom-aggregation-metrics-count-of-instruments");
        this.customSearchMetricConfigMap.forEach(
                (metricName, customSearchMetricConfig) -> initializeCustomMetricTimer(metricName,
                        customSearchMetricConfig,
                        searchConfig.getOperatorMetricsConfig().getScrapeInterval()));
        initializeCustomMetricsCleanupTimers(searchConfig.getOperatorMetricsConfig());
    }

    /**
     * Create Props for this actor.
     *
     * @param searchConfig the searchConfig to use
     * @return the Props object.
     */
    public static Props props(final SearchConfig searchConfig) {
        return Props.create(OperatorAggregateMetricsProviderActor.class, searchConfig);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(GatherMetricsCommand.class, this::handleGatheringMetrics)
                .match(AggregateThingsMetricsBatch.class, this::handleAggregateThingsBatch)
                .match(CleanupUnusedMetricsCommand.class, this::handleCleanupUnusedMetrics)
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private ActorRef initializeAggregationThingsMetricsActor(final SearchConfig searchConfig) {
        final DittoMongoClient mongoDbClient = MongoClientExtension.get(getContext().system()).getSearchClient();
        final var props = AggregateThingsMetricsActor.props(
                MongoThingsAggregationPersistence.of(mongoDbClient, searchConfig, log));
        final ActorRef aggregationThingsMetricsActorProxy = ClusterUtil
                .startSingletonProxy(getContext(), CLUSTER_ROLE,
                        ClusterUtil.startSingleton(getContext(), CLUSTER_ROLE, AggregateThingsMetricsActor.ACTOR_NAME,
                                props));
        log.info("Started child actor <{}> with path <{}>.", AggregateThingsMetricsActor.ACTOR_NAME,
                aggregationThingsMetricsActorProxy);
        return aggregationThingsMetricsActorProxy;
    }

    private void handleGatheringMetrics(final GatherMetricsCommand gatherMetricsCommand) {
        final CustomAggregationMetricConfig config = gatherMetricsCommand.config();
        final String metricName = config.getMetricName();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("aggregation-metrics_" + metricName + "_" + UUID.randomUUID())
                .build();

        final AggregateThingsMetrics
                aggregateThingsMetrics = AggregateThingsMetrics.of(metricName, config.getGroupBy(), config.getFilter().orElse(null),
                config.getNamespaces(), config.getIndexHint().orElse(null), dittoHeaders);
        aggregateThingsMetricsActorSingletonProxy.tell(aggregateThingsMetrics, getSelf());
    }


    private void handleAggregateThingsBatch(final AggregateThingsMetricsBatch batch) {
        final String metricName = batch.metricName();
        final Set<GageIdentifier> seenInBatch = new HashSet<>();

        for (final AggregateThingsMetricsResponse response : batch.responses()) {
            final Optional<Long> result = response.getResult();
            result.ifPresentOrElse(value -> {
                final CustomAggregationMetricConfig config = customSearchMetricConfigMap.get(metricName);
                final TagSet tagSet = resolveTags(config, response);
                log.withCorrelationId(response)
                        .debug("Received aggregate things response for metric name <{} : {}>: {}, " +
                                        "extracted result: <{}> - in thread: {}",
                                metricName, tagSet, response, result, Thread.currentThread().getName());
                recordMetric(metricName, tagSet, value);
                seenInBatch.add(new GageIdentifier(metricName, tagSet));
            }, () -> log.withCorrelationId(response)
                    .info("No result for metric name <{}> in aggregate things response: {}. " +
                                    "Should not happen, at least 0 is expected in each result",
                            metricName, response));
        }

        reconcileVanishedBuckets(metricName, seenInBatch);
    }

    private void reconcileVanishedBuckets(final String metricName, final Set<GageIdentifier> seenInBatch) {
        final Iterator<Map.Entry<GageIdentifier, TimestampedGauge>> iterator =
                metricsGauges.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<GageIdentifier, TimestampedGauge> entry = iterator.next();
            if (entry.getKey().metricName().equals(metricName) && !seenInBatch.contains(entry.getKey())) {
                log.debug("Zeroing vanished gauge for metric <{}>: {}", metricName, entry.getKey().tags());
                entry.getValue().set(0L);
                if (Kamon.gauge(metricName)
                        .remove(KamonTagSetConverter.getKamonTagSet(entry.getValue().getTagSet()))) {
                    log.debug("Removed vanished custom search metric instrument: {} {}",
                            metricName, entry.getValue().getTagSet());
                    iterator.remove();
                    decrementMonitorGauge(metricName);
                } else {
                    log.warning("Could not remove vanished custom search metric instrument: {}",
                            entry.getKey());
                }
            }
        }
    }

    private void recordMetric(final String metricName, final TagSet tagSet, final Long value) {
        metricsGauges.compute(new GageIdentifier(metricName, tagSet), (gageIdentifier, timestampedGauge) -> {
            if (timestampedGauge == null) {
                final Gauge gauge = KamonGauge.newGauge(metricName)
                        .tags(tagSet);
                gauge.set(value);
                incrementMonitorGauge(metricName);
                return new TimestampedGauge(gauge);
            } else {
                return timestampedGauge.set(value);
            }
        });
    }

    private TagSet resolveTags(final CustomAggregationMetricConfig customAggregationMetricConfig,
            final AggregateThingsMetricsResponse response) {
        return TagSet.ofTagCollection(customAggregationMetricConfig.getTags().entrySet().stream().map(tagEntry -> {
            if (!isPlaceHolder(tagEntry.getValue())) {
                return Tag.of(tagEntry.getKey(), tagEntry.getValue());
            } else {

                final ExpressionResolver expressionResolver =
                        PlaceholderFactory.newExpressionResolver(List.of(
                                new GroupByPlaceholderResolver(customAggregationMetricConfig.getGroupBy().keySet(),
                                        response.getGroupedBy())));
                return expressionResolver.resolve(tagEntry.getValue())
                        .findFirst()
                        .map(resolvedValue -> Tag.of(tagEntry.getKey(), resolvedValue))
                        .orElse(Tag.of(tagEntry.getKey(), tagEntry.getValue()));
            }
        }).collect(Collectors.toSet()));
    }

    private void handleCleanupUnusedMetrics(final CleanupUnusedMetricsCommand cleanupCommand) {
        // remove metrics who were not used for longer than two times the metric's own scrape interval
        final long currentTime = System.currentTimeMillis();
        final long unusedPeriod = cleanupCommand.scrapeInterval().multipliedBy(2).toMillis();
        final Iterator<Map.Entry<GageIdentifier, TimestampedGauge>> iterator = metricsGauges.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<GageIdentifier, TimestampedGauge> next = iterator.next();
            if (!next.getKey().metricName().equals(cleanupCommand.metricName())) {
                continue;
            }
            final long lastUpdated = next.getValue().getLastUpdated();
            final long expire = lastUpdated + unusedPeriod;
            log.debug("cleanup metrics:  expired: {}, time left: {} lastUpdated: {}  expire: {} currentTime: {}",
                    currentTime > expire, expire - currentTime, lastUpdated, expire, currentTime);
            if (currentTime > expire) {
                final String metricName = next.getKey().metricName();
                // setting to zero as there is a bug in Kamon where the gauge is not removed and is still reported
                // https://github.com/kamon-io/Kamon/issues/566
                next.getValue().set(0L);
                if (Kamon.gauge(metricName).remove(KamonTagSetConverter.getKamonTagSet(next.getValue().getTagSet()))) {
                    log.debug("Removed custom search metric instrument: {} {}", metricName,
                            next.getValue().getTagSet());
                    iterator.remove();
                    decrementMonitorGauge(metricName);
                } else {
                    log.warning("Could not remove unused custom search metric instrument: {}", next.getKey());
                }
            }
        }
    }

    private void initializeCustomMetricTimer(final String metricName, final CustomAggregationMetricConfig config,
            final Duration defaultScrapeInterval) {
        if (!config.isEnabled()) {
            log.info("Custom search metric Gauge for metric <{}> is DISABLED. Skipping init.", metricName);
            return;
        }
        final Duration scrapeInterval = config.getScrapeInterval()
                .orElse(defaultScrapeInterval);
        // start each custom metric provider with a random initialDelay
        final Duration initialDelay = Duration.ofSeconds(
                ThreadLocalRandom.current().nextInt(MIN_INITIAL_DELAY_SECONDS, MAX_INITIAL_DELAY_SECONDS)
        );
        log.info("Initializing custom metric timer for metric <{}> with initialDelay <{}> and scrapeInterval <{}>",
                metricName,
                initialDelay, scrapeInterval);
        getTimers().startTimerAtFixedRate(metricName, new GatherMetricsCommand(config), initialDelay,
                scrapeInterval);
    }

    private void initializeCustomMetricsCleanupTimers(final OperatorMetricsConfig operatorMetricsConfig) {
        final Duration defaultScrapeInterval = operatorMetricsConfig.getScrapeInterval();
        operatorMetricsConfig.getCustomAggregationMetricConfigs().forEach((metricName, metricConfig) -> {
            if (metricConfig.isEnabled()) {
                final Duration scrapeInterval = metricConfig.getScrapeInterval().orElse(defaultScrapeInterval);
                final Duration cleanupInterval = scrapeInterval.multipliedBy(2);
                log.info("Initializing custom metric cleanup timer for metric <{}> with interval <{}>",
                        metricName, cleanupInterval);
                getTimers().startTimerAtFixedRate("cleanup-" + metricName,
                        new CleanupUnusedMetricsCommand(metricName, scrapeInterval),
                        cleanupInterval);
            }
        });
    }

    private boolean isPlaceHolder(final String value) {
        return value.startsWith("{{") && value.endsWith("}}");
    }

    private void incrementMonitorGauge(final String metricName) {
        customSearchMetricsGauge.tag(Tag.of(METRIC_NAME, metricName)).increment();
    }


    private void decrementMonitorGauge(final String metricName) {
        customSearchMetricsGauge.tag(Tag.of(METRIC_NAME, metricName)).decrement();
    }

    private static final class TimestampedGauge {

        private final Gauge gauge;
        private final Long timestamp;

        private TimestampedGauge(Gauge gauge) {
            this.gauge = gauge;
            this.timestamp = System.currentTimeMillis();
        }

        public TimestampedGauge set(final Long value) {
            gauge.set(value);
            return new TimestampedGauge(gauge);
        }

        private TagSet getTagSet() {
            return gauge.getTagSet();
        }

        private long getLastUpdated() {
            return timestamp;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final TimestampedGauge that = (TimestampedGauge) o;
            return Objects.equals(gauge, that.gauge) && Objects.equals(timestamp, that.timestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gauge, timestamp);
        }

        @Override
        public String toString() {
            return "TimestampedGauge{" +
                    "gauge=" + gauge +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    private record GatherMetricsCommand(CustomAggregationMetricConfig config) {}

    private record CleanupUnusedMetricsCommand(String metricName, Duration scrapeInterval) {}

    private record GageIdentifier(String metricName, TagSet tags) {}
}
