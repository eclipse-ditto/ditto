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
package org.eclipse.ditto.gateway.service.endpoints.routes.things;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.util.config.endpoints.MessageConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;

import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /features}.
 */
final class FeaturesRoute extends AbstractRoute {

    static final String PATH_FEATURES = "features";
    static final String PATH_PROPERTIES = "properties";
    static final String PATH_DESIRED_PROPERTIES = "desiredProperties";
    static final String PATH_DEFINITION = "definition";

    private final MessagesRoute messagesRoute;

    /**
     * Constructs a {@code FeaturesRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @param messageConfig the MessageConfig.
     * @param claimMessageConfig the MessageConfig for claim messages.
     * @throws NullPointerException if any argument is {@code null}.
     */
    FeaturesRoute(final RouteBaseProperties routeBaseProperties,
            final MessageConfig messageConfig,
            final MessageConfig claimMessageConfig) {

        super(routeBaseProperties);
        messagesRoute = new MessagesRoute(routeBaseProperties, messageConfig, claimMessageConfig);
    }

    /**
     * Builds the {@code /features} route.
     *
     * @return the {@code /features} route.
     */
    public Route buildFeaturesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders, final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_FEATURES), () ->
                concat(
                        features(ctx, dittoHeaders, thingId),
                        featuresEntry(ctx, dittoHeaders, thingId),
                        featuresEntryDefinition(ctx, dittoHeaders, thingId),
                        featuresEntryProperties(ctx, dittoHeaders, thingId),
                        featuresEntryPropertiesEntry(ctx, dittoHeaders, thingId),
                        featuresEntryDesiredProperties(ctx, dittoHeaders, thingId),
                        featuresEntryDesiredPropertiesEntry(ctx, dittoHeaders, thingId),
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
                        // GET /features?fields=<fieldsString>
                        get(() -> parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                        handlePerRequest(ctx, RetrieveFeatures.of(thingId,
                                                calculateSelectedFields(fieldsString).orElse(null),
                                                dittoHeaders))
                                )
                        ),
                        // PUT /features
                        put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                featuresJson -> ModifyFeatures.of(thingId,
                                                        ThingsModelFactory.newFeatures(featuresJson),
                                                        dittoHeaders))
                                )
                        ),
                        // PATCH /features
                        patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                featuresJson -> MergeThing.withFeatures(thingId,
                                                        ThingsModelFactory.newFeatures(featuresJson),
                                                        dittoHeaders))
                                )
                        ),
                        // DELETE /features
                        delete(() -> handlePerRequest(ctx, DeleteFeatures.of(thingId, dittoHeaders)))
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
                                // GET /features/{featureId}?fields=<fieldsString>
                                get(() -> parameterOptional(ThingsParameter.FIELDS.toString(),
                                                fieldsString -> handlePerRequest(ctx, RetrieveFeature.of(thingId, featureId,
                                                        calculateSelectedFields(fieldsString).orElse(null),
                                                        dittoHeaders))
                                        )
                                ),
                                // PUT /features/<featureId>
                                put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                                payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        featureJson ->
                                                                ModifyFeature.of(thingId,
                                                                        ThingsModelFactory
                                                                                .newFeatureBuilder(featureJson)
                                                                                .useId(featureId)
                                                                                .build(),
                                                                        dittoHeaders))
                                        )
                                ),
                                // PATCH /features/<featureId>
                                patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx, dittoHeaders,
                                                payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        featureJson -> MergeThing.withFeature(thingId,
                                                                ThingsModelFactory.newFeatureBuilder(featureJson)
                                                                        .useId(featureId)
                                                                        .build(),
                                                                dittoHeaders))
                                        )
                                ),
                                // DELETE /features/<featureId>
                                delete(() -> handlePerRequest(ctx, DeleteFeature.of(thingId, featureId, dittoHeaders)))
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
                                        // GET /features/{featureId}/definition
                                        get(() -> handlePerRequest(ctx,
                                                RetrieveFeatureDefinition.of(thingId, featureId, dittoHeaders))
                                        ),
                                        // PUT /features/{featureId}/definition
                                        put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders, payloadSource -> handlePerRequest(ctx, dittoHeaders,
                                                                payloadSource, definitionJson ->
                                                                        ModifyFeatureDefinition.of(thingId,
                                                                                featureId,
                                                                                ThingsModelFactory.newFeatureDefinition(
                                                                                        definitionJson),
                                                                                dittoHeaders))
                                                )
                                        ),
                                        // PATCH /features/{featureId}/definition
                                        patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx,
                                                        dittoHeaders, payloadSource -> handlePerRequest(ctx, dittoHeaders,
                                                                payloadSource, definitionJson ->
                                                                        MergeThing.withFeatureDefinition(thingId, featureId,
                                                                                ThingsModelFactory.newFeatureDefinition(
                                                                                        definitionJson), dittoHeaders))
                                                )
                                        ),
                                        // DELETE /features/{featureId}/definition
                                        delete(() -> handlePerRequest(ctx,
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
                                        // GET /features/{featureId}/properties?fields=<fieldsString>
                                        get(() -> parameterOptional(ThingsParameter.FIELDS.toString(),
                                                        fieldsString -> handlePerRequest(ctx,
                                                                RetrieveFeatureProperties.of(thingId, featureId,
                                                                        calculateSelectedFields(fieldsString).orElse(null),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // PUT /features/{featureId}/properties
                                        put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                propertiesJson -> ModifyFeatureProperties.of(thingId,
                                                                        featureId,
                                                                        ThingsModelFactory.newFeatureProperties(propertiesJson),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // PATCH /features/{featureId}/properties
                                        patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx, dittoHeaders,
                                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                propertiesJson -> MergeThing.withFeatureProperties(thingId,
                                                                        featureId,
                                                                        ThingsModelFactory.newFeatureProperties(propertiesJson),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // DELETE /features/{featureId}/properties
                                        delete(() -> handlePerRequest(ctx,
                                                DeleteFeatureProperties.of(thingId, featureId, dittoHeaders))
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
                                        // GET /features/{featureId}/properties/<propertyJsonPointerStr>
                                        get(() -> handlePerRequest(ctx, RetrieveFeatureProperty.of(thingId, featureId,
                                                JsonFactory.newPointer(jsonPointerString), dittoHeaders))
                                        ),
                                        // PUT /features/{featureId}/properties/<propertyJsonPointerStr>
                                        put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                propertyJson -> ModifyFeatureProperty.of(thingId, featureId,
                                                                        JsonFactory.newPointer(jsonPointerString),
                                                                        DittoJsonException.wrapJsonRuntimeException(
                                                                                () -> JsonFactory.readFrom(propertyJson)),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // PATCH /features/{featureId}/properties/<propertyJsonPointerStr>
                                        patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                propertyJson -> MergeThing.withFeatureProperty(thingId,
                                                                        featureId, JsonFactory.newPointer(jsonPointerString),
                                                                        DittoJsonException.wrapJsonRuntimeException(
                                                                                () -> JsonFactory.readFrom(propertyJson)),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // DELETE /features/{featureId}/properties/<propertyJsonPointerStr>
                                        delete(() -> handlePerRequest(ctx, DeleteFeatureProperty.of(thingId, featureId,
                                                JsonFactory.newPointer(jsonPointerString), dittoHeaders))
                                        )
                                )
                )
        );
    }

    /*
     * Describes {@code /features/<featureId>/desiredProperties} route.
     *
     * @return {@code /features/<featureId>/desiredProperties} route.
     */
    private Route featuresEntryDesiredProperties(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), featureId ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_DESIRED_PROPERTIES), () ->
                        pathEndOrSingleSlash(() ->
                                concat(
                                        // GET /features/{featureId}/desiredProperties?fields=<fieldsString>
                                        get(() -> parameterOptional(ThingsParameter.FIELDS.toString(),
                                                        fieldsString -> handlePerRequest(ctx,
                                                                RetrieveFeatureDesiredProperties.of(thingId, featureId,
                                                                        calculateSelectedFields(fieldsString).orElse(null),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // PUT /features/{featureId}/desiredProperties
                                        put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                propertiesJson -> ModifyFeatureDesiredProperties.of(thingId,
                                                                        featureId,
                                                                        ThingsModelFactory.newFeatureProperties(propertiesJson),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // PATCH /features/{featureId}/desiredProperties
                                        patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                propertiesJson -> MergeThing.withFeatureDesiredProperties(
                                                                        thingId,
                                                                        featureId,
                                                                        ThingsModelFactory.newFeatureProperties(propertiesJson),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // DELETE /features/{featureId}/desiredProperties
                                        delete(() -> handlePerRequest(ctx,
                                                DeleteFeatureDesiredProperties.of(thingId, featureId, dittoHeaders))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /features/<featureId>/desiredProperties/<propertyJsonPointer>} route.
     *
     * @return {@code /features/<featureId>/desiredProperties/<propertyJsonPointer>} route.
     */
    private Route featuresEntryDesiredPropertiesEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), featureId ->
                rawPathPrefix(PathMatchers.slash()
                                .concat(PATH_DESIRED_PROPERTIES)
                                .concat(PathMatchers.slash())
                                .concat(PathMatchers.remaining())
                                .map(path -> UriEncoding.decode(path, UriEncoding.EncodingType.RFC3986))
                                .map(path -> "/" + path), // Prepend slash to path to fail request with double slashes
                        jsonPointerString ->
                                concat(
                                        // GET /features/{featureId}/desiredProperties/<desiredPropertyJsonPointerStr>
                                        get(() -> handlePerRequest(ctx,
                                                RetrieveFeatureDesiredProperty.of(thingId, featureId,
                                                        JsonFactory.newPointer(jsonPointerString),
                                                        dittoHeaders))
                                        ),
                                        // PUT /features/{featureId}/desiredProperties/<desiredPropertyJsonPointerStr>
                                        put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                propertyJson -> ModifyFeatureDesiredProperty.of(thingId,
                                                                        featureId,
                                                                        JsonFactory.newPointer(jsonPointerString),
                                                                        DittoJsonException.wrapJsonRuntimeException(
                                                                                () -> JsonFactory.readFrom(propertyJson)),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // PATCH /features/{featureId}/desiredProperties/<desiredPropertyJsonPointerStr>
                                        patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                propertyJson -> MergeThing.withFeatureDesiredProperty(thingId,
                                                                        featureId, JsonFactory.newPointer(jsonPointerString),
                                                                        DittoJsonException.wrapJsonRuntimeException(
                                                                                () -> JsonFactory.readFrom(propertyJson)),
                                                                        dittoHeaders))
                                                )
                                        ),
                                        // DELETE /features/{featureId}/desiredProperties/<desiredPropertyJsonPointerStr>
                                        delete(() -> handlePerRequest(ctx,
                                                DeleteFeatureDesiredProperty.of(thingId, featureId,
                                                        JsonFactory.newPointer(jsonPointerString),
                                                        dittoHeaders))
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
        // POST /features/{featureId}/<inbox|outbox>
        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), featureId ->
                messagesRoute.buildFeaturesInboxOutboxRoute(ctx, dittoHeaders, thingId, featureId)
        );
    }

}
