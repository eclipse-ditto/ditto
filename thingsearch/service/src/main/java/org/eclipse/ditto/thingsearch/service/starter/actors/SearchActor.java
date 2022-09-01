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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.enforcement.pre.PreEnforcerProvider;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;
import org.eclipse.ditto.thingsearch.api.commands.sudo.StreamThings;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoCountThings;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoRetrieveNamespaceReport;
import org.eclipse.ditto.thingsearch.model.SearchModelFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThingsResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThingsResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.common.model.TimestampedThingId;
import org.eclipse.ditto.thingsearch.service.persistence.query.QueryParser;
import org.eclipse.ditto.thingsearch.service.persistence.read.ThingsSearchPersistence;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.Graph;
import akka.stream.SourceRef;
import akka.stream.SourceShape;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;

/**
 * Actor handling all supported {@link ThingSearchCommand}s. Currently, those are {@link CountThings} and {@link
 * QueryThings}.
 * <p>
 * Passes the commands to the appropriate query actor which is determined by the API version of each received command
 * (see {@link DittoHeaders#getSchemaVersion()}).
 * <p>
 * Commands are parsed into {@link Query} objects.
 * <p>
 * Query executes against the passed {@link ThingsSearchPersistence}.
 * <p>
 * The ThingsSearchPersistence returns only Thing IDs. Thus, to provide complete Thing information to the requester,
 * things have to be retrieved from Things Service via distributed pub/sub.
 */
public final class SearchActor extends AbstractActor {

    /**
     * The name of this actor in the system.
     */
    static final String ACTOR_NAME = ThingsSearchConstants.SEARCH_ACTOR_NAME;

    private static final String SEARCH_DISPATCHER_ID = "search-dispatcher";

    private static final String TRACING_THINGS_SEARCH = "things_wildcard_search_query";
    private static final String QUERY_PARSING_SEGMENT_NAME = "query_parsing";
    private static final String DATABASE_ACCESS_SEGMENT_NAME = "database_access";
    private static final String QUERY_TYPE_TAG = "query_type";
    private static final String API_VERSION_TAG = "api_version";

    private static final Map<String, ThreadSafeDittoLogger> NAMESPACE_INSPECTION_LOGGERS = new HashMap<>();

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final QueryParser queryParser;
    private final ThingsSearchPersistence searchPersistence;
    private final PreEnforcerProvider preEnforcer;
    private final SignalTransformer signalTransformer;

    @SuppressWarnings("unused")
    private SearchActor(final QueryParser queryParser,
            final ThingsSearchPersistence searchPersistence) {

        this.queryParser = queryParser;
        this.searchPersistence = searchPersistence;
        final var system = getContext().getSystem();
        final Config config = system.settings().config();
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(config);
        preEnforcer = PreEnforcerProvider.get(system, dittoExtensionsConfig);
        signalTransformer = SignalTransformers.get(system, dittoExtensionsConfig);
        final var dittoScopedConfig = DefaultScopedConfig.dittoScoped(getSystem().settings().config());

        final EnforcementConfig enforcementConfig = DefaultEnforcementConfig.of(dittoScopedConfig);
        enforcementConfig.getSpecialLoggingInspectedNamespaces()
                .forEach(loggedNamespace -> NAMESPACE_INSPECTION_LOGGERS.put(
                        loggedNamespace,
                        DittoLoggerFactory.getThreadSafeLogger(SearchActor.class.getName() +
                                ".namespace." + loggedNamespace)));
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

        Patterns.pipe(
                searchPersistence.generateNamespaceCountReport().runWith(Sink.head(),
                        SystemMaterializer.get(getSystem()).materializer()),
                getContext().dispatcher()
        ).to(getSender());
    }

    private CompletionStage<Signal<?>> applySignalTransformation(final Signal<?> signal, final ActorRef sender) {
        return signalTransformer.apply(signal)
                .whenComplete((transformed, error) -> {
                    if (error != null) {
                        final var dre = DittoRuntimeException.asDittoRuntimeException(error,
                                reason -> DittoInternalErrorException.newBuilder()
                                        .dittoHeaders(signal.getDittoHeaders())
                                        .cause(reason)
                                        .build());
                        sender.tell(dre, ActorRef.noSender());
                    }
                });
    }

