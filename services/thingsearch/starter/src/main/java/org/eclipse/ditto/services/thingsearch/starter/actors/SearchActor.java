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

import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoRetrieveNamespaceReport;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.IsPolicyLookupNeededVisitor;
import org.eclipse.ditto.services.thingsearch.query.actors.AggregationQueryActor;
import org.eclipse.ditto.services.thingsearch.query.actors.QueryActor;
import org.eclipse.ditto.services.thingsearch.query.actors.QueryFilterCriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
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
import akka.cluster.pubsub.DistributedPubSubMediator;
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
import kamon.Kamon;
import kamon.trace.Segment;
import kamon.trace.TraceContext;
import scala.Option;
import scala.Some;
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
    private static final String THINGS_ACTOR_PATH = "/user/gatewayRoot/proxy";

    private static final int QUERY_ASK_TIMEOUT = 500;
    private static final int THINGS_ASK_TIMEOUT = 60 * 1000;

    private static final String TRACE_SEARCH_QUERY_PREFIX = "things.search.query.";
    private static final String TRACE_SEARCH_COUNT_PREFIX = "things.search.count.";
    private static final String THINGS_SEARCH = "Things_Search";
    private static final String QUERY_PARSING = "Things_Search_Query_Parsing";
    private static final String DATABASE_ACCESS = "Things_Search_DB_access";
    private static final String THINGS_SERVICE_ACCESS = "Things_Service_access";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory =
            new QueryFilterCriteriaFactory(new CriteriaFactoryImpl(), new ThingsFieldExpressionFactoryImpl());

    private final ActorRef pubSubMediator;
    private final ActorRef aggregationQueryActor;
    private final ActorRef findQueryActor;
    private final ThingsSearchPersistence searchPersistence;
    private final ActorMaterializer materializer;
    private final ExecutionContextExecutor dispatcher;

    private SearchActor(final ActorRef pubSubMediator,
            final ActorRef aggregationQueryActor,
            final ActorRef findQueryActor,
            final ThingsSearchPersistence searchPersistence) {

        this.pubSubMediator = pubSubMediator;
        this.aggregationQueryActor = aggregationQueryActor;
        this.findQueryActor = findQueryActor;
        this.searchPersistence = searchPersistence;
        materializer = ActorMaterializer.create(getContext().system());

        dispatcher = getContext().system().dispatchers().lookup(SEARCH_DISPATCHER_ID);
    }

    /**
     * Creates Akka configuration object Props for this SearchActor.
     *
     * @param pubSubMediator ActorRef for the {@link DistributedPubSubMediator} to use for asking Things-Service for
     * Things.
     * @param aggregationQueryActor ActorRef for the {@link AggregationQueryActor} to use in order to create {@link
     * PolicyRestrictedSearchAggregation}s from {@link ThingSearchCommand}s.
     * @param findQueryActor ActorRef for the {@link QueryActor} to construct find queries.
     * @param searchPersistence the {@link ThingsSearchPersistence} to use in order to execute {@link
     * PolicyRestrictedSearchAggregation}s.
     * @return the Akka configuration Props object.
     */
    static Props props(final ActorRef pubSubMediator,
            final ActorRef aggregationQueryActor,
            final ActorRef findQueryActor,
            final ThingsSearchPersistence searchPersistence) {

        return Props.create(SearchActor.class, new Creator<SearchActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public SearchActor create() throws Exception {
                return new SearchActor(pubSubMediator, aggregationQueryActor, findQueryActor, searchPersistence);
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
        LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
        log.info("Processing CountThings command: {}", countThings);
        final JsonSchemaVersion version = countThings.getImplementedSchemaVersion();


        final Option<String> token = dittoHeaders.getCorrelationId()
                .<Option<String>>map(Some::apply)
                .orElse(Option.empty());
        final TraceContext traceContext = Kamon.tracer()
                .newContext(prefixJsonSchemaVersion(TRACE_SEARCH_COUNT_PREFIX, version), token);
        final Segment querySegment =
                traceContext.startSegment(QUERY_PARSING, THINGS_SEARCH, SearchActor.class.getSimpleName());

        final ActorRef sender = getSender();

        // choose a query actor based on the API version in command headers
        final ActorRef chosenQueryActor = chooseQueryActor(version, countThings);

        PatternsCS.pipe(
                Source.fromCompletionStage(PatternsCS.ask(chosenQueryActor, countThings, QUERY_ASK_TIMEOUT))
                        .flatMapConcat(query -> {
                            LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                            querySegment.finish();
                            if (query instanceof PolicyRestrictedSearchAggregation) {
                                // aggregation-based count for things with policies
                                return processSearchPersistenceResult(
                                        () -> searchPersistence.count((PolicyRestrictedSearchAggregation) query),
                                        traceContext, dittoHeaders)
                                        .map(count -> CountThingsResponse.of(count, dittoHeaders));
                            } else if (query instanceof Query) {
                                // count without aggregation for things without policies
                                return processSearchPersistenceResult(() -> searchPersistence.count((Query) query),
                                        traceContext, dittoHeaders)
                                        .map(count -> CountThingsResponse.of(count, dittoHeaders));
                            } else if (query instanceof DittoRuntimeException) {
                                log.info("QueryActor responded with DittoRuntimeException: {}", query);
                                return Source.failed((Throwable) query);
                            } else {
                                log.error("Expected 'PolicyRestrictedSearchAggregation', but got: {}", query);
                                return Source.single(CountThingsResponse.of(-1, dittoHeaders));
                            }
                        })
                        .via(Flow.fromFunction(foo -> {
                            traceContext.finish(); // finish kamon trace
                            return foo;
                        }))
                        .runWith(Sink.head(), materializer), dispatcher)
                .to(sender);
    }

    private static String prefixJsonSchemaVersion(final String prefix, final JsonSchemaVersion version) {
        return prefix + version.toInt();
    }

    private void query(final QueryThings queryThings) {
        final DittoHeaders dittoHeaders = queryThings.getDittoHeaders();
        LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
        log.info("Processing QueryThings command: {}", queryThings);
        final JsonSchemaVersion version = queryThings.getImplementedSchemaVersion();

        final Option<String> token = dittoHeaders.getCorrelationId()
                .<Option<String>>map(Some::apply)
                .orElse(Option.empty());
        final TraceContext traceContext = Kamon.tracer()
                .newContext(prefixJsonSchemaVersion(TRACE_SEARCH_QUERY_PREFIX, version), token);
        final Segment querySegment = traceContext.startSegment(QUERY_PARSING, THINGS_SEARCH,
                SearchActor.class.getSimpleName());

        final ActorRef sender = getSender();

        // choose a query actor based on the API version in command headers
        final ActorRef chosenQueryActor = chooseQueryActor(version, queryThings);

        PatternsCS.pipe(
                Source.fromCompletionStage(PatternsCS.ask(chosenQueryActor, queryThings, QUERY_ASK_TIMEOUT))
                        .flatMapConcat(query -> {
                            LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                            querySegment.finish();

                            if (query instanceof PolicyRestrictedSearchAggregation) {
                                // policy-based search via aggregation
                                return processSearchPersistenceResult(
                                        () -> searchPersistence.findAll((PolicyRestrictedSearchAggregation) query),
                                        traceContext, dittoHeaders)
                                        .flatMapConcat(resultList -> retrieveThingsForIds(resultList, queryThings,
                                                traceContext));
                            } else if (query instanceof Query) {
                                // api/1 search via 'find'
                                return processSearchPersistenceResult(() -> searchPersistence.findAll((Query) query),
                                        traceContext, dittoHeaders)
                                        .flatMapConcat(resultList -> retrieveThingsForIds(resultList, queryThings,
                                                traceContext));
                            } else if (query instanceof DittoRuntimeException) {
                                log.info("QueryActor responded with DittoRuntimeException: {}", query);
                                return Source.failed((Throwable) query);
                            } else {
                                log.error("Expected 'PolicyRestrictedSearchAggregation' or 'query', but got: {}",
                                        query);
                                return Source.single(
                                        QueryThingsResponse.of(SearchModelFactory.emptySearchResult(), dittoHeaders));
                            }
                        })
                        .via(Flow.fromFunction(foo -> {
                            traceContext.finish(); // finish kamon trace
                            return foo;
                        }))
                        .runWith(Sink.head(), materializer), dispatcher)
                .to(sender);
    }

    private <T> Source<T, NotUsed> processSearchPersistenceResult(final Supplier<Source<T, NotUsed>> resultSupplier,
            final TraceContext traceContext, final DittoHeaders dittoHeaders) {

        final Segment persistenceSegment =
                traceContext.startSegment(DATABASE_ACCESS, THINGS_SEARCH, SearchActor.class.getSimpleName());

        final Source<T, NotUsed> source = resultSupplier.get();

        final Flow<T, T, NotUsed> logAndFinishPersistenceSegmentFlow =
                Flow.fromFunction(result -> {
                    // we know that the source provides exactly one ResultList
                    LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                    persistenceSegment.finish();
                    log.debug("Persistence returned: {}", result);
                    return result;
                });

        return source.via(logAndFinishPersistenceSegmentFlow);
    }

    private Graph<SourceShape<QueryThingsResponse>, NotUsed> retrieveThingsForIds(final ResultList<String> thingIds,
            final QueryThings queryThings, final TraceContext traceContext) {

        final Graph<SourceShape<QueryThingsResponse>, NotUsed> result;

        final DittoHeaders dittoHeaders = queryThings.getDittoHeaders();
        LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
        if (thingIds.isEmpty()) {
            result = Source.single(QueryThingsResponse.of(SearchModelFactory.emptySearchResult(), dittoHeaders));
        } else if (queryThings.getFields()
                .map(JsonFieldSelector::getPointers)
                .filter(jsonPointers -> jsonPointers.size() == 1)
                .filter(jsonPointers -> jsonPointers.contains(Thing.JsonFields.ID.getPointer()))
                .isPresent()) {
            // if only "thingId" was selected in the fieldSelectors
            // we don't need to make a lookup of the Things at the Things-Service
            final JsonArray items = thingIds.stream()
                    .map(JsonValue::of)
                    .map(jsonStr -> JsonObject.newBuilder()
                            .set(Thing.JsonFields.ID.getPointer(), jsonStr)
                            .build()
                    )
                    .collect(JsonCollectors.valuesToArray());
            final SearchResult searchResult = SearchModelFactory.newSearchResult(items, thingIds.nextPageOffset());

            result = Source.single(QueryThingsResponse.of(searchResult, dittoHeaders));
        } else {
            final RetrieveThings retrieveThings = RetrieveThings.getBuilder(thingIds)
                    .dittoHeaders(dittoHeaders)
                    .selectedFields(queryThings.getFields())
                    .build();

            log.debug("About to send command to Things: {}", retrieveThings);
            final Segment thingsSegment =
                    traceContext.startSegment(THINGS_SERVICE_ACCESS, THINGS_SEARCH, SearchActor.class.getSimpleName());

            result = retrieveFromThings(thingIds, retrieveThings, thingsSegment);
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

    private Graph<SourceShape<QueryThingsResponse>, NotUsed> retrieveFromThings(final ResultList<String> thingIds,
            final RetrieveThings retrieveThings, final Segment thingsSegment) {
        final DittoHeaders dittoHeaders = retrieveThings.getDittoHeaders();
        return Source.fromCompletionStage(
                PatternsCS.ask(pubSubMediator, new DistributedPubSubMediator.Send(
                        THINGS_ACTOR_PATH, retrieveThings, true), THINGS_ASK_TIMEOUT))
                .flatMapConcat(response -> {
                    LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                    log.debug("Thing search returned: {}", response);
                    thingsSegment.finish();

                    if (response instanceof ThingCommandResponse) {
                        final ThingCommandResponse tcr = (ThingCommandResponse) response;
                        if (RetrieveThingsResponse.TYPE.equals(tcr.getType())) {
                            final JsonArray items = ((RetrieveThingsResponse) tcr).getEntity().asArray();
                            final SearchResult searchResult =
                                    SearchModelFactory.newSearchResult(items, thingIds.nextPageOffset());
                            return Source.single(QueryThingsResponse.of(searchResult, dittoHeaders));
                        } else if (ThingErrorResponse.TYPE.equals(tcr.getType())) {
                            final ThingErrorResponse error = (ThingErrorResponse) tcr;
                            return Source.failed(error.getDittoRuntimeException());
                        }
                    }
                    log.warning("Retrieved a response from the things service which was not expected: {}", response);
                    return Source.single(
                            QueryThingsResponse.of(SearchModelFactory.emptySearchResult(), dittoHeaders));
                });
    }

}
