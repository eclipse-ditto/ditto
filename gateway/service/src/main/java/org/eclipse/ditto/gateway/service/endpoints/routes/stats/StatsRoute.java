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
package org.eclipse.ditto.gateway.service.endpoints.routes.stats;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevOpsOAuth2AuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.gateway.service.endpoints.actors.AbstractHttpRequestActor;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoCountThings;
import org.eclipse.ditto.base.api.devops.signals.commands.DevOpsCommand;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatistics;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.stream.javadsl.Keep;
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

    private static final String ENTITY_PARAM = "entity";
    private static final String NAMESPACE_PARAM = "namespace";

    private final DevopsAuthenticationDirective devOpsAuthenticationDirective;

    /**
     * Constructs a {@code StatsRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @param devOpsAuthenticationDirective the authentication handler for the Devops directive.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public StatsRoute(final RouteBaseProperties routeBaseProperties,
            final DevopsAuthenticationDirective devOpsAuthenticationDirective) {

        super(routeBaseProperties);
        this.devOpsAuthenticationDirective =
                ConditionChecker.checkNotNull(devOpsAuthenticationDirective, "devOpsAuthenticationDirective");
    }

    /*
     * Describes {@code /stats} route.
     *
     * @param correlationId the correlation id
     * @return route for /stats resource.
     */
    public Route buildStatsRoute(final CharSequence correlationId) {
        return Directives.rawPathPrefix(PathMatchers.slash().concat(STATISTICS_PATH_PREFIX),
                () -> // /stats/*
                        extractRequestContext(ctx ->
                                get(() -> // GET
                                        buildSubRoutes(ctx, correlationId)
                                )
                        )
        );
    }

    private Route buildSubRoutes(final RequestContext ctx, final CharSequence correlationId) {
        return concat(
                pathPrefix(THINGS_PATH, () -> // /stats/things
                        concat(
                                path(DETAILS_PATH, () -> buildDetailsRoute(ctx, correlationId)),
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

    private Route buildDetailsRoute(final RequestContext ctx, final CharSequence correlationId) {
        return devOpsAuthenticationDirective.authenticateDevOps(DevOpsOAuth2AuthenticationDirective.REALM_DEVOPS,
                parameterList(ENTITY_PARAM, shardRegions ->
                        parameterList(NAMESPACE_PARAM, namespaces ->
                                handleDevOpsPerRequest(ctx, RetrieveStatisticsDetails.of(shardRegions, namespaces,
                                        buildDevOpsDittoHeaders(correlationId)))
                        )
                ));
    }

    private Route handleDevOpsPerRequest(final RequestContext ctx, final DevOpsCommand<?> command) {
        return handleDevOpsPerRequest(ctx, Source.empty(), emptyRequestBody -> command);
    }

    private Route handleDevOpsPerRequest(final RequestContext ctx,
            final Source<ByteString, ?> payloadSource,
            final Function<String, DevOpsCommand<?>> requestJsonToCommandFunction) {
        final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();

        runWithSupervisionStrategy(payloadSource
                .fold(ByteString.emptyByteString(), ByteString::concat)
                .map(ByteString::utf8String)
                .map(requestJsonToCommandFunction)
                .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                        AbstractHttpRequestActor.COMPLETE_MESSAGE))
        );

        return completeWithFuture(httpResponseFuture);
    }

    private Route handleSudoCountThingsPerRequest(final RequestContext ctx, final SudoCountThings command) {
        final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();

        runWithSupervisionStrategy(Source.single(command)
                .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                        AbstractHttpRequestActor.COMPLETE_MESSAGE))
        );

        final CompletionStage<HttpResponse> allThingsCountHttpResponse = runWithSupervisionStrategy(
                Source.fromCompletionStage(httpResponseFuture)
                        .flatMapConcat(httpResponse -> httpResponse.entity().getDataBytes())
                        .fold(ByteString.emptyByteString(), ByteString::concat)
                        .map(ByteString::utf8String)
                        .map(Integer::valueOf)
                        .map(count -> JsonObject.newBuilder().set("allThingsCount", count).build())
                        .map(jsonObject -> HttpResponse.create()
                                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(jsonObject.toString()))
                                .withStatus(HttpStatus.OK.getCode()))
                        .toMat(Sink.head(), Keep.right())
        );

        return completeWithFuture(allThingsCountHttpResponse);
    }

    private static DittoHeaders buildDevOpsDittoHeaders(final CharSequence correlationId) {
        return DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_2)
                .correlationId(correlationId)
                .build();
    }

}
