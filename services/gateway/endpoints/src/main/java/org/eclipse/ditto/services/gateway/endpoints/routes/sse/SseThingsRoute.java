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
package org.eclipse.ditto.services.gateway.endpoints.routes.sse;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsParameter;
import org.eclipse.ditto.services.gateway.endpoints.utils.EventSniffer;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingEventToThingConverter;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.marshalling.sse.EventStreamMarshalling;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.JavaPartialFunction;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;

/**
 * Builder for creating Akka HTTP routes for SSE (Server Sent Events) {@code /things} routes.
 */
public class SseThingsRoute extends AbstractRoute {

    private static final String PATH_THINGS = "things";

    private static final String STREAMING_TYPE_SSE = "SSE";

    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_NAMESPACES = "namespaces";

    private final HttpConfig httpConfig;
    private final ActorRef streamingActor;
    private final EventSniffer<ServerSentEvent> eventSniffer;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;
    private final HeaderTranslator headerTranslator;

    private SseThingsRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final ActorRef streamingActor,
            final EventSniffer<ServerSentEvent> eventSniffer,
            final QueryFilterCriteriaFactory queryFilterCriteriaFactory,
            final HeaderTranslator headerTranslator) {

        super(proxyActor, actorSystem, httpConfig, headerTranslator);
        this.httpConfig = httpConfig;
        this.streamingActor = streamingActor;
        this.eventSniffer = eventSniffer;
        this.queryFilterCriteriaFactory = queryFilterCriteriaFactory;
        this.headerTranslator = headerTranslator;
    }

    /**
     * Constructs the SSE - ServerSentEvents supporting {@code /things} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public SseThingsRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final ActorRef streamingActor,
            final HeaderTranslator headerTranslator) {

        this(proxyActor, actorSystem, httpConfig, streamingActor, EventSniffer.noOp(),
                new QueryFilterCriteriaFactory(new CriteriaFactoryImpl(), new ModelBasedThingsFieldExpressionFactory()),
                headerTranslator);
    }

    /**
     * Create a copy of this object with a different event sniffer.
     *
     * @param eventSniffer the new event sniffer.
     * @return a copy of this object with a new event sniffer.
     */
    public SseThingsRoute withEventSniffer(final EventSniffer<ServerSentEvent> eventSniffer) {
        return new SseThingsRoute(proxyActor, actorSystem, httpConfig, streamingActor, eventSniffer,
                queryFilterCriteriaFactory, headerTranslator);
    }

    /**
     * Describes {@code /things} SSE route.
     *
     * @return {@code /things} SSE route.
     */
    @SuppressWarnings("squid:S1172") // allow unused ctx-Param in order to have a consistent route-"interface"
    public Route buildThingsSseRoute(final RequestContext ctx, final Supplier<DittoHeaders> dittoHeadersSupplier) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_THINGS), () ->
                pathEndOrSingleSlash(() ->
                        get(() ->
                                headerValuePF(AcceptHeaderExtractor.INSTANCE, accept ->
                                        doBuildThingsSseRoute(dittoHeadersSupplier.get())
                                )
                        )
                )
        );
    }

    private Route doBuildThingsSseRoute(final DittoHeaders dittoHeaders) {
        return parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                parameterOptional(ThingsParameter.IDS.toString(),
                        idsString -> // "ids" is optional for SSE
                                parameterOptional(PARAM_NAMESPACES, namespacesString ->
                                        parameterOptional(PARAM_FILTER, filterString ->
                                                createSseRoute(dittoHeaders,
                                                        calculateSelectedFields(
                                                                fieldsString).orElse(null),
                                                        idsString.map(this::splitThingIdString)
                                                                .orElseGet(Collections::emptyList),
                                                        namespacesString.map(str -> str.split(","))
                                                                .map(Arrays::asList)
                                                                .orElse(Collections.emptyList()),
                                                        filterString.orElse(null))
                                        )
                                )
                )
        );
    }

    private List<ThingId> splitThingIdString(final String thingIdString) {
        return Arrays.stream(thingIdString.split(","))
                .map(ThingId::of)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("squid:CommentedOutCodeLine")
    // Javascript example e.g. in Chrome console:
      /*
         var source = new EventSource('http://localhost:8080/api/1/things?ids=org.eclipse.ditto:foo2000&fields=attributes');
         source.addEventListener('message', function (e) {
             console.log(e.data);
         }, false);
       */
    private Route createSseRoute(final DittoHeaders dittoHeaders,
            final JsonFieldSelector fieldSelector,
            final List<ThingId> targetThingIds,
            final List<String> namespaces,
            @Nullable final String filterString) {

        return extractRequest(request ->
                createSseRoute(request, dittoHeaders, fieldSelector, targetThingIds, namespaces, filterString));
    }


    private Route createSseRoute(final HttpRequest request,
            final DittoHeaders dittoHeaders,
            final JsonFieldSelector fieldSelector,
            final List<ThingId> targetThingIds,
            final List<String> namespaces,
            @Nullable final String filterString) {

        final String connectionCorrelationId = dittoHeaders.getCorrelationId()
                .orElseGet(() -> UUID.randomUUID().toString());
        final JsonSchemaVersion jsonSchemaVersion =
                dittoHeaders.getSchemaVersion().orElse(dittoHeaders.getImplementedSchemaVersion());

        final Counter messageCounter = DittoMetrics.counter("streaming_messages")
                        .tag("type", "sse")
                        .tag("direction", "out");

        if (filterString != null) {
            // will throw an InvalidRqlExpressionException if the RQL expression was not valid:
            queryFilterCriteriaFactory.filterCriteria(filterString, dittoHeaders);
        }

        final Source<ServerSentEvent, NotUsed> sseSource =
                Source.<Jsonifiable.WithPredicate<JsonObject, JsonField>>actorPublisher(
                        EventAndResponsePublisher.props(10))
                        .mapMaterializedValue(actorRef -> {
                            streamingActor.tell(new Connect(actorRef, connectionCorrelationId, STREAMING_TYPE_SSE, null),
                                    null);
                            streamingActor.tell(
                                    new StartStreaming(StreamingType.EVENTS, connectionCorrelationId,
                                            dittoHeaders.getAuthorizationContext(), namespaces, filterString),
                                    null);
                            return NotUsed.getInstance();
                        })
                        .filter(jsonifiable -> jsonifiable instanceof ThingEvent)
                        .map(jsonifiable -> ((ThingEvent) jsonifiable))
                        .filter(thingEvent -> targetThingIds.isEmpty() ||
                                        targetThingIds.contains(thingEvent.getThingEntityId())
                                // only Events of the target thingIds
                        )
                        .filter(thingEvent -> namespaces.isEmpty() || namespaces.contains(namespaceFromId(thingEvent)))
                        .map(ThingEventToThingConverter::thingEventToThing)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(thing -> fieldSelector != null ? thing.toJson(jsonSchemaVersion, fieldSelector) :
                                thing.toJson(jsonSchemaVersion))
                        .filter(thingJson -> fieldSelector == null || fieldSelector.getPointers().stream()
                                .filter(p -> !p.equals(Thing.JsonFields.ID.getPointer())) // ignore "thingId"
                                .anyMatch(
                                        thingJson::contains)) // check if the resulting JSON did contain ANY of the requested fields
                        .filter(thingJson -> !thingJson.isEmpty()) // avoid sending back empty jsonValues
                        .map(jsonValue -> ServerSentEvent.create(jsonValue.toString()))
                        .via(Flow.fromFunction(msg -> {
                            messageCounter.increment();
                            return msg;
                        }))
                        .viaMat(eventSniffer.toAsyncFlow(request), Keep.left()) // sniffer shouldn't sniff heartbeats
                        .keepAlive(Duration.ofSeconds(1), ServerSentEvent::heartbeat);

        return completeOK(sseSource, EventStreamMarshalling.toEventStream());
    }

    private static String namespaceFromId(final ThingEvent thingEvent) {
        return thingEvent.getEntityId().getNamespace();
    }

    private static final class AcceptHeaderExtractor extends JavaPartialFunction<HttpHeader, Accept> {

        private static final AcceptHeaderExtractor INSTANCE = new AcceptHeaderExtractor();

        private AcceptHeaderExtractor() {
        }

        @Override
        public Accept apply(final HttpHeader x, final boolean isCheck) {
            if (x instanceof Accept) {
                if (isCheck) {
                    return null;
                } else if (matchesTextEventStream((Accept) x)) {
                    return ((Accept) x);
                }
            }
            throw noMatch();
        }

        private static boolean matchesTextEventStream(final Accept accept) {
            return StreamSupport.stream(accept.getMediaRanges().spliterator(), false)
                    .filter(mr -> !"*".equals(mr.mainType()))
                    .anyMatch(mr -> mr.matches(MediaTypes.TEXT_EVENT_STREAM));
        }

    }
}
