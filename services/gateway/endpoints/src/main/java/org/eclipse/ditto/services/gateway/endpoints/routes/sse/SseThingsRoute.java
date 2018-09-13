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
package org.eclipse.ditto.services.gateway.endpoints.routes.sse;

import static akka.http.javadsl.server.Directives.completeOK;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.headerValuePF;
import static akka.http.javadsl.server.Directives.parameterOptional;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsParameter;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingEventToThingConverter;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.marshalling.sse.EventStreamMarshalling;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.http.scaladsl.model.sse.ServerSentEvent$;
import akka.japi.JavaPartialFunction;
import akka.stream.javadsl.Source;
import scala.concurrent.duration.FiniteDuration;

/**
 * Builder for creating Akka HTTP routes for SSE (Server Sent Events) {@code /things} routes.
 */
public class SseThingsRoute extends AbstractRoute {

    private static final String PATH_THINGS = "things";

    private static final String STREAMING_TYPE_SSE = "SSE";

    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_NAMESPACES = "namespaces";

    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;

    private ActorRef streamingActor;

    /**
     * Constructs the SSE - ServerSentEvents supporting {@code /things} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public SseThingsRoute(final ActorRef proxyActor, final ActorSystem actorSystem, final ActorRef streamingActor) {
        super(proxyActor, actorSystem);
        this.streamingActor = streamingActor;

        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory =
                new ModelBasedThingsFieldExpressionFactory();
        queryFilterCriteriaFactory = new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);
    }

    /**
     * Describes {@code /things} SSE route.
     *
     * @return {@code /things} SSE route.
     */
    @SuppressWarnings("squid:S1172") // allow unused ctx-Param in order to have a consistent route-"interface"
    public Route buildThingsSseRoute(final RequestContext ctx, final Supplier<DittoHeaders> dittoHeadersSupplier) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_THINGS), () ->
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
                                                        idsString.map(ids -> ids.split(","))
                                                                .map(Arrays::asList)
                                                                .orElse(Collections.emptyList()),
                                                        namespacesString.map(str -> str.split(","))
                                                                .map(Arrays::asList)
                                                                .orElse(Collections.emptyList()),
                                                        filterString.orElse(null))
                                        )
                                )
                )
        );
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
            final List<String> targetThingIds,
            final List<String> namespaces,
            @Nullable final String filterString) {

        final String connectionCorrelationId = dittoHeaders.getCorrelationId()
                .orElseGet(() -> UUID.randomUUID().toString());
        final JsonSchemaVersion jsonSchemaVersion =
                dittoHeaders.getSchemaVersion().orElse(dittoHeaders.getImplementedSchemaVersion());

        if (filterString != null) {
            // will throw an InvalidRqlExpressionException if the RQL expression was not valid:
            queryFilterCriteriaFactory.filterCriteria(filterString, dittoHeaders);
        }

        final Source<ServerSentEvent, NotUsed> sseSource =
                Source.<Jsonifiable.WithPredicate<JsonObject, JsonField>>actorPublisher(
                        EventAndResponsePublisher.props(10))
                        .mapMaterializedValue(actorRef -> {
                            streamingActor.tell(new Connect(actorRef, connectionCorrelationId, STREAMING_TYPE_SSE),
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
                                targetThingIds.contains(thingEvent.getThingId()) // only Events of the target thingIds
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
                        .keepAlive(FiniteDuration.apply(1, TimeUnit.SECONDS),
                                ServerSentEvent$.MODULE$::heartbeat);

        return completeOK(sseSource, EventStreamMarshalling.toEventStream());
    }

    private static String namespaceFromId(final WithId withId) {
        return withId.getId().split(":", 2)[0];
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
