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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;


import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.endpoints.utils.UriEncoding;
import org.eclipse.ditto.services.gateway.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.MessageConfig;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /features}.
 */
final class FeaturesRoute extends AbstractRoute {

    static final String PATH_PREFIX = "features";
    static final String PATH_PROPERTIES = "properties";
    static final String PATH_DEFINITION = "definition";

    private final MessagesRoute messagesRoute;

    /**
     * Constructs the {@code /features} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @param messageConfig the MessageConfig.
     * @param claimMessageConfig the MessageConfig for claim messages.
     * @param commandConfig the configuration settings of the Gateway service's incoming command processing.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    FeaturesRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig,
            final MessageConfig messageConfig,
            final MessageConfig claimMessageConfig,
            final HeaderTranslator headerTranslator) {

        super(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator);

        messagesRoute = new MessagesRoute(proxyActor, actorSystem, httpConfig, commandConfig, messageConfig,
                claimMessageConfig, headerTranslator);
    }

    /**
     * Builds the {@code /features} route.
     *
     * @return the {@code /features} route.
     */
    public Route buildFeaturesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders, final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_PREFIX), () ->
                concat(
                        features(ctx, dittoHeaders, thingId),
                        featuresEntry(ctx, dittoHeaders, thingId),
                        featuresEntryDefinition(ctx, dittoHeaders, thingId),
                        featuresEntryProperties(ctx, dittoHeaders, thingId),
                        featuresEntryPropertiesEntry(ctx, dittoHeaders, thingId),
                        featuresEntryInboxOutbox(ctx, dittoHeaders, thingId)
                )
        );
    }

    /*
     * Describes {@code /features} route.
     *
     * @return {@code /features} route.
     */
    private Route features(final RequestContext ctx, final DittoHeaders dittoHeaders, final ThingId thingId) {
        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /features?fields=<fieldsString>
                                parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                        handlePerRequest(ctx, RetrieveFeatures
                                                .of(thingId,
                                                        calculateSelectedFields(fieldsString).orElse(
                                                                null), dittoHeaders))
                                )
                        ),
                        put(() -> // PUT /features
                                extractDataBytes(payloadSource ->
                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                featuresJson -> ModifyFeatures
                                                        .of(thingId, ThingsModelFactory.newFeatures(
                                                                featuresJson), dittoHeaders))
                                )
                        ),
                        delete(() -> // DELETE /features
                                handlePerRequest(ctx, DeleteFeatures.of(thingId, dittoHeaders))
                        )
                )
        );
    }

    /*
     * Describes {@code /features/<featureId>} route.
     *
     * @return {@code /features/<featureId>} route.
     */
    private Route featuresEntry(final RequestContext ctx, final DittoHeaders dittoHeaders, final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), featureId ->
                pathEndOrSingleSlash(() ->
                        concat(
                                get(() -> // GET /features/{featureId}?fields=<fieldsString>
                                        parameterOptional(ThingsParameter.FIELDS.toString(),
                                                fieldsString ->
                                                        handlePerRequest(ctx,
                                                                RetrieveFeature.of(thingId, featureId,
                                                                        calculateSelectedFields(
                                                                                fieldsString).orElse(
                                                                                null), dittoHeaders))
                                        )
                                ),
                                put(() -> // PUT /features/<featureId>
                                        extractDataBytes(payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        featureJson ->
                                                                ModifyFeature.of(thingId,
                                                                        ThingsModelFactory
                                                                                .newFeatureBuilder(
                                                                                        featureJson)
                                                                                .useId(featureId)
                                                                                .build(),
                                                                        dittoHeaders))
                                        )
                                ),
                                delete(() -> // DELETE /features/<featureId>
                                        handlePerRequest(ctx,
                                                DeleteFeature.of(thingId, featureId, dittoHeaders))
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /features/<featureId>/definition} route.
     *
     * @return {@code /features/<featureId>/definition} route.
     */
    private Route featuresEntryDefinition(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), featureId ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_DEFINITION), () ->
                        pathEndOrSingleSlash(() ->
                                concat(
                                        get(() -> // GET /features/{featureId}/definition
                                                handlePerRequest(ctx,
                                                        RetrieveFeatureDefinition.of(thingId, featureId, dittoHeaders))
                                        ),
                                        put(() -> // PUT /features/{featureId}/definition
                                                extractDataBytes(payloadSource ->
                                                        handlePerRequest(ctx, dittoHeaders,
                                                                payloadSource, definitionJson ->
                                                                        ModifyFeatureDefinition.of(thingId, featureId,
                                                                                ThingsModelFactory.
                                                                                        newFeatureDefinition(
                                                                                                definitionJson),
                                                                                dittoHeaders))
                                                )
                                        ),
                                        delete(() -> // DELETE /features/{featureId}/definition
                                                handlePerRequest(ctx,
                                                        DeleteFeatureDefinition.of(thingId, featureId, dittoHeaders))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /features/<featureId>/properties} route.
     *
     * @return {@code /features/<featureId>/properties} route.
     */
    private Route featuresEntryProperties(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), featureId ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_PROPERTIES), () ->
                        pathEndOrSingleSlash(() ->
                                concat(
                                        get(() -> // GET /features/{featureId}/properties?fields=<fieldsString>
                                                parameterOptional(ThingsParameter.FIELDS.toString(),
                                                        fieldsString ->
                                                                handlePerRequest(ctx,
                                                                        RetrieveFeatureProperties
                                                                                .of(thingId, featureId,
                                                                                        calculateSelectedFields(
                                                                                                fieldsString)
                                                                                                .orElse(null),
                                                                                        dittoHeaders))
                                                )
                                        ),
                                        put(() -> // PUT /features/{featureId}/properties
                                                extractDataBytes(payloadSource ->
                                                        handlePerRequest(ctx, dittoHeaders,
                                                                payloadSource, propertiesJson ->
                                                                        ModifyFeatureProperties.of(
                                                                                thingId, featureId,
                                                                                ThingsModelFactory.
                                                                                        newFeatureProperties(
                                                                                                propertiesJson),
                                                                                dittoHeaders))
                                                )
                                        ),
                                        delete(() -> // DELETE /features/{featureId}/properties
                                                handlePerRequest(ctx,
                                                        DeleteFeatureProperties.of(thingId, featureId,
                                                                dittoHeaders))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /features/<featureId>/properties/<propertyJsonPointer>} route.
     *
     * @return {@code /features/<featureId>/properties/<propertyJsonPointer>} route.
     */
    private Route featuresEntryPropertiesEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), featureId ->
                rawPathPrefix(PathMatchers.slash()
                                .concat(PATH_PROPERTIES)
                                .concat(PathMatchers.slash())
                                .concat(PathMatchers.remaining())
                                .map(path -> UriEncoding.decode(path, UriEncoding.EncodingType.RFC3986))
                                .map(path -> "/" + path), // Prepend slash to path to fail request with double slashes
                        jsonPointerString ->
                                concat(
                                        get(() -> // GET /features/{featureId}/properties/<propertyJsonPointerStr>
                                                handlePerRequest(ctx, RetrieveFeatureProperty.of(thingId, featureId,
                                                        JsonFactory.newPointer(jsonPointerString), dittoHeaders))
                                        ),
                                        put(() ->
                                                extractDataBytes(payloadSource ->
                                                        // PUT /features/{featureId}/properties/<propertyJsonPointerStr>
                                                        handlePerRequest(ctx, dittoHeaders,
                                                                payloadSource, propertyJson ->
                                                                        ModifyFeatureProperty.of(
                                                                                thingId,
                                                                                featureId,
                                                                                JsonFactory.newPointer(
                                                                                        jsonPointerString),
                                                                                DittoJsonException.wrapJsonRuntimeException(
                                                                                        () -> JsonFactory.readFrom(
                                                                                                propertyJson)),
                                                                                dittoHeaders))
                                                )
                                        ),
                                        delete(() ->
                                                // DELETE /features/{featureId}/properties/<propertyJsonPointerStr>
                                                handlePerRequest(ctx, DeleteFeatureProperty.of(thingId, featureId,
                                                        JsonFactory.newPointer(jsonPointerString), dittoHeaders))
                                        )
                                )
                )
        );
    }

    /*
     * Describes {@code /features/{featureId}/{inbox|outbox}} route.
     *
     * @return {@code /features/{featureId}/{inbox|outbox}} route.
     */
    private Route featuresEntryInboxOutbox(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), featureId ->
                // POST /features/{featureId}/<inbox|outbox>
                messagesRoute.buildFeaturesInboxOutboxRoute(ctx, dittoHeaders, thingId, featureId)
        );
    }

}
