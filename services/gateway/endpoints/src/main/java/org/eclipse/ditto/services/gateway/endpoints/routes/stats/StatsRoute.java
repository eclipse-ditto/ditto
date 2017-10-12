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
package org.eclipse.ditto.services.gateway.endpoints.routes.stats;

import static akka.http.javadsl.server.Directives.completeWithFuture;
import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.route;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.gateway.endpoints.HttpRequestActor;
import org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Builder for Akka HTTP route for providing statistics.
 */
public final class StatsRoute extends AbstractRoute {

    static final String STATISTICS_PATH_PREFIX = "stats";
    static final String THINGS_PATH = "things";
    static final String SEARCH_PATH = "search";

    /**
     * Constructs the {@code /stats} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the akka actor system.
     */
    public StatsRoute(final ActorRef proxyActor, final ActorSystem actorSystem) {
        super(proxyActor, actorSystem);
    }

    /*
     * Describes {@code /stats} route.
     *
     * @param correlationId the correlation id
     * @return route for /stats resource.
     */
    public Route buildStatsRoute(final String correlationId) {
        return Directives.rawPathPrefix(CustomPathMatchers.mergeDoubleSlashes().concat(STATISTICS_PATH_PREFIX),
                () -> // /stats/*
                extractRequestContext(ctx ->
                        get(() -> // GET
                                buildSubRoutes(ctx, correlationId)
                        )
                )
        );
    }

    private Route buildSubRoutes(final RequestContext ctx, final String correlationId) {
        return route(
                path(THINGS_PATH, () -> // /stats/things
                        pathEndOrSingleSlash(() ->
                                handleDevOpsPerRequest(ctx,
                                        RetrieveStatistics.of(
                                                buildSudoDittoHeaders(correlationId)))
                        )
                ),
                path(SEARCH_PATH, () -> // /stats/search
                        pathEndOrSingleSlash(() ->
                                handleSudoCountThingsPerRequest(ctx,
                                        SudoCountThings.of(
                                                buildSudoDittoHeaders(correlationId)))
                        )
                )
        );
    }

    private Route handleDevOpsPerRequest(final RequestContext ctx, final DevOpsCommand command) {
        return handleDevOpsPerRequest(ctx, Source.empty(), emptyRequestBody -> command);
    }

    private Route handleDevOpsPerRequest(final RequestContext ctx,
            final Source<ByteString, ?> payloadSource,
            final Function<String, DevOpsCommand> requestJsonToCommandFunction) {
        final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();

        payloadSource
                .fold(ByteString.empty(), ByteString::concat)
                .map(ByteString::utf8String)
                .map(requestJsonToCommandFunction)
                .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                        HttpRequestActor.COMPLETE_MESSAGE))
                .run(materializer);

        return completeWithFuture(httpResponseFuture);
    }

    private Route handleSudoCountThingsPerRequest(final RequestContext ctx, final SudoCountThings command) {
        final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();

        Source.single(command)
                .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                        HttpRequestActor.COMPLETE_MESSAGE))
                .run(materializer);

        final CompletionStage<HttpResponse> allThingsCountHttpResponse = Source.fromCompletionStage(httpResponseFuture)
                .flatMapConcat(httpResponse -> httpResponse.entity().getDataBytes())
                .fold(ByteString.empty(), ByteString::concat)
                .map(ByteString::utf8String)
                .map(Integer::valueOf)
                .map(count -> JsonObject.newBuilder().set("allThingsCount", count).build())
                .map(jsonObject -> HttpResponse.create()
                        .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(jsonObject.toString()))
                        .withStatus(HttpStatusCode.OK.toInt()))
                .runWith(Sink.head(), materializer);

        return completeWithFuture(allThingsCountHttpResponse);
    }

    private static DittoHeaders buildSudoDittoHeaders(final CharSequence correlationId) {
        return DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_1)
                .correlationId(correlationId)
                .build();
    }

}
