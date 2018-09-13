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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoRetrieveNamespaceReport;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.persistence.query.AggregationQueryActor;
import org.eclipse.ditto.services.thingsearch.persistence.query.QueryActor;
import org.eclipse.ditto.services.thingsearch.persistence.read.PolicyRestrictedSearchAggregation;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.IsPolicyLookupNeededVisitor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.ThingSearchQueryCommand;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.concurrent.ExecutionContextExecutor;

/**
 * Actor handling all supported {@link ThingSearchCommand}s. Currently those are {@link CountThings} and {@link
 * QueryThings}.
 * <p>
 * Passes the commands to the appropriate query actor which is determined by the API version of each received command
 * (see {@link DittoHeaders#getSchemaVersion()}).
 * <p>
 * Commands with version 1 are delegated to the {@link QueryActor} which creates a {@link Query} out of the commands.
 * <p>
 * Commands with version 2 are delegated to the {@link AggregationQueryActor} which creates a {@link
 * PolicyRestrictedSearchAggregation} out of the commands.
 * <p>
 * Both, Query and PolicyRestrictedSearchAggregation are executed against the passed {@link ThingsSearchPersistence}.
 * <p>
 * The ThingsSearchPersistence returns only Thing IDs. Thus to provide complete Thing information to the requester,
 * things have to be retrieved from Things Service via distributed pub/sub.
 */
public final class SearchActor extends AbstractActor {

    /**
     * The name of this actor in the system.
     */
    public static final String ACTOR_NAME = "thingsSearch";

    private static final String SEARCH_DISPATCHER_ID = "search-dispatcher";

    private static final int QUERY_ASK_TIMEOUT = 500;

    private static final String TRACING_THINGS_SEARCH = "things_search_query";
    private static final String QUERY_PARSING_SEGMENT_NAME = "query_parsing";
    private static final String DATABASE_ACCESS_SEGMENT_NAME = "database_access";
    private static final String QUERY_TYPE_TAG = "query_type";
    private static final String API_VERSION_TAG = "api_version";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory =
            new QueryFilterCriteriaFactory(new CriteriaFactoryImpl(), new ThingsFieldExpressionFactoryImpl());

    private final ActorRef aggregationQueryActor;
    private final ActorRef findQueryActor;
    private final ThingsSearchPersistence searchPersistence;
    private final ActorMaterializer materializer;
    private final ExecutionContextExecutor dispatcher;

    private SearchActor(final ActorRef aggregationQueryActor,
            final ActorRef findQueryActor,
            final ThingsSearchPersistence searchPersistence) {

        this.aggregationQueryActor = aggregationQueryActor;
        this.findQueryActor = findQueryActor;
        this.searchPersistence = searchPersistence;
        materializer = ActorMaterializer.create(getContext().system());

        dispatcher = getContext().system().dispatchers().lookup(SEARCH_DISPATCHER_ID);
    }

