/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.sse;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.commands.streaming.SubscribeForPersistedEvents;
import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.things.ThingsParameter;
import org.eclipse.ditto.gateway.service.endpoints.utils.GatewaySignalEnrichmentProvider;
import org.eclipse.ditto.gateway.service.streaming.StreamingAuthorizationEnforcer;
import org.eclipse.ditto.gateway.service.streaming.actors.SessionedJsonifiable;
import org.eclipse.ditto.gateway.service.streaming.actors.StreamingSession;
import org.eclipse.ditto.gateway.service.streaming.actors.SupervisedStream;
import org.eclipse.ditto.gateway.service.streaming.signals.Connect;
import org.eclipse.ditto.gateway.service.streaming.signals.StartStreaming;
import org.eclipse.ditto.gateway.service.util.config.streaming.StreamingConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.search.SearchSource;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.protocol.placeholders.ResourcePlaceholder;
import org.eclipse.ditto.protocol.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.http.javadsl.marshalling.sse.EventStreamMarshalling;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.directives.RouteDirectives;
import akka.japi.pf.PFBuilder;
import akka.pattern.Patterns;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * Builder for creating Akka HTTP routes for SSE (Server Sent Events) {@code /things} and {@code /search} routes.
 */
@NotThreadSafe
public final class ThingsSseRouteBuilder extends RouteDirectives implements SseRouteBuilder {

    private static final String PATH_SEARCH = "search";
    private static final String PATH_THINGS = "things";

    private static final Pattern INBOX_OUTBOX_PATTERN = Pattern.compile(
            "(/features/[^/]+)?/(inbox|outbox)/messages(/.*)?");

    private static final Pattern INBOX_OUTBOX_WITH_SUBJECT_PATTERN = Pattern.compile(
            "(/features/[^/]+)?/(inbox|outbox)/messages/.+");

    private static final String STREAMING_TYPE_SSE = "SSE";
    private static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";

    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_FIELDS = ThingsParameter.FIELDS.toString();
    private static final String PARAM_OPTION = "option";
    private static final String PARAM_NAMESPACES = "namespaces";
    private static final String PARAM_EXTRA_FIELDS = "extraFields";

    private static final String PARAM_FROM_HISTORICAL_REVISION = "from-historical-revision";
    private static final String PARAM_TO_HISTORICAL_REVISION = "to-historical-revision";
    private static final String PARAM_FROM_HISTORICAL_TIMESTAMP = "from-historical-timestamp";
    private static final String PARAM_TO_HISTORICAL_TIMESTAMP = "to-historical-timestamp";

    private static final JsonFieldDefinition<JsonObject> CONTEXT =
            JsonFactory.newJsonObjectFieldDefinition("_context");

    private static final PartialFunction<HttpHeader, Accept> ACCEPT_HEADER_EXTRACTOR = newAcceptHeaderExtractor();

    private static final Counter THINGS_SSE_COUNTER = getCounterFor(PATH_THINGS);
    private static final Counter SEARCH_SSE_COUNTER = getCounterFor(PATH_SEARCH);

