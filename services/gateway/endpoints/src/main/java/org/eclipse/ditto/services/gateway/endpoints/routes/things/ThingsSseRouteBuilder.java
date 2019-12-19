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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
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
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseAuthorizationEnforcer;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseConnectionSupervisor;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseRouteBuilder;
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
import akka.http.javadsl.marshalling.sse.EventStreamMarshalling;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.http.javadsl.server.Directives;
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
@NotThreadSafe
public final class ThingsSseRouteBuilder implements SseRouteBuilder {

    private static final String PATH_THINGS = "things";

    private static final String STREAMING_TYPE_SSE = "SSE";

    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_NAMESPACES = "namespaces";
    private static final String PARAM_EXTRA_FIELDS = "extraFields";

    private final ActorRef streamingActor;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;

    private SseAuthorizationEnforcer sseAuthorizationEnforcer;
    private SseConnectionSupervisor sseConnectionSupervisor;
    private EventSniffer<ServerSentEvent> eventSniffer;

    private ThingsSseRouteBuilder(final ActorRef streamingActor,
            final QueryFilterCriteriaFactory queryFilterCriteriaFactory) {

        this.streamingActor = streamingActor;
        this.queryFilterCriteriaFactory = queryFilterCriteriaFactory;
        sseAuthorizationEnforcer = new NoOpSseAuthorizationEnforcer();
        sseConnectionSupervisor = new NoOpSseConnectionSupervisor();
        eventSniffer = EventSniffer.noOp();
    }

    /**
     * Returns an instance of this class.
     *
     * @param streamingActor is used for actual event streaming.
     * @return the instance.
     * @throws NullPointerException if {@code streamingActor} is {@code null}.
     */
    public static ThingsSseRouteBuilder getInstance(final ActorRef streamingActor) {
        checkNotNull(streamingActor, "streamingActor");
        final QueryFilterCriteriaFactory queryFilterCriteriaFactory =
                new QueryFilterCriteriaFactory(new CriteriaFactoryImpl(), new ModelBasedThingsFieldExpressionFactory());

        return new ThingsSseRouteBuilder(streamingActor, queryFilterCriteriaFactory);
    }

    @Override
    public SseRouteBuilder withAuthorizationEnforcer(final SseAuthorizationEnforcer enforcer) {
        sseAuthorizationEnforcer = checkNotNull(enforcer, "enforcer");
        return this;
    }

    @Override
    public ThingsSseRouteBuilder withEventSniffer(final EventSniffer<ServerSentEvent> eventSniffer) {
        this.eventSniffer = checkNotNull(eventSniffer, "eventSniffer");
        return this;
    }

    @Override
    public SseRouteBuilder withSseConnectionSupervisor(final SseConnectionSupervisor sseConnectionSupervisor) {
        this.sseConnectionSupervisor = checkNotNull(sseConnectionSupervisor, "sseConnectionSupervisor");
        return this;
    }

