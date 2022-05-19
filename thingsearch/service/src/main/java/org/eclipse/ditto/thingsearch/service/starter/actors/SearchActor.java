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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoCountThings;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoRetrieveNamespaceReport;
import org.eclipse.ditto.thingsearch.model.SearchModelFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThingsResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThingsResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.StreamThings;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.common.model.TimestampedThingId;
import org.eclipse.ditto.thingsearch.service.persistence.query.QueryParser;
import org.eclipse.ditto.thingsearch.service.persistence.read.ThingsSearchPersistence;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.Graph;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.SourceShape;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;

/**
 * Actor handling all supported {@link ThingSearchCommand}s. Currently those are {@link CountThings} and {@link
 * QueryThings}.
 * <p>
 * Passes the commands to the appropriate query actor which is determined by the API version of each received command
 * (see {@link DittoHeaders#getSchemaVersion()}).
 * <p>
 * Commands are parsed into {@link Query} objects.
 * <p>
 * Query executes against the passed {@link org.eclipse.ditto.thingsearch.service.persistence.read.ThingsSearchPersistence}.
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

    private static final String TRACING_THINGS_SEARCH = "things_wildcard_search_query";
    private static final String QUERY_PARSING_SEGMENT_NAME = "query_parsing";
    private static final String DATABASE_ACCESS_SEGMENT_NAME = "database_access";
    private static final String QUERY_TYPE_TAG = "query_type";
    private static final String API_VERSION_TAG = "api_version";

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final QueryParser queryParser;
    private final ThingsSearchPersistence searchPersistence;

    @SuppressWarnings("unused")
    private SearchActor(
            final QueryParser queryParser,
            final ThingsSearchPersistence searchPersistence) {

        this.queryParser = queryParser;
        this.searchPersistence = searchPersistence;
    }

    /**
     * Creates Akka configuration object Props for this SearchActor.
     *
     * @param queryFactory factory of query objects.
     * @param searchPersistence the {@link org.eclipse.ditto.thingsearch.service.persistence.read.ThingsSearchPersistence} to use in order to execute queries.
     * @return the Akka configuration Props object.
     */
    static Props props(
            final QueryParser queryFactory,
            final ThingsSearchPersistence searchPersistence) {

        return Props.create(SearchActor.class, queryFactory, searchPersistence)
                .withDispatcher(SEARCH_DISPATCHER_ID);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CountThings.class, this::count)
                .match(SudoCountThings.class, this::sudoCount)
                .match(QueryThings.class, this::query)
                .match(SudoRetrieveNamespaceReport.class, this::namespaceReport)
                .match(StreamThings.class, this::stream)
                .matchAny(any -> log.warning("Got unknown message '{}'", any))
                .build();
    }

    private void namespaceReport(final SudoRetrieveNamespaceReport namespaceReport) {
        final var dittoHeaders = namespaceReport.getDittoHeaders();
        log.withCorrelationId(dittoHeaders)
                .info("Processing SudoRetrieveNamespaceReport command: {}", namespaceReport);

        Patterns.pipe(searchPersistence.generateNamespaceCountReport()
                        .runWith(Sink.head(), SystemMaterializer.get(getSystem()).materializer()), getContext().dispatcher())
                .to(getSender());
    }

    private void count(final CountThings countThings) {
        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(countThings);
        l.info("Processing CountThings command with namespaces <{}> and filter: <{}>",
                countThings.getNamespaces(), countThings.getFilter());
        l.debug("Processing CountThings command: <{}>", countThings);
        executeCount(countThings, queryParser::parse, false);
    }

    private void sudoCount(final SudoCountThings sudoCountThings) {
        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(sudoCountThings);
        l.info("Processing SudoCountThings command with filter: <{}>", sudoCountThings.getFilter());
        l.debug("Processing SudoCountThings command: <{}>", sudoCountThings);
        executeCount(sudoCountThings, queryParser::parseSudoCountThings, true);
    }

    private <T extends Command<?>> void executeCount(final T countCommand,
            final Function<T, CompletionStage<Query>> queryParseFunction,
            final boolean isSudo) {
        final var dittoHeaders = countCommand.getDittoHeaders();
        final JsonSchemaVersion version = countCommand.getImplementedSchemaVersion();
        final var queryType = "count";
        final StartedTimer countTimer = startNewTimer(version, queryType, countCommand);
        final StartedTimer queryParsingTimer = countTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);
        final ActorRef sender = getSender();

        final Source<Object, ?> replySource = createQuerySource(queryParseFunction, countCommand)
                .flatMapConcat(query -> {
                    stopTimer(queryParsingTimer);
                    final StartedTimer databaseAccessTimer =
                            countTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);

                    final Source<Long, NotUsed> countResultSource = isSudo
                            ? searchPersistence.sudoCount(query)
                            : searchPersistence.count(query,
                            countCommand.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds());

                    return processSearchPersistenceResult(countResultSource, dittoHeaders)
                            .via(Flow.fromFunction(result -> {
                                stopTimer(databaseAccessTimer);
                                return result;
                            }))
                            .map(count -> CountThingsResponse.of(count, dittoHeaders));
                })
                .via(stopTimerAndHandleError(countTimer, countCommand));

        Materializer.createMaterializer(this::getContext);
        Patterns.pipe(replySource.runWith(Sink.head(), SystemMaterializer.get(getSystem()).materializer()),
                getContext().dispatcher()).to(sender);
    }

    private void stream(final StreamThings streamThings) {
        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(streamThings);
        l.info("Processing StreamThings command: {}", streamThings);

        final JsonSchemaVersion version = streamThings.getImplementedSchemaVersion();
        final var queryType = "query"; // same as queryThings
        final StartedTimer searchTimer = startNewTimer(version, queryType, streamThings);
        final StartedTimer queryParsingTimer = searchTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);
        final ActorRef sender = getSender();
        final Set<String> namespaces = streamThings.getNamespaces().orElse(null);

        final Source<Optional<ThingsSearchCursor>, NotUsed> cursorSource =
                ThingsSearchCursor.extractCursor(streamThings);
        final Source<SourceRef<String>, NotUsed> sourceRefSource = cursorSource.flatMapConcat(cursor -> {
            cursor.ifPresent(c -> c.logCursorCorrelationId(l));
            return createQuerySource(queryParser::parse, streamThings).map(parsedQuery -> {
                final var query = ThingsSearchCursor.adjust(cursor, parsedQuery, queryParser.getCriteriaFactory());
                stopTimer(queryParsingTimer);
                searchTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME); // segment stopped by stopTimerAndHandleError
                final List<String> subjectIds =
                        streamThings.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds();
                return searchPersistence.findAllUnlimited(query, subjectIds, namespaces)
                        .map(ThingId::toString) // for serialization???
                        .runWith(StreamRefs.sourceRef(), SystemMaterializer.get(getSystem()).materializer());
            });
        });
        final Source<Object, NotUsed> replySourceWithErrorHandling =
                sourceRefSource.via(stopTimerAndHandleError(searchTimer, streamThings));

        Patterns.pipe(
                        replySourceWithErrorHandling.runWith(Sink.head(), SystemMaterializer.get(getSystem()).materializer()),
                        getContext().dispatcher())
                .to(sender);
    }

    private void query(final QueryThings queryThings) {
        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(queryThings);
        l.debug("Starting to process QueryThings command: {}", queryThings);

        final JsonSchemaVersion version = queryThings.getImplementedSchemaVersion();
        final var queryType = "query";
        final StartedTimer searchTimer = startNewTimer(version, queryType, queryThings);
        final StartedTimer queryParsingTimer = searchTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);
        final ActorRef sender = getSender();
        final Set<String> namespaces = queryThings.getNamespaces().orElse(null);

        final Source<Optional<ThingsSearchCursor>, ?> cursorSource =
                ThingsSearchCursor.extractCursor(queryThings, getSystem());

        final Source<Object, ?> replySource = cursorSource.flatMapConcat(cursor -> {
            cursor.ifPresent(c -> c.logCursorCorrelationId(l));
            final QueryThings command = ThingsSearchCursor.adjust(cursor, queryThings);
            final var dittoHeaders = command.getDittoHeaders();
            l.info("Processing QueryThings command with namespaces <{}> and filter: <{}>",
                    queryThings.getNamespaces(), queryThings.getFilter());
            l.debug("Processing QueryThings command: <{}>", queryThings);
            return createQuerySource(queryParser::parse, command)
                    .flatMapConcat(parsedQuery -> {
                        final var query =
                                ThingsSearchCursor.adjust(cursor, parsedQuery, queryParser.getCriteriaFactory());

                        stopTimer(queryParsingTimer);
                        final StartedTimer databaseAccessTimer =
                                searchTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);

                        final List<String> subjectIds =
                                command.getDittoHeaders().getAuthorizationContext().getAuthorizationSubjectIds();
                        final Source<ResultList<TimestampedThingId>, NotUsed> findAllResult =
                                searchPersistence.findAll(query, subjectIds, namespaces);
                        return processSearchPersistenceResult(findAllResult, dittoHeaders)
                                .via(Flow.fromFunction(result -> {
                                    stopTimer(databaseAccessTimer);
                                    return result;
                                }))
                                .map(ids -> toQueryThingsResponse(command, cursor.orElse(null), ids));
                    });
        });

        final Source<Object, ?> replySourceWithErrorHandling =
                replySource.via(stopTimerAndHandleError(searchTimer, queryThings));

        Patterns.pipe(
                        replySourceWithErrorHandling.runWith(Sink.head(), SystemMaterializer.get(getSystem()).materializer()),
                        getContext().dispatcher())
                .to(sender);
    }

    private ActorSystem getSystem() {
        return getContext().getSystem();
    }

    private <T> Flow<T, Object, NotUsed> stopTimerAndHandleError(final StartedTimer searchTimer,
            final WithDittoHeaders command) {
        return Flow.<T, Object>fromFunction(
                        element -> {
                            stopTimer(searchTimer);
                            return element;
                        })
                .recoverWithRetries(1, new PFBuilder<Throwable, Graph<SourceShape<Object>, NotUsed>>()
                        .matchAny(error -> {
                            stopTimer(searchTimer);
                            return Source.single(asDittoRuntimeException(error, command));
                        })
                        .build()
                );
    }

    private <T> Source<T, NotUsed> processSearchPersistenceResult(Source<T, NotUsed> source,
            final DittoHeaders dittoHeaders) {

        final Flow<T, T, NotUsed> logAndFinishPersistenceSegmentFlow =
                Flow.fromFunction(result -> {
                    // we know that the source provides exactly one ResultList
                    log.withCorrelationId(dittoHeaders)
                            .debug("Persistence returned: {}", result);
                    return result;
                });

        return source.via(logAndFinishPersistenceSegmentFlow);
    }

    private DittoRuntimeException asDittoRuntimeException(final Throwable error, final WithDittoHeaders trigger) {
        return DittoRuntimeException.asDittoRuntimeException(error, t -> {
            log.error(error, "SearchActor failed to execute <{}>", trigger);
            return DittoInternalErrorException.newBuilder()
                    .dittoHeaders(trigger.getDittoHeaders())
                    .message(error.getClass() + ": " + error.getMessage())
                    .cause(t)
                    .build();
        });
    }

    private QueryThingsResponse toQueryThingsResponse(final QueryThings queryThings,
            @Nullable ThingsSearchCursor cursor,
            final ResultList<TimestampedThingId> thingIds) {

        final var dittoHeaders = queryThings.getDittoHeaders();
        if (thingIds.isEmpty()) {
            return QueryThingsResponse.of(SearchModelFactory.emptySearchResult(), dittoHeaders);
        } else {
            // only respond with the determined "thingIds", the lookup of the things is done in gateway:
            final JsonArray items = getItems(thingIds);
            final Instant lastModified = getLastModified(thingIds);
            final var searchResults =
                    SearchModelFactory.newSearchResult(items, thingIds.nextPageOffset(), lastModified);
            final var processedResults =
                    ThingsSearchCursor.processSearchResult(queryThings, cursor, searchResults, thingIds);

            return QueryThingsResponse.of(processedResults, dittoHeaders);
        }
    }

    private static Instant getLastModified(final ResultList<TimestampedThingId> thingIds) {
        final var now = Instant.now();
        return thingIds.stream()
                .map(timestampedThingId -> timestampedThingId.lastModified().orElse(now))
                .max(Comparator.naturalOrder())
                .orElse(now);
    }

    private static JsonArray getItems(final ResultList<TimestampedThingId> thingIds) {
        return thingIds.stream()
                .map(TimestampedThingId::thingId)
                .map(JsonValue::of)
                .map(jsonStr -> JsonObject.newBuilder()
                        .set(Thing.JsonFields.ID.getPointer(), jsonStr)
                        .build()
                )
                .collect(JsonCollectors.valuesToArray());
    }

    private static StartedTimer startNewTimer(final JsonSchemaVersion version, final String queryType,
            final WithDittoHeaders withDittoHeaders) {
        final StartedTimer startedTimer = DittoMetrics.timer(TRACING_THINGS_SEARCH)
                .tag(QUERY_TYPE_TAG, queryType)
                .tag(API_VERSION_TAG, version.toString())
                .start();
        DittoTracing.wrapTimer(DittoTracing.extractTraceContext(withDittoHeaders), startedTimer);
        return startedTimer;
    }

    private static <T> Source<Query, NotUsed> createQuerySource(final Function<T, CompletionStage<Query>> parser,
            final T command) {

        try {
            return Source.fromCompletionStage(parser.apply(command))
                    .recoverWithRetries(1, new PFBuilder<Throwable, Source<Query, NotUsed>>()
                            .match(CompletionException.class, e -> Source.failed(e.getCause()))
                            .build());
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
