/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.KamonGauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoCountThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThingsResponse;
import org.eclipse.ditto.thingsearch.service.common.config.CustomMetricConfig;
import org.eclipse.ditto.thingsearch.service.common.config.OperatorMetricsConfig;

/**
 * Actor which is started as singleton for "search" role and is responsible for querying for operator defined
 * "custom metrics" (configured via Ditto search service configuration) to expose as {@code Gauge} via Prometheus.
 */
public final class OperatorMetricsProviderActor extends AbstractActorWithTimers {

    /**
     * This Actor's actor name.
     */
    public static final String ACTOR_NAME = "operatorMetricsProvider";

    private static final int MIN_INITIAL_DELAY_SECONDS = 30;
    private static final int MAX_INITIAL_DELAY_SECONDS = 90;
    private static final int DEFAULT_COUNT_TIMEOUT_SECONDS = 60;

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef searchActor;
    private final Map<String, Gauge> metricsGauges;

    @SuppressWarnings("unused")
    private OperatorMetricsProviderActor(final OperatorMetricsConfig operatorMetricsConfig,
            final ActorRef searchActor) {

        this.searchActor = searchActor;
        metricsGauges = new HashMap<>();
        operatorMetricsConfig.getCustomMetricConfigurations().forEach((metricName, config) -> {
            if (config.isEnabled()) {
                initializeCustomMetric(operatorMetricsConfig, metricName, config);
            } else {
                log.info("Initializing custom metric Gauge for metric <{}> is DISABLED", metricName);
            }
        });
    }

    /**
     * Create Props for this actor.
     *
     * @param operatorMetricsConfig the config to use
     * @param searchActor the SearchActor Actor reference
     * @return the Props object.
     */
    public static Props props(final OperatorMetricsConfig operatorMetricsConfig, final ActorRef searchActor) {
        return Props.create(OperatorMetricsProviderActor.class, operatorMetricsConfig, searchActor);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(GatherMetrics.class, this::handleGatheringMetrics)
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private void initializeCustomMetric(final OperatorMetricsConfig operatorMetricsConfig, final String metricName,
            final CustomMetricConfig config) {
        // start each custom metric provider with a random initialDelay
        final Duration initialDelay = Duration.ofSeconds(
                ThreadLocalRandom.current().nextInt(MIN_INITIAL_DELAY_SECONDS, MAX_INITIAL_DELAY_SECONDS)
        );
        final Duration scrapeInterval = config.getScrapeInterval()
                .orElse(operatorMetricsConfig.getScrapeInterval());
        getTimers().startTimerAtFixedRate(
                metricName, createGatherCustomMetric(metricName, config), initialDelay, scrapeInterval);

        final List<Tag> tags = config.getTags().entrySet().stream()
                .map(e -> Tag.of(e.getKey(), e.getValue()))
                .toList();
        log.info("Initializing custom metric Gauge for metric <{}> with tags <{}>, initial delay <{}> " +
                "and a scrape-interval of <{}>", metricName, tags, initialDelay, scrapeInterval);
        final Gauge gauge = KamonGauge.newGauge(metricName)
                .tags(TagSet.ofTagCollection(tags));
        metricsGauges.put(metricName, gauge);
    }

    private static GatherMetrics createGatherCustomMetric(final String metricName, final CustomMetricConfig config) {
        return new GatherMetrics(metricName, config);
    }

    private void handleGatheringMetrics(final GatherMetrics gatherMetrics) {
        final String metricName = gatherMetrics.metricName();
        final CustomMetricConfig config = gatherMetrics.config();
        final String filter = config.getFilter();
        final List<String> namespaces = config.getNamespaces();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("gather-metrics_" + metricName + "_" + UUID.randomUUID())
                .build();
        final SudoCountThings sudoCountThings = SudoCountThings.of(
                filter.isEmpty() ? null : filter, namespaces.isEmpty() ? null : namespaces, dittoHeaders);

        final long startTs = System.nanoTime();
        log.withCorrelationId(dittoHeaders)
                .debug("Asking for count of custom metric <{}>..", metricName);

        Patterns.ask(searchActor, sudoCountThings, Duration.ofSeconds(DEFAULT_COUNT_TIMEOUT_SECONDS))
                .whenComplete((response, throwable) -> {
                    if (response instanceof CountThingsResponse countThingsResponse) {
                        log.withCorrelationId(countThingsResponse)
                                .info("Received sudo CountThingsResponse for custom metric count <{}>: {} - " +
                                                "duration: <{}ms>",
                                        metricName, countThingsResponse.getCount(),
                                        Duration.ofNanos(System.nanoTime() - startTs).toMillis()
                                );
                        metricsGauges.get(metricName).set(countThingsResponse.getCount());
                    } else if (response instanceof DittoRuntimeException dre) {
                        log.withCorrelationId(dittoHeaders).warning(
                                "Received DittoRuntimeException when gathering count for " +
                                        "custom metric <{}>: {}", metricName, dre.getMessage(), dre
                        );
                    } else {
                        log.withCorrelationId(dittoHeaders).warning(throwable,
                                "Received unexpected result or throwable when gathering count for " +
                                        "custom metric <{}>: {}", metricName, response
                        );
                    }
                });
    }

    private record GatherMetrics(String metricName, CustomMetricConfig config) {}
}