    private void count(final CountThings countThings) {
        final var sender = getSender();
        performLogging(countThings);

        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(countThings);
        l.info("Processing CountThings command with namespaces <{}> and filter: <{}>",
                countThings.getNamespaces(), countThings.getFilter());
        l.debug("Processing CountThings command: <{}>", countThings);
        applySignalTransformation(countThings, sender)
                .thenCompose(preEnforcer::apply)
                .thenAccept(signal -> executeCount((CountThings) signal, queryParser::parse, false, sender));
    }

    private void sudoCount(final SudoCountThings sudoCountThings) {
        final var sender = getSender();
        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(sudoCountThings);
        l.info("Processing SudoCountThings command with filter: <{}>", sudoCountThings.getFilter());
        l.debug("Processing SudoCountThings command: <{}>", sudoCountThings);
        applySignalTransformation(sudoCountThings, sender)
                .thenAccept(signal -> executeCount((SudoCountThings) signal, queryParser::parseSudoCountThings, true,
                        sender));
    }

    private <T extends Command<?>> void executeCount(final T countCommand,
            final Function<T, CompletionStage<Query>> queryParseFunction,
            final boolean isSudo,
            final ActorRef sender) {

        final var dittoHeaders = countCommand.getDittoHeaders();
        final JsonSchemaVersion version = countCommand.getImplementedSchemaVersion();
        final var queryType = "count";
        final StartedTimer countTimer = startNewTimer(version, queryType, countCommand);
        final StartedTimer queryParsingTimer = countTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);

