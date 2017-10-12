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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
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

import de.heikoseeberger.akkasse.javadsl.marshalling.EventStreamMarshalling;
import de.heikoseeberger.akkasse.javadsl.model.MediaTypes;
import de.heikoseeberger.akkasse.javadsl.model.ServerSentEvent;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.JavaPartialFunction;
import akka.stream.javadsl.Source;
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;

/**
 * Builder for creating Akka HTTP routes for SSE (Server Sent Events) {@code /things} routes.
 */
public class SseThingsRoute extends AbstractRoute {

    private static final String PATH_THINGS = "things";

    private static final String STREAMING_TYPE_SSE = "SSE";

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
    }

    private static final PartialFunction<HttpHeader, Accept> extractAccept =
            new JavaPartialFunction<HttpHeader, Accept>() {
                @Override
                public Accept apply(final HttpHeader x, final boolean isCheck) throws Exception {
                    if (x instanceof Accept) {
                        if (isCheck) {
                            return null;
                        } else if (matchesTextEventStream((Accept) x)) {
                            return ((Accept) x);
                        }
                    }
                    throw noMatch();
                }
            };

    private static boolean matchesTextEventStream(final Accept accept) {
        return StreamSupport.stream(accept.getMediaRanges().spliterator(), false)
                .filter(mr -> !"*".equals(mr.mainType()))
                .anyMatch(mr -> mr.matches(MediaTypes.TEXT_EVENT_STREAM));
    }

    /**
     * Describes {@code /things} SSE route.
     *
     * @return {@code /things} SSE route.
     */
    public Route buildThingsSseRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_THINGS), () ->
                pathEndOrSingleSlash(() ->
                        get(() ->
                                headerValuePF(extractAccept, accept ->
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

    private Route createSseRoute(final DittoHeaders dittoHeaders, final JsonFieldSelector fieldSelector,
            final Optional<String[]> thingIds) {
        final Optional<List<String>> targetThingIds = thingIds.map(Arrays::asList);

        final String connectionCorrelationId = dittoHeaders.getCorrelationId()
                .orElseGet(() -> UUID.randomUUID().toString());
        final JsonSchemaVersion jsonSchemaVersion =
                dittoHeaders.getSchemaVersion().orElse(dittoHeaders.getImplementedSchemaVersion());

        final Source<ServerSentEvent, NotUsed> sseSource =
                Source.<ThingEvent>actorPublisher(EventAndResponsePublisher.props(10))
                        .mapMaterializedValue(actorRef -> {
                            streamingActor.tell(new Connect(actorRef, connectionCorrelationId, STREAMING_TYPE_SSE),
                                    null);
                            streamingActor.tell(
                                    new StartStreaming(StreamingType.EVENTS, connectionCorrelationId,
                                            dittoHeaders.getAuthorizationContext()),
                                    null);
                            return NotUsed.getInstance();
                        })
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
                                de.heikoseeberger.akkasse.scaladsl.model.ServerSentEvent::heartbeat);

        // Javascript example e.g. in Chrome console:
      /*
         var source = new EventSource('http://localhost:8080/api/1/things?ids=org.eclipse.ditto:foo2000&fields=attributes');
         source.addEventListener('message', function (e) {
             console.log(e.data);
         }, false);
       */

        return completeOK(sseSource, EventStreamMarshalling.toEventStream());
    }

    /**
     * Creates a Thing from the passed ThingEvent
     */
    private Thing thingEventToThing(final ThingEvent te) {
        final ThingBuilder.FromScratch tb = Thing.newBuilder().setId(te.getThingId()).setRevision(te.getRevision());
        if (te instanceof ThingCreated) {
            return ((ThingCreated) te).getThing().toBuilder().setRevision(te.getRevision()).build();
        }
        if (te instanceof ThingModified) {
            return ((ThingModified) te).getThing().toBuilder().setRevision(te.getRevision()).build();
        }
        if (te instanceof ThingDeleted) {
            return tb.build();
        }
        if (te instanceof AclModified) {
            return tb.setPermissions(((AclModified) te).getAccessControlList()).build();
        }
        if (te instanceof AclEntryCreated) {
            return tb.setPermissions(((AclEntryCreated) te).getAclEntry()).build();
        }
        if (te instanceof AclEntryModified) {
            return tb.setPermissions(((AclEntryModified) te).getAclEntry()).build();
        }
        if (te instanceof AclEntryDeleted) {
            return tb.build();
        }
        if (te instanceof PolicyIdCreated) {
            return tb.setPolicyId(((PolicyIdCreated) te).getPolicyId()).build();
        }
        if (te instanceof PolicyIdModified) {
            return tb.setPolicyId(((PolicyIdModified) te).getPolicyId()).build();
        }
        if (te instanceof AttributesCreated) {
            return tb.setAttributes(((AttributesCreated) te).getCreatedAttributes()).build();
        }
        if (te instanceof AttributesModified) {
            return tb.setAttributes(((AttributesModified) te).getModifiedAttributes()).build();
        }
        if (te instanceof AttributesDeleted) {
            return tb.build();
        }
        if (te instanceof AttributeCreated) {
            return tb.setAttribute(((AttributeCreated) te).getAttributePointer(),
                    ((AttributeCreated) te).getAttributeValue()).build();
        }
        if (te instanceof AttributeModified) {
            return tb.setAttribute(((AttributeModified) te).getAttributePointer(),
                    ((AttributeModified) te).getAttributeValue()).build();
        }
        if (te instanceof AttributeDeleted) {
            return tb.build();
        }
        if (te instanceof FeaturesCreated) {
            return tb.setFeatures(((FeaturesCreated) te).getFeatures()).build();
        }
        if (te instanceof FeaturesModified) {
            return tb.setFeatures(((FeaturesModified) te).getFeatures()).build();
        }
        if (te instanceof FeaturesDeleted) {
            return tb.build();
        }
        if (te instanceof FeatureCreated) {
            return tb.setFeature(((FeatureCreated) te).getFeature()).build();
        }
        if (te instanceof FeatureModified) {
            return tb.setFeature(((FeatureModified) te).getFeature()).build();
        }
        if (te instanceof FeatureDeleted) {
            return tb.build();
        }
        if (te instanceof FeaturePropertiesCreated) {
            return tb.setFeature(Feature.newBuilder()
                    .properties(((FeaturePropertiesCreated) te).getProperties())
                    .withId(((FeaturePropertiesCreated) te).getFeatureId())
                    .build()).build();
        }
        if (te instanceof FeaturePropertiesModified) {
            return tb.setFeature(Feature.newBuilder()
                    .properties(((FeaturePropertiesModified) te).getProperties())
                    .withId(((FeaturePropertiesModified) te).getFeatureId())
                    .build()).build();
        }
        if (te instanceof FeaturePropertiesDeleted) {
            return tb.build();
        }
        if (te instanceof FeaturePropertyCreated) {
            return tb.setFeatureProperty(((FeaturePropertyCreated) te).getFeatureId(),
                    ((FeaturePropertyCreated) te).getPropertyPointer(),
                    ((FeaturePropertyCreated) te).getPropertyValue()).build();
        }
        if (te instanceof FeaturePropertyModified) {
            return tb.setFeatureProperty(((FeaturePropertyModified) te).getFeatureId(),
                    ((FeaturePropertyModified) te).getPropertyPointer(),
                    ((FeaturePropertyModified) te).getPropertyValue()).build();
        }
        if (te instanceof FeaturePropertyDeleted) {
            return tb.build();
        }

        return null;
    }
}