    /**
     * Describes {@code /things} SSE route.
     *
     * @return {@code /things} SSE route.
     */
    @SuppressWarnings("squid:S1172") // allow unused ctx-Param in order to have a consistent route-"interface"
    @Override
    public Route build(final RequestContext ctx, final Supplier<CompletionStage<DittoHeaders>> dittoHeadersSupplier) {
        return Directives.rawPathPrefix(PathMatchers.slash().concat(PATH_THINGS),
                () -> Directives.pathEndOrSingleSlash(
                        () -> Directives.get(
                                () -> Directives.headerValuePF(AcceptHeaderExtractor.INSTANCE,
                                        accept -> {
                                            final CompletionStage<DittoHeaders> dittoHeadersCompletionStage =
                                                    dittoHeadersSupplier.get()
                                                            .thenApply(
                                                                    ThingsSseRouteBuilder::getDittoHeadersWithCorrelationId);
                                            return buildThingsSseRoute(ctx, dittoHeadersCompletionStage);
                                        }))));
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

    private Route buildThingsSseRoute(final RequestContext ctx, final CompletionStage<DittoHeaders> dittoHeaders) {
        return Directives.parameterMap(parameters -> createSseRoute(ctx, dittoHeaders, parameters));
    }

    private Route createSseRoute(final RequestContext ctx, final CompletionStage<DittoHeaders> dittoHeadersStage,
            final Map<String, String> parameters) {

        final String filterString = parameters.get(PARAM_FILTER);
        final List<String> namespaces = getNamespaces(parameters.get(PARAM_NAMESPACES));
        final List<ThingId> targetThingIds = getThingIds(parameters.get(ThingsParameter.IDS.toString()));
        @Nullable final JsonFieldSelector fields = getFieldSelector(parameters.get(ThingsParameter.FIELDS.toString()));
        @Nullable final JsonFieldSelector extraFields = getFieldSelector(parameters.get(PARAM_EXTRA_FIELDS));

        final CompletionStage<Source<ServerSentEvent, NotUsed>> sseSourceStage =
                dittoHeadersStage.thenApply(dittoHeaders -> {

                    sseAuthorizationEnforcer.checkAuthorization(ctx, dittoHeaders);

                    final Counter messageCounter = DittoMetrics.counter("streaming_messages")
                            .tag("type", "sse")
                            .tag("direction", "out");

                    if (filterString != null) {
                        // will throw an InvalidRqlExpressionException if the RQL expression was not valid:
                        queryFilterCriteriaFactory.filterCriteria(filterString, dittoHeaders);
                    }

                    return Source.<Jsonifiable.WithPredicate<JsonObject, JsonField>>actorPublisher(
                            EventAndResponsePublisher.props(10))
                            .mapMaterializedValue(publisherActor -> {
                                final String connectionCorrelationId = dittoHeaders.getCorrelationId().get();
                                sseConnectionSupervisor.supervise(publisherActor, connectionCorrelationId,
                                        dittoHeaders);
                                streamingActor.tell(
                                        new Connect(publisherActor, connectionCorrelationId, STREAMING_TYPE_SSE, null),
                                        null);
                                final StartStreaming startStreaming =
                                        StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                                                dittoHeaders.getAuthorizationContext())
                                                .withNamespaces(namespaces)
                                                .withFilter(filterString)
                                                .withExtraFields(extraFields)
                                                .build();
                                streamingActor.tell(startStreaming, null);
                                return NotUsed.getInstance();
                            })
                            .filter(jsonifiable -> jsonifiable instanceof ThingEvent)
                            .map(jsonifiable -> (ThingEvent) jsonifiable)
                            .filter(thingEvent -> targetThingIds.isEmpty() ||
                                            targetThingIds.contains(thingEvent.getThingEntityId())
                                    // only Events of the target thingIds
                            )
                            .filter(thingEvent -> namespaces.isEmpty() ||
                                    namespaces.contains(namespaceFromId(thingEvent)))
                            .map(ThingEventToThingConverter::thingEventToThing)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(thing -> {
                                final JsonSchemaVersion jsonSchemaVersion = dittoHeaders.getSchemaVersion()
                                        .orElse(dittoHeaders.getImplementedSchemaVersion());
                                return null != fields
                                        ? thing.toJson(jsonSchemaVersion, fields)
                                        : thing.toJson(jsonSchemaVersion);
                            })
                            .filter(thingJson -> fields == null || fields.getPointers().stream()
                                    .filter(p -> !p.equals(Thing.JsonFields.ID.getPointer())) // ignore "thingId"
                                    .anyMatch(
                                            thingJson::contains)) // check if the resulting JSON did contain ANY of the requested fields
                            .filter(thingJson -> !thingJson.isEmpty()) // avoid sending back empty jsonValues
                            .map(jsonValue -> ServerSentEvent.create(jsonValue.toString()))
                            .via(Flow.fromFunction(msg -> {
                                messageCounter.increment();
                                return msg;
                            }))
                            .viaMat(eventSniffer.toAsyncFlow(ctx.getRequest()),
                                    Keep.left()) // sniffer shouldn't sniff heartbeats
                            .keepAlive(Duration.ofSeconds(1), ServerSentEvent::heartbeat);
                });

        return Directives.completeOKWithFuture(sseSourceStage, EventStreamMarshalling.toEventStream());
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
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Nullable
    private static JsonFieldSelector getFieldSelector(@Nullable final String fieldsString) {
        if (null != fieldsString) {
            return JsonFactory.newFieldSelector(fieldsString, AbstractRoute.JSON_FIELD_SELECTOR_PARSE_OPTIONS);
        }
        return null;
    }

    private static String namespaceFromId(final ThingEvent thingEvent) {
        return thingEvent.getEntityId().getNamespace();
    }

    private static final class AcceptHeaderExtractor extends JavaPartialFunction<HttpHeader, Accept> {

        private static final AcceptHeaderExtractor INSTANCE = new AcceptHeaderExtractor();

        private AcceptHeaderExtractor() {
            super();
        }

        @Override
        public Accept apply(final HttpHeader x, final boolean isCheck) {
            if (x instanceof Accept) {
                if (isCheck) {
                    return null;
                } else if (matchesTextEventStream((Accept) x)) {
                    return (Accept) x;
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

    /**
     * Null implementation for {@link SseAuthorizationEnforcer}.
     */
    private static final class NoOpSseAuthorizationEnforcer implements SseAuthorizationEnforcer {

        @Override
        public void checkAuthorization(final RequestContext requestContext, final DittoHeaders dittoHeaders) {
            // Does nothing.
        }

    }

    /**
     * Null implementation for {@link SseConnectionSupervisor}.
     */
    private static final class NoOpSseConnectionSupervisor implements SseConnectionSupervisor {

        @Override
        public void supervise(final ActorRef sseConnectionActor, final CharSequence connectionCorrelationId,
                final DittoHeaders dittoHeaders) {

            // Does nothing.
        }
    }


}
