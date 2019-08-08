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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoRetrieveNamespaceReport;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.persistence.query.QueryParser;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
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
 * Commands are parsed into {@link Query} objects.
 * <p>
 * Query executes against the passed {@link ThingsSearchPersistence}.
 * <p>
 * The ThingsSearchPersistence returns only Thing IDs. Thus to provide complete Thing information to the requester,
 * things have to be retrieved from Things Service via distributed pub/sub.
 */
public final class SearchActor extends AbstractActor {

    /**
     * The name of this actor in the system.
     */
    static final String ACTOR_NAME = "thingsSearch";

    private static final String SEARCH_DISPATCHER_ID = "search-dispatcher";

    private static final String TRACING_THINGS_SEARCH = "things_search_query";
    private static final String QUERY_PARSING_SEGMENT_NAME = "query_parsing";
    private static final String DATABASE_ACCESS_SEGMENT_NAME = "database_access";
    private static final String QUERY_TYPE_TAG = "query_type";
    private static final String API_VERSION_TAG = "api_version";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final QueryParser queryParser;
    private final ThingsSearchPersistence searchPersistence;
    private final ActorMaterializer materializer;
    private final ExecutionContextExecutor dispatcher;

    @SuppressWarnings("unused")
    private SearchActor(
            final QueryParser queryParser,
            final ThingsSearchPersistence searchPersistence) {

        this.queryParser = queryParser;
        this.searchPersistence = searchPersistence;
        materializer = ActorMaterializer.create(getContext().system());

        dispatcher = getContext().system().dispatchers().lookup(SEARCH_DISPATCHER_ID);
    }

