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
package org.eclipse.ditto.services.gateway.endpoints.routes.stats;

import static akka.http.javadsl.server.Directives.completeWithFuture;
import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.pathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.services.gateway.endpoints.directives.DevOpsBasicAuthenticationDirective.REALM_DEVOPS;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.HttpRequestActor;
import org.eclipse.ditto.services.gateway.endpoints.config.DevOpsConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers;
import org.eclipse.ditto.services.gateway.endpoints.directives.DevOpsBasicAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SudoCountThings;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;

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
    private static final String DETAILS_PATH = "details";

    private final DevOpsConfig devOpsConfig;

    /**
     * Constructs the {@code /stats} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the akka actor system.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param devOpsConfig the configuration settings of the Gateway service's DevOps endpoint.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public StatsRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final DevOpsConfig devOpsConfig,
            final HeaderTranslator headerTranslator) {

        super(proxyActor, actorSystem, httpConfig, headerTranslator);
        this.devOpsConfig = devOpsConfig;
    }

    /*
     * Describes {@code /stats} route.
     *
     * @param correlationId the correlation id
     * @return route for /stats resource.
     */
    public Route buildStatsRoute(final CharSequence correlationId) {
        return Directives.rawPathPrefix(CustomPathMatchers.mergeDoubleSlashes().concat(STATISTICS_PATH_PREFIX),
                () -> // /stats/*
                        extractRequestContext(ctx ->
                                get(() -> // GET
                                        buildSubRoutes(ctx, correlationId)
                                )
                        )
        );
    }

    private Route buildSubRoutes(final RequestContext ctx, final CharSequence correlationId) {
        return route(
                pathPrefix(THINGS_PATH, () -> // /stats/things
                        route(
                                path(DETAILS_PATH, () -> {
                                    final DevOpsBasicAuthenticationDirective devOpsBasicAuthenticationDirective =
                                            DevOpsBasicAuthenticationDirective.getInstance(devOpsConfig);
                                    return devOpsBasicAuthenticationDirective.authenticateDevOpsBasic(REALM_DEVOPS,
                                            handleDevOpsPerRequest(ctx,
                                                    RetrieveStatisticsDetails.of(
                                                            buildDevOpsDittoHeaders(correlationId))));
                                }),
                                pathEndOrSingleSlash(() ->
                                        handleDevOpsPerRequest(ctx,
                                                RetrieveStatistics.of(
                                                        buildDevOpsDittoHeaders(correlationId)))
                                )
                        )
                ),
                path(SEARCH_PATH, () -> // /stats/search
                        handleSudoCountThingsPerRequest(ctx,
                                SudoCountThings.of(
                                        buildDevOpsDittoHeaders(correlationId)))
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

    private static DittoHeaders buildDevOpsDittoHeaders(final CharSequence correlationId) {
        return DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_2)
                .correlationId(correlationId)
                .build();
    }

}
