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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.read;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresAggregationQuery;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresAggregationQuery.GroupKey;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetrics;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsAggregationPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * PostgreSQL implementation of the backend-neutral {@link ThingsAggregationPersistence} — the operator "custom
 * aggregation metrics" path (plan §3.5), the Postgres counterpart of {@code MongoThingsAggregationPersistence}. It
 * translates each metric's {@code $match}+{@code $group} pipeline into a single {@code SELECT … GROUP BY} over
 * {@code search_things} (see {@link PostgresAggregationQuery} for the transcription) and emits, per group, the exact
 * backend-neutral {@link JsonObject} the consumer ({@code AggregateThingsMetricsResponse}) parses.
 * <p>
 * The metric filter is parsed here (from the command's RQL {@code filter} string) via a {@link QueryFilterCriteriaFactory}
 * built from the neutral {@code simple-field-mappings} — exactly like the Mongo impl — and translated in the <b>sudo</b>
 * (no-auth) form, because operator metrics are system-level.
 * </p>
 * <p>
 * <b>Ignored Mongo-only per-metric {@code index-hint} (plan §3.6, req 2).</b> A metric may carry an {@code indexHint}
 * (Mongo {@code hint}); PostgreSQL chooses its own plan, so it is ignored — WARNed once per metric name (the provider
 * additionally WARNs any {@code index-hint} it finds in config at boot).
 * </p>
 * <p>
 * <b>Mongo {@code $group} zero-input parity (no-grouping metrics).</b> See
 * {@link PostgresAggregationQuery the query class javadoc} for the full rationale: a no-{@code GROUP BY}
 * {@code count(*)} always returns one row (count 0) even over zero matches, whereas Mongo's {@code $group} would emit
 * no documents at all. That lone artifact row is suppressed here (via
 * {@link PostgresAggregationQuery#isVanishedNoGroupRow}) so the emitted {@link Source} carries ZERO elements for a
 * no-grouping metric with zero matches — exactly like Mongo — which matters because
 * {@code OperatorAggregateMetricsProviderActor.reconcileVanishedBuckets} uses an empty batch as the signal to zero
 * and remove a stale Kamon gauge; a spurious {@code {"_id":{},"count":0}} element would otherwise be read as "still
 * present, now zero" and the gauge would never be reconciled away.
 * </p>
 *
 * @since 4.0.0
 */
@ThreadSafe
public final class PostgresThingsAggregationPersistence implements ThingsAggregationPersistence {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresThingsAggregationPersistence.class);

    private final ConnectionFactory connectionFactory;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;
    /** Metric names whose ignored per-metric index-hint has already been WARNed (req 2: once per metric). */
    private final Set<String> indexHintWarned = ConcurrentHashMap.newKeySet();

    private PostgresThingsAggregationPersistence(final ConnectionFactory connectionFactory,
            final Map<String, String> simpleFieldMappings) {
        this.connectionFactory = connectionFactory;
        this.queryFilterCriteriaFactory = QueryFilterCriteriaFactory.of(
                ThingsFieldExpressionFactory.of(simpleFieldMappings), RqlPredicateParser.getInstance());
    }

    /**
     * @param client the shared PostgreSQL client (one connection pool per service, via {@code PostgresClientExtension}).
     * @param simpleFieldMappings the {@code ditto.search.simple-field-mappings} used to resolve the metric filter's RQL
     * field names (the same mappings the Mongo impl consumes) — the provider reads them neutrally, so this persistence
     * takes no Mongo-coupled service config.
     * @return the aggregation persistence.
     */
    public static PostgresThingsAggregationPersistence of(final DittoPostgresClient client,
            final Map<String, String> simpleFieldMappings) {
        return new PostgresThingsAggregationPersistence(
                Objects.requireNonNull(client, "client").getConnectionPool(),
                Map.copyOf(Objects.requireNonNull(simpleFieldMappings, "simpleFieldMappings")));
    }

    /**
     * @param connectionFactory a connection factory (a pool, or a plain testkit factory) whose connections back the
     * aggregation statement.
     * @param simpleFieldMappings the RQL field-name mappings for the metric filter.
     * @return the aggregation persistence.
     */
    static PostgresThingsAggregationPersistence forConnectionFactory(final ConnectionFactory connectionFactory,
            final Map<String, String> simpleFieldMappings) {
        return new PostgresThingsAggregationPersistence(Objects.requireNonNull(connectionFactory, "connectionFactory"),
                Map.copyOf(Objects.requireNonNull(simpleFieldMappings, "simpleFieldMappings")));
    }

    @Override
    public Source<JsonObject, NotUsed> aggregateThings(final AggregateThingsMetrics aggregateCommand) {
        warnOnIgnoredIndexHint(aggregateCommand);

        final Criteria filterCriteria = queryFilterCriteriaFactory.filterCriteria(
                aggregateCommand.getFilter().orElse(null), aggregateCommand.getDittoHeaders());
        // Sort namespaces to a stable order so the rendered SQL is deterministic (semantics are set-membership).
        final List<String> sortedNamespaces = new ArrayList<>(aggregateCommand.getNamespaces());
        java.util.Collections.sort(sortedNamespaces);
        final List<GroupKey> groupKeys = PostgresAggregationQuery.groupKeysOf(aggregateCommand.getGroupingBy());
        final RenderedSql rendered = PostgresAggregationQuery.assemble(filterCriteria, sortedNamespaces, groupKeys);

        final Flux<JsonObject> results = Flux.usingWhen(
                Mono.from(connectionFactory.create()),
                connection -> {
                    final Statement statement = rendered.applyTo(connection.createStatement(rendered.sql()));
                    return Flux.from(statement.execute())
                            .flatMap(result -> result.map((row, meta) -> toAggregationRow(row, groupKeys)))
                            // Suppress the spurious no-GROUP-BY zero-count row (Mongo $group zero-input parity,
                            // see PostgresAggregationQuery javadoc); grouped rows are never suppressed.
                            .filter(aggregationRow -> !PostgresAggregationQuery.isVanishedNoGroupRow(groupKeys,
                                    aggregationRow.count()))
                            .map(AggregationRow::json);
                },
                Connection::close);
        return Source.fromPublisher(results);
    }

    /**
     * One mapped result row, carrying both the raw {@code count(*)} (for the {@link
     * PostgresAggregationQuery#isVanishedNoGroupRow(List, long) vanished-no-group-row} check) and the emitted
     * {@link JsonObject} shape (for the consumer).
     */
    private record AggregationRow(long count, JsonObject json) {}

    private static AggregationRow toAggregationRow(final Row row, final List<GroupKey> groupKeys) {
        final Long count = row.get("count", Long.class);
        final long countValue = count != null ? count : 0L;
        final JsonObject json = PostgresAggregationQuery.toAggregationJson(groupKeys,
                alias -> row.get(alias, String.class), countValue);
        return new AggregationRow(countValue, json);
    }

    private void warnOnIgnoredIndexHint(final AggregateThingsMetrics aggregateCommand) {
        if (aggregateCommand.getIndexHint().isPresent()
                && indexHintWarned.add(aggregateCommand.getMetricName())) {
            LOGGER.warn("Ignoring per-metric 'index-hint' <{}> for operator aggregation metric <{}>: PostgreSQL chooses "
                            + "its own query plan; there is no per-query index hint (plan §3.6).",
                    aggregateCommand.getIndexHint().get(), aggregateCommand.getMetricName());
        }
    }

}
