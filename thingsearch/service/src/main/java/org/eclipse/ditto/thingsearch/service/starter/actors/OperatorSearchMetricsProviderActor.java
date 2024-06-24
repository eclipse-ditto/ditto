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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.edge.service.dispatching.ThingsAggregatorProxyActor;
import org.eclipse.ditto.edge.service.placeholders.ThingJsonPlaceholder;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.KamonGauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.KamonTagSetConverter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingsResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThingsResponse;
import org.eclipse.ditto.thingsearch.service.common.config.CustomSearchMetricConfig;
import org.eclipse.ditto.thingsearch.service.common.config.OperatorMetricsConfig;

import kamon.Kamon;
import scala.Tuple2;

/**
 * Actor which is started as singleton for "search" role and is responsible for querying for extended operator defined
 * "custom metrics" (configured via Ditto search service configuration) to expose as {@link Gauge} via Prometheus.
 */
public final class OperatorSearchMetricsProviderActor extends AbstractActorWithTimers {

    /**
     * This Actor's actor name.
     */
    public static final String ACTOR_NAME = "operatorSearchMetricsProvider";

    private static final int MIN_INITIAL_DELAY_SECONDS = 30;
    private static final int MAX_INITIAL_DELAY_SECONDS = 90;
    private static final int DEFAULT_SEARCH_TIMEOUT_SECONDS = 60;

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final ThingJsonPlaceholder thingJsonPlaceholder = ThingJsonPlaceholder.getInstance();
    private final Map<TagSet, Double> aggregatedByTagsValues = new HashMap<>();

    private final ActorRef searchActor;
    private final Map<String, Tuple2<Gauge, Long>> metricsGauges;
    private final Gauge customSearchMetricsGauge;
    private final ActorRef thingsAggregatorProxyActor;

    @SuppressWarnings("unused")
    private OperatorSearchMetricsProviderActor(final OperatorMetricsConfig operatorMetricsConfig,
            final ActorRef searchActor, final ActorRef pubSubMediator) {
        customSearchMetricsGauge = KamonGauge.newGauge("custom-search-metrics");
        this.searchActor = searchActor;
        thingsAggregatorProxyActor = getContext().actorOf(ThingsAggregatorProxyActor.props(pubSubMediator),
                ThingsAggregatorProxyActor.ACTOR_NAME);
        metricsGauges = new HashMap<>();
        operatorMetricsConfig.getCustomSearchMetricConfigurations().forEach((metricName, config) -> {
            if (config.isEnabled()) {
                initializeCustomMetricTimer(operatorMetricsConfig, metricName, config);
            } else {
                log.info("Initializing custom search metric Gauge for metric <{}> is DISABLED", metricName);
            }
        });
        initializeCustomMetricsCleanupTimer(operatorMetricsConfig);
    }

