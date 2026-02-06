/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.routes.wot;

import java.util.List;
import java.util.Map;

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpCharsets;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.util.config.endpoints.WotDirectoryConfig;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.model.Action;
import org.eclipse.ditto.wot.model.ActionFormElement;
import org.eclipse.ditto.wot.model.ActionForms;
import org.eclipse.ditto.wot.model.Actions;
import org.eclipse.ditto.wot.model.AtContext;
import org.eclipse.ditto.wot.model.AtType;
import org.eclipse.ditto.wot.model.Description;
import org.eclipse.ditto.wot.model.FormElementAdditionalResponse;
import org.eclipse.ditto.wot.model.FormElementAdditionalResponses;
import org.eclipse.ditto.wot.model.FormElementExpectedResponse;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.PropertyFormElement;
import org.eclipse.ditto.wot.model.PropertyForms;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.SinglePropertyFormElementOp;
import org.eclipse.ditto.wot.model.SingleUriAtContext;
import org.eclipse.ditto.wot.model.ThingDescription;
import org.eclipse.ditto.wot.model.Title;
import org.eclipse.ditto.wot.model.UriVariables;
import org.eclipse.ditto.wot.model.Version;

/**
 * Route providing a WoT (Web of Things) "Thing Directory" at specified endpoint {@code /.well-known/wot}.
 *
 * @since 3.9.0
 */
public final class WotDiscoveryThingDirectoryRoute extends AbstractRoute {

    public static final String PATH_WELLKNOWN_WOT = ".well-known";
    public static final String PATH_WOT = "wot";

    private static final org.apache.pekko.http.javadsl.model.ContentType.NonBinary CONTENT_TYPE_TD_JSON =
            ContentTypes.create(
                    MediaTypes.applicationWithFixedCharset("td+json", HttpCharsets.UTF_8));

    private final HttpResponse thingDirectoryResponse;