    /**
     * Timeout asking the local streaming actor.
     */
    private static final Duration LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5L);

    private final ActorRef streamingActor;
    private final StreamingConfig streamingConfig;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;
    private final ActorRef pubSubMediator;
    private SseConnectionSupervisor sseConnectionSupervisor;
    private SseEventSniffer eventSniffer;
    private StreamingAuthorizationEnforcer sseAuthorizationEnforcer;
    @Nullable private GatewaySignalEnrichmentProvider signalEnrichmentProvider;
    @Nullable private ActorRef proxyActor;

    private ThingsSseRouteBuilder(final ActorSystem actorSystem,
            final ActorRef streamingActor,
            final StreamingConfig streamingConfig,
            final QueryFilterCriteriaFactory queryFilterCriteriaFactory,
            final ActorRef pubSubMediator) {

        this.streamingActor = streamingActor;
        this.streamingConfig = streamingConfig;
        this.queryFilterCriteriaFactory = queryFilterCriteriaFactory;
        this.pubSubMediator = pubSubMediator;

        final Config config = actorSystem.settings().config();
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(config);
        eventSniffer = SseEventSniffer.get(actorSystem, dittoExtensionsConfig);
        sseConnectionSupervisor = SseConnectionSupervisor.get(actorSystem, dittoExtensionsConfig);

        final var sseConfig = ScopedConfig.getOrEmpty(config, "ditto.gateway.streaming.sse");
        sseAuthorizationEnforcer = StreamingAuthorizationEnforcer.get(actorSystem, sseConfig);
    }

    /**
     * Returns an instance of this class.
     *
     * @param streamingActor is used for actual event streaming.
     * @param streamingConfig the streaming configuration.
     * @param pubSubMediator akka pub-sub mediator for error reporting by the search source.
     * @return the instance.
     * @throws NullPointerException if {@code streamingActor} is {@code null}.
     */
    public static ThingsSseRouteBuilder getInstance(final ActorSystem actorSystem,
            final ActorRef streamingActor,
            final StreamingConfig streamingConfig,
            final ActorRef pubSubMediator) {

        checkNotNull(streamingActor, "streamingActor");
        final var queryFilterCriteriaFactory =
                QueryFilterCriteriaFactory.modelBased(RqlPredicateParser.getInstance(),
                        TopicPathPlaceholder.getInstance(), ResourcePlaceholder.getInstance(),
                        TimePlaceholder.getInstance());

        return new ThingsSseRouteBuilder(actorSystem, streamingActor, streamingConfig, queryFilterCriteriaFactory,
                pubSubMediator);
    }

    @Override
    public SseRouteBuilder withAuthorizationEnforcer(final StreamingAuthorizationEnforcer enforcer) {
        sseAuthorizationEnforcer = checkNotNull(enforcer, "enforcer");
        return this;
    }

    @Override
    public ThingsSseRouteBuilder withEventSniffer(final SseEventSniffer eventSniffer) {
        this.eventSniffer = checkNotNull(eventSniffer, "eventSniffer");
        return this;
    }

    @Override
    public SseRouteBuilder withSseConnectionSupervisor(final SseConnectionSupervisor sseConnectionSupervisor) {
        this.sseConnectionSupervisor = checkNotNull(sseConnectionSupervisor, "sseConnectionSupervisor");
        return this;
    }

    @Override
    public SseRouteBuilder withSignalEnrichmentProvider(@Nullable final GatewaySignalEnrichmentProvider provider) {
        signalEnrichmentProvider = provider;
        return this;
    }

    @Override
    public SseRouteBuilder withProxyActor(@Nullable final ActorRef proxyActor) {
        this.proxyActor = proxyActor;
        return this;
    }

    /**
     * Describes {@code /things} SSE route.
     *
     * @return {@code /things} SSE route.
     */
    @Override
    public Route build(final RequestContext ctx, final Supplier<CompletionStage<DittoHeaders>> dittoHeadersSupplier) {
        return headerValuePF(ACCEPT_HEADER_EXTRACTOR, accept -> get(() ->
                concat(
                        // /things
                        buildThingsSseRoute(ctx, dittoHeadersSupplier),
                        // /search/things
                        buildSearchSseRoute(ctx, dittoHeadersSupplier)
                )
        ));
    }

    private Route buildThingsSseRoute(final RequestContext ctx,
            final Supplier<CompletionStage<DittoHeaders>> dittoHeadersSupplier) {

        // /things
        return rawPathPrefix(PathMatchers.slash().concat(PATH_THINGS), () -> {
            final CompletionStage<DittoHeaders> dhcs = dittoHeadersSupplier.get()
                    .thenApply(ThingsSseRouteBuilder::getDittoHeadersWithCorrelationId);

            return concat(
                    pathEndOrSingleSlash(() ->
                            // /things
                            buildThingsSseRouteForAllThings(ctx, dhcs)
                    ),
                    // /things/<thingId>
                    rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), thingId ->
                            parameterMap(parameters -> {
                                final HashMap<String, String> params = new HashMap<>(parameters);
                                params.put(ThingsParameter.IDS.toString(), thingId);

                                return concat(
                                        // /things/<thingId>
                                        pathEndOrSingleSlash(() ->
                                                createSseRoute(ctx, dhcs, JsonPointer.empty(), params)
                                        ),
                                        // /things/<thingId>/<jsonPointer>
                                        rawPathPrefix(PathMatchers.slash()
                                                        .concat(PathMatchers.remaining())
                                                        .map(path -> UriEncoding.decode(path,
                                                                UriEncoding.EncodingType.RFC3986))
                                                        .map(path -> "/" + path),
                                                jsonPointerString -> {
                                                    if (INBOX_OUTBOX_PATTERN.matcher(jsonPointerString).matches()) {
                                                        return createMessagesSseRoute(ctx, dhcs, thingId,
                                                                jsonPointerString);
                                                    } else {
                                                        params.put(PARAM_FIELDS, jsonPointerString);
                                                        return createSseRoute(ctx, dhcs,
                                                                JsonPointer.of(jsonPointerString),
                                                                params
                                                        );
                                                    }
                                                }
                                        )
                                );
                            })
                    )
            );
        });
    }

    private Route buildSearchSseRoute(final RequestContext ctx,
            final Supplier<CompletionStage<DittoHeaders>> dittoHeadersSupplier) {

        return rawPathPrefix(PathMatchers.slash().concat(PATH_SEARCH).slash().concat(PATH_THINGS), () ->
                pathEndOrSingleSlash(() -> {
                    final CompletionStage<DittoHeaders> dittoHeaders = dittoHeadersSupplier.get()
                            .thenApply(ThingsSseRouteBuilder::getDittoHeadersWithCorrelationId);
                    return parameterMap(parameters -> createSearchSseRoute(ctx, dittoHeaders, parameters));
                })
        );
    }

    private static DittoHeaders getDittoHeadersWithCorrelationId(final DittoHeaders dittoHeaders) {
        final Optional<String> correlationIdOptional = dittoHeaders.getCorrelationId();
        if (correlationIdOptional.isPresent()) {
            return dittoHeaders;
        }
        return dittoHeaders.toBuilder()
                .correlationId(String.valueOf(UUID.randomUUID()))
                .build();
    }

    private Route buildThingsSseRouteForAllThings(final RequestContext ctx,
            final CompletionStage<DittoHeaders> dittoHeaders) {
        return parameterMap(parameters -> createSseRoute(ctx, dittoHeaders, JsonPointer.empty(), parameters));
    }

    private Route createSseRoute(final RequestContext ctx, final CompletionStage<DittoHeaders> dittoHeadersStage,
            final JsonPointer fieldPointer,
            final Map<String, String> parameters) {

        @Nullable final var filterString = parameters.get(PARAM_FILTER);
        final List<String> namespaces = getNamespaces(parameters.get(PARAM_NAMESPACES));
        final List<ThingId> targetThingIds = getThingIds(parameters.get(ThingsParameter.IDS.toString()));
        @Nullable final ThingFieldSelector fields = getFieldSelector(parameters.get(PARAM_FIELDS));
        @Nullable final ThingFieldSelector extraFields = getFieldSelector(parameters.get(PARAM_EXTRA_FIELDS));

        @Nullable final Long fromHistoricalRevision = Optional.ofNullable(
                parameters.get(PARAM_FROM_HISTORICAL_REVISION))
                .map(Long::parseLong)
                .orElse(null);
        @Nullable final Long toHistoricalRevision = Optional.ofNullable(
                parameters.get(PARAM_TO_HISTORICAL_REVISION))
                .map(Long::parseLong)
                .orElse(null);

        @Nullable final Instant fromHistoricalTimestamp = Optional.ofNullable(
                parameters.get(PARAM_FROM_HISTORICAL_TIMESTAMP))
                .map(Instant::parse)
                .orElse(null);
        @Nullable final Instant toHistoricalTimestamp = Optional.ofNullable(
                parameters.get(PARAM_TO_HISTORICAL_TIMESTAMP))
                .map(Instant::parse)
                .orElse(null);

        final CompletionStage<SignalEnrichmentFacade> facadeStage = signalEnrichmentProvider == null
                ? CompletableFuture.completedStage(null)
                : signalEnrichmentProvider.getFacade(ctx.getRequest());


        final var sseSourceStage = facadeStage.thenCompose(facade -> dittoHeadersStage.thenCompose(
                dittoHeaders -> sseAuthorizationEnforcer.checkAuthorization(ctx, dittoHeaders).thenApply(unused -> {
                    if (filterString != null) {
                        // will throw an InvalidRqlExpressionException if the RQL expression was not valid:
                        queryFilterCriteriaFactory.filterCriteria(filterString, dittoHeaders);
                    }

                    final String connectionCorrelationId = dittoHeaders.getCorrelationId()
                            .orElseThrow(() -> new IllegalStateException(
                                    "Expected correlation-id in SSE DittoHeaders: " + dittoHeaders));
                    final var authorizationContext = dittoHeaders.getAuthorizationContext();
                    final Object startStreaming;
                    if (null != fromHistoricalRevision) {
                        FeatureToggle
                                .checkHistoricalApiAccessFeatureEnabled(SubscribeForPersistedEvents.TYPE, dittoHeaders);
                        startStreaming = SubscribeForPersistedEvents.of(targetThingIds.get(0),
                                fieldPointer,
                                fromHistoricalRevision,
                                null != toHistoricalRevision ? toHistoricalRevision : Long.MAX_VALUE,
                                dittoHeaders);
                    } else if (null != fromHistoricalTimestamp) {
                        FeatureToggle
                                .checkHistoricalApiAccessFeatureEnabled(SubscribeForPersistedEvents.TYPE, dittoHeaders);
                        startStreaming = SubscribeForPersistedEvents.of(targetThingIds.get(0),
                                fieldPointer,
                                fromHistoricalTimestamp,
                                toHistoricalTimestamp,
                                dittoHeaders);
                    } else {
                        startStreaming =
                                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                                                authorizationContext)
                                        .withNamespaces(namespaces)
                                        .withFilter(filterString)
                                        .withExtraFields(extraFields)
                                        .build();
                    }

                    final Source<SessionedJsonifiable, SupervisedStream.WithQueue> publisherSource =
                            SupervisedStream.sourceQueue(10);

                    return publisherSource.viaMat(KillSwitches.single(), Keep.both())
                            .mapMaterializedValue(pair -> {
                                final SupervisedStream.WithQueue withQueue = pair.first();
                                final KillSwitch killSwitch = pair.second();

                                final var jsonSchemaVersion = dittoHeaders.getSchemaVersion()
                                        .orElse(JsonSchemaVersion.LATEST);
                                sseConnectionSupervisor.supervise(withQueue.getSupervisedStream(),
                                        connectionCorrelationId, dittoHeaders);
                                final var connect = new Connect(withQueue.getSourceQueue(), connectionCorrelationId,
                                        STREAMING_TYPE_SSE, jsonSchemaVersion, null, Set.of(),
                                        authorizationContext, null);
                                Patterns.ask(streamingActor, connect, LOCAL_ASK_TIMEOUT)
                                        .thenApply(ActorRef.class::cast)
                                        .thenAccept(streamingSessionActor ->
                                                streamingSessionActor.tell(startStreaming, ActorRef.noSender()))
                                        .exceptionally(e -> {
                                            killSwitch.abort(e);
                                            return null;
                                        });
                                return NotUsed.getInstance();
                            })
                            .mapAsync(streamingConfig.getParallelism(), jsonifiable ->
                                    postprocess(jsonifiable, facade, targetThingIds, namespaces, fieldPointer, fields))
                            .mapConcat(jsonValues -> jsonValues)
                            .map(jsonValue -> {
                                THINGS_SSE_COUNTER.increment();
                                return ServerSentEvent.create(jsonValue.toString());
                            })
                            .log("SSE " + PATH_THINGS)
                            // sniffer shouldn't sniff heartbeats
                            .viaMat(eventSniffer.toAsyncFlow(ctx.getRequest()), Keep.none())
                            .keepAlive(Duration.ofSeconds(1), ServerSentEvent::heartbeat);
                })
        ));

        return completeOKWithFuture(sseSourceStage, EventStreamMarshalling.toEventStream());
    }

    private Route createMessagesSseRoute(final RequestContext ctx,
            final CompletionStage<DittoHeaders> dittoHeadersStage,
            final String thingId,
            final String messagePath) {

        final List<ThingId> targetThingIds = List.of(ThingId.of(thingId));
        final CompletionStage<SignalEnrichmentFacade> facadeStage = signalEnrichmentProvider == null
                ? CompletableFuture.completedStage(null)
                : signalEnrichmentProvider.getFacade(ctx.getRequest());

        final var sseSourceStage = facadeStage.thenCompose(facade -> dittoHeadersStage.thenCompose(
                dittoHeaders ->
                        sseAuthorizationEnforcer.checkAuthorization(ctx, dittoHeaders).thenApply(unused -> {

                            final Source<SessionedJsonifiable, SupervisedStream.WithQueue> publisherSource =
                                    SupervisedStream.sourceQueue(10);

                            return publisherSource.viaMat(KillSwitches.single(), Keep.both())
                                    .mapMaterializedValue(pair -> {
                                        final SupervisedStream.WithQueue withQueue = pair.first();
                                        final KillSwitch killSwitch = pair.second();
                                        final String connectionCorrelationId = dittoHeaders.getCorrelationId()
                                                .orElseThrow(() -> new IllegalStateException(
                                                        "Expected correlation-id in SSE DittoHeaders: " +
                                                                dittoHeaders));

                                        final var jsonSchemaVersion = dittoHeaders.getSchemaVersion()
                                                .orElse(JsonSchemaVersion.LATEST);
                                        sseConnectionSupervisor.supervise(withQueue.getSupervisedStream(),
                                                connectionCorrelationId, dittoHeaders);
                                        final var authorizationContext = dittoHeaders.getAuthorizationContext();
                                        final var connect =
                                                new Connect(withQueue.getSourceQueue(), connectionCorrelationId,
                                                        STREAMING_TYPE_SSE, jsonSchemaVersion, null, Set.of(),
                                                        authorizationContext, null);
                                        final String resourcePathRqlStatement;
                                        if (INBOX_OUTBOX_WITH_SUBJECT_PATTERN.matcher(messagePath).matches()) {
                                            resourcePathRqlStatement = String.format(
                                                    "eq(resource:path,'%s')", messagePath);
                                        } else {
                                            resourcePathRqlStatement = String.format(
                                                    "like(resource:path,'%s*')", messagePath);
                                        }
                                        final var startStreaming = StartStreaming.getBuilder(
                                                        StreamingType.MESSAGES,
                                                        connectionCorrelationId,
                                                        authorizationContext)
                                                .withFilter(MessageFormat.format("and(" +
                                                        "eq(entity:id,''{0}'')," +
                                                        "{1}" +
                                                        ")", thingId, resourcePathRqlStatement)
                                                )
                                                .build();
                                        Patterns.ask(streamingActor, connect, LOCAL_ASK_TIMEOUT)
                                                .thenApply(ActorRef.class::cast)
                                                .thenAccept(streamingSessionActor ->
                                                        streamingSessionActor.tell(startStreaming, ActorRef.noSender()))
                                                .exceptionally(e -> {
                                                    killSwitch.abort(e);
                                                    return null;
                                                });
                                        return NotUsed.getInstance();
                                    })
                                    .filter(jsonifiable -> jsonifiable.getJsonifiable() instanceof MessageCommand)
                                    .mapAsync(streamingConfig.getParallelism(), jsonifiable ->
                                            postprocessMessages(targetThingIds,
                                                    (MessageCommand<?, ?>) jsonifiable.getJsonifiable(),
                                                    facade,
                                                    jsonifiable
                                            )
                                    )
                                    .mapConcat(messages -> messages)
                                    .map(message -> {
                                        THINGS_SSE_COUNTER.increment();
                                        final Optional<Charset> charset = determineCharsetFromContentType(
                                                message.getContentType());
                                        final String data = message.getRawPayload()
                                                .map(body -> new String(body.array(),
                                                        charset.orElse(StandardCharsets.UTF_8)))
                                                .orElse("");
                                        return ServerSentEvent.create(data, message.getSubject());
                                    })
                                    .log("SSE " + PATH_THINGS + "/" + messagePath)
                                    .keepAlive(Duration.ofSeconds(1), ServerSentEvent::heartbeat);
                        })
        ));
        return completeOKWithFuture(sseSourceStage, EventStreamMarshalling.toEventStream());
    }

    private static Optional<Charset> determineCharsetFromContentType(final Optional<String> fullContentTypeString) {
        // determine charset, if one was set in the form of:
        // application/json; charset=utf-8
        return fullContentTypeString.filter(ct -> ct.contains(";"))
                .map(ct -> ct.split(";")[1])
                .filter(charsetStr -> charsetStr.contains("="))
                .map(charsetStr -> charsetStr.split("=")[1])
                .filter(Charset::isSupported)
                .map(Charset::forName);
    }

    private Route createSearchSseRoute(final RequestContext ctx,
            final CompletionStage<DittoHeaders> dittoHeadersStage,
            final Map<String, String> parameters) {

        if (proxyActor == null) {
            return complete(StatusCodes.NOT_IMPLEMENTED);
        }

        final CompletionStage<Source<ServerSentEvent, NotUsed>> sseSourceStage =
                dittoHeadersStage.thenApply(dittoHeaders -> {
                    sseAuthorizationEnforcer.checkAuthorization(ctx, dittoHeaders);

                    final var searchSourceBuilder = SearchSource.newBuilder()
                            .pubSubMediator(pubSubMediator)
                            .commandForwarder(ActorSelection.apply(proxyActor, ""))
                            .filter(parameters.get(PARAM_FILTER))
                            .options(parameters.get(PARAM_OPTION))
                            .fields(parameters.get(PARAM_FIELDS))
                            .namespaces(parameters.get(PARAM_NAMESPACES))
                            .dittoHeaders(dittoHeaders);

                    // ctx.getRequest().getHeader(LastEventId.class) is not working
                    ctx.getRequest()
                            .getHeader(LAST_EVENT_ID_HEADER)
                            .ifPresent(lastEventId -> searchSourceBuilder.lastThingId(lastEventId.value()));

                    return searchSourceBuilder.build()
                            .startAsPair(builder -> {})
                            .via(AbstractRoute.throttleByConfig(
                                    streamingConfig.getSseConfig().getThrottlingConfig()))
                            .map(pair -> {
                                SEARCH_SSE_COUNTER.increment();
                                return ServerSentEvent.create(pair.second().toString(),
                                        Optional.empty(),
                                        Optional.of(pair.first()),
                                        OptionalInt.empty()
                                );
                            })
                            .recoverWithRetries(1, new PFBuilder<Throwable, Source<ServerSentEvent, NotUsed>>()
                                    .match(DittoRuntimeException.class, dittoRuntimeException -> Source.single(
                                            ServerSentEvent.create(dittoRuntimeException.toJsonString())
                                    ))
                                    .build())
                            .log("SSE " + PATH_SEARCH)
                            .via(eventSniffer.toAsyncFlow(ctx.getRequest()));
                });

        return completeOKWithFuture(sseSourceStage, EventStreamMarshalling.toEventStream());
    }

    private CompletionStage<Collection<JsonValue>> postprocess(final SessionedJsonifiable jsonifiable,
            @Nullable final SignalEnrichmentFacade facade,
            final Collection<ThingId> targetThingIds,
            final Collection<String> namespaces,
            final JsonPointer fieldPointer,
            @Nullable final JsonFieldSelector fields) {

        final Supplier<CompletableFuture<Collection<JsonValue>>> emptySupplier =
                () -> CompletableFuture.completedFuture(Collections.emptyList());

        if (jsonifiable.getJsonifiable() instanceof ThingEvent<?> event) {
            final boolean isLiveEvent = StreamingType.isLiveSignal(event);
            if (!isLiveEvent && namespaceMatches(event, namespaces) &&
                    targetThingIdMatches(event, targetThingIds)) {
                return jsonifiable.getSession()
                        .map(session -> jsonifiable.retrieveExtraFields(facade)
                                .thenApply(extra ->
                                        Optional.of(session.mergeThingWithExtra(event, extra))
                                                .filter(thing -> session.matchesFilter(thing, event))
                                                .map(thing -> toNonemptyValue(thing, event, fieldPointer, fields))
                                                .orElseGet(Collections::emptyList)
                                )
                                .exceptionally(error -> {
                                    final var errorToReport =
                                            DittoRuntimeException.asDittoRuntimeException(error, t ->
                                                    SignalEnrichmentFailedException.newBuilder().cause(t).build());
                                    jsonifiable.getSession().map(StreamingSession::getLogger).ifPresent(logger ->
                                            logger.withCorrelationId(event)
                                                    .warning("During extra fields retrieval in <SSE> session got " +
                                                                    "exception <{}>: <{}> - emitting: <{}>",
                                                            error.getClass().getSimpleName(), error.getMessage(),
                                                            errorToReport
                                                    )
                                    );
                                    return Collections.singletonList(errorToReport.toJson());
                                })
                        )
                        .orElseGet(emptySupplier);
            }
        }
        return emptySupplier.get();
    }

    private <P, M extends MessageCommand<P, ?>> CompletionStage<List<Message<P>>> postprocessMessages(
            final List<ThingId> targetThingIds,
            final M messageCommand,
            @Nullable final SignalEnrichmentFacade facade,
            final SessionedJsonifiable jsonifiable) {

        final Supplier<CompletionStage<List<Message<P>>>> emptySupplier =
                () -> CompletableFuture.completedStage(List.of());
        if (targetThingIds.contains(messageCommand.getEntityId())) {
            return jsonifiable.getSession()
                    .map(session -> jsonifiable.retrieveExtraFields(facade)
                            .thenApply(extra ->
                                    Optional.of(session.mergeThingWithExtra(messageCommand, extra))
                                            .filter(thing -> session.matchesFilter(thing, messageCommand))
                                            .map(thing -> List.of(messageCommand.getMessage()))
                                            .orElseGet(List::of)
                            )
                            .exceptionally(error -> {
                                final var errorToReport =
                                        DittoRuntimeException.asDittoRuntimeException(
                                                error, t -> SignalEnrichmentFailedException.newBuilder()
                                                        .cause(t)
                                                        .build());
                                jsonifiable.getSession()
                                        .map(StreamingSession::getLogger)
                                        .ifPresent(logger ->
                                                logger.withCorrelationId(messageCommand)
                                                        .warning("During extra fields retrieval in <SSE> session got " +
                                                                        "exception <{}>: <{}> - emitting: <{}>",
                                                                error.getClass().getSimpleName(),
                                                                error.getMessage(),
                                                                errorToReport
                                                        )
                                        );
                                return List.of();
                            })
                    )
                    .orElseGet(emptySupplier);
        }
        return emptySupplier.get();
    }

    private static boolean namespaceMatches(final ThingEvent<?> event, final Collection<String> namespaces) {
        return namespaces.isEmpty() || namespaces.contains(namespaceFromId(event));
    }

    private static boolean targetThingIdMatches(final ThingEvent<?> event,
            final Collection<ThingId> targetThingIds) {
        return targetThingIds.isEmpty() || targetThingIds.contains(event.getEntityId());
    }

    private static Collection<JsonValue> toNonemptyValue(final Thing thing, final ThingEvent<?> event,
            final JsonPointer fieldPointer,
            @Nullable final JsonFieldSelector fields) {
        final var jsonSchemaVersion = event.getDittoHeaders()
                .getSchemaVersion()
                .orElse(event.getImplementedSchemaVersion());
        final JsonObject thingJson = null != fields
                ? thing.toJson(jsonSchemaVersion, fields)
                : thing.toJson(jsonSchemaVersion);

        @Nullable final JsonValue returnValue;
        if (!fieldPointer.isEmpty()) {
            returnValue = thingJson.getValue(fieldPointer).orElse(null);
        } else {
            final boolean includeContext = Optional.ofNullable(fields)
                    .filter(field -> field.getPointers().stream()
                            .map(JsonPointer::getRoot)
                            .anyMatch(p -> p.equals(CONTEXT.getPointer().getRoot()))
                    ).isPresent();
            if (includeContext) {
                returnValue = addContext(thingJson.toBuilder(), event).get(fields);
            } else {
                returnValue = thingJson;
            }
        }
        return (thingJson.isEmpty() || null == returnValue) ? Collections.emptyList() :
                Collections.singletonList(returnValue);
    }

    private static List<String> getNamespaces(@Nullable final String namespacesParameter) {
        if (null != namespacesParameter) {
            return Arrays.asList(namespacesParameter.split(","));
        }
        return Collections.emptyList();
    }

    private static List<ThingId> getThingIds(@Nullable final String thingIdString) {
        if (null != thingIdString) {
            return Stream.of(thingIdString.split(","))
                    .map(ThingId::of)
                    .toList();
        }
        return Collections.emptyList();
    }

    @Nullable
    private static ThingFieldSelector getFieldSelector(@Nullable final String fieldsString) {
        return fieldsString == null ? null : ThingFieldSelector.fromString(fieldsString);
    }

    private static String namespaceFromId(final ThingEvent<?> thingEvent) {
        return thingEvent.getEntityId().getNamespace();
    }

    private static PartialFunction<HttpHeader, Accept> newAcceptHeaderExtractor() {
        return new PFBuilder<HttpHeader, Accept>()
                .match(Accept.class, ThingsSseRouteBuilder::matchesTextEventStream, accept -> accept)
                .build();
    }

    private static boolean matchesTextEventStream(final Accept accept) {
        return StreamSupport.stream(accept.getMediaRanges().spliterator(), false)
                .filter(mr -> !"*".equals(mr.mainType()))
                .anyMatch(mr -> mr.matches(MediaTypes.TEXT_EVENT_STREAM));
    }

    private static Counter getCounterFor(final String path) {
        return DittoMetrics.counter("streaming_messages")
                .tag("type", "sse")
                .tag("direction", "out")
                .tag("path", path);
    }

    /**
     * Add a JSON object at {@code _context} key containing e.g. the {@code headers} of the passed
     * {@code withDittoHeaders}.
     *
     * @param objectBuilder the JsonObject build to add the {@code _context} to.
     * @param withDittoHeaders the object to extract the {@code DittoHeaders} from.
     * @return the built JsonObject including the {@code _context}.
     */
    private static JsonObject addContext(final JsonObjectBuilder objectBuilder,
            final WithDittoHeaders withDittoHeaders) {

        objectBuilder.set(CONTEXT, JsonObject.newBuilder()
                .set("headers", dittoHeadersToJson(withDittoHeaders.getDittoHeaders()))
                .build()
        );
        return objectBuilder.build();
    }

    private static JsonObject dittoHeadersToJson(final DittoHeaders dittoHeaders) {
        return dittoHeaders.entrySet()
                .stream()
                .map(entry -> JsonFactory.newField(JsonKey.of(entry.getKey()), JsonFactory.newValue(entry.getValue())))
                .collect(JsonCollectors.fieldsToObject());
    }

}