    /**
     * Create Props for this actor.
     *
     * @param operatorMetricsConfig the config to use
     * @param searchActor the SearchActor Actor reference
     * @return the Props object.
     */
    public static Props props(final OperatorMetricsConfig operatorMetricsConfig, final ActorRef searchActor,
            final ActorRef pubSubMediator) {
        return Props.create(OperatorSearchMetricsProviderActor.class, operatorMetricsConfig, searchActor,
                pubSubMediator);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(GatherMetrics.class, this::handleGatheringMetrics)
                .match(CleanupUnusedMetrics.class, this::handleCleanupUnusedMetrics)
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private void initializeCustomMetricTimer(final OperatorMetricsConfig operatorMetricsConfig, final String metricName,
            final CustomSearchMetricConfig config) {
        // start each custom metric provider with a random initialDelay
        final Duration initialDelay = Duration.ofSeconds(
                ThreadLocalRandom.current().nextInt(MIN_INITIAL_DELAY_SECONDS, MAX_INITIAL_DELAY_SECONDS)
        );
        final Duration scrapeInterval = config.getScrapeInterval()
                .orElse(operatorMetricsConfig.getScrapeInterval());
        log.info("Initializing custom metric timer for metric <{}> with initialDelay <{}> and scrapeInterval <{}>",
                metricName,
                initialDelay, scrapeInterval);
        getTimers().startTimerAtFixedRate(
                metricName, createGatherCustomMetric(metricName, config), initialDelay, scrapeInterval);
    }

    private void initializeCustomMetricsCleanupTimer(final OperatorMetricsConfig operatorMetricsConfig) {
        final Duration interval = getMaxConfiguredScrapeInterval(operatorMetricsConfig).multipliedBy(3);
        log.info("Initializing custom metric cleanup timer Interval <{}>", interval);
        getTimers().startTimerAtFixedRate("cleanup-unused-metrics", new CleanupUnusedMetrics(operatorMetricsConfig),
                interval);
    }

    private void handleCleanupUnusedMetrics(CleanupUnusedMetrics cleanupUnusedMetrics) {
        // remove metrics who were not used for longer than three times the max configured scrape interval
        final long currentTime = System.currentTimeMillis();
        metricsGauges.entrySet().stream()
                .filter(entry -> {
                    final long time = entry.getValue()._2();
                    return currentTime - time > getMaxConfiguredScrapeInterval(cleanupUnusedMetrics.config())
                            .multipliedBy(3).toMillis();
                })
                .forEach(entry -> {
                    final String realName = realName(entry.getKey());
                    if (Kamon.gauge(realName)
                            .withTags(KamonTagSetConverter.getKamonTagSet(entry.getValue()._1().getTagSet()))
                            .remove()) {
                        log.debug("Removed custom search metric instrument: {} {}", realName,
                                entry.getValue()._1().getTagSet());
                        customSearchMetricsGauge.decrement();
                    } else {
                        log.info("Could not remove unused custom search metric instrument: {}", entry.getKey());
                    }
                });
    }

    private void handleGatheringMetrics(final GatherMetrics gatherMetrics) {
        final String metricName = gatherMetrics.metricName();
        final CustomSearchMetricConfig config = gatherMetrics.config();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("gather-search-metrics_" + metricName + "_" + UUID.randomUUID())
                .putHeader("ditto-sudo", "true")
                .build();

        final long startTs = System.nanoTime();
        config.getFilterConfigs().forEach(filterConfig -> {
            QueryThings searchThings = QueryThings.of(filterConfig.getFilter(), null, null,
                    new HashSet<>(config.getNamespaces()), dittoHeaders);
            askSearchActor(searchThings, metricName, startTs, filterConfig, config, dittoHeaders);
        });
    }

    private static GatherMetrics createGatherCustomMetric(final String metricName,
            final CustomSearchMetricConfig config) {
        return new GatherMetrics(metricName, config);
    }

    private void askSearchActor(final QueryThings searchThings, final String metricName, final long startTs,
            final CustomSearchMetricConfig.FilterConfig filterConfig, final CustomSearchMetricConfig config,
            final DittoHeaders dittoHeaders) {
        log.withCorrelationId(dittoHeaders).debug("Asking for things for custom metric <{}>..", metricName);
        Patterns.ask(searchActor, searchThings, Duration.ofSeconds(DEFAULT_SEARCH_TIMEOUT_SECONDS))
                .whenComplete((response, throwable) -> {
                    if (response instanceof QueryThingsResponse queryThingsResponse) {
                        log.withCorrelationId(queryThingsResponse)
                                .debug("Received QueryThingsResponse for custom search metric <{}>: {} - " +
                                                "duration: <{}ms>",
                                        metricName, queryThingsResponse.getSearchResult().getItems().getSize(),
                                        Duration.ofNanos(System.nanoTime() - startTs).toMillis()
                                );
                        aggregateResponse(queryThingsResponse, filterConfig, metricName, config, dittoHeaders);
                        if (queryThingsResponse.getSearchResult().hasNextPage() &&
                                queryThingsResponse.getSearchResult().getCursor().isPresent()) {

                            QueryThings nextPageSearch = QueryThings.of(searchThings.getFilter().orElse(null),
                                    List.of("cursor(" + queryThingsResponse.getSearchResult().getCursor().get() + ")"),
                                    null,
                                    new HashSet<>(config.getNamespaces()), dittoHeaders);
                            log.withCorrelationId(queryThingsResponse)
                                    .debug("Asking for next page {} for custom search metric <{}>..",
                                            queryThingsResponse.getSearchResult().getNextPageOffset().orElse(-1L),
                                            metricName);
                            askSearchActor(nextPageSearch, metricName, startTs, filterConfig, config, dittoHeaders);
                        }
                        recordMetric(metricName);

                    } else if (response instanceof DittoRuntimeException dre) {
                        log.withCorrelationId(dittoHeaders).warning(
                                "Received DittoRuntimeException when gathering things for " +
                                        "custom search metric <{}> with queryThings {}: {}", metricName, searchThings,
                                dre.getMessage(), dre
                        );
                    } else {
                        log.withCorrelationId(dittoHeaders).warning(
                                "Received unexpected result or throwable when gathering things for " +
                                        "custom search metric <{}> with queryThings {}: {}", metricName, searchThings,
                                response, throwable
                        );
                    }
                });
    }

    private void aggregateResponse(QueryThingsResponse queryThingsResponse,
            CustomSearchMetricConfig.FilterConfig filterConfig, String metricName,
            CustomSearchMetricConfig config, DittoHeaders dittoHeaders) {
        if (isOnlyThingIdField(filterConfig.getFields())) {
            final List<Thing> things = queryThingsResponse.getSearchResult()
                    .getItems()
                    .stream()
                    .map(jsonValue -> ThingsModelFactory.newThing(
                            jsonValue.asObject()))
                    .toList();
            aggregateByTags(things, filterConfig, metricName, config, dittoHeaders);
        } else {
            final List<ThingId> thingIds = queryThingsResponse.getSearchResult()
                    .getItems()
                    .stream()
                    .map(jsonValue -> ThingsModelFactory.newThing(jsonValue.asObject()).getEntityId())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            log.withCorrelationId(dittoHeaders)
                    .debug("Retrieved Things for custom search metric {}: {}", metricName, thingIds);
            final SudoRetrieveThings sudoRetrieveThings = SudoRetrieveThings.of(thingIds,
                    JsonFactory.newFieldSelector(ensureThingId(filterConfig.getFields()), JsonParseOptions.newBuilder()
                            .withoutUrlDecoding().build()), dittoHeaders);
            Patterns.ask(thingsAggregatorProxyActor, sudoRetrieveThings,
                            Duration.ofSeconds(DEFAULT_SEARCH_TIMEOUT_SECONDS))
                    .handle((response, throwable) -> {
                        if (response instanceof SudoRetrieveThingsResponse retrieveThingsResponse) {
                            // aggregate response by tags and record after all pages are received
                            aggregateByTags(retrieveThingsResponse.getThings(), filterConfig, metricName, config,
                                    dittoHeaders);
                        } else {
                            log.withCorrelationId(dittoHeaders).warning(
                                    "Received unexpected result or throwable when gathering things for " +
                                            "custom search metric <{}> with queryThings {}: {}", metricName,
                                    queryThingsResponse,
                                    response, throwable
                            );
                        }
                        return null;
                    });
        }
    }

    private List<String> ensureThingId(final List<String> fields) {
        return fields.contains("thingId") ? fields : Stream.concat(fields.stream(), Stream.of("thingId"))
                .collect(Collectors.toList());
    }

    private void aggregateByTags(final List<Thing> things,
            final CustomSearchMetricConfig.FilterConfig filterConfig, final String metricName,
            final CustomSearchMetricConfig config, final DittoHeaders dittoHeaders) {
        final List<TagSet> tagSets = things.stream()
                .map(thing -> TagSet.ofTagCollection(config.getTags().entrySet().stream()
                        .map(e -> Tag.of(e.getKey(),
                                resolvePlaceHolder(thing.toJson().asObject(), e.getValue(), filterConfig, dittoHeaders,
                                        metricName)))
                        .sorted(Comparator.comparing(Tag::getValue))
                        .toList())).toList();
        tagSets.forEach(tagSet -> aggregatedByTagsValues.merge(tagSet, 1.0, Double::sum));
    }

    private void recordMetric(final String metricName) {
        aggregatedByTagsValues.forEach(
                (tags, value) -> metricsGauges.computeIfAbsent(uniqueName(metricName, tags), name -> {
                            log.info("Initializing custom search metric Gauge for metric <{}> with tags <{}>",
                                    metricName, tags);
                            customSearchMetricsGauge.increment();
                            return Tuple2.apply(KamonGauge.newGauge(metricName)
                                    .tags(tags), System.currentTimeMillis());
                        })
                        ._1().set(value));

        aggregatedByTagsValues.forEach(
                (tags, value) -> metricsGauges.compute(uniqueName(metricName, tags), (key, tupleValue) -> {
                            if (tupleValue == null) {
                                log.info("Initializing custom search metric Gauge for metric <{}> with tags <{}>",
                                        metricName, tags);
                                customSearchMetricsGauge.increment();
                                return Tuple2.apply(KamonGauge.newGauge(metricName)
                                        .tags(tags), System.currentTimeMillis());

                            } else {
                                // update the timestamp
                                log.debug("Updating custom search metric Gauge for metric <{}> with tags <{}>",
                                        metricName, tags);
                                return Tuple2.apply(tupleValue._1(), System.currentTimeMillis());
                            }
                        })
                        ._1().set(value));

        aggregatedByTagsValues.clear();
    }

    private String resolvePlaceHolder(final JsonObject thingJson, final String value,
            final CustomSearchMetricConfig.FilterConfig filterConfig, final DittoHeaders dittoHeaders,
            final String metricName) {
        if (!isPlaceHolder(value)) {
            return value;
        }
        String placeholder = value.substring(2, value.length() - 2).trim();
        final List<String> resolvedValues =
                new ArrayList<>(
                        thingJsonPlaceholder.resolveValues(ThingsModelFactory.newThing(thingJson), placeholder));

        if (resolvedValues.isEmpty()) {
            filterConfig.getInlinePlaceholderValues().forEach((k, v) -> {
                if (placeholder.equals(k)) {
                    resolvedValues.add(v);
                }
            });
        }
        if (resolvedValues.isEmpty()) {
            log.withCorrelationId(dittoHeaders)
                    .warning("Custom search metric {}. Could not resolve placeholder <{}> in thing <{}>. " +
                                    "Check that you have your fields configured correctly.", metricName,
                            placeholder, thingJson);
            return value;
        }


        return resolvedValues.stream().findFirst()
                .orElse(value);
    }

    private String uniqueName(final String metricName, final TagSet tags) {
        final ArrayList<Tag> list = new ArrayList<>();
        tags.iterator().forEachRemaining(list::add);
        return list.stream().sorted(Comparator.comparing(Tag::getKey)).map(t -> t.getKey() + "=" + t.getValue())
                .collect(Collectors.joining("_", metricName + "#", ""));
    }

    private String realName(final String uniqueName) {
        return uniqueName.substring(0, uniqueName.indexOf("#"));
    }

    private boolean isPlaceHolder(final String value) {
        return value.startsWith("{{") && value.endsWith("}}");
    }

    private boolean isOnlyThingIdField(final List<String> filterConfig) {
        return filterConfig.isEmpty() ||
                (filterConfig.size() == 1 && filterConfig.get(0).equals(Thing.JsonFields.ID.getPointer().toString()));
    }

    private Duration getMaxConfiguredScrapeInterval(final OperatorMetricsConfig operatorMetricsConfig) {
        return Stream.concat(Stream.of(operatorMetricsConfig.getScrapeInterval()),
                        operatorMetricsConfig.getCustomSearchMetricConfigurations().values().stream()
                                .map(CustomSearchMetricConfig::getScrapeInterval)
                                .filter(Optional::isPresent)
                                .map(Optional::get))
                .max(Comparator.naturalOrder())
                .orElse(operatorMetricsConfig.getScrapeInterval());
    }

    private record GatherMetrics(String metricName, CustomSearchMetricConfig config) {}

    private record CleanupUnusedMetrics(OperatorMetricsConfig config) {}
}