    /**
     * Constructs a {@code WotDiscoveryThingDirectoryRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if {@code routeBaseProperties} is {@code null}.
     */
    public WotDiscoveryThingDirectoryRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);
        final var wotDirectoryConfig = routeBaseProperties.getGatewayConfig().getWotDirectoryConfig();
        final var thingDirectoryDescription = buildThingDirectoryDescription(wotDirectoryConfig);
        thingDirectoryResponse = HttpResponse.create()
                .withStatus(StatusCodes.OK)
                .withEntity(CONTENT_TYPE_TD_JSON, thingDirectoryDescription.toJson().toString());
    }

    /**
     * Builds the route for the WoT Discovery Thing Directory endpoint.
     *
     * @param ctx the request context.
     * @param dittoHeaders the Ditto headers.
     * @return the route.
     */
    public Route buildRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return pathPrefix(PATH_WELLKNOWN_WOT, () -> pathPrefix(PATH_WOT, () ->
                wot()
        ));
    }

    private Route wot() {
        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /.well-known/wot
                                complete(thingDirectoryResponse)
                        ),
                        head(() -> // HEAD /.well-known/wot
                                complete(thingDirectoryResponse.withEntity(
                                        CONTENT_TYPE_TD_JSON, ""))
                        )
                )
        );
    }

    static ThingDescription buildThingDirectoryDescription(final WotDirectoryConfig wotDirectoryConfig) {
        return ThingDescription.newBuilder()
                .setAtContext(AtContext.newMultipleAtContext(List.of(
                        SingleUriAtContext.W3ORG_2022_WOT_TD_V11,
                        SingleUriAtContext.W3ORG_2022_WOT_DISCOVERY
                )))
                .setAtType(AtType.newSingleAtType("ThingDirectory"))
                .setId(IRI.of("urn:ditto:wot:thing-directory"))
                .setTitle(Title.of("Thing Description Directory (TDD) of Eclipse Ditto"))
                .setVersion(Version.newBuilder()
                        .setModel("1.0.0")
                        .setInstance("1.0.0")
                        .build()
                )
                .setBase(IRI.of(wotDirectoryConfig.getBasePrefix()))
                .setAll(wotDirectoryConfig.getJsonTemplate())
                .setProperties(Properties.of(Map.of("things", Property.newBuilder("things")
                        .setDescription(Description.of("Retrieve all Thing Descriptions"))
                        .setSchema(SingleDataSchema.newArraySchemaBuilder()
                                .setItems(SingleDataSchema.newObjectSchemaBuilder().build())
                                .build()
                        )
                        .setUriVariables(UriVariables.of(Map.of(
                                "offset", SingleDataSchema.newIntegerSchemaBuilder()
                                        .setTitle(Title.of("Offset"))
                                        .setDescription(Description.of(
                                                "Number of Thing Descriptions to skip for pagination"))
                                        .setMinimum(0)
                                        .build(),
                                "limit", SingleDataSchema.newIntegerSchemaBuilder()
                                        .setTitle(Title.of("Limit"))
                                        .setDescription(Description.of(
                                                "Maximum number of Thing Descriptions to return"))
                                        .setMinimum(1)
                                        .build(),
                                "format", SingleDataSchema.newStringSchemaBuilder()
                                        .setTitle(Title.of("Format"))
                                        .setDescription(Description.of(
                                                "Response format: 'array' returns a plain JSON array " +
                                                        "of Thing Descriptions"))
                                        .setEnum(List.of(JsonValue.of("array")))
                                        .build()
                        )))
                        .setReadOnly(true)
                        .setForms(PropertyForms.of(List.of(
                                PropertyFormElement.newBuilder()
                                        .setHref(IRI.of("api/2/things{?offset,limit,format}"))
                                        .setOp(SinglePropertyFormElementOp.READPROPERTY)
                                        .setContentType(ContentType.APPLICATION_JSON.getValue())
                                        .set("htv:statusCode", 200)
                                        .setAdditionalResponses(
                                                FormElementAdditionalResponses.of(List.of(
                                                        FormElementAdditionalResponse.newBuilder()
                                                                .setContentType(
                                                                        ContentType.APPLICATION_TD_JSON
                                                                                .getValue())
                                                                .set("htv:statusCode", 200)
                                                                .setSuccess(true)
                                                                .build()
                                                )))
                                        .build()
                        )))
                        .build()
                )))
                .setActions(Actions.of(Map.of("retrieveThing", Action.newBuilder("retrieveThing")
                                .setDescription(Description.of("Retrieve a Thing Description by its Thing ID."))
                                .setUriVariables(UriVariables.of(Map.of(
                                        "thingId",
                                        SingleDataSchema.newStringSchemaBuilder()
                                                .setAtType(AtType.newSingleAtType("ThingID"))
                                                .setTitle(Title.of("Thing ID"))
                                                .setFormat("iri-reference")
                                                .build()
                                )))
                                .setOutput(SingleDataSchema.newObjectSchemaBuilder()
                                        .setDescription(Description.of(
                                                "The schema is implied by the content type"))
                                        .build()
                                )
                                .setSafe(true)
                                .setIdempotent(true)
                                .setForms(ActionForms.of(List.of(
                                        ActionFormElement.newBuilder()
                                                .setHref(IRI.of("api/2/things/{thingId}"))
                                                .set("htv:methodName", "GET")
                                                .set("htv:headers", JsonArray.of(
                                                        JsonFactory.newObjectBuilder()
                                                                .set("htv:fieldName", "Accept")
                                                                .set("htv:fieldValue",
                                                                        ContentType.APPLICATION_TD_JSON
                                                                                .getValue())
                                                                .build()
                                                ))
                                                .setExpectedResponse(
                                                        FormElementExpectedResponse.newBuilder()
                                                                .setContentType(
                                                                        ContentType.APPLICATION_TD_JSON
                                                                                .getValue())
                                                                .set("htv:statusCode", 200)
                                                                .build()
                                                )
                                                .setAdditionalResponses(
                                                        FormElementAdditionalResponses.of(List.of(
                                                                FormElementAdditionalResponse.newBuilder()
                                                                        .setContentType(
                                                                                ContentType.APPLICATION_JSON
                                                                                        .getValue()
                                                                        )
                                                                        .set("htv:statusCode", 404)
                                                                        .setSuccess(false)
                                                                        .build()
                                                        )))
                                                .build()
                                )))
                                .build()
                        )
                ))
                .build();
    }

}
