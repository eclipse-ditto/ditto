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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsParameter;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingType;
import org.eclipse.ditto.services.gateway.streaming.actors.EventAndResponsePublisher;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;

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

    private ActorRef streamingActor;

    private static final Map<Class<?>, BiFunction<ThingEvent, ThingBuilder.FromScratch, Thing>> EVENT_TO_THING_MAPPERS =
            createEventToThingMappers();

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
    }

    /**
     * Describes {@code /things} SSE route.
     *
     * @return {@code /things} SSE route.
     */
    @SuppressWarnings("squid:S1172") // allow unused ctx-Param in order to have a consistent route-"interface"
    public Route buildThingsSseRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_THINGS), () ->
                pathEndOrSingleSlash(() ->
                        get(() ->
                                headerValuePF(AcceptHeaderExtractor.INSTANCE, accept ->
                                        parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                                parameterOptional(ThingsParameter.IDS.toString(),
                                                        idsString -> // "ids" is optional for SSE
                                                                createSseRoute(dittoHeaders,
                                                                        calculateSelectedFields(fieldsString).orElse(
                                                                                null),
                                                                        idsString.map(ids -> ids.split(",")))
                                                )
                                        )
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
    private Route createSseRoute(final DittoHeaders dittoHeaders, final JsonFieldSelector fieldSelector,
            final Optional<String[]> thingIds) {
        final Optional<List<String>> targetThingIds = thingIds.map(Arrays::asList);

        final String connectionCorrelationId = dittoHeaders.getCorrelationId()
                .orElseGet(() -> UUID.randomUUID().toString());
        final JsonSchemaVersion jsonSchemaVersion =
                dittoHeaders.getSchemaVersion().orElse(dittoHeaders.getImplementedSchemaVersion());

        final Source<ServerSentEvent, NotUsed> sseSource =
                Source.<Jsonifiable.WithPredicate<JsonObject, JsonField>>actorPublisher(
                        EventAndResponsePublisher.props(10))
                        .mapMaterializedValue(actorRef -> {
                            streamingActor.tell(new Connect(actorRef, connectionCorrelationId, STREAMING_TYPE_SSE),
                                    null);
                            streamingActor.tell(
                                    new StartStreaming(StreamingType.EVENTS, connectionCorrelationId,
                                            dittoHeaders.getAuthorizationContext()),
                                    null);
                            return NotUsed.getInstance();
                        })
                        .filter(jsonifiable -> jsonifiable instanceof ThingEvent)
                        .map(jsonifiable -> ((ThingEvent) jsonifiable))
                        .filter(thingEvent -> !targetThingIds.isPresent() || targetThingIds.get().contains(
                                thingEvent.getThingId())) // only Events of the target thingIds
                        .map(this::thingEventToThing)
                        .filter(Objects::nonNull)
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

    /**
     * Creates a Thing from the passed ThingEvent
     */
    private Thing thingEventToThing(final ThingEvent te) {
        final BiFunction<ThingEvent, ThingBuilder.FromScratch, Thing> eventToThingMapper =
                EVENT_TO_THING_MAPPERS.get(te.getClass());
        if (eventToThingMapper == null) {
            return null;
        }

        final ThingBuilder.FromScratch tb = Thing.newBuilder().setId(te.getThingId()).setRevision(te.getRevision());
        return eventToThingMapper.apply(te, tb);
    }

    private static Map<Class<?>, BiFunction<ThingEvent, ThingBuilder.FromScratch, Thing>> createEventToThingMappers() {
        final Map<Class<?>, BiFunction<ThingEvent, ThingBuilder.FromScratch, Thing>> mappers = new HashMap<>();

        mappers.put(ThingCreated.class,
                (te, tb) -> ((ThingCreated) te).getThing().toBuilder().setRevision(te.getRevision()).build());
        mappers.put(ThingModified.class,
                (te, tb) -> ((ThingModified) te).getThing().toBuilder().setRevision(te.getRevision()).build());
        mappers.put(ThingDeleted.class,
                (te, tb) -> tb.build());

        mappers.put(AclModified.class,
                (te, tb) -> tb.setPermissions(((AclModified) te).getAccessControlList()).build());
        mappers.put(AclEntryCreated.class,
                (te, tb) -> tb.setPermissions(((AclEntryCreated) te).getAclEntry()).build());
        mappers.put(AclEntryModified.class,
                (te, tb) -> tb.setPermissions(((AclEntryModified) te).getAclEntry()).build());
        mappers.put(AclEntryDeleted.class,
                (te, tb) -> tb.build());

        mappers.put(PolicyIdCreated.class,
                (te, tb) -> tb.setPolicyId(((PolicyIdCreated) te).getPolicyId()).build());
        mappers.put(PolicyIdModified.class,
                (te, tb) -> tb.setPolicyId(((PolicyIdModified) te).getPolicyId()).build());

        mappers.put(AttributesCreated.class,
                (te, tb) -> tb.setAttributes(((AttributesCreated) te).getCreatedAttributes()).build());
        mappers.put(AttributesModified.class,
                (te, tb) -> tb.setAttributes(((AttributesModified) te).getModifiedAttributes()).build());
        mappers.put(AttributesDeleted.class, (te, tb) -> tb.build());
        mappers.put(AttributeCreated.class, (te, tb) -> tb.setAttribute(((AttributeCreated) te).getAttributePointer(),
                ((AttributeCreated) te).getAttributeValue()).build());
        mappers.put(AttributeModified.class, (te, tb) -> tb.setAttribute(((AttributeModified) te).getAttributePointer(),
                ((AttributeModified) te).getAttributeValue()).build());
        mappers.put(AttributeDeleted.class, (te, tb) -> tb.build());

        mappers.put(FeaturesCreated.class, (te, tb) -> tb.setFeatures(((FeaturesCreated) te).getFeatures()).build());
        mappers.put(FeaturesModified.class, (te, tb) -> tb.setFeatures(((FeaturesModified) te).getFeatures()).build());
        mappers.put(FeaturesDeleted.class, (te, tb) -> tb.build());
        mappers.put(FeatureCreated.class, (te, tb) -> tb.setFeature(((FeatureCreated) te).getFeature()).build());
        mappers.put(FeatureModified.class, (te, tb) -> tb.setFeature(((FeatureModified) te).getFeature()).build());
        mappers.put(FeatureDeleted.class, (te, tb) -> tb.build());

        mappers.put(FeaturePropertiesCreated.class, (te, tb) -> tb.setFeature(Feature.newBuilder()
                .properties(((FeaturePropertiesCreated) te).getProperties())
                .withId(((FeaturePropertiesCreated) te).getFeatureId())
                .build()).build());
        mappers.put(FeaturePropertiesModified.class, (te, tb) -> tb.setFeature(Feature.newBuilder()
                .properties(((FeaturePropertiesModified) te).getProperties())
                .withId(((FeaturePropertiesModified) te).getFeatureId())
                .build()).build());
        mappers.put(FeaturePropertiesDeleted.class, (te, tb) -> tb.build());
        mappers.put(FeaturePropertyCreated.class, (te, tb) ->
                tb.setFeatureProperty(((FeaturePropertyCreated) te).getFeatureId(),
                        ((FeaturePropertyCreated) te).getPropertyPointer(),
                        ((FeaturePropertyCreated) te).getPropertyValue()).build());
        mappers.put(FeaturePropertyModified.class, (te, tb) ->
                tb.setFeatureProperty(((FeaturePropertyModified) te).getFeatureId(),
                        ((FeaturePropertyModified) te).getPropertyPointer(),
                        ((FeaturePropertyModified) te).getPropertyValue()).build());
        mappers.put(FeaturePropertyDeleted.class, (te, tb) -> tb.build());

        return mappers;
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
