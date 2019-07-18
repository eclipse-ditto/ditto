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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.actors.HttpRequestActor;
import org.eclipse.ditto.services.gateway.endpoints.actors.HttpRequestActorPropsFactory;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Supervision;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;

/**
 * Base class for Akka HTTP routes.
 */
public abstract class AbstractRoute extends AllDirectives {

    /**
     * Don't configure URL decoding as JsonParseOptions because Akka-Http already decodes the fields-param and we would
     * decode twice.
     */
    private static final JsonParseOptions JSON_FIELD_SELECTOR_PARSE_OPTIONS = JsonFactory.newParseOptionsBuilder()
            .withoutUrlDecoding()
            .build();

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRoute.class);

    protected final ActorRef proxyActor;
    protected final ActorMaterializer materializer;
    protected final ActorSystem actorSystem;

    private final HttpConfig httpConfig;
    private final HeaderTranslator headerTranslator;
    private final HttpRequestActorPropsFactory httpRequestActorPropsFactory;

    /**
     * Constructs the abstract route builder.
     *
     * @param proxyActor an actor selection of the actor handling delegating to persistence.
     * @param actorSystem the ActorSystem to use.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final HeaderTranslator headerTranslator) {

        this.proxyActor = checkNotNull(proxyActor, "delegate actor");
        this.actorSystem = checkNotNull(actorSystem, "actor system");
        this.httpConfig = httpConfig;
        this.headerTranslator = checkNotNull(headerTranslator, "header translator");

        LOGGER.debug("Using headerTranslator <{}>.", headerTranslator);

        materializer = ActorMaterializer.create(ActorMaterializerSettings.create(actorSystem)
                .withSupervisionStrategy((Function<Throwable, Supervision.Directive>) exc -> {
                            if (exc instanceof DittoRuntimeException) {
                                LogUtil.logWithCorrelationId(LOGGER, (DittoRuntimeException) exc, logger ->
                                        logger.debug("DittoRuntimeException during materialization of HTTP request: [{}] {}",
                                                exc.getClass().getSimpleName(), exc.getMessage()));
                            } else {
                                LOGGER.warn("Exception during materialization of HTTP request: {}", exc.getMessage(), exc);
                            }
                            return Supervision.stop(); // in any case, stop!
                        }
                ), actorSystem);

        httpRequestActorPropsFactory =
                AkkaClassLoader.instantiate(actorSystem, HttpRequestActorPropsFactory.class,
                        httpConfig.getActorPropsFactoryFullQualifiedClassname());
    }

    /**
     * Calculates a JsonFieldSelector from the passed {@code fieldsString}.
     *
     * @param fieldsString the fields as string.
     * @return the Optional JsonFieldSelector
     */
    protected static Optional<JsonFieldSelector> calculateSelectedFields(final Optional<String> fieldsString) {
        return fieldsString.map(fs -> JsonFactory.newFieldSelector(fs, JSON_FIELD_SELECTOR_PARSE_OPTIONS));
    }

    protected Route handlePerRequest(final RequestContext ctx, final Command command) {
        return handlePerRequest(ctx, command.getDittoHeaders(), Source.empty(), emptyRequestBody -> command);
    }

    protected Route handlePerRequest(final RequestContext ctx, final Command command,
            final Function<JsonValue, JsonValue> responseTransformFunction) {

        return handlePerRequest(ctx, command.getDittoHeaders(), Source.empty(),
                emptyRequestBody -> command, responseTransformFunction);
    }

    protected Route handlePerRequest(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final Source<ByteString, ?> payloadSource,
            final Function<String, Command> requestJsonToCommandFunction) {

        return handlePerRequest(ctx, dittoHeaders, payloadSource, requestJsonToCommandFunction, null);
    }

    protected Route handlePerRequest(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final Source<ByteString, ?> payloadSource,
            final Function<String, Command> requestJsonToCommandFunction,
            final Function<JsonValue, JsonValue> responseTransformFunction) {

        final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();

        payloadSource
                .fold(ByteString.empty(), ByteString::concat)
                .map(ByteString::utf8String)
                .map(requestJsonToCommandFunction)
                .map(command -> {
                    final JsonSchemaVersion schemaVersion =
                            dittoHeaders.getSchemaVersion().orElse(command.getImplementedSchemaVersion());
                    return command.implementsSchemaVersion(schemaVersion) ? command
                            : CommandNotSupportedException.newBuilder(schemaVersion.toInt())
                            .dittoHeaders(dittoHeaders)
                            .build();
                })
                .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                        HttpRequestActor.COMPLETE_MESSAGE))
                .run(materializer);

        // optional step: transform the response entity:
        if (responseTransformFunction != null) {
            final CompletableFuture<HttpResponse> transformedResponse = httpResponseFuture.thenApply(response -> {
                final boolean isSuccessfulResponse = response.status().isSuccess();
                // we have to check if response is empty, because otherwise we'll get an IOException when trying to
                // read it
                final boolean isEmptyResponse = response.entity().isKnownEmpty();
                if (isSuccessfulResponse && !isEmptyResponse) {
                    final InputStream inputStream = response.entity()
                            .getDataBytes()
                            .fold(ByteString.empty(), ByteString::concat)
                            .runWith(StreamConverters.asInputStream(), materializer);
                    final JsonValue jsonValue = JsonFactory.readFrom(new InputStreamReader(inputStream));
                    try {
                        final JsonValue transformed = responseTransformFunction.apply(jsonValue);
                        return response.withEntity(ContentTypes.APPLICATION_JSON, transformed.toString());
                    } catch (final Exception e) {
                        throw JsonParseException.newBuilder()
                                .message("Could not transform JSON: " + e.getMessage())
                                .cause(e)
                                .build();
                    }
                } else {
                    // for non-successful and empty responses, don't transform the response body
                    return response;
                }
            });
            return completeWithFuture(preprocessResponse(transformedResponse));
        } else {
            return completeWithFuture(preprocessResponse(httpResponseFuture));
        }
    }

    /**
     * Processes the {@link HttpResponse} by consuming the CompletionStage and returning another (or the same)
     * CompletionStage. May be used to modify the HttpResponse before it is sent back to client.
     */
    protected CompletionStage<HttpResponse> preprocessResponse(final CompletionStage<HttpResponse> responseStage) {
        return responseStage; // default: do nothing
    }

    /**
     * Create HTTP request actor by the dynamically loaded props factory.
     *
     * @param ctx the request context.
     * @param httpResponseFuture the promise of a response to be fulfilled by the HTTP request actor.
     * @return reference of the created actor.
     */
    protected ActorRef createHttpPerRequestActor(final RequestContext ctx,
            final CompletableFuture<HttpResponse> httpResponseFuture) {

        final Props props = httpRequestActorPropsFactory.props(
                proxyActor, headerTranslator, ctx.getRequest(), httpResponseFuture, httpConfig);

        return actorSystem.actorOf(props);
    }

}