    /**
     * Creates Akka configuration object Props for this SearchActor.
     *
     * @param aggregationQueryActor ActorRef for the {@link AggregationQueryActor} to use in order to create {@link
     * PolicyRestrictedSearchAggregation}s from {@link ThingSearchCommand}s.
     * @param findQueryActor ActorRef for the {@link QueryActor} to construct find queries.
     * @param searchPersistence the {@link ThingsSearchPersistence} to use in order to execute {@link
     * PolicyRestrictedSearchAggregation}s.
     * @return the Akka configuration Props object.
     */
    static Props props(final ActorRef aggregationQueryActor,
            final ActorRef findQueryActor,
            final ThingsSearchPersistence searchPersistence) {

        return Props.create(SearchActor.class, new Creator<SearchActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public SearchActor create() {
                return new SearchActor(aggregationQueryActor, findQueryActor, searchPersistence);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CountThings.class, this::count)
                .match(SudoCountThings.class, this::count)
                .match(QueryThings.class, this::query)
                .match(SudoRetrieveNamespaceReport.class, this::namespaceReport)
                .matchAny(any -> log.warning("Got unknown message '{}'", any))
                .build();
    }

    private void namespaceReport(final SudoRetrieveNamespaceReport namespaceReport) {
        final DittoHeaders dittoHeaders = namespaceReport.getDittoHeaders();
        LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
        log.info("Processing SudoRetrieveNamespaceReport command: {}", namespaceReport);

        PatternsCS.pipe(searchPersistence.generateNamespaceCountReport()
                .runWith(Sink.head(), materializer), dispatcher)
                .to(getSender());
    }

    private void count(final Command countThings) {
        final DittoHeaders dittoHeaders = countThings.getDittoHeaders();
        final Optional<String> correlationIdOpt = dittoHeaders.getCorrelationId();
        LogUtil.enhanceLogWithCorrelationId(log, correlationIdOpt);
        log.info("Processing CountThings command: {}", countThings);
        final JsonSchemaVersion version = countThings.getImplementedSchemaVersion();

        final String queryType = "count";

        final StartedTimer countTimer = startNewTimer(version, queryType);

        final StartedTimer queryParsingTimer = countTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);

        final ActorRef sender = getSender();

        // choose a query actor based on the API version in command headers
        final ActorRef chosenQueryActor = chooseQueryActor(version, countThings);

        PatternsCS.pipe(
                Source.fromCompletionStage(PatternsCS.ask(chosenQueryActor, countThings, QUERY_ASK_TIMEOUT))
                        .flatMapConcat(query -> {
                            LogUtil.enhanceLogWithCorrelationId(log, correlationIdOpt);
                            queryParsingTimer.stop();
                            if (query instanceof PolicyRestrictedSearchAggregation) {
                                final StartedTimer databaseAccessTimer =
                                        countTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);
                                // aggregation-based count for things with policies
                                return processSearchPersistenceResult(
                                        () -> searchPersistence.count((PolicyRestrictedSearchAggregation) query),
                                        dittoHeaders)
                                        .via(Flow.fromFunction(result -> {
                                            databaseAccessTimer.stop();
                                            return result;
                                        }))
                                        .map(count -> CountThingsResponse.of(count, dittoHeaders));
                            } else if (query instanceof Query) {
                                final StartedTimer databaseAccessTimer =
                                        countTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);
                                // count without aggregation for things without policies
                                return processSearchPersistenceResult(() -> searchPersistence.count((Query) query),
                                        dittoHeaders)
                                        .via(Flow.fromFunction(result -> {
                                            databaseAccessTimer.stop();
                                            return result;
                                        }))
                                        .map(count -> CountThingsResponse.of(count, dittoHeaders));
                            } else if (query instanceof DittoRuntimeException) {
                                log.info("QueryActor responded with DittoRuntimeException: {}", query);
                                return Source.<Object>failed((Throwable) query);
                            } else {
                                log.error("Expected 'PolicyRestrictedSearchAggregation', but got: {}", query);
                                return Source.<Object>single(CountThingsResponse.of(-1, dittoHeaders));
                            }
                        })
                        .via(Flow.fromFunction(result -> {
                            countTimer.stop();
                            return result;
                        }))
                        .runWith(Sink.head(), materializer), dispatcher)
                .to(sender);
    }

    private void query(final QueryThings queryThings) {
        final DittoHeaders dittoHeaders = queryThings.getDittoHeaders();
        final Optional<String> correlationIdOpt = dittoHeaders.getCorrelationId();
        LogUtil.enhanceLogWithCorrelationId(log, correlationIdOpt);
        log.info("Processing QueryThings command: {}", queryThings);
        final JsonSchemaVersion version = queryThings.getImplementedSchemaVersion();

        final String queryType = "query";
        final StartedTimer searchTimer = startNewTimer(version, queryType);
        final StartedTimer queryParsingTimer = searchTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);

        final ActorRef sender = getSender();

        // choose a query actor based on the API version in command headers
        final ActorRef chosenQueryActor = chooseQueryActor(version, queryThings);

        PatternsCS.pipe(
                Source.fromCompletionStage(PatternsCS.ask(chosenQueryActor, queryThings, QUERY_ASK_TIMEOUT))
                        .flatMapConcat(query -> {
                            LogUtil.enhanceLogWithCorrelationId(log, correlationIdOpt);
                            queryParsingTimer.stop();

                            if (query instanceof PolicyRestrictedSearchAggregation) {
                                final StartedTimer databaseAccessTimer =
                                        searchTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);
                                // policy-based search via aggregation
                                return processSearchPersistenceResult(
                                        () -> searchPersistence.findAll((PolicyRestrictedSearchAggregation) query),
                                        dittoHeaders)
                                        .via(Flow.fromFunction(result -> {
                                            databaseAccessTimer.stop();
                                            return result;
                                        }))
                                        .flatMapConcat(resultList -> retrieveThingsForIds(resultList, queryThings));
                            } else if (query instanceof Query) {
                                final StartedTimer databaseAccessTimer =
                                        searchTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);
                                // api/1 search via 'find'
                                return processSearchPersistenceResult(() -> searchPersistence.findAll((Query) query),
                                        dittoHeaders)
                                        .via(Flow.fromFunction(result -> {
                                            databaseAccessTimer.stop();
                                            return result;
                                        }))
                                        .flatMapConcat(resultList -> retrieveThingsForIds(resultList, queryThings));
                            } else if (query instanceof DittoRuntimeException) {
                                log.info("QueryActor responded with DittoRuntimeException: {}", query);
                                return Source.<QueryThingsResponse>failed((Throwable) query);
                            } else {
                                log.error("Expected 'PolicyRestrictedSearchAggregation' or 'query', but got: {}",
                                        query);
                                return Source.<QueryThingsResponse>single(
                                        QueryThingsResponse.of(SearchModelFactory.emptySearchResult(), dittoHeaders));
                            }
                        })
                        .via(Flow.fromFunction(result -> {
                            searchTimer.stop();
                            return result;
                        }))
                        .runWith(Sink.head(), materializer), dispatcher)
                .to(sender);
    }

    private <T> Source<T, NotUsed> processSearchPersistenceResult(final Supplier<Source<T, NotUsed>> resultSupplier,
            final DittoHeaders dittoHeaders) {
        final Source<T, NotUsed> source = resultSupplier.get();

        final Flow<T, T, NotUsed> logAndFinishPersistenceSegmentFlow =
                Flow.fromFunction(result -> {
                    // we know that the source provides exactly one ResultList
                    LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                    log.debug("Persistence returned: {}", result);
                    return result;
                });

        return source.<T, NotUsed>via(logAndFinishPersistenceSegmentFlow);
    }

    private Graph<SourceShape<QueryThingsResponse>, NotUsed> retrieveThingsForIds(final ResultList<String> thingIds,
            final QueryThings queryThings) {

        final Graph<SourceShape<QueryThingsResponse>, NotUsed> result;

        final DittoHeaders dittoHeaders = queryThings.getDittoHeaders();
        final Optional<String> correlationIdOpt = dittoHeaders.getCorrelationId();
        LogUtil.enhanceLogWithCorrelationId(log, correlationIdOpt);
        if (thingIds.isEmpty()) {
            result = Source.<QueryThingsResponse>single(QueryThingsResponse.of(SearchModelFactory.emptySearchResult(), dittoHeaders));
        } else  {
            // only respond with the determined "thingIds", the lookup of the things is done in gateway:
            final JsonArray items = thingIds.stream()
                    .map(JsonValue::of)
                    .map(jsonStr -> JsonObject.newBuilder()
                            .set(Thing.JsonFields.ID.getPointer(), jsonStr)
                            .build()
                    )
                    .collect(JsonCollectors.valuesToArray());
            final SearchResult searchResult = SearchModelFactory.newSearchResult(items, thingIds.nextPageOffset());

            result = Source.<QueryThingsResponse>single(QueryThingsResponse.of(searchResult, dittoHeaders));
        }

        return result;
    }

    private ActorRef chooseQueryActor(final JsonSchemaVersion version, final Command<?> command) {
        if (command instanceof ThingSearchQueryCommand<?>) {
            final String filter = ((ThingSearchQueryCommand<?>) command).getFilter().orElse(null);
            // useless parsing of command just to choose another actor to "parse" the filter string
            try {
                final Criteria criteria = queryFilterCriteriaFactory.filterCriteria(filter, command.getDittoHeaders());
                final boolean needToLookupPolicy =
                        JsonSchemaVersion.V_1 != version && criteria.accept(new IsPolicyLookupNeededVisitor());
                return needToLookupPolicy ? aggregationQueryActor : findQueryActor;
            } catch (final DittoRuntimeException e) {
                // criteria is invalid, let the query actor deal with it
                return findQueryActor;
            }
        } else {
            // don't bother with aggregation for sudo commands
            return findQueryActor;
        }
    }

    private static StartedTimer startNewTimer(final JsonSchemaVersion version, final String queryType) {
        return DittoMetrics.expiringTimer(TRACING_THINGS_SEARCH)
                .tag(QUERY_TYPE_TAG, queryType)
                .tag(API_VERSION_TAG, version.toString())
                .build();
    }

}