        final Source<CountThingsResponse, ?> countThingsResponseSource =
                createQuerySource(queryParseFunction, countCommand)
                        .flatMapConcat(query -> {
                            stopTimer(queryParsingTimer);
                            final StartedTimer databaseAccessTimer =
                                    countTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);

                            final Source<Long, NotUsed> countResultSource = isSudo
                                    ? searchPersistence.sudoCount(query)
                                    : searchPersistence.count(query,
                                    countCommand.getDittoHeaders()
                                            .getAuthorizationContext()
                                            .getAuthorizationSubjectIds());

                            return processSearchPersistenceResult(countResultSource, dittoHeaders)
                                    .via(Flow.fromFunction(result -> {
                                        stopTimer(databaseAccessTimer);
                                        return result;
                                    }))
                                    .map(count -> CountThingsResponse.of(count, dittoHeaders));
                        });

        final Source<Object, ?> replySourceWithErrorHandling =
                countThingsResponseSource.via(stopTimerAndHandleError(countTimer, countCommand));

        Patterns.pipe(
                replySourceWithErrorHandling.runWith(Sink.head(), SystemMaterializer.get(getSystem()).materializer()),
                getContext().dispatcher()
        ).to(sender);
    }

    private void stream(final StreamThings streamThings) {
        final var sender = getSender();
        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(streamThings);
        l.info("Processing StreamThings command: {}", streamThings);
        applySignalTransformation(streamThings, sender)
                .thenCompose(preEnforcer::apply)
                .thenAccept(stream -> performStream((StreamThings) stream, sender, l));
    }

    private void performStream(final StreamThings streamThings, final ActorRef sender,
            final ThreadSafeDittoLoggingAdapter l) {

        final JsonSchemaVersion version = streamThings.getImplementedSchemaVersion();
        final var queryType = "query"; // same as queryThings
        final StartedTimer searchTimer = startNewTimer(version, queryType, streamThings);
        final StartedTimer queryParsingTimer = searchTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);
        final Set<String> namespaces = streamThings.getNamespaces().orElse(null);

        final Source<SourceRef<String>, NotUsed> thingIdSourceRefSource =
                ThingsSearchCursor.extractCursor(streamThings).flatMapConcat(cursor -> {
                    cursor.ifPresent(c -> c.logCursorCorrelationId(l));
                    return createQuerySource(queryParser::parse, streamThings).map(parsedQuery -> {
                        final var query =
                                ThingsSearchCursor.adjust(cursor, parsedQuery, queryParser.getCriteriaFactory());
                        stopTimer(queryParsingTimer);
                        searchTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME); // segment stopped by stopTimerAndHandleError
                        final List<String> subjectIds =
                                streamThings.getDittoHeaders()
                                        .getAuthorizationContext()
                                        .getAuthorizationSubjectIds();
                        return searchPersistence.findAllUnlimited(query, subjectIds, namespaces)
                                .map(ThingId::toString) // for serialization???
                                .runWith(StreamRefs.sourceRef(), SystemMaterializer.get(getSystem()).materializer());
                    });
                });

        final Source<Object, NotUsed> replySourceWithErrorHandling =
                thingIdSourceRefSource.via(stopTimerAndHandleError(searchTimer, streamThings));

        Patterns.pipe(
                replySourceWithErrorHandling.runWith(Sink.head(), SystemMaterializer.get(getSystem()).materializer()),
                getContext().dispatcher()
        ).to(sender);
    }

    private void query(final QueryThings queryThings) {
        final var sender = getSender();
        performLogging(queryThings);

        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(queryThings);
        l.debug("Starting to process QueryThings command: {}", queryThings);
        applySignalTransformation(queryThings, sender)
                .thenCompose(preEnforcer::apply)
                .thenAccept(query -> performQuery((QueryThings) query, sender));
    }

    private void performQuery(final QueryThings queryThings, final ActorRef sender) {
        performLogging(queryThings);

        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(queryThings);
        l.debug("Starting to process QueryThings command: {}", queryThings);

        final JsonSchemaVersion version = queryThings.getImplementedSchemaVersion();
        final var queryType = "query";
        final StartedTimer searchTimer = startNewTimer(version, queryType, queryThings);
        final StartedTimer queryParsingTimer = searchTimer.startNewSegment(QUERY_PARSING_SEGMENT_NAME);
        final Set<String> namespaces = queryThings.getNamespaces().orElse(null);

        final Source<QueryThingsResponse, ?> queryThingsResponseSource =
                ThingsSearchCursor.extractCursor(queryThings, getSystem()).flatMapConcat(cursor -> {
                    cursor.ifPresent(c -> c.logCursorCorrelationId(l));
                    final QueryThings command = ThingsSearchCursor.adjust(cursor, queryThings);
                    final var dittoHeaders = command.getDittoHeaders();
                    l.info("Processing QueryThings command with namespaces <{}> and filter: <{}>",
                            queryThings.getNamespaces(), queryThings.getFilter());
                    l.debug("Processing QueryThings command: <{}>", queryThings);
                    return createQuerySource(queryParser::parse, command)
                            .flatMapConcat(parsedQuery -> {
                                final var query =
                                        ThingsSearchCursor.adjust(cursor, parsedQuery,
                                                queryParser.getCriteriaFactory());

                                stopTimer(queryParsingTimer);
                                final StartedTimer databaseAccessTimer =
                                        searchTimer.startNewSegment(DATABASE_ACCESS_SEGMENT_NAME);

                                final List<String> subjectIds =
                                        command.getDittoHeaders()
                                                .getAuthorizationContext()
                                                .getAuthorizationSubjectIds();
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
                queryThingsResponseSource.via(stopTimerAndHandleError(searchTimer, queryThings));

        Patterns.pipe(
                replySourceWithErrorHandling.runWith(Sink.head(), SystemMaterializer.get(getSystem()).materializer()),
                getContext().dispatcher()
        ).to(sender);
    }

    private void performLogging(final ThingSearchQueryCommand<?> thingSearchQueryCommand) {
        final Set<String> namespaces = thingSearchQueryCommand.getNamespaces().orElseGet(Set::of);
        NAMESPACE_INSPECTION_LOGGERS.entrySet().stream()
                .filter(entry -> namespaces.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .forEach(l -> {
                    final String filter = thingSearchQueryCommand.getFilter().orElse(null);
                    l.withCorrelationId(thingSearchQueryCommand).info(
                            "Forwarding search query command type <{}> with filter <{}> and " +
                                    "fields <{}>",
                            thingSearchQueryCommand.getType(),
                            filter,
                            thingSearchQueryCommand.getSelectedFields().orElse(null));
                });
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
            final var searchResults = SearchModelFactory.newSearchResult(items);
            final var processedResults =
                    ThingsSearchCursor.processSearchResult(queryThings, cursor, searchResults, thingIds);

            return QueryThingsResponse.of(processedResults, dittoHeaders);
        }
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
            return Source.completionStage(parser.apply(command))
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