    /**
     * Creates Akka configuration object Props for this SearchActor.
     *
     * @param queryFactory factory of query objects.
     * @param searchPersistence the {@link ThingsSearchPersistence} to use in order to execute queries.
     * @return the Akka configuration Props object.
     */
    static Props props(
            final QueryParser queryFactory,
            final ThingsSearchPersistence searchPersistence) {

        return Props.create(SearchActor.class, queryFactory, searchPersistence);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CountThings.class, this::count)
                .match(SudoCountThings.class, this::sudoCount)
                .match(QueryThings.class, this::query)
                .match(SudoRetrieveNamespaceReport.class, this::namespaceReport)
                .matchAny(any -> log.warning("Got unknown message '{}'", any))
                .build();
    }

    private void namespaceReport(final SudoRetrieveNamespaceReport namespaceReport) {
        final DittoHeaders dittoHeaders = namespaceReport.getDittoHeaders();
        LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
        log.info("Processing SudoRetrieveNamespaceReport command: {}", namespaceReport);

        Patterns.pipe(searchPersistence.generateNamespaceCountReport()
                .runWith(Sink.head(), materializer), dispatcher)
                .to(getSender());
    }

    private void count(final CountThings countThings) {
        executeCount(countThings, queryParser::parse, false);
    }

    private void sudoCount(final SudoCountThings sudoCountThings) {
        executeCount(sudoCountThings, queryParser::parseSudoCountThings, true);
    }

    private <T extends Command> void executeCount(final T countCommand,
            final Function<T, Query> queryParseFunction,
            final boolean isSudo) {
        final DittoHeaders dittoHeaders = countCommand.getDittoHeaders();
        final Optional<String> correlationIdOpt = dittoHeaders.getCorrelationId();
        LogUtil.enhanceLogWithCorrelationId(log, correlationIdOpt);
        log.info("Processing CountThings command: {}", countCommand);
        final JsonSchemaVersion version = countCommand.getImplementedSchemaVersion();

        final String queryType = "count";

        final StartedTimer countTimer = startNewTimer(version, queryType);

        final StartedTimer queryParsingTimer = countTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);

        final ActorRef sender = getSender();

        final Source<Object, ?> replySource = createQuerySource(queryParseFunction, countCommand)
                .flatMapConcat(query -> {
                    LogUtil.enhanceLogWithCorrelationId(log, correlationIdOpt);
                    stopTimer(queryParsingTimer);
                    final StartedTimer databaseAccessTimer =
                            countTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);

                    final Source<Long, NotUsed> countResultSource = isSudo
                            ? searchPersistence.sudoCount(query)
                            : searchPersistence.count(query,
                            countCommand.getDittoHeaders().getAuthorizationSubjects());

                    return processSearchPersistenceResult(countResultSource, dittoHeaders)
                            .via(Flow.fromFunction(result -> {
                                stopTimer(databaseAccessTimer);
                                return result;
                            }))
                            .map(count -> CountThingsResponse.of(count, dittoHeaders));
                })
                .<Object>map(result -> {
                    stopTimer(countTimer);
                    return result;
                })
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<Object, NotUsed>>()
                        .matchAny(error -> {
                            stopTimer(countTimer);
                            return Source.single(asDittoRuntimeException(error, countCommand));
                        })
                        .build());

        Patterns.pipe(replySource.runWith(Sink.head(), materializer), dispatcher).to(sender);
    }

    private void query(final QueryThings queryThings) {
        LogUtil.enhanceLogWithCorrelationId(log, queryThings);
        log.debug("Starting to process QueryThings command: {}", queryThings);
        final JsonSchemaVersion version = queryThings.getImplementedSchemaVersion();

        final String queryType = "query";
        final StartedTimer searchTimer = startNewTimer(version, queryType);
        final StartedTimer queryParsingTimer = searchTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);

        final ActorRef sender = getSender();
        final Set<String> namespaces = queryThings.getNamespaces().orElse(null);

        final Source<Optional<ThingsSearchCursor>, ?> cursorSource =
                ThingsSearchCursor.extractCursor(queryThings, materializer);

        final Source<Object, ?> replySource = cursorSource.flatMapConcat(cursor -> {
            cursor.ifPresent(c -> c.logCursorCorrelationId(log, queryThings));
            final QueryThings command = ThingsSearchCursor.adjust(cursor, queryThings);
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            LogUtil.enhanceLogWithCorrelationId(log, queryThings);
            log.info("Processing QueryThings command: {}", queryThings);
            return createQuerySource(queryParser::parse, command)
                    .flatMapConcat(parsedQuery -> {
                        final Query query =
                                ThingsSearchCursor.adjust(cursor, parsedQuery, queryParser.getCriteriaFactory());

                        stopTimer(queryParsingTimer);
                        final StartedTimer databaseAccessTimer =
                                searchTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);

                        final List<String> subjectIds = command.getDittoHeaders().getAuthorizationSubjects();
                        final Source<ResultList<ThingId>, NotUsed> findAllResult =
                                searchPersistence.findAll(query, subjectIds, namespaces);
                        return processSearchPersistenceResult(findAllResult, dittoHeaders)
                                .via(Flow.fromFunction(result -> {
                                    stopTimer(databaseAccessTimer);
                                    return result;
                                }))
                                .map(ids -> toQueryThingsResponse(command, cursor.orElse(null), ids));
                    })
                    .map(result -> {
                        stopTimer(searchTimer);
                        return result;
                    });
        });

        final Source<Object, ?> replySourceWithErrorHandling =
                replySource.recoverWithRetries(1, new PFBuilder<Throwable, Source<Object, NotUsed>>()
                        .matchAny(error -> {
                            stopTimer(searchTimer);
                            return Source.single(asDittoRuntimeException(error, queryThings));
                        })
                        .build());

        Patterns.pipe(replySourceWithErrorHandling.runWith(Sink.head(), materializer), dispatcher).to(sender);
    }

    private <T> Source<T, NotUsed> processSearchPersistenceResult(Source<T, NotUsed> source,
            final DittoHeaders dittoHeaders) {

        final Flow<T, T, NotUsed> logAndFinishPersistenceSegmentFlow =
                Flow.fromFunction(result -> {
                    // we know that the source provides exactly one ResultList
                    LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                    log.debug("Persistence returned: {}", result);
                    return result;
                });

        return source.via(logAndFinishPersistenceSegmentFlow);
    }

    private DittoRuntimeException asDittoRuntimeException(final Throwable error, final WithDittoHeaders trigger) {
        if (error instanceof DittoRuntimeException) {
            return ((DittoRuntimeException) error).setDittoHeaders(trigger.getDittoHeaders());
        } else {
            log.error(error, "SearchActor failed to execute <{}>", trigger);
            return GatewayInternalErrorException.newBuilder()
                    .dittoHeaders(trigger.getDittoHeaders())
                    .cause(error)
                    .build();
        }
    }

    private QueryThingsResponse toQueryThingsResponse(final QueryThings queryThings,
            @Nullable ThingsSearchCursor cursor,
            final ResultList<ThingId> thingIds) {

        final DittoHeaders dittoHeaders = queryThings.getDittoHeaders();
        final Optional<String> correlationIdOpt = dittoHeaders.getCorrelationId();
        LogUtil.enhanceLogWithCorrelationId(log, correlationIdOpt);
        if (thingIds.isEmpty()) {
            return QueryThingsResponse.of(SearchModelFactory.emptySearchResult(), dittoHeaders);
        } else {
            // only respond with the determined "thingIds", the lookup of the things is done in gateway:
            final JsonArray items = thingIds.stream()
                    .map(JsonValue::of)
                    .map(jsonStr -> JsonObject.newBuilder()
                            .set(Thing.JsonFields.ID.getPointer(), jsonStr)
                            .build()
                    )
                    .collect(JsonCollectors.valuesToArray());
            final SearchResult searchResults = SearchModelFactory.newSearchResult(items, thingIds.nextPageOffset());
            final SearchResult processedResults =
                    ThingsSearchCursor.processSearchResult(queryThings, cursor, searchResults, thingIds);

            return QueryThingsResponse.of(processedResults, dittoHeaders);
        }
    }

    private static StartedTimer startNewTimer(final JsonSchemaVersion version, final String queryType) {
        return DittoMetrics.expiringTimer(TRACING_THINGS_SEARCH)
                .tag(QUERY_TYPE_TAG, queryType)
                .tag(API_VERSION_TAG, version.toString())
                .build();
    }

    private static <T> Source<Query, NotUsed> createQuerySource(final Function<T, Query> parser,
            final T command) {

        try {
            return Source.single(parser.apply(command));
        } catch (final Throwable e) {
            return Source.failed(e);
        }
    }

    private static void stopTimer(final StartedTimer timer) {
        try {
            timer.stop();
        } catch (final IllegalStateException e) {
            // it is okay if the timer was stopped.
        }
    }

}
