/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.pekko.http.javadsl.model.FormData;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;

/**
 * Builder for creating Akka HTTP routes for {@code /search/things}.
 */
public final class ThingSearchRouteTest extends EndpointTestBase {

    private static final Function<Jsonifiable<?>, Optional<Object>> DUMMY_RESPONSE_PROVIDER =
            m -> DummyThingModifyCommandResponse.echo((Jsonifiable<JsonValue>) () -> {
                if (m instanceof WithDittoHeaders withDittoHeaders) {
                    return JsonObject.newBuilder()
                            .set("payload", m.toJson())
                            .set("headers", withDittoHeaders.getDittoHeaders()
                                    .toBuilder()
                                    .removeHeader("correlation-id")
                                    .build()
                                    .toJson()
                            )
                            .build();
                } else {
                    return m.toJson();
                }
            });

    private ThingSearchRoute thingsSearchRoute;
    private TestRoute underTest;

    @Override
    protected Function<Jsonifiable<?>, Optional<Object>> getResponseProvider() {
        return DUMMY_RESPONSE_PROVIDER;
    }

    @Before
    public void setUp() {
        thingsSearchRoute = new ThingSearchRoute(routeBaseProperties);
        final Route route = extractRequestContext(ctx -> thingsSearchRoute.buildSearchRoute(ctx, dittoHeaders));
        underTest = testRoute(handleExceptions(() -> route));
    }

    @Test
    public void postSearchThingsShouldGetParametersFromBody() {
        final var formData = FormData.create(
                List.of(
                        new Pair<>("filter", "and(like(definition,\"*test*\"))"),
                        new Pair<>("option", "sort(+thingId,-attributes/type)"),
                        new Pair<>("option", "limit(0,5)"),
                        new Pair<>("namespaces", "org.eclipse.ditto,foo.bar")
                ));
        final var result = underTest.run(HttpRequest.POST("/search/things")
                .withEntity(formData.toEntity()));

        result.assertStatusCode(StatusCodes.OK);
        result.assertEntity(JsonObject.newBuilder()
                .set("payload", JsonObject.newBuilder()
                        .set("type", "thing-search.commands:queryThings")
                        .set("filter", "and(and(like(definition,\"*test*\")))")
                        .set("options", JsonArray.newBuilder()
                                .add("limit(0,5)")
                                .add("sort(+thingId,-attributes/type)")
                                .build()
                        )
                        .set("namespaces", JsonArray.newBuilder()
                                .add("foo.bar")
                                .add("org.eclipse.ditto")
                                .build()
                        )
                        .build()
                )
                .set("headers", JsonObject.empty())
                .build()
                .toString()
        );
    }

    @Test
    public void postSearchThingsShouldGetMultipleFiltersAndConditionsFromBody() {
        final var formData = FormData.create(
                List.of(
                        new Pair<>("filter", "like(definition,\"*test1*\")"),
                        new Pair<>("filter", "like(definition,\"*test2*\")"),
                        new Pair<>("condition", "like(definition,\"*test1*\")"),
                        new Pair<>("condition", "like(definition,\"*test2*\")")
                ));
        final var result = underTest.run(HttpRequest.POST("/search/things")
                .withEntity(formData.toEntity()));

        result.assertStatusCode(StatusCodes.OK);
        result.assertEntity(JsonObject.newBuilder()
                .set("payload", JsonObject.newBuilder()
                        .set("type", "thing-search.commands:queryThings")
                        .set("filter", "and(like(definition,\"*test2*\"),like(definition,\"*test1*\"))")
                        .build()
                )
                .set("headers", JsonObject.newBuilder()
                        .set("condition", "and(like(definition,\"*test2*\"),like(definition,\"*test1*\"))")
                        .build()
                ).build().toString());
    }

    @Test
    public void searchThingsShouldGetParametersFromUrl() {

        final var result = underTest.run(HttpRequest.GET(
                "/search/things?" +
                        "namespaces=org.eclipse.ditto&" +
                        "fields=thingId&" +
                        "option=sort(%2Bfeature/property,-attributes/type,%2BthingId),size(2),cursor(nextCursor)")
        );

        result.assertStatusCode(StatusCodes.OK);
        result.assertEntity(JsonObject.newBuilder()
                .set("payload", JsonObject.newBuilder()
                        .set("type", "thing-search.commands:queryThings")
                        .set("options", JsonArray.newBuilder()
                                .add("sort(+feature/property,-attributes/type,+thingId)")
                                .add("size(2)")
                                .add("cursor(nextCursor)")
                                .build()
                        )
                        .set("fields", "/thingId")
                        .set("namespaces", JsonArray.newBuilder()
                                .add("org.eclipse.ditto")
                                .build()
                        )
                        .build()
                )
                .set("headers", JsonObject.empty())
                .build()
                .toString()
        );
    }

    @Test
    public void searchThingsShouldGetFilter() {
        final var form = "and(and(like(definition,\"*test*\")))";

        final var result = underTest.run(
                HttpRequest.GET(
                        "/search/things?filter=and(like(definition,\"*test*\"))&option=sort(+thingId)&option=limit(0,5)&namespaces=org.eclipse.ditto,foo.bar"));

        result.assertStatusCode(StatusCodes.OK);

        assertThat(JsonObject.of(result.entityString()))
                .contains(
                        JsonPointer.of("payload/filter"),
                        form
                );
    }

    @Test
    public void countThingsShouldGetFilterFromBody() {
        final var formData = FormData.create(
                List.of(
                        new Pair<>("filter", "and(like(definition,\"*test*\"))"),
                        new Pair<>("option", "sort(+thingId)"),
                        new Pair<>("option", "limit(0,5)"),
                        new Pair<>("namespaces", "org.eclipse.ditto,foo.bar")
                ));
        final var result = underTest.run(HttpRequest.POST("/search/things/count")
                .withEntity(formData.toEntity()));

        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(
                        JsonPointer.of("payload/filter"),
                        "and(and(like(definition,\"*test*\")))"
                );
    }

    @Test
    public void countThingsShouldAssertBadRequest() {
        final var result = underTest.run(HttpRequest.POST("/search/things/count"));
        result.assertStatusCode(StatusCodes.UNSUPPORTED_MEDIA_TYPE);
    }
}
